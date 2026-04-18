package com.syschimp.glucoripper.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * One parsed entry from the Glucose Measurement characteristic (0x2A18).
 *
 * Concentration is normalized to mg/dL since that's what Contour Next One reports.
 * The raw SFLOAT carries either kg/L (mass conc.) or mol/L; we convert kg/L → mg/dL.
 */
data class GlucoseRecord(
    val sequenceNumber: Int,
    val time: Instant,
    /** mg/dL, or null if the reading carried no concentration value. */
    val mgPerDl: Double?,
    val sampleType: SampleType?,
    val sampleLocation: SampleLocation?,
    val sensorStatus: Int,
    val hasContextFollows: Boolean = false,
    val mealRelation: MealRelation? = null,
) {
    enum class MealRelation(val code: Int) {
        PREPRANDIAL(1),
        POSTPRANDIAL(2),
        FASTING(3),
        CASUAL(4),
        BEDTIME(5);
        companion object { fun of(code: Int) = entries.firstOrNull { it.code == code } }
    }


    enum class SampleType(val code: Int) {
        CAPILLARY_WHOLE_BLOOD(1),
        CAPILLARY_PLASMA(2),
        VENOUS_WHOLE_BLOOD(3),
        VENOUS_PLASMA(4),
        ARTERIAL_WHOLE_BLOOD(5),
        ARTERIAL_PLASMA(6),
        UNDETERMINED_WHOLE_BLOOD(7),
        UNDETERMINED_PLASMA(8),
        INTERSTITIAL_FLUID(9),
        CONTROL_SOLUTION(10);
        companion object { fun of(code: Int) = entries.firstOrNull { it.code == code } }
    }

    enum class SampleLocation(val code: Int) {
        FINGER(1),
        ALTERNATE_SITE_TEST(2),
        EARLOBE(3),
        CONTROL_SOLUTION(4),
        NOT_AVAILABLE(15);
        companion object { fun of(code: Int) = entries.firstOrNull { it.code == code } }
    }

    val isControlSolution: Boolean
        get() = sampleType == SampleType.CONTROL_SOLUTION ||
                sampleLocation == SampleLocation.CONTROL_SOLUTION

    /**
     * Bit 0 of Sensor Status Annunciation = "Device battery low at time of measurement"
     * per the Bluetooth Glucose Service spec.
     */
    val wasDeviceBatteryLow: Boolean
        get() = (sensorStatus and 0x0001) != 0
}

object GlucoseMeasurementParser {
    private const val FLAG_TIME_OFFSET = 0x01
    private const val FLAG_CONCENTRATION = 0x02
    private const val FLAG_UNITS_MOL_PER_L = 0x04
    private const val FLAG_SENSOR_STATUS = 0x08
    private const val FLAG_CONTEXT_FOLLOWS = 0x10

    /** @throws IllegalArgumentException if the payload is malformed. */
    fun parse(payload: ByteArray): GlucoseRecord {
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.remaining() >= 10) { "Glucose Measurement too short: ${payload.size}" }

        val flags = buf.get().toInt() and 0xFF
        val sequence = buf.short.toInt() and 0xFFFF

        val year = buf.short.toInt() and 0xFFFF
        val month = buf.get().toInt() and 0xFF
        val day = buf.get().toInt() and 0xFF
        val hour = buf.get().toInt() and 0xFF
        val minute = buf.get().toInt() and 0xFF
        val second = buf.get().toInt() and 0xFF

        var offsetMinutes = 0
        if (flags and FLAG_TIME_OFFSET != 0) {
            require(buf.remaining() >= 2) { "Missing time offset" }
            offsetMinutes = buf.short.toInt()
        }

        // Base time is in the meter's local time. We convert to UTC instant by
        // subtracting the offset-from-UTC the meter is reporting (which is 0 if absent).
        val base = LocalDateTime.of(
            year.coerceIn(1582, 9999),
            month.coerceIn(1, 12),
            day.coerceIn(1, 31),
            hour.coerceIn(0, 23),
            minute.coerceIn(0, 59),
            second.coerceIn(0, 59),
        )
        val offset = ZoneOffset.ofTotalSeconds(offsetMinutes * 60)
        val instant = base.toInstant(offset)

        var mgPerDl: Double? = null
        var sampleType: GlucoseRecord.SampleType? = null
        var sampleLocation: GlucoseRecord.SampleLocation? = null
        if (flags and FLAG_CONCENTRATION != 0) {
            require(buf.remaining() >= 3) { "Missing concentration + type/location" }
            val raw = decodeSfloat(buf.short)
            mgPerDl = if (flags and FLAG_UNITS_MOL_PER_L != 0) {
                // mol/L → mg/dL (assume glucose, 180.156 g/mol; mg/dL = mmol/L × 18.0156)
                raw * 1000.0 * 18.0156
            } else {
                // kg/L → mg/dL; kg/L × 100_000 = mg/dL
                raw * 100_000.0
            }
            val typeLoc = buf.get().toInt() and 0xFF
            sampleType = GlucoseRecord.SampleType.of(typeLoc and 0x0F)
            sampleLocation = GlucoseRecord.SampleLocation.of((typeLoc ushr 4) and 0x0F)
        }

        var sensorStatus = 0
        if (flags and FLAG_SENSOR_STATUS != 0 && buf.remaining() >= 2) {
            sensorStatus = buf.short.toInt() and 0xFFFF
        }

        return GlucoseRecord(
            sequenceNumber = sequence,
            time = instant,
            mgPerDl = mgPerDl,
            sampleType = sampleType,
            sampleLocation = sampleLocation,
            sensorStatus = sensorStatus,
            hasContextFollows = (flags and FLAG_CONTEXT_FOLLOWS) != 0,
        )
    }

    data class Context(val sequenceNumber: Int, val mealRelation: GlucoseRecord.MealRelation?)

    private const val CTX_FLAG_CARBS = 0x01
    private const val CTX_FLAG_MEAL = 0x02
    private const val CTX_FLAG_TESTER_HEALTH = 0x04
    private const val CTX_FLAG_EXERCISE = 0x08
    private const val CTX_FLAG_MEDICATION = 0x10
    private const val CTX_FLAG_HBA1C = 0x40
    private const val CTX_FLAG_EXTENDED = 0x80

    /** Parse a Glucose Measurement Context (0x2A34) notification. */
    fun parseContext(payload: ByteArray): Context {
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.remaining() >= 3) { "Context too short: ${payload.size}" }
        val flags = buf.get().toInt() and 0xFF
        val sequence = buf.short.toInt() and 0xFFFF
        if (flags and CTX_FLAG_EXTENDED != 0 && buf.remaining() >= 1) buf.get() // skip extended flags
        if (flags and CTX_FLAG_CARBS != 0 && buf.remaining() >= 3) {
            buf.get(); buf.short // carbohydrate ID + SFLOAT (skipped)
        }
        var meal: GlucoseRecord.MealRelation? = null
        if (flags and CTX_FLAG_MEAL != 0 && buf.remaining() >= 1) {
            meal = GlucoseRecord.MealRelation.of(buf.get().toInt() and 0xFF)
        }
        // (Further fields: tester-health, exercise, medication, HbA1c — not used yet.)
        return Context(sequenceNumber = sequence, mealRelation = meal)
    }

    /**
     * IEEE-11073 16-bit SFLOAT: 4-bit signed exponent + 12-bit signed mantissa.
     * Special values (NaN/NRes/+Inf/-Inf/Reserved) decode to 0.0.
     */
    internal fun decodeSfloat(raw: Short): Double {
        val r = raw.toInt() and 0xFFFF
        var mantissa = r and 0x0FFF
        var exponent = (r ushr 12) and 0x0F
        if (mantissa >= 0x0800) mantissa -= 0x1000
        if (exponent >= 0x08) exponent -= 0x10
        // Reserved: 0x07FF (+inf), 0x0800 (-inf), 0x07FE (NaN), 0x07FD (NRes), 0x0802 (reserved)
        return when (r and 0x0FFF) {
            0x07FF, 0x0800, 0x07FE, 0x07FD, 0x0802 -> 0.0
            else -> mantissa * Math.pow(10.0, exponent.toDouble())
        }
    }
}

package com.syschimp.glucoripper.ble

import com.google.common.truth.Truth.assertThat
import com.syschimp.glucoripper.ble.GlucoseMeasurementParser.decodeSfloat
import org.junit.Test
import java.time.Instant

/**
 * Covers the SFLOAT + Glucose Measurement (0x2A18) + Glucose Measurement Context
 * (0x2A34) parse paths. Fixtures are constructed per the Bluetooth SIG spec so
 * the test stays valid even if Contour Next One firmware updates shift byte
 * layouts — a real-firmware capture would make the tests tautological.
 */
class GlucoseMeasurementParserTest {

    // ---------- SFLOAT ----------

    @Test fun sfloat_decodes_positive_mantissa_positive_exponent() {
        // 123 × 10^0 = 123. mantissa=0x07B, exponent=0x0 → raw=0x007B.
        assertThat(decodeSfloat(0x007B.toShort())).isEqualTo(123.0)
    }

    @Test fun sfloat_decodes_small_positive_via_negative_exponent() {
        // 1 × 10^-3 = 0.001. mantissa=0x001, exponent=-3 (0xD) → raw=0xD001.
        assertThat(decodeSfloat(0xD001.toShort())).isWithin(1e-9).of(0.001)
    }

    @Test fun sfloat_decodes_negative_mantissa() {
        // -1 × 10^0. mantissa=-1 encoded as 0xFFF, exponent=0 → raw=0x0FFF.
        // 0x07FF is reserved (+inf), so verify on 0x0FFE instead.
        // -2 × 10^0. mantissa=-2=0xFFE, exponent=0 → raw=0x0FFE.
        assertThat(decodeSfloat(0x0FFE.toShort())).isEqualTo(-2.0)
    }

    @Test fun sfloat_special_values_decode_to_zero() {
        // Per IEEE-11073: +inf, -inf, NaN, NRes, Reserved all map to 0.0 in our parser.
        for (raw in listOf(0x07FF, 0x0800, 0x07FE, 0x07FD, 0x0802)) {
            assertThat(decodeSfloat(raw.toShort())).isEqualTo(0.0)
        }
    }

    // ---------- Glucose Measurement ----------

    @Test fun parses_minimal_record_without_optional_fields() {
        // Flag byte 0x00: no time offset, no concentration, no sensor status, no context.
        // seq=7, 2024-03-15T10:30:00.
        val payload = byteArrayOf(
            0x00, 0x07, 0x00,                           // flags + seq LE
            0xE8.toByte(), 0x07,                        // year 2024
            0x03, 0x0F, 0x0A, 0x1E, 0x00,               // Mar 15, 10:30:00
        )
        val r = GlucoseMeasurementParser.parse(payload)
        assertThat(r.sequenceNumber).isEqualTo(7)
        assertThat(r.time).isEqualTo(Instant.parse("2024-03-15T10:30:00Z"))
        assertThat(r.mgPerDl).isNull()
        assertThat(r.sampleType).isNull()
        assertThat(r.sampleLocation).isNull()
        assertThat(r.sensorStatus).isEqualTo(0)
        assertThat(r.hasContextFollows).isFalse()
    }

    @Test fun parses_kg_per_L_concentration_as_mg_per_dL() {
        // Flags 0x02: concentration present, units kg/L (bit2 clear).
        // SFLOAT 0xD001 = 0.001 kg/L → 100 mg/dL. type=1 (capillary whole blood), loc=1 (finger).
        val payload = byteArrayOf(
            0x02, 0x7B, 0x00,                           // flags + seq=123
            0xE8.toByte(), 0x07, 0x03, 0x0F, 0x0A, 0x1E, 0x00,  // 2024-03-15 10:30:00
            0x01, 0xD0.toByte(),                        // SFLOAT 0xD001
            0x11,                                       // type_loc: type=1, loc=1
        )
        val r = GlucoseMeasurementParser.parse(payload)
        assertThat(r.sequenceNumber).isEqualTo(123)
        assertThat(r.mgPerDl).isWithin(1e-6).of(100.0)
        assertThat(r.sampleType).isEqualTo(GlucoseRecord.SampleType.CAPILLARY_WHOLE_BLOOD)
        assertThat(r.sampleLocation).isEqualTo(GlucoseRecord.SampleLocation.FINGER)
    }

    @Test fun parses_mol_per_L_concentration_using_shared_conversion() {
        // Flags 0x06: concentration present + units mol/L. SFLOAT 0xD005 = 0.005 mol/L
        // = 5 mmol/L. Expected mg/dL ≈ 5 × 18.0156 = 90.078.
        val payload = byteArrayOf(
            0x06, 0x01, 0x00,
            0xE8.toByte(), 0x07, 0x03, 0x0F, 0x0A, 0x1E, 0x00,
            0x05, 0xD0.toByte(),
            0x11,
        )
        val r = GlucoseMeasurementParser.parse(payload)
        assertThat(r.mgPerDl).isWithin(1e-3).of(90.078)
    }

    @Test fun parses_time_offset_and_adjusts_instant_to_UTC() {
        // Flags 0x01: time offset present (no concentration).
        // Meter reports 10:30:00 with offset -300 min (UTC-5). UTC = 15:30:00.
        val payload = byteArrayOf(
            0x01, 0x01, 0x00,
            0xE8.toByte(), 0x07, 0x03, 0x0F, 0x0A, 0x1E, 0x00,
            0xD4.toByte(), 0xFE.toByte(),  // offset = -300 (LE two's complement)
        )
        val r = GlucoseMeasurementParser.parse(payload)
        assertThat(r.time).isEqualTo(Instant.parse("2024-03-15T15:30:00Z"))
    }

    @Test fun parses_sensor_status_and_exposes_low_battery_flag() {
        // Flags 0x0A: concentration + sensor status (no offset, no context).
        val payload = byteArrayOf(
            0x0A, 0x02, 0x00,
            0xE8.toByte(), 0x07, 0x03, 0x0F, 0x0A, 0x1E, 0x00,
            0x01, 0xD0.toByte(),                        // SFLOAT for 100 mg/dL
            0x11,
            0x01, 0x00,                                 // sensor status bit 0 = low battery
        )
        val r = GlucoseMeasurementParser.parse(payload)
        assertThat(r.sensorStatus).isEqualTo(0x0001)
        assertThat(r.wasDeviceBatteryLow).isTrue()
    }

    @Test fun classifies_control_solution_via_sample_type() {
        // Flags 0x02, sample type=10 (CONTROL_SOLUTION), location=1.
        val payload = byteArrayOf(
            0x02, 0x03, 0x00,
            0xE8.toByte(), 0x07, 0x03, 0x0F, 0x0A, 0x1E, 0x00,
            0x01, 0xD0.toByte(),
            0x1A,  // type=10, loc=1 → 0x1A
        )
        val r = GlucoseMeasurementParser.parse(payload)
        assertThat(r.isControlSolution).isTrue()
    }

    @Test fun propagates_context_follows_flag() {
        val payload = byteArrayOf(
            0x10, 0x04, 0x00,                           // FLAG_CONTEXT_FOLLOWS only
            0xE8.toByte(), 0x07, 0x03, 0x0F, 0x0A, 0x1E, 0x00,
        )
        val r = GlucoseMeasurementParser.parse(payload)
        assertThat(r.hasContextFollows).isTrue()
    }

    @Test fun rejects_payload_below_minimum_length() {
        assertThrows(IllegalArgumentException::class.java) {
            GlucoseMeasurementParser.parse(byteArrayOf(0x00, 0x01))
        }
    }

    // ---------- Context (0x2A34) ----------

    @Test fun context_meal_only_decodes_meal_relation() {
        // Flags 0x02 (meal present only). seq=42, meal=3 (FASTING).
        val payload = byteArrayOf(0x02, 0x2A, 0x00, 0x03)
        val ctx = GlucoseMeasurementParser.parseContext(payload)
        assertThat(ctx.sequenceNumber).isEqualTo(42)
        assertThat(ctx.mealRelation).isEqualTo(GlucoseRecord.MealRelation.FASTING)
    }

    @Test fun context_skips_carbs_before_reading_meal() {
        // Flags 0x03 (carbs + meal). seq=5, carb_id=1, carb_sfloat=0x007B (=123 units),
        // meal=2 (POSTPRANDIAL).
        val payload = byteArrayOf(
            0x03, 0x05, 0x00,
            0x01, 0x7B, 0x00,  // carb id + SFLOAT
            0x02,              // meal
        )
        val ctx = GlucoseMeasurementParser.parseContext(payload)
        assertThat(ctx.sequenceNumber).isEqualTo(5)
        assertThat(ctx.mealRelation).isEqualTo(GlucoseRecord.MealRelation.POSTPRANDIAL)
    }

    @Test fun context_with_no_meal_flag_returns_null_relation() {
        // Flags 0x01 (carbs only, no meal).
        val payload = byteArrayOf(
            0x01, 0x09, 0x00,
            0x01, 0x7B, 0x00,
        )
        val ctx = GlucoseMeasurementParser.parseContext(payload)
        assertThat(ctx.mealRelation).isNull()
    }

    private fun assertThrows(type: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected ${type.simpleName} to be thrown")
        } catch (t: Throwable) {
            if (!type.isInstance(t)) throw t
        }
    }
}

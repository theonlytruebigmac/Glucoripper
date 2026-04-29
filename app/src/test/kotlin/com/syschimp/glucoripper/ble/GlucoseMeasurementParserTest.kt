package com.syschimp.glucoripper.ble

import com.google.common.truth.Truth.assertThat
import com.syschimp.glucoripper.ble.GlucoseMeasurementParser.decodeSfloat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

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
        // seq=7, 2024-03-15T10:30:00. With defaultZone=UTC the instant matches the wall-clock.
        val payload = byteArrayOf(
            0x00, 0x07, 0x00,                           // flags + seq LE
            0xE8.toByte(), 0x07,                        // year 2024
            0x03, 0x0F, 0x0A, 0x1E, 0x00,               // Mar 15, 10:30:00
        )
        val r = GlucoseMeasurementParser.parse(payload, ZoneOffset.UTC)
        assertThat(r.sequenceNumber).isEqualTo(7)
        assertThat(r.time).isEqualTo(Instant.parse("2024-03-15T10:30:00Z"))
        assertThat(r.mgPerDl).isNull()
        assertThat(r.sampleType).isNull()
        assertThat(r.sampleLocation).isNull()
        assertThat(r.sensorStatus).isEqualTo(0)
        assertThat(r.hasContextFollows).isFalse()
    }

    @Test fun missing_time_offset_falls_back_to_default_zone() {
        // Same fixture as above. With defaultZone=America/New_York (UTC-4 on 2024-03-15
        // since DST began Mar 10), wall-clock 10:30 EDT → 14:30 UTC.
        val payload = byteArrayOf(
            0x00, 0x07, 0x00,
            0xE8.toByte(), 0x07,
            0x03, 0x0F, 0x0A, 0x1E, 0x00,
        )
        val r = GlucoseMeasurementParser.parse(payload, ZoneId.of("America/New_York"))
        assertThat(r.time).isEqualTo(Instant.parse("2024-03-15T14:30:00Z"))
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

    @Test fun applies_time_offset_to_base_per_spec() {
        // GLS 1.0.1 §3.1.2.4: actual time of measurement = Base Time + Time Offset.
        // Base = 2024-03-15 17:00:00, Offset = -300 min → corrected local 12:00:00.
        // Anchored to America/New_York (EDT in mid-March, UTC-4) → 16:00:00 UTC.
        val payload = byteArrayOf(
            0x01, 0x01, 0x00,
            0xE8.toByte(), 0x07, 0x03, 0x0F, 0x11, 0x00, 0x00,
            0xD4.toByte(), 0xFE.toByte(),  // offset = -300 (LE two's complement)
        )
        val r = GlucoseMeasurementParser.parse(payload, ZoneId.of("America/New_York"))
        assertThat(r.time).isEqualTo(Instant.parse("2024-03-15T16:00:00Z"))
    }

    @Test fun non_standard_time_offset_handled_per_spec() {
        // Real-world Contour Next One case: user nudged clock by 5 min, so the
        // meter reports a non-standard offset of -235 min (-3h55m). The prior
        // parser doubled the error; with spec-correct math this lands cleanly.
        // Base = 2026-04-29 17:07:24, Offset = -235 → corrected local 13:12:24.
        // In America/New_York (EDT, UTC-4) → 17:12:24 UTC.
        val payload = byteArrayOf(
            0x03, 0xB8.toByte(), 0x00,
            0xEA.toByte(), 0x07, 0x04, 0x1D, 0x11, 0x07, 0x18,
            0x15, 0xFF.toByte(),                       // offset = -235
            0x01, 0xD0.toByte(),                       // SFLOAT 100 mg/dL
            0x11,
        )
        val r = GlucoseMeasurementParser.parse(payload, ZoneId.of("America/New_York"))
        assertThat(r.time).isEqualTo(Instant.parse("2026-04-29T17:12:24Z"))
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

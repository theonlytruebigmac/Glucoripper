package com.syschimp.glucoripper.data

import android.content.Context
import android.net.Uri
import androidx.health.connect.client.records.BloodGlucoseRecord
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val ts = DateTimeFormatter.ISO_INSTANT

    fun export(context: Context, target: Uri, readings: List<BloodGlucoseRecord>): Int {
        context.contentResolver.openOutputStream(target, "wt")?.use { out ->
            val w = out.bufferedWriter()
            w.append("timestamp_utc,local_time,mg_dl,mmol_l,relation_to_meal,specimen_source,client_record_id\n")
            val zone = ZoneId.systemDefault()
            readings.forEach { r ->
                val mgDl = r.level.inMilligramsPerDeciliter
                w.append(csvField(ts.format(r.time)))
                w.append(',')
                w.append(csvField(r.time.atZone(zone).toLocalDateTime().toString()))
                w.append(',')
                w.append("%.1f".format(mgDl))
                w.append(',')
                w.append("%.2f".format(mgDl.mgDlToMmol()))
                w.append(',')
                w.append(csvField(mealString(r.relationToMeal)))
                w.append(',')
                w.append(csvField(specimenString(r.specimenSource)))
                w.append(',')
                w.append(csvField(r.metadata.clientRecordId ?: r.metadata.id))
                w.append('\n')
            }
            w.flush()
        } ?: return 0
        return readings.size
    }

    /**
     * RFC 4180 quoting plus a defense against CSV-formula injection when the
     * file is opened in Excel/Sheets: values that start with `=`, `+`, `-`,
     * `@`, tab, or carriage return get a leading single quote so the spreadsheet
     * treats them as text rather than a formula.
     */
    private fun csvField(value: String): String {
        val defended = if (value.isNotEmpty() && value[0] in FORMULA_TRIGGER_CHARS) "'$value" else value
        val needsQuoting = defended.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"" + defended.replace("\"", "\"\"") + "\"" else defended
    }

    private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    private fun mealString(code: Int): String = when (code) {
        BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "before"
        BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "after"
        BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "fasting"
        BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "general"
        else -> "unknown"
    }

    private fun specimenString(code: Int): String = when (code) {
        BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD -> "whole_blood"
        BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA -> "plasma"
        BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID -> "interstitial_fluid"
        BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD -> "capillary"
        BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM -> "serum"
        BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS -> "tears"
        else -> "unknown"
    }
}

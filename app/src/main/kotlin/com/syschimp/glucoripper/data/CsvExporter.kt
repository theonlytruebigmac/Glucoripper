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
                w.append(ts.format(r.time))
                w.append(',')
                w.append(r.time.atZone(zone).toLocalDateTime().toString())
                w.append(',')
                w.append("%.1f".format(mgDl))
                w.append(',')
                w.append("%.2f".format(mgDl.mgDlToMmol()))
                w.append(',')
                w.append(mealString(r.relationToMeal))
                w.append(',')
                w.append(specimenString(r.specimenSource))
                w.append(',')
                w.append((r.metadata.clientRecordId ?: r.metadata.id).replace(",", " "))
                w.append('\n')
            }
            w.flush()
        } ?: return 0
        return readings.size
    }

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

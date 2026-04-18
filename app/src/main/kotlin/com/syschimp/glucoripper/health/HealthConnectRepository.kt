package com.syschimp.glucoripper.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import android.util.Log
import com.syschimp.glucoripper.ble.GlucoseRecord
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectRepository(private val context: Context) {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getWritePermission(BloodGlucoseRecord::class),
    )

    fun availability(): Int = HealthConnectClient.getSdkStatus(context)

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(): Boolean {
        if (availability() != HealthConnectClient.SDK_AVAILABLE) return false
        val granted = client().permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    /** Writes measured glucose records to Health Connect. Returns the number actually written. */
    suspend fun writeGlucoseRecords(
        meterAddress: String,
        records: List<GlucoseRecord>,
    ): Int {
        if (records.isEmpty()) return 0

        val device = Device(
            type = Device.TYPE_UNKNOWN,
            manufacturer = "Ascensia",
            model = "Contour Next One",
        )

        // Contour Next One's internal RTC drifts; clamp future timestamps by subtracting
        // the drift (max record - now) from every reading. Preserves relative timing.
        val now = Instant.now()
        val maxRecordTime = records.maxOfOrNull { it.time }
        val drift = if (maxRecordTime != null && maxRecordTime.isAfter(now)) {
            Duration.between(now, maxRecordTime).plusSeconds(1)
        } else {
            Duration.ZERO
        }
        if (!drift.isZero) {
            Log.i("HealthRepo", "Meter clock ahead by ${drift.seconds}s; shifting timestamps back")
        }

        val hcRecords = records.mapNotNull { r ->
            val mgDl = r.mgPerDl ?: return@mapNotNull null
            BloodGlucoseRecord(
                time = r.time.minus(drift),
                zoneOffset = ZoneOffset.UTC,
                level = BloodGlucose.milligramsPerDeciliter(mgDl),
                specimenSource = mapSpecimenSource(r.sampleType),
                mealType = MealType.MEAL_TYPE_UNKNOWN,
                relationToMeal = mapRelationToMeal(r.mealRelation),
                metadata = Metadata(
                    clientRecordId = "$meterAddress/${r.sequenceNumber}",
                    clientRecordVersion = 1L,
                    device = device,
                    recordingMethod = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
                ),
            )
        }
        if (hcRecords.isEmpty()) return 0

        // Re-inserting the same clientRecordId is a no-op (version not greater), so
        // duplicate runs are safe.
        val response = client().insertRecords(hcRecords)
        return response.recordIdsList.size
    }

    /**
     * Re-insert an existing reading with an updated `relationToMeal` and a bumped
     * `clientRecordVersion`. Health Connect upserts on the same clientRecordId.
     */
    suspend fun updateMealRelation(original: BloodGlucoseRecord, newRelation: Int) {
        val clientId = original.metadata.clientRecordId ?: return
        val newVersion = (original.metadata.clientRecordVersion) + 1
        val updated = BloodGlucoseRecord(
            time = original.time,
            zoneOffset = original.zoneOffset,
            level = original.level,
            specimenSource = original.specimenSource,
            mealType = original.mealType,
            relationToMeal = newRelation,
            metadata = Metadata(
                clientRecordId = clientId,
                clientRecordVersion = newVersion,
                device = original.metadata.device,
                recordingMethod = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
            ),
        )
        client().insertRecords(listOf(updated))
    }

    /** Most recent [limit] blood-glucose readings stored in Health Connect, newest first. */
    suspend fun readRecentReadings(limit: Int = 20): List<BloodGlucoseRecord> {
        if (availability() != HealthConnectClient.SDK_AVAILABLE) return emptyList()
        if (!hasAllPermissions()) return emptyList()
        val request = ReadRecordsRequest(
            recordType = BloodGlucoseRecord::class,
            timeRangeFilter = TimeRangeFilter.before(Instant.now()),
            ascendingOrder = false,
            pageSize = limit.coerceIn(1, 1000),
        )
        return runCatching { client().readRecords(request).records }
            .getOrDefault(emptyList())
    }

    private fun mapRelationToMeal(meal: GlucoseRecord.MealRelation?): Int = when (meal) {
        GlucoseRecord.MealRelation.PREPRANDIAL -> BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
        GlucoseRecord.MealRelation.POSTPRANDIAL -> BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
        GlucoseRecord.MealRelation.FASTING -> BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
        GlucoseRecord.MealRelation.CASUAL -> BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
        GlucoseRecord.MealRelation.BEDTIME, null -> BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
    }

    private fun mapSpecimenSource(type: GlucoseRecord.SampleType?): Int = when (type) {
        GlucoseRecord.SampleType.CAPILLARY_WHOLE_BLOOD,
        GlucoseRecord.SampleType.UNDETERMINED_WHOLE_BLOOD,
        GlucoseRecord.SampleType.VENOUS_WHOLE_BLOOD,
        GlucoseRecord.SampleType.ARTERIAL_WHOLE_BLOOD ->
            BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD
        GlucoseRecord.SampleType.CAPILLARY_PLASMA,
        GlucoseRecord.SampleType.UNDETERMINED_PLASMA,
        GlucoseRecord.SampleType.VENOUS_PLASMA,
        GlucoseRecord.SampleType.ARTERIAL_PLASMA ->
            BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA
        GlucoseRecord.SampleType.INTERSTITIAL_FLUID ->
            BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID
        else -> BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN
    }
}

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
import com.syschimp.glucoripper.data.StagedReading
import timber.log.Timber
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

    /**
     * Push staged readings to Health Connect. Returns the number actually inserted.
     * Per-record clamp: any reading whose timestamp is after `now` is shifted to
     * `now - 1s`, so a single glitched-RTC reading can't drag the whole batch back
     * (HC dedupes on clientRecordId, so a wrong timestamp would be sticky).
     */
    suspend fun pushStaged(readings: List<StagedReading>): Int {
        if (readings.isEmpty()) return 0
        val device = Device(
            type = Device.TYPE_UNKNOWN,
            manufacturer = "Ascensia",
            model = "Contour Next One",
        )

        val now = Instant.now()
        val hcRecords = readings.map { r ->
            val effectiveTime = if (r.time.isAfter(now)) {
                Timber.w(
                    "Reading %s has future timestamp %s; clamping to now",
                    r.id, r.time,
                )
                now.minusSeconds(1)
            } else r.time
            BloodGlucoseRecord(
                time = effectiveTime,
                zoneOffset = ZoneOffset.UTC,
                level = BloodGlucose.milligramsPerDeciliter(r.mgPerDl),
                specimenSource = r.specimenSource,
                mealType = MealType.MEAL_TYPE_UNKNOWN,
                relationToMeal = r.effectiveMeal,
                metadata = Metadata(
                    clientRecordId = r.id,
                    clientRecordVersion = 1L,
                    device = device,
                    recordingMethod = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
                ),
            )
        }
        return client().insertRecords(hcRecords).recordIdsList.size
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

}

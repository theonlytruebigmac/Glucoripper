package com.syschimp.glucoripper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.stagingStore by preferencesDataStore(name = "staging")

/**
 * A reading pulled from the meter that hasn't been pushed to Health Connect yet.
 * The glucose value is immutable; the user can edit meal, feeling, and note before
 * pushing.
 */
data class StagedReading(
    val meterAddress: String,
    val sequenceNumber: Int,
    val time: Instant,
    val mgPerDl: Double,
    val meterMeal: Int,       // BloodGlucoseRecord.RELATION_TO_MEAL_*
    val specimenSource: Int,  // BloodGlucoseRecord.SPECIMEN_SOURCE_*
    val userMeal: Int? = null,
    val feeling: Feeling? = null,
    val note: String? = null,
    val stagedAtMillis: Long = System.currentTimeMillis(),
) {
    val id: String get() = "$meterAddress/$sequenceNumber"
    val effectiveMeal: Int get() = userMeal ?: meterMeal
}

class StagingStore(private val context: Context) {
    private val key = stringPreferencesKey("staged_v1")
    private val sep = "\u001f"
    private val lineSep = "\n"

    val flow: Flow<List<StagedReading>> = context.stagingStore.data.map { p ->
        p[key]?.let(::deserialize).orEmpty()
    }

    suspend fun addOrReplace(readings: List<StagedReading>) {
        if (readings.isEmpty()) return
        context.stagingStore.edit { p ->
            val current = (p[key]?.let(::deserialize).orEmpty()).associateBy { it.id }.toMutableMap()
            readings.forEach { incoming ->
                // Preserve user-edited fields on re-sync.
                val prev = current[incoming.id]
                current[incoming.id] = if (prev != null) {
                    incoming.copy(
                        userMeal = prev.userMeal,
                        feeling = prev.feeling,
                        note = prev.note,
                        stagedAtMillis = prev.stagedAtMillis,
                    )
                } else incoming
            }
            p[key] = serialize(current.values.sortedByDescending { it.time })
        }
    }

    suspend fun update(id: String, transform: (StagedReading) -> StagedReading) {
        context.stagingStore.edit { p ->
            val list = p[key]?.let(::deserialize).orEmpty().toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = transform(list[idx])
                p[key] = serialize(list)
            }
        }
    }

    suspend fun remove(ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.stagingStore.edit { p ->
            val filtered = p[key]?.let(::deserialize).orEmpty().filterNot { it.id in ids }
            p[key] = serialize(filtered)
        }
    }

    suspend fun clear() {
        context.stagingStore.edit { it.remove(key) }
    }

    private fun serialize(items: List<StagedReading>): String =
        items.joinToString(lineSep) { r ->
            listOf(
                r.meterAddress,
                r.sequenceNumber.toString(),
                r.time.toEpochMilli().toString(),
                r.mgPerDl.toString(),
                r.meterMeal.toString(),
                r.specimenSource.toString(),
                r.userMeal?.toString() ?: "",
                r.feeling?.name ?: "",
                (r.note ?: "").replace("\n", " ").replace(sep, " "),
                r.stagedAtMillis.toString(),
            ).joinToString(sep)
        }

    private fun deserialize(raw: String): List<StagedReading> =
        raw.split(lineSep).mapNotNull { line ->
            val p = line.split(sep)
            if (p.size < 10) return@mapNotNull null
            runCatching {
                StagedReading(
                    meterAddress = p[0],
                    sequenceNumber = p[1].toInt(),
                    time = Instant.ofEpochMilli(p[2].toLong()),
                    mgPerDl = p[3].toDouble(),
                    meterMeal = p[4].toInt(),
                    specimenSource = p[5].toInt(),
                    userMeal = p[6].toIntOrNull(),
                    feeling = Feeling.fromName(p[7].ifEmpty { null }),
                    note = p[8].ifEmpty { null },
                    stagedAtMillis = p[9].toLong(),
                )
            }.getOrNull()
        }
}

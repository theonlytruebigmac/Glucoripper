package com.syschimp.glucoripper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.annotationStore by preferencesDataStore(name = "annotations")

/** Per-reading annotations the user added after the fact. */
data class ReadingAnnotation(
    val clientRecordId: String,
    val mealOverride: Int? = null, // BloodGlucoseRecord.RELATION_TO_MEAL_*
    val feeling: Feeling? = null,
    val note: String? = null,
) {
    val isEmpty: Boolean
        get() = mealOverride == null && feeling == null && note.isNullOrBlank()
}

enum class Feeling(val emoji: String, val label: String) {
    GREAT("😃", "Great"),
    GOOD("🙂", "Good"),
    OKAY("😐", "Okay"),
    OFF("😕", "Off"),
    BAD("😞", "Bad");
    companion object {
        fun fromName(s: String?) = entries.firstOrNull { it.name == s }
    }
}

class Annotations(private val context: Context) {
    private val key = stringPreferencesKey("entries_v1")
    private val sep = "\u001f"
    private val lineSep = "\n"

    val flow: Flow<Map<String, ReadingAnnotation>> = context.annotationStore.data.map { p ->
        p[key]?.let(::deserialize).orEmpty()
    }

    suspend fun update(
        clientRecordId: String,
        transform: (ReadingAnnotation) -> ReadingAnnotation,
    ) {
        context.annotationStore.edit { p ->
            val existing = p[key]?.let(::deserialize).orEmpty().toMutableMap()
            val current = existing[clientRecordId] ?: ReadingAnnotation(clientRecordId)
            val next = transform(current)
            if (next.isEmpty) existing.remove(clientRecordId) else existing[clientRecordId] = next
            p[key] = serialize(existing)
        }
    }

    private fun serialize(map: Map<String, ReadingAnnotation>): String =
        map.values.joinToString(lineSep) { a ->
            listOf(
                a.clientRecordId,
                a.mealOverride?.toString() ?: "",
                a.feeling?.name ?: "",
                (a.note ?: "").replace("\n", " ").replace(sep, " "),
            ).joinToString(sep)
        }

    private fun deserialize(raw: String): Map<String, ReadingAnnotation> =
        raw.split(lineSep).mapNotNull { line ->
            val parts = line.split(sep)
            if (parts.size < 4 || parts[0].isBlank()) return@mapNotNull null
            ReadingAnnotation(
                clientRecordId = parts[0],
                mealOverride = parts[1].toIntOrNull(),
                feeling = Feeling.fromName(parts[2].ifEmpty { null }),
                note = parts[3].ifEmpty { null },
            )
        }.associateBy { it.clientRecordId }
}

package com.syschimp.glucoripper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

private val Context.eventsStore by preferencesDataStore(name = "health_events")

enum class EventKind(val emoji: String, val label: String) {
    MEAL("🍽", "Meal"),
    SNACK("🥨", "Snack"),
    INSULIN("💉", "Insulin"),
    EXERCISE("🏃", "Exercise"),
    MEDICATION("💊", "Medication"),
    NOTE("📝", "Note");
    companion object {
        fun fromName(s: String?) = entries.firstOrNull { it.name == s }
    }
}

data class HealthEvent(
    val id: String,
    val time: Instant,
    val kind: EventKind,
    val note: String? = null,
)

class Events(private val context: Context) {
    private val key = stringPreferencesKey("events_v1")
    private val sep = "\u001f"
    private val lineSep = "\n"

    val flow: Flow<List<HealthEvent>> = context.eventsStore.data.map { p ->
        p[key]?.let(::deserialize).orEmpty().sortedByDescending { it.time }
    }

    suspend fun add(event: HealthEvent) {
        context.eventsStore.edit { p ->
            val existing = p[key]?.let(::deserialize).orEmpty().toMutableList()
            existing += event
            p[key] = serialize(existing)
        }
    }

    suspend fun remove(id: String) {
        context.eventsStore.edit { p ->
            val existing = p[key]?.let(::deserialize).orEmpty().filterNot { it.id == id }
            p[key] = serialize(existing)
        }
    }

    private fun serialize(list: List<HealthEvent>): String =
        list.joinToString(lineSep) { e ->
            listOf(
                e.id,
                e.time.toEpochMilli().toString(),
                e.kind.name,
                (e.note ?: "").replace("\n", " ").replace(sep, " "),
            ).joinToString(sep)
        }

    private fun deserialize(raw: String): List<HealthEvent> =
        raw.split(lineSep).mapNotNull { line ->
            val parts = line.split(sep)
            if (parts.size < 4 || parts[0].isBlank()) return@mapNotNull null
            HealthEvent(
                id = parts[0],
                time = Instant.ofEpochMilli(parts[1].toLongOrNull() ?: return@mapNotNull null),
                kind = EventKind.fromName(parts[2]) ?: return@mapNotNull null,
                note = parts[3].ifEmpty { null },
            )
        }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

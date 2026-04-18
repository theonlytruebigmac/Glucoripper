package com.syschimp.glucoripper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.historyStore by preferencesDataStore(name = "sync_history")

data class SyncHistoryEntry(
    val timestampMillis: Long,
    val meterAddress: String,
    val success: Boolean,
    val pulled: Int,
    val written: Int,
    val skippedControl: Int,
    val message: String?,
)

class SyncHistory(private val context: Context) {
    private val key = stringPreferencesKey("entries_v1")
    private val max = 50

    val flow: Flow<List<SyncHistoryEntry>> = context.historyStore.data.map { p ->
        p[key]?.let(::deserialize).orEmpty()
    }

    suspend fun append(entry: SyncHistoryEntry) {
        context.historyStore.edit { p ->
            val existing = p[key]?.let(::deserialize).orEmpty()
            val updated = (listOf(entry) + existing).take(max)
            p[key] = serialize(updated)
        }
    }

    suspend fun clear() {
        context.historyStore.edit { it.remove(key) }
    }

    // Tab-separated lines, pipe-escaped messages. Simple and reliable.
    private fun serialize(entries: List<SyncHistoryEntry>): String =
        entries.joinToString("\n") { e ->
            listOf(
                e.timestampMillis.toString(),
                e.meterAddress,
                if (e.success) "1" else "0",
                e.pulled.toString(),
                e.written.toString(),
                e.skippedControl.toString(),
                (e.message ?: "").replace("\t", " ").replace("\n", " "),
            ).joinToString("\t")
        }

    private fun deserialize(s: String): List<SyncHistoryEntry> =
        s.split("\n").mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size < 7) return@mapNotNull null
            runCatching {
                SyncHistoryEntry(
                    timestampMillis = parts[0].toLong(),
                    meterAddress = parts[1],
                    success = parts[2] == "1",
                    pulled = parts[3].toInt(),
                    written = parts[4].toInt(),
                    skippedControl = parts[5].toInt(),
                    message = parts[6].ifEmpty { null },
                )
            }.getOrNull()
        }
}

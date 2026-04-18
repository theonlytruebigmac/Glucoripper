package com.syschimp.glucoripper.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncStore by preferencesDataStore(name = "sync_state")

/**
 * Persists per-meter sync checkpoints so subsequent syncs only fetch new records.
 * Keyed by the meter's bluetooth MAC address.
 */
class SyncState(private val context: Context) {
    private fun lastSequenceKey(address: String) =
        intPreferencesKey("last_seq_${address.uppercase()}")

    private fun lastSyncTimeKey(address: String) =
        longPreferencesKey("last_sync_ms_${address.uppercase()}")

    suspend fun lastSequence(address: String): Int? =
        context.syncStore.data.map { it[lastSequenceKey(address)] }.first()

    suspend fun setLastSequence(address: String, sequence: Int) {
        context.syncStore.edit { prefs ->
            val key = lastSequenceKey(address)
            val prev = prefs[key] ?: -1
            if (sequence > prev) prefs[key] = sequence
            prefs[lastSyncTimeKey(address)] = System.currentTimeMillis()
        }
    }

    fun lastSyncTimeFlow(address: String): Flow<Long?> =
        context.syncStore.data.map { it[lastSyncTimeKey(address)] }

    suspend fun reset(address: String) {
        context.syncStore.edit {
            it.remove(lastSequenceKey(address))
            it.remove(lastSyncTimeKey(address))
        }
    }
}

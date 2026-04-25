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

    private fun lastTotalCountKey(address: String) =
        intPreferencesKey("last_total_${address.uppercase()}")

    suspend fun lastSequence(address: String): Int? =
        context.syncStore.data.map { it[lastSequenceKey(address)] }.first()

    /**
     * Persist the latest observed sequence number. We *always* overwrite (no monotonic
     * guard) because a meter that wraps its uint16 sequence at 65535 would otherwise
     * leave us pinned to a stale checkpoint forever — see SyncCoordinator's rollover
     * detection, which calls this with the post-rollover max.
     */
    suspend fun setLastSequence(address: String, sequence: Int) {
        context.syncStore.edit { prefs ->
            prefs[lastSequenceKey(address)] = sequence
            prefs[lastSyncTimeKey(address)] = System.currentTimeMillis()
        }
    }

    suspend fun lastTotalCount(address: String): Int? =
        context.syncStore.data.map { it[lastTotalCountKey(address)] }.first()

    suspend fun setLastTotalCount(address: String, total: Int) {
        context.syncStore.edit { it[lastTotalCountKey(address)] = total }
    }

    fun lastSyncTimeFlow(address: String): Flow<Long?> =
        context.syncStore.data.map { it[lastSyncTimeKey(address)] }

    suspend fun reset(address: String) {
        context.syncStore.edit {
            it.remove(lastSequenceKey(address))
            it.remove(lastSyncTimeKey(address))
            it.remove(lastTotalCountKey(address))
        }
    }
}

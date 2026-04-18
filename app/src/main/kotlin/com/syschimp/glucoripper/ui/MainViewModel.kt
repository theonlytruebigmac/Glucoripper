package com.syschimp.glucoripper.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.IntentSender
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.syschimp.glucoripper.companion.MeterPairingManager
import com.syschimp.glucoripper.data.Annotations
import com.syschimp.glucoripper.data.CsvExporter
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.Preferences
import com.syschimp.glucoripper.data.ReadingAnnotation
import com.syschimp.glucoripper.data.SyncHistory
import com.syschimp.glucoripper.data.SyncHistoryEntry
import com.syschimp.glucoripper.data.UserPreferences
import com.syschimp.glucoripper.health.HealthConnectRepository
import com.syschimp.glucoripper.sync.SyncBus
import com.syschimp.glucoripper.sync.SyncForegroundService
import com.syschimp.glucoripper.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairedMeter(
    val associationId: Int,
    val address: String,
    val displayName: String?,
    val lastSyncMillis: Long?,
)

enum class HealthConnectState { UNAVAILABLE, NEEDS_PERMISSIONS, READY }

data class UiState(
    val bluetoothEnabled: Boolean = false,
    val healthConnectState: HealthConnectState = HealthConnectState.UNAVAILABLE,
    val meters: List<PairedMeter> = emptyList(),
    val recentReadings: List<BloodGlucoseRecord> = emptyList(),
    val syncing: Boolean = false,
    val lastMessage: String? = null,
    val lowBatteryFlag: Boolean = false,
    val prefs: UserPreferences = UserPreferences(GlucoseUnit.MG_PER_DL, 70.0, 140.0),
    val syncHistory: List<SyncHistoryEntry> = emptyList(),
    val annotations: Map<String, ReadingAnnotation> = emptyMap(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val pairing = MeterPairingManager(app)
    private val healthRepo = HealthConnectRepository(app)
    private val syncState = SyncState(app)
    private val prefs = Preferences(app)
    private val history = SyncHistory(app)
    private val annotationsStore = Annotations(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefs.flow,
                history.flow,
                SyncBus.state,
                annotationsStore.flow,
            ) { p, h, bus, ann -> listOf(p, h, bus, ann) }.collect { tuple ->
                @Suppress("UNCHECKED_CAST")
                val p = tuple[0] as UserPreferences
                @Suppress("UNCHECKED_CAST")
                val h = tuple[1] as List<SyncHistoryEntry>
                val bus = tuple[2] as SyncBus.State
                @Suppress("UNCHECKED_CAST")
                val ann = tuple[3] as Map<String, ReadingAnnotation>
                _state.update {
                    it.copy(
                        prefs = p,
                        syncHistory = h,
                        syncing = bus.running,
                        lastMessage = bus.lastMessage ?: it.lastMessage,
                        lowBatteryFlag = bus.lastLowBatteryFlag,
                        annotations = ann,
                    )
                }
                if (!bus.running) refresh() // reload readings on completion
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val meters = pairing.associations()
                .filter { !it.address.isNullOrBlank() }
                .map {
                    PairedMeter(
                        associationId = it.id,
                        address = it.address!!,
                        displayName = it.displayName,
                        lastSyncMillis = runCatching {
                            syncState.lastSyncTimeFlow(it.address!!).first()
                        }.getOrNull(),
                    )
                }

            val hcState = when {
                healthRepo.availability() != HealthConnectClient.SDK_AVAILABLE ->
                    HealthConnectState.UNAVAILABLE
                !healthRepo.hasAllPermissions() -> HealthConnectState.NEEDS_PERMISSIONS
                else -> HealthConnectState.READY
            }

            val btAdapter: BluetoothAdapter? = getApplication<Application>()
                .getSystemService(BluetoothManager::class.java)?.adapter

            val readings = if (hcState == HealthConnectState.READY) {
                healthRepo.readRecentReadings(200)
            } else emptyList()

            _state.update {
                it.copy(
                    bluetoothEnabled = btAdapter?.isEnabled == true,
                    healthConnectState = hcState,
                    meters = meters,
                    recentReadings = readings,
                )
            }
        }
    }

    suspend fun requestPairingIntent(): IntentSender = pairing.requestAssociation()

    fun healthPermissions(): Set<String> = healthRepo.requiredPermissions

    fun onHealthPermissionsGranted() {
        _state.update { it.copy(healthConnectState = HealthConnectState.READY) }
    }

    fun syncNow(meter: PairedMeter) {
        SyncForegroundService.trigger(getApplication(), meter.address, forceFull = false)
    }

    fun forceFullResync(meter: PairedMeter) {
        SyncForegroundService.trigger(getApplication(), meter.address, forceFull = true)
    }

    fun unpair(meter: PairedMeter) {
        pairing.disassociate(meter.associationId)
        viewModelScope.launch {
            syncState.reset(meter.address)
            refresh()
        }
    }

    fun setUnit(unit: GlucoseUnit) {
        viewModelScope.launch { prefs.setUnit(unit) }
    }

    fun setTargetRange(lowMgDl: Double, highMgDl: Double) {
        viewModelScope.launch { prefs.setTargetRange(lowMgDl, highMgDl) }
    }

    fun clearSyncHistory() {
        viewModelScope.launch { history.clear() }
    }

    fun setMealRelation(record: BloodGlucoseRecord, relation: Int) {
        val clientId = record.metadata.clientRecordId ?: return
        viewModelScope.launch {
            runCatching { healthRepo.updateMealRelation(record, relation) }
            annotationsStore.update(clientId) { it.copy(mealOverride = relation) }
            refresh()
        }
    }

    fun setFeeling(clientRecordId: String?, feeling: Feeling?) {
        if (clientRecordId.isNullOrBlank()) return
        viewModelScope.launch {
            annotationsStore.update(clientRecordId) { it.copy(feeling = feeling) }
        }
    }

    fun setNote(clientRecordId: String?, note: String?) {
        if (clientRecordId.isNullOrBlank()) return
        viewModelScope.launch {
            annotationsStore.update(clientRecordId) {
                it.copy(note = note?.trim()?.ifEmpty { null })
            }
        }
    }

    fun exportCsv(uri: Uri, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val all = healthRepo.readRecentReadings(1000)
            val count = runCatching { CsvExporter.export(getApplication(), uri, all) }
                .getOrDefault(0)
            onDone(count)
        }
    }
}

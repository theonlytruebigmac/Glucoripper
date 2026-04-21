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
import com.syschimp.glucoripper.data.AutoPushMode
import com.syschimp.glucoripper.data.CsvExporter
import com.syschimp.glucoripper.data.Events
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.HealthEvent
import com.syschimp.glucoripper.data.Preferences
import com.syschimp.glucoripper.data.ReadingAnnotation
import com.syschimp.glucoripper.data.StagedReading
import com.syschimp.glucoripper.data.StagingStore
import com.syschimp.glucoripper.data.ThemeMode
import com.syschimp.glucoripper.data.SyncHistory
import com.syschimp.glucoripper.data.SyncHistoryEntry
import com.syschimp.glucoripper.data.UserPreferences
import com.syschimp.glucoripper.health.HealthConnectRepository
import com.syschimp.glucoripper.sync.AutoPushScheduler
import com.syschimp.glucoripper.sync.StagingPusher
import com.syschimp.glucoripper.sync.SyncBus
import com.syschimp.glucoripper.sync.SyncForegroundService
import com.syschimp.glucoripper.sync.SyncState
import com.syschimp.glucoripper.wear.WearBridge
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val prefs: UserPreferences = UserPreferences(
        unit = GlucoseUnit.MG_PER_DL,
        targetLowMgDl = 70.0,
        targetHighMgDl = 140.0,
        autoPushMode = AutoPushMode.OFF,
    ),
    val syncHistory: List<SyncHistoryEntry> = emptyList(),
    val annotations: Map<String, ReadingAnnotation> = emptyMap(),
    val staged: List<StagedReading> = emptyList(),
    val pushing: Boolean = false,
    val events: List<HealthEvent> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val pairing = MeterPairingManager(app)
    private val healthRepo = HealthConnectRepository(app)
    private val syncState = SyncState(app)
    private val prefs = Preferences(app)
    private val history = SyncHistory(app)
    private val annotationsStore = Annotations(app)
    private val stagingStore = StagingStore(app)
    private val pusher = StagingPusher(app)
    private val eventsStore = Events(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Whenever the auto-push mode changes, re-apply the WorkManager schedule.
        viewModelScope.launch {
            prefs.flow
                .map { it.autoPushMode }
                .distinctUntilChanged()
                .collect { AutoPushScheduler.apply(getApplication(), it) }
        }
        viewModelScope.launch {
            @Suppress("UNCHECKED_CAST")
            val flows = arrayOf(
                prefs.flow,
                history.flow,
                SyncBus.state,
                annotationsStore.flow,
                stagingStore.flow,
                eventsStore.flow,
            ) as Array<kotlinx.coroutines.flow.Flow<Any?>>
            combine(*flows) { arr -> arr }.collect { tuple ->
                @Suppress("UNCHECKED_CAST")
                val p = tuple[0] as UserPreferences
                @Suppress("UNCHECKED_CAST")
                val h = tuple[1] as List<SyncHistoryEntry>
                val bus = tuple[2] as SyncBus.State
                @Suppress("UNCHECKED_CAST")
                val ann = tuple[3] as Map<String, ReadingAnnotation>
                @Suppress("UNCHECKED_CAST")
                val staged = tuple[4] as List<StagedReading>
                @Suppress("UNCHECKED_CAST")
                val events = tuple[5] as List<HealthEvent>
                _state.update {
                    it.copy(
                        prefs = p,
                        syncHistory = h,
                        syncing = bus.running,
                        lastMessage = bus.lastMessage ?: it.lastMessage,
                        lowBatteryFlag = bus.lastLowBatteryFlag,
                        annotations = ann,
                        staged = staged,
                        events = events,
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
                // 1000 is the Health Connect page-size ceiling. At 4–8 fingersticks
                // a day this covers 125–250 days — enough to populate the 90d
                // Insights window without paginating.
                healthRepo.readRecentReadings(1000)
            } else emptyList()

            _state.update {
                it.copy(
                    bluetoothEnabled = btAdapter?.isEnabled == true,
                    healthConnectState = hcState,
                    meters = meters,
                    recentReadings = readings,
                )
            }
            if (hcState == HealthConnectState.READY && readings.isNotEmpty()) {
                WearBridge.push(getApplication())
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

    fun setChartRange(minMgDl: Double, maxMgDl: Double) {
        viewModelScope.launch { prefs.setChartRange(minMgDl, maxMgDl) }
    }

    fun setFastingRange(low: Double, high: Double) {
        viewModelScope.launch { prefs.setFastingRange(low, high) }
    }
    fun setPreMealRange(low: Double, high: Double) {
        viewModelScope.launch { prefs.setPreMealRange(low, high) }
    }
    fun setPostMealRange(low: Double, high: Double) {
        viewModelScope.launch { prefs.setPostMealRange(low, high) }
    }

    fun setWarningBuffer(buffer: Double) {
        viewModelScope.launch { prefs.setWarningBuffer(buffer) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun clearSyncHistory() {
        viewModelScope.launch { history.clear() }
    }

    // ─── Staging ───

    fun updateStaged(id: String, transform: (StagedReading) -> StagedReading) {
        viewModelScope.launch { stagingStore.update(id, transform) }
    }

    fun discardStaged(id: String) {
        viewModelScope.launch { stagingStore.remove(listOf(id)) }
    }

    fun pushStaged(ids: Collection<String>? = null) {
        if (_state.value.pushing) return
        val all = _state.value.staged
        val toPush = if (ids == null) all else all.filter { it.id in ids }
        if (toPush.isEmpty()) return
        _state.update { it.copy(pushing = true, lastMessage = "Pushing ${toPush.size} to Health Connect…") }
        viewModelScope.launch {
            pusher.push(toPush).fold(
                onSuccess = { report ->
                    _state.update { it.copy(pushing = false,
                        lastMessage = "Pushed ${report.written} to Health Connect") }
                },
                onFailure = { t ->
                    _state.update { it.copy(pushing = false,
                        lastMessage = "Push failed: ${t.message}") }
                },
            )
            refresh()
        }
    }

    fun setAutoPushMode(mode: AutoPushMode) {
        viewModelScope.launch { prefs.setAutoPushMode(mode) }
    }

    // ─── Synced (Health Connect) edits ───

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

    fun logEvent(event: HealthEvent) {
        viewModelScope.launch { eventsStore.add(event) }
    }

    fun removeEvent(id: String) {
        viewModelScope.launch { eventsStore.remove(id) }
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

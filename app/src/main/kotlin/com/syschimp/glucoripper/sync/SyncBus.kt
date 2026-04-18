package com.syschimp.glucoripper.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide coordination for sync runs.
 *
 * Both the companion-device wake service and the UI "Sync now" button need to
 * trigger syncs, but Android's BLE stack chokes if two `BluetoothGatt` instances
 * are talking to the same device simultaneously. This singleton guards against
 * that with a single mutex and publishes the current state so the UI can react.
 */
object SyncBus {
    /** Held for the entire duration of an in-flight sync. */
    val mutex = Mutex()

    data class State(
        val running: Boolean = false,
        val currentAddress: String? = null,
        val lastMessage: String? = null,
        val lastLowBatteryFlag: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setRunning(address: String) {
        _state.value = _state.value.copy(running = true, currentAddress = address)
    }

    fun setIdle(message: String?, lowBattery: Boolean) {
        _state.value = State(
            running = false,
            currentAddress = null,
            lastMessage = message,
            lastLowBatteryFlag = lowBattery,
        )
    }
}

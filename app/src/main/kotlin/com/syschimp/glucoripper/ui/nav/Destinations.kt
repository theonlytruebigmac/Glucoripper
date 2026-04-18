package com.syschimp.glucoripper.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching as OutlinedBluetoothSearchingIcon
import androidx.compose.material.icons.filled.History as FilledHistoryIcon
import androidx.compose.material.icons.filled.Settings as FilledSettingsIcon
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.outlined.History as OutlinedHistoryIcon
import androidx.compose.material.icons.outlined.Settings as OutlinedSettingsIcon
import androidx.compose.material.icons.outlined.SpaceDashboard as OutlinedSpaceDashboardIcon
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopDestination(val route: String, val label: String) {
    Dashboard("dashboard", "Dashboard"),
    History("history", "History"),
    Devices("devices", "Devices"),
    Settings("settings", "Settings");

    val iconOutlined: ImageVector
        get() = when (this) {
            Dashboard -> Icons.Outlined.OutlinedSpaceDashboardIcon
            History -> Icons.Outlined.OutlinedHistoryIcon
            Devices -> Icons.AutoMirrored.Outlined.OutlinedBluetoothSearchingIcon
            Settings -> Icons.Outlined.OutlinedSettingsIcon
        }

    val iconFilled: ImageVector
        get() = when (this) {
            Dashboard -> Icons.Filled.SpaceDashboard
            History -> Icons.Filled.FilledHistoryIcon
            Devices -> Icons.AutoMirrored.Filled.BluetoothSearching
            Settings -> Icons.Filled.FilledSettingsIcon
        }

    companion object {
        val ordered: List<TopDestination> = listOf(Dashboard, History, Devices, Settings)
    }
}

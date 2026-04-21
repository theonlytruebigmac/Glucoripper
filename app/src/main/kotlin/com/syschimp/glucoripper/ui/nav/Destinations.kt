package com.syschimp.glucoripper.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History as FilledHistoryIcon
import androidx.compose.material.icons.filled.Insights as FilledInsightsIcon
import androidx.compose.material.icons.filled.Settings as FilledSettingsIcon
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.outlined.History as OutlinedHistoryIcon
import androidx.compose.material.icons.outlined.Insights as OutlinedInsightsIcon
import androidx.compose.material.icons.outlined.Settings as OutlinedSettingsIcon
import androidx.compose.material.icons.outlined.SpaceDashboard as OutlinedSpaceDashboardIcon
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopDestination(val route: String, val label: String) {
    Dashboard("dashboard", "Dashboard"),
    History("history", "History"),
    Insights("insights", "Insights"),
    Settings("settings", "Settings");

    val iconOutlined: ImageVector
        get() = when (this) {
            Dashboard -> Icons.Outlined.OutlinedSpaceDashboardIcon
            History -> Icons.Outlined.OutlinedHistoryIcon
            Insights -> Icons.Outlined.OutlinedInsightsIcon
            Settings -> Icons.Outlined.OutlinedSettingsIcon
        }

    val iconFilled: ImageVector
        get() = when (this) {
            Dashboard -> Icons.Filled.SpaceDashboard
            History -> Icons.Filled.FilledHistoryIcon
            Insights -> Icons.Filled.FilledInsightsIcon
            Settings -> Icons.Filled.FilledSettingsIcon
        }

    companion object {
        val ordered: List<TopDestination> = listOf(Dashboard, History, Insights, Settings)
    }
}

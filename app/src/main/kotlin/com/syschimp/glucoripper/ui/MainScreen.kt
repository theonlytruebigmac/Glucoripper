package com.syschimp.glucoripper.ui

import android.content.IntentSender
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.syschimp.glucoripper.ui.nav.TopDestination
import com.syschimp.glucoripper.ui.screens.DashboardScreen
import com.syschimp.glucoripper.ui.screens.HistoryScreen
import com.syschimp.glucoripper.ui.screens.InsightsScreen
import com.syschimp.glucoripper.ui.screens.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onPairMeter: suspend () -> IntentSender,
    onRequestHealthPermissions: () -> Unit,
    onExportCsv: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.lastMessage) {
        // Call showSnackbar directly (no wrapping scope.launch) so that the
        // LaunchedEffect cancelling on a new message also dismisses the prior
        // snackbar, preventing them from stacking on rapid state changes.
        state.lastMessage?.let { msg -> snackbarHostState.showSnackbar(msg) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                TopDestination.ordered.forEach { destination ->
                    val selected = backStackEntry?.destination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(TopDestination.Dashboard.route) {
                                        saveState = true
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) destination.iconFilled
                                else destination.iconOutlined,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Dashboard.route,
            modifier = Modifier.padding(inner),
        ) {
            composable(TopDestination.Dashboard.route) {
                DashboardScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onSyncNow = viewModel::syncNow,
                    onRequestHealthPermissions = onRequestHealthPermissions,
                    onPushStaged = viewModel::pushStaged,
                    onUpdateStaged = viewModel::updateStaged,
                    onDiscardStaged = viewModel::discardStaged,
                    onSetMealRelation = viewModel::setMealRelation,
                    onSetFeeling = viewModel::setFeeling,
                    onSetNote = viewModel::setNote,
                    onLogEvent = viewModel::logEvent,
                    onRemoveEvent = viewModel::removeEvent,
                    onNavigateToHistory = {
                        navController.navigate(TopDestination.History.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToDevices = {
                        navController.navigate(TopDestination.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToInsights = {
                        navController.navigate(TopDestination.Insights.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(TopDestination.History.route) {
                HistoryScreen(
                    state = state,
                    onSetMealRelation = viewModel::setMealRelation,
                    onSetFeeling = viewModel::setFeeling,
                    onSetNote = viewModel::setNote,
                )
            }
            composable(TopDestination.Insights.route) {
                InsightsScreen(state = state)
            }
            composable(TopDestination.Settings.route) {
                SettingsScreen(
                    state = state,
                    onPairMeter = onPairMeter,
                    onSyncMeter = viewModel::syncNow,
                    onForceResyncMeter = viewModel::forceFullResync,
                    onUnpairMeter = viewModel::unpair,
                    onRequestHealthPermissions = onRequestHealthPermissions,
                    onSaveUnit = viewModel::setUnit,
                    onSaveRange = viewModel::setTargetRange,
                    onSaveChartRange = viewModel::setChartRange,
                    onSaveThemeMode = viewModel::setThemeMode,
                    onSaveFastingRange = viewModel::setFastingRange,
                    onSavePreMealRange = viewModel::setPreMealRange,
                    onSavePostMealRange = viewModel::setPostMealRange,
                    onSaveWarningBuffer = viewModel::setWarningBuffer,
                    onSaveAutoPushMode = viewModel::setAutoPushMode,
                    onExportCsv = onExportCsv,
                    onClearHistory = viewModel::clearSyncHistory,
                )
            }
        }
    }
}

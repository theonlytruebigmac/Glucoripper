package com.syschimp.glucoripper

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.health.connect.client.PermissionController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.syschimp.glucoripper.ui.MainScreen
import com.syschimp.glucoripper.ui.MainViewModel
import com.syschimp.glucoripper.ui.theme.GlucoripperTheme
import com.syschimp.glucoripper.ui.theme.resolveDarkTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    private val healthPermissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(viewModel.healthPermissions())) {
            viewModel.onHealthPermissionsGranted()
        }
        viewModel.refresh()
    }

    private val csvExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsv(uri) { count ->
                Toast.makeText(
                    this,
                    if (count > 0) "Exported $count readings" else "Export failed",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        setContent {
            val state by viewModel.state.collectAsState()
            val darkTheme = resolveDarkTheme(state.prefs.themeMode)
            // Keep status-bar icon color in sync with theme
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !darkTheme
            GlucoripperTheme(darkTheme = darkTheme) {
                MainScreen(
                    viewModel = viewModel,
                    onPairMeter = { viewModel.requestPairingIntent() },
                    onRequestHealthPermissions = {
                        healthPermissionsLauncher.launch(viewModel.healthPermissions())
                    },
                    onExportCsv = {
                        csvExportLauncher.launch("glucoripper_${LocalDate.now()}.csv")
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun requestRuntimePermissions() {
        val needed = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        runtimePermissions.launch(needed)
    }
}

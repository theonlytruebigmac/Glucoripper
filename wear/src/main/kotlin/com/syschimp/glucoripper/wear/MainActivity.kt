package com.syschimp.glucoripper.wear

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.syschimp.glucoripper.wear.data.GlucosePayload
import com.syschimp.glucoripper.wear.data.GlucoseStore
import com.syschimp.glucoripper.wear.ui.GlucoWearTheme
import com.syschimp.glucoripper.wear.ui.WearHomePager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainActivity : ComponentActivity() {

    private val viewModel: GlucoseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            GlucoWearTheme {
                WearHomePager(state)
            }
        }
    }
}

class GlucoseViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GlucoseStore(app)

    val state: StateFlow<GlucosePayload> = store.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        GlucosePayload.Empty,
    )
}

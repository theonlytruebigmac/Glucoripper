package com.syschimp.glucoripper.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.syschimp.glucoripper.wear.data.GlucosePayload
import com.syschimp.glucoripper.wear.data.GlucoseStore
import com.syschimp.glucoripper.wear.ui.WearMainScreen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = GlucoseStore(applicationContext)
        setContent {
            val state by remember {
                store.flow.stateIn(scope, SharingStarted.Eagerly, GlucosePayload.Empty)
            }.collectAsState()
            WearMainScreen(state)
        }
    }
}

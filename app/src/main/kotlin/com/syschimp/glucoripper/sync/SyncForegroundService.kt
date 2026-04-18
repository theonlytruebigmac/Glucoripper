package com.syschimp.glucoripper.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.syschimp.glucoripper.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service: fired by [com.syschimp.glucoripper.companion.MeterCompanionService]
 * when the meter comes into range. Runs one sync and stops itself.
 */
class SyncForegroundService : Service() {
    private val tag = "SyncService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        val forceFull = intent?.getBooleanExtra(EXTRA_FORCE_FULL, false) == true
        if (address.isNullOrBlank()) {
            Log.w(tag, "Missing meter address; stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startAsForeground()

        if (currentJob?.isActive == true) {
            Log.i(tag, "Sync already running; ignoring duplicate trigger")
            return START_NOT_STICKY
        }

        currentJob = scope.launch {
            try {
                val result = SyncCoordinator(applicationContext).runOnce(address, forceFull)
                result
                    .onSuccess {
                        Log.i(
                            tag,
                            "Sync ok: pulled=${it.pulled} staged=${it.staged} " +
                                    "skippedCtrl=${it.skippedControlSolutions} highest=${it.highestSequence}"
                        )
                        notifyResult(
                            title = getString(R.string.notif_sync_done),
                            text = getString(R.string.notif_sync_done_body, it.staged),
                        )
                    }
                    .onFailure {
                        Log.e(tag, "Sync failed", it)
                        notifyResult(
                            title = getString(R.string.notif_sync_failed),
                            text = it.message ?: "Unknown error",
                        )
                    }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        ensureChannel(this)
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.notif_syncing))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID_ONGOING,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIF_ID_ONGOING, notif)
        }
    }

    private fun notifyResult(title: String, text: String) {
        ensureChannel(this)
        val nm = getSystemService<NotificationManager>() ?: return
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(NOTIF_ID_RESULT, notif)
    }

    companion object {
        private const val EXTRA_ADDRESS = "meter_address"
        private const val EXTRA_FORCE_FULL = "force_full"
        private const val CHANNEL_ID = "glucose_sync"
        private const val NOTIF_ID_ONGOING = 1001
        private const val NOTIF_ID_RESULT = 1002

        fun trigger(context: Context, address: String, forceFull: Boolean = false) {
            val intent = Intent(context, SyncForegroundService::class.java)
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_FORCE_FULL, forceFull)
            context.startForegroundService(intent)
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService<NotificationManager>() ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_sync),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }
}

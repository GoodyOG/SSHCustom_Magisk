package com.sshcustom.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sshcustom.app.R
import com.sshcustom.app.SshCustomApplication
import com.sshcustom.app.data.ApiClient
import com.sshcustom.app.data.StatusData
import com.sshcustom.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

/**
 * Foreground service that keeps a live status connection to the daemon
 * and updates the persistent notification. Falls back to polling if
 * SSE is unavailable (daemon offline or not initialized yet).
 */
class TunnelMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sse: EventSource? = null
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat(buildNotif(getString(R.string.notif_monitor_text_busy)))
        connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        sse?.cancel()
        sse = null
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun connect() {
        if (sse != null || pollJob != null) return
        try {
            val source = ApiClient.openEvents(object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    stopPolling()
                }

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (type != "status") return
                    runCatching {
                        ApiClient.json.decodeFromString(StatusData.serializer(), data)
                    }.onSuccess { updateNotif(notifText(it)) }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    sse = null
                    startPolling()
                }
            })
            if (source != null) {
                sse = source
            } else {
                // ApiClient not initialized — fall back to polling
                startPolling()
            }
        } catch (_: Exception) {
            startPolling()
        }
    }

    private fun startPolling() {
        if (pollJob != null) return
        pollJob = scope.launch {
            while (true) {
                try {
                    ApiClient.status()
                        .onSuccess { updateNotif(notifText(it)) }
                        .onFailure { updateNotif(getString(R.string.notif_monitor_text_offline)) }
                } catch (_: Exception) {
                    updateNotif(getString(R.string.notif_monitor_text_offline))
                }
                delay(5000)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun notifText(status: StatusData): String {
        val rt = status.runtime
        return when {
            rt.connected && rt.sshAuthenticated -> {
                val via = rt.selectedProfile.ifBlank { "tunnel" }
                getString(R.string.notif_monitor_text_connected, via)
            }
            rt.running -> getString(R.string.notif_monitor_text_busy)
            else -> getString(R.string.notif_monitor_text_offline)
        }
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotif(text))
    }

    private fun buildNotif(text: String): Notification {
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, SshCustomApplication.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_monitor_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    private fun startForegroundCompat(notif: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val NOTIF_ID = 4242
    }
}

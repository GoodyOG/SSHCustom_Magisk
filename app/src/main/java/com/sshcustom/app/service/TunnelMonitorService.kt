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
 * Foreground service. Two responsibilities:
 *
 * 1. **Persistent reminder.** The notification stays visible while the
 *    user has the app or the tile in active use, so it's clear which
 *    process owns the SSH tunnel surface.
 * 2. **Live status pipe.** The service holds a long-lived SSE connection
 *    to /api/v1/events and updates the notification text every time the
 *    daemon's state changes. On Android 8+ this is the most reliable way
 *    to keep a network read alive across battery savers and Doze.
 *
 * Lifecycle:
 *   - Started by MainActivity when the user opens the app.
 *   - Started by BootReceiver after BOOT_COMPLETED.
 *   - Stopped only when the user explicitly removes the app from recents
 *     AND the daemon is offline (see hasOpenWork()). The service does
 *     NOT auto-stop on activity destruction so the user keeps getting
 *     notification updates with the app backgrounded.
 *
 * If SSE fails (legacy daemon, proxy weirdness, etc.) we fall back to
 * polling /api/v1/status every 5 s — same fallback the WebUI uses.
 */
class TunnelMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sse: EventSource? = null
    private var pollJob: Job? = null
    private var lastStatus: StatusData? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat(buildNotif(text = getString(R.string.notif_monitor_text_busy)))
        connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // onCreate already started the foreground state; STICKY so Android
        // restarts us if the process is killed.
        return START_STICKY
    }

    override fun onDestroy() {
        sse?.cancel()
        sse = null
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun connect() {
        if (sse != null || pollJob != null) return
        sse = ApiClient.openEvents(object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                stopPolling()
                updateNotif(getString(R.string.notif_monitor_text_busy))
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type != "status") return
                runCatching {
                    ApiClient.json.decodeFromString(StatusData.serializer(), data)
                }.onSuccess { status ->
                    lastStatus = status
                    updateNotif(notifText(status))
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                sse = null
                // Fall back to slow polling. We don't aggressively reconnect
                // SSE here because the foreground service is a passive
                // observer; if the user re-opens the app, MainActivity
                // restarts the connection through MainViewModel.
                startPolling()
            }
        })
    }

    private fun startPolling() {
        if (pollJob != null) return
        pollJob = scope.launch {
            while (true) {
                ApiClient.status().onSuccess { status ->
                    lastStatus = status
                    updateNotif(notifText(status))
                }.onFailure {
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
                val via = rt.iface.takeIf { it.isNotBlank() }
                    ?: rt.selectedProfile.takeIf { it.isNotBlank() }
                    ?: "tunnel"
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

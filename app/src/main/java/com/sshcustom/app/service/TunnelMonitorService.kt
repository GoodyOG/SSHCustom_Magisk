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
import com.sshcustom.app.ui.MainActivity

/**
 * Foreground service that owns the persistent notification while the user
 * has the app open. Two reasons it exists:
 *
 * 1. **Battery savers.** Aggressive OEMs (Xiaomi/MIUI, Samsung One UI in
 *    deep-sleep) will kill background SSE connections. A foreground
 *    service keeps the OkHttp socket alive long enough to react to
 *    daemon state changes — same trick KSU-Next uses for its module
 *    status updates.
 * 2. **Notification posture.** Users explicitly asked for "is this
 *    really controlling my tunnel?" feedback; the persistent
 *    notification is the cheapest answer.
 *
 * The full Quick Settings Tile + autostart hooks land in PR 5; this
 * file is the skeleton so PR 4 doesn't need to touch the service
 * lifecycle on top of all the UI work.
 */
class TunnelMonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = NotificationCompat.Builder(this, SshCustomApplication.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_monitor_title))
            .setContentText(getString(R.string.notif_monitor_text_busy))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

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

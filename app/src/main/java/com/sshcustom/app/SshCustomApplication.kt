package com.sshcustom.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.sshcustom.app.data.ApiClient
import com.topjohnwu.superuser.Shell

/**
 * Application entry point.
 *
 * Three things happen here:
 *
 * 1. **libsu shell defaults.** Configured at the application level so every
 *    [Shell.cmd] call inherits a consistent timeout. The 10-second mount-master
 *    timeout matches what KSU-Next / Magisk Manager use.
 * 2. **Notification channel registration.** The foreground monitor service
 *    needs a channel with at least IMPORTANCE_LOW so the persistent
 *    notification can show without making sound.
 * 3. **API client warmup.** Building the OkHttp client once at startup means
 *    the first network call in the UI doesn't pay TLS/connection-pool init
 *    cost. The daemon is on loopback so this is microseconds, but it also
 *    primes the SSE EventSource factory.
 */
class SshCustomApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10L)
        )

        registerNotificationChannels()

        ApiClient.initialize(this)
    }

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_MONITOR,
                getString(R.string.notif_channel_monitor),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_monitor_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_MONITOR = "tunnel_monitor"
    }
}

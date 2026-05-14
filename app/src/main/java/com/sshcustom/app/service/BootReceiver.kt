package com.sshcustom.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.sshcustom.app.data.RootClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BOOT_COMPLETED handler. Two responsibilities:
 *
 * 1. **Defer to the Magisk module for daemon autostart.** If the user
 *    enabled Autostart in Settings, the on-disk marker
 *    `<run_dir>/autostart` is present and `service.sh` from the Magisk
 *    module already handles starting the daemon. We do NOT duplicate
 *    that logic here. The marker is authoritative; the app just
 *    respects it.
 * 2. **Start the foreground monitor service** so the user has the
 *    persistent notification immediately on reboot, without needing to
 *    open the app first.
 *
 * Background service starts on Android 8+ are restricted; we use
 * ContextCompat.startForegroundService which works under the
 * BOOT_COMPLETED exemption all the way through Android 14.
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }
        Log.i(TAG, "boot completed; starting tunnel monitor service")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val svc = Intent(context, TunnelMonitorService::class.java)
                ContextCompat.startForegroundService(context, svc)
                if (RootClient.isRooted()) {
                    Log.i(TAG, "root available; service.sh has authority over daemon start")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SshCustom.Boot"
    }
}

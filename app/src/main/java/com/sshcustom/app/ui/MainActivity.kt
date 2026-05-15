package com.sshcustom.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.sshcustom.app.R
import com.sshcustom.app.service.TunnelMonitorService
import com.sshcustom.app.ui.screens.AppRoot
import com.sshcustom.app.ui.theme.SshCustomTheme

/**
 * Single-activity entry point. The whole app is one Compose root with a
 * bottom navigation bar driving four screens; activity lifecycle is the
 * unit of session for the SSE connection too (see [MainViewModel]).
 *
 * The splash theme set in the manifest holds the launcher icon for the
 * fraction of a second between app launch and the Compose first frame;
 * after that we reset to the regular theme so the Compose surface
 * background takes over.
 */
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Whether granted or not, try starting the service. On Android 13+
        // without the permission, the notification just won't show — but
        // the service still runs and keeps the SSE connection alive. We
        // gracefully degrade rather than crashing.
        startMonitorServiceSafe()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_SshCustom)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS on Android 13+ before starting the
        // foreground service. Without this, startForeground() crashes on
        // API 33+ because the system can't post the required notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startMonitorServiceSafe()
            }
        } else {
            startMonitorServiceSafe()
        }

        setContent {
            SshCustomTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(viewModel = vm)
                }
            }
        }
    }

    /**
     * Start the foreground monitor service wrapped in a try/catch. On some
     * devices (especially with aggressive battery savers), starting a
     * foreground service can throw if the app is considered "background".
     * We never want the app to crash just because the service couldn't start.
     */
    private fun startMonitorServiceSafe() {
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, TunnelMonitorService::class.java),
            )
        } catch (e: Exception) {
            // Swallow: the app works fine without the service — it just
            // won't have a persistent notification or live status updates
            // when backgrounded. The SSE connection in the ViewModel still
            // works independently.
            android.util.Log.w("SshCustom", "Could not start monitor service: ${e.message}")
        }
    }
}

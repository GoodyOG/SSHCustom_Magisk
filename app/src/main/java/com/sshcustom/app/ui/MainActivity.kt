package com.sshcustom.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.sshcustom.app.R
import com.sshcustom.app.data.RootClient
import com.sshcustom.app.service.TunnelMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single-activity WebView shell. The entire dashboard UI is served by
 * the daemon at http://127.0.0.1:9190/. The app provides:
 *   - Foreground service (persistent notification + SSE status updates)
 *   - Quick Settings Tile (one-tap toggle)
 *   - Boot receiver (autostart)
 *   - Root access for daemon lifecycle when API is unreachable
 *
 * If the daemon is not running, the activity shows a simple offline
 * placeholder with a "Start Module" button that uses libsu.
 */
class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var webView: WebView
    private lateinit var offlineView: View
    private var daemonReachable = false

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> startMonitorServiceSafe() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startMonitorServiceSafe()
            }
        } else {
            startMonitorServiceSafe()
        }

        // Build layout programmatically — no XML needed for a WebView shell
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(0xFF121212.toInt())
        }

        // Offline placeholder
        offlineView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )

            val title = TextView(this@MainActivity).apply {
                text = getString(R.string.app_name)
                textSize = 22f
                setTextColor(0xFFE0E0E0.toInt())
                gravity = android.view.Gravity.CENTER
            }
            addView(title)

            val subtitle = TextView(this@MainActivity).apply {
                text = "Daemon offline. Start the module first."
                textSize = 14f
                setTextColor(0xFF9E9E9E.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 16, 0, 32)
            }
            addView(subtitle)

            val btn = android.widget.Button(this@MainActivity).apply {
                text = "Start Module"
                setOnClickListener {
                    scope.launch(Dispatchers.IO) {
                        RootClient.control("start")
                        delay(2000)
                        launch(Dispatchers.Main) { loadDashboard() }
                    }
                }
            }
            addView(btn)
        }
        root.addView(offlineView)

        // WebView
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportZoom(false)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    daemonReachable = true
                    visibility = View.VISIBLE
                    offlineView.visibility = View.GONE
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        daemonReachable = false
                        visibility = View.GONE
                        offlineView.visibility = View.VISIBLE
                    }
                }
            }
            webChromeClient = WebChromeClient()
        }
        root.addView(webView)

        setContentView(root)
        loadDashboard()
    }

    private fun loadDashboard() {
        webView.loadUrl("http://127.0.0.1:9190/")
    }

    @Deprecated("Use OnBackPressedDispatcher", ReplaceWith(""))
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload if the daemon was offline and may now be online
        if (!daemonReachable) {
            loadDashboard()
        }
    }

    private fun startMonitorServiceSafe() {
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, TunnelMonitorService::class.java),
            )
        } catch (e: Exception) {
            android.util.Log.w("SshCustom", "Monitor service start failed: ${e.message}")
        }
    }
}

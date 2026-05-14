package com.sshcustom.app.ui.screens

import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sshcustom.app.R
import com.sshcustom.app.ui.MainViewModel
import com.sshcustom.app.ui.components.ChipKind
import com.sshcustom.app.ui.components.InfoCard
import com.sshcustom.app.ui.components.SectionPill
import com.sshcustom.app.ui.components.StatusChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LogKind(val key: String, val labelRes: Int) {
    Core("core", R.string.runtime_log_core),
    Control("control", R.string.runtime_log_control),
    Action("action", R.string.runtime_log_action),
}

@Composable
fun RuntimeScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val status by viewModel.status.collectAsState()
    val rt = status?.runtime
    val paths = status?.paths

    var selectedLog by rememberSaveable { mutableStateOf(LogKind.Core) }
    var logText by rememberSaveable { mutableStateOf("Loading log…") }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedLog) {
        logText = viewModel.fetchLog(selectedLog.key)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionPill(stringResource(R.string.runtime_section_details)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                InfoCard(
                    label = "SSH Pool",
                    value = "${rt?.poolHealthy ?: 0}/${rt?.poolSize ?: 0}",
                    supporting = "${rt?.poolReconnecting ?: 0} reconnecting" +
                            (rt?.poolLastError?.takeIf { it.isNotBlank() }?.let { " / $it" } ?: ""),
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
                InfoCard(
                    label = "Streams",
                    value = (rt?.poolStreams ?: 0).toString(),
                    supporting = "target ${rt?.poolMaxStreams ?: 0} per SSH",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                InfoCard(
                    label = "CPU",
                    value = "%.1f%%".format(rt?.cpuPercent ?: 0.0),
                    supporting = "daemon process",
                    modifier = Modifier.weight(1f),
                )
                InfoCard(
                    label = "Memory",
                    value = fmtBytes(rt?.memoryRssBytes ?: 0),
                    supporting = "system mem ${"%.0f".format(rt?.systemMemUsedPercent ?: 0.0)}%",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                InfoCard(
                    label = "Route",
                    value = rt?.iface?.takeIf { it.isNotBlank() } ?: "--",
                    supporting = rt?.gateway?.takeIf { it.isNotBlank() }?.let { "gateway $it" }
                        ?: rt?.sourceIp?.takeIf { it.isNotBlank() }
                        ?: "gateway unavailable",
                    modifier = Modifier.weight(1f),
                )
                InfoCard(
                    label = "Resolver",
                    value = rt?.resolverMethod?.takeIf { it.isNotBlank() }
                        ?: rt?.dnsMode?.takeIf { it.isNotBlank() }
                        ?: "--",
                    supporting = rt?.resolvedIps?.joinToString(", ")
                        ?.takeIf { it.isNotBlank() }
                        ?: "no DNS activity yet",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { SectionPill(stringResource(R.string.runtime_section_paths)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = paths?.let {
                        listOfNotNull(
                            "work_dir: ${it.workDir}".takeIf { _ -> it.workDir.isNotBlank() },
                            "config_path: ${it.configPath}".takeIf { _ -> it.configPath.isNotBlank() },
                            "profiles_path: ${it.profilesPath}".takeIf { _ -> it.profilesPath.isNotBlank() },
                            "run_dir: ${it.runDir}".takeIf { _ -> it.runDir.isNotBlank() },
                            "webroot: ${it.webroot}".takeIf { _ -> it.webroot.isNotBlank() },
                        ).joinToString("\n")
                    }?.takeIf { it.isNotBlank() } ?: "Paths are available when the daemon is online.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        item { SectionPill(stringResource(R.string.runtime_section_logs)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LogKind.entries.forEach { kind ->
                    StatusChip(
                        text = stringResource(kind.labelRes),
                        kind = if (kind == selectedLog) ChipKind.Primary else ChipKind.Neutral,
                        modifier = Modifier.weight(1f).clickable { selectedLog = kind },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { scope.launch { logText = viewModel.fetchLog(selectedLog.key) } },
                    modifier = Modifier.weight(1f),
                ) { Text("Refresh") }
                FilledTonalButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(stringResource(R.string.runtime_log_clear)) }
            }
        }
        item {
            // The logs are rendered in a WebView pointing at the daemon's
            // /api/v1/logs/<kind> endpoint so the styling stays in lockstep
            // with the WebUI. The native log fetch above is kept as a
            // fallback so refresh still has fresh data even when the
            // WebView is suspended.
            Card(
                modifier = Modifier.fillMaxWidth().height(380.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(8.dp),
            ) {
                LogWebView(kind = selectedLog.key)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.runtime_log_clear_confirm_title)) },
            text = { Text(stringResource(R.string.runtime_log_clear_confirm_body)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearLog(selectedLog.key)
                    confirmClear = false
                    scope.launch {
                        delay(400)
                        logText = viewModel.fetchLog(selectedLog.key)
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LogWebView(kind: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    setSupportZoom(false)
                }
                webViewClient = WebViewClient()
                isVerticalScrollBarEnabled = true
            }
        },
        update = { web ->
            web.loadUrl("http://127.0.0.1:9190/api/v1/logs/$kind")
        },
    )
}

private fun fmtBytes(n: Long): String = when {
    n <= 0 -> "--"
    n >= 1_073_741_824 -> "%.1f GB".format(n / 1_073_741_824.0)
    n >= 1_048_576 -> "%.1f MB".format(n / 1_048_576.0)
    n >= 1_024 -> "%d KB".format(n / 1024)
    else -> "$n B"
}

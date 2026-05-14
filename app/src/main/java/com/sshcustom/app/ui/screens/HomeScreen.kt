package com.sshcustom.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sshcustom.app.R
import com.sshcustom.app.data.RuntimeState
import com.sshcustom.app.ui.MainViewModel
import com.sshcustom.app.ui.components.ChipKind
import com.sshcustom.app.ui.components.InfoCard
import com.sshcustom.app.ui.components.OfflineBanner
import com.sshcustom.app.ui.components.SectionPill
import com.sshcustom.app.ui.components.StatusChip

/**
 * Home tab. Mirrors the WebUI's home page so users get the same
 * information whether they open the app or `127.0.0.1:9190`.
 */
@Composable
fun HomeScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val status by viewModel.status.collectAsState()
    val publicIp by viewModel.publicIp.collectAsState()
    val rooted by viewModel.rooted.collectAsState()
    val runtime: RuntimeState? = status?.runtime

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeroCard(runtime = runtime)
        }

        if (rooted == false) {
            item { OfflineBanner(stringResource(R.string.err_root_unavailable)) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.controlAction("restart") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.home_action_restart))
                }
                FilledTonalButton(
                    onClick = { viewModel.controlAction("stop") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(stringResource(R.string.home_action_stop))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                InfoCard(
                    label = stringResource(R.string.home_card_tunnel_ip),
                    value = publicIp?.tunnel?.takeIf { it.ok }?.ip ?: "Unavailable",
                    supporting = publicIp?.tunnel?.let { ipMeta(it) } ?: "Waiting for tunnel lookup",
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
                InfoCard(
                    label = stringResource(R.string.home_card_device_ip),
                    value = runtime?.sourceIp.takeIf { !it.isNullOrBlank() } ?: "--",
                    supporting = deviceIpMeta(runtime),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                InfoCard(
                    label = stringResource(R.string.home_card_runtime),
                    value = runtime?.uptimeSeconds?.takeIf { it > 0 }?.let(::fmtUptime) ?: "--",
                    supporting = listOfNotNull(runtime?.selectedMode, runtime?.transportChain)
                        .filter { it.isNotBlank() }
                        .joinToString(" / ")
                        .ifBlank { "No active session" },
                    modifier = Modifier.weight(1f),
                )
                InfoCard(
                    label = stringResource(R.string.home_card_performance),
                    value = "%.1f%% CPU".format(runtime?.cpuPercent ?: 0.0),
                    supporting = "RSS ${fmtBytes(runtime?.memoryRssBytes ?: 0)}, mem ${"%.0f".format(runtime?.systemMemUsedPercent ?: 0.0)}%",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(runtime: RuntimeState?) {
    val connected = runtime?.connected == true && runtime.sshAuthenticated
    val busy = runtime?.running == true && !connected
    val title = when {
        connected -> stringResource(R.string.home_state_connected)
        busy -> runtime?.state?.takeIf { it.isNotBlank() } ?: stringResource(R.string.home_state_connecting)
        else -> stringResource(R.string.home_state_offline)
    }
    val subtitle = runtime?.lastError?.takeIf { it.isNotBlank() }
        ?: runtime?.lastEvent?.takeIf { it.isNotBlank() }
        ?: if (connected) "Transparent TCP and local SOCKS are available."
        else stringResource(R.string.home_subtitle_offline)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(
                    text = when {
                        connected -> "connected"
                        busy -> "connecting"
                        else -> "offline"
                    },
                    kind = when {
                        connected -> ChipKind.Primary
                        busy -> ChipKind.Warn
                        else -> ChipKind.Error
                    },
                )
                if (!runtime?.selectedProfile.isNullOrBlank()) {
                    StatusChip(text = "profile: ${runtime?.selectedProfile}")
                }
                if (!runtime?.iface.isNullOrBlank()) {
                    StatusChip(text = "via: ${runtime?.iface}")
                }
            }
        }
    }
}

private fun fmtUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

private fun fmtBytes(n: Long): String = when {
    n <= 0 -> "--"
    n >= 1_073_741_824 -> "%.1f GB".format(n / 1_073_741_824.0)
    n >= 1_048_576 -> "%.1f MB".format(n / 1_048_576.0)
    n >= 1_024 -> "%d KB".format(n / 1024)
    else -> "$n B"
}

private fun ipMeta(d: com.sshcustom.app.data.PublicIpDetails): String {
    if (!d.ok) return d.error.ifBlank { "lookup failed" }
    val loc = listOf(d.city, d.region, d.country).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "unknown location" }
    val net = listOf(d.isp, d.asn).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "unknown network" }
    return "$loc / $net" + if (d.cached) " / cached" else ""
}

private fun deviceIpMeta(rt: RuntimeState?): String {
    if (rt == null) return "Waiting for daemon"
    val parts = mutableListOf<String>()
    if (rt.iface.isNotBlank()) parts.add("via ${rt.iface}")
    if (rt.gateway.isNotBlank()) parts.add("gateway ${rt.gateway}")
    return if (parts.isEmpty()) {
        if (rt.networkOnline) "route detected" else "no network route"
    } else parts.joinToString(" / ")
}

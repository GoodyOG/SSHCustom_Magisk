package com.sshcustom.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sshcustom.app.R
import com.sshcustom.app.ui.MainViewModel
import com.sshcustom.app.ui.components.SectionPill

private val DNS_MODES = listOf(
    "device" to "Device default",
    "google" to "Google DNS",
    "cloudflare" to "Cloudflare DNS",
    "custom" to "Custom DNS",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val status by viewModel.status.collectAsState()
    val autostart by viewModel.autostart.collectAsState()
    val cfg = status?.config

    var dnsMode by rememberSaveable { mutableStateOf(cfg?.dns?.mode ?: "device") }
    var customServers by rememberSaveable { mutableStateOf((cfg?.dns?.servers ?: emptyList()).joinToString(", ")) }
    var hotspotEnabled by rememberSaveable { mutableStateOf(cfg?.hotspot?.let { it.enabled && it.tcp } ?: false) }
    var dnsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(cfg) {
        // Pull initial values from server config the first time we see it,
        // but don't overwrite user edits on subsequent SSE pushes. Compose's
        // `rememberSaveable` already preserves the in-progress edit; we
        // only re-seed when the user hasn't changed it yet.
        cfg?.let {
            if (dnsMode == "device" && it.dns?.mode != null && it.dns.mode != "device") dnsMode = it.dns.mode
            if (customServers.isBlank() && it.dns?.servers?.isNotEmpty() == true) {
                customServers = it.dns.servers.joinToString(", ")
            }
            it.hotspot?.let { hs -> hotspotEnabled = hs.enabled && hs.tcp }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionPill(stringResource(R.string.settings_section_dns)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_dns_mode), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.settings_dns_subtitle),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    ExposedDropdownMenuBox(expanded = dnsMenu, onExpandedChange = { dnsMenu = !dnsMenu }) {
                        OutlinedTextField(
                            value = DNS_MODES.firstOrNull { it.first == dnsMode }?.second ?: dnsMode,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dnsMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        androidx.compose.material3.ExposedDropdownMenu(expanded = dnsMenu, onDismissRequest = { dnsMenu = false }) {
                            DNS_MODES.forEach { (id, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { dnsMode = id; dnsMenu = false })
                            }
                        }
                    }
                    if (dnsMode == "custom") {
                        OutlinedTextField(
                            value = customServers,
                            onValueChange = { customServers = it },
                            label = { Text(stringResource(R.string.settings_dns_custom_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item { SectionPill(stringResource(R.string.settings_section_sharing)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_hotspot_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_hotspot_subtitle),
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                    Switch(checked = hotspotEnabled, onCheckedChange = { hotspotEnabled = it })
                }
            }
        }
        item {
            Button(
                onClick = {
                    val servers = customServers.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    viewModel.applyNetwork(dnsMode, servers, hotspotEnabled)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_apply))
            }
        }

        item { SectionPill(stringResource(R.string.settings_section_boot)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_autostart_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_autostart_subtitle),
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                    Switch(
                        checked = autostart == true,
                        onCheckedChange = { viewModel.toggleAutostart(it) },
                    )
                }
            }
        }

        item { SectionPill(stringResource(R.string.settings_section_about)) }
        item {
            val ver = status?.runtime?.version
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "version ${ver ?: "--"}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    Text(stringResource(R.string.about_built_by), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openUrl(ctx, "https://github.com/GoodyOG/SSHCustom_Magisk") }) {
                            Text(stringResource(R.string.about_repo))
                        }
                        Button(onClick = { openUrl(ctx, "https://github.com/GoodyOG/SSHCustom_Magisk/issues") }) {
                            Text(stringResource(R.string.about_issues))
                        }
                        Button(onClick = { openUrl(ctx, "https://github.com/GoodyOG/SSHCustom_Magisk/releases") }) {
                            Text(stringResource(R.string.about_releases))
                        }
                    }
                    Text(
                        stringResource(R.string.about_license),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

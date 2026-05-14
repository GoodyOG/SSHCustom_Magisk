package com.sshcustom.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sshcustom.app.R
import com.sshcustom.app.data.HttpProxyConfig
import com.sshcustom.app.data.PayloadConfig
import com.sshcustom.app.data.Profile
import com.sshcustom.app.data.SaveProfileRequest
import com.sshcustom.app.data.SshConfig
import com.sshcustom.app.data.TlsConfig
import com.sshcustom.app.data.TransportConfig
import com.sshcustom.app.ui.MainViewModel
import com.sshcustom.app.ui.components.ChipKind
import com.sshcustom.app.ui.components.SectionPill
import com.sshcustom.app.ui.components.StatusChip

private val MODES = listOf(
    "direct" to "Direct SSH",
    "http_proxy" to "HTTP Proxy",
    "tls_sni" to "TLS/SNI",
    "http_proxy_tls_sni" to "HTTP Proxy + TLS/SNI",
)

@Composable
fun ProfilesScreen(viewModel: MainViewModel, contentPadding: PaddingValues) {
    val pf by viewModel.profiles.collectAsState()
    val list = pf?.profiles.orEmpty()
    var editing: Profile? by remember { mutableStateOf(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { creating = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.profiles_new))
                }
                FilledTonalButton(
                    onClick = { viewModel.refreshProfiles() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.profiles_refresh))
                }
            }
        }

        if (list.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.profiles_empty_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.profiles_empty_subtitle),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        } else {
            items(list, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    isActive = profile.id == pf?.selectedId || profile.selected,
                    onUse = { viewModel.selectProfile(profile.id, restart = false) },
                    onEdit = { editing = profile },
                )
            }
        }
    }

    if (creating || editing != null) {
        ProfileEditorDialog(
            profile = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { req ->
                viewModel.saveProfile(req)
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    isActive: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name.ifBlank { profile.id }, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${profile.ssh.host}:${profile.ssh.port} - ${MODES.firstOrNull { it.first == profile.transport.mode }?.second ?: profile.transport.mode}",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
            if (isActive) {
                StatusChip(stringResource(R.string.profile_active), ChipKind.Primary)
            } else {
                FilledTonalButton(onClick = onUse) { Text(stringResource(R.string.profile_use)) }
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.profile_edit)) }
        }
    }
}

/**
 * Bottom-sheet-ish profile editor. We use AlertDialog rather than a real
 * bottom sheet because the editor has many fields and on small screens the
 * dialog handles scrolling more reliably than ModalBottomSheet for now.
 *
 * The two action buttons exactly mirror the WebUI:
 *   - Save                 (no select, no restart)
 *   - Save, Use & Restart  (select=true, restart=true)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditorDialog(
    profile: Profile?,
    onDismiss: () -> Unit,
    onSave: (SaveProfileRequest) -> Unit,
) {
    val source = profile
    var name by rememberSaveable { mutableStateOf(source?.name ?: "") }
    var mode by rememberSaveable { mutableStateOf(source?.transport?.mode ?: "direct") }
    var host by rememberSaveable { mutableStateOf(source?.ssh?.host ?: "") }
    var portText by rememberSaveable { mutableStateOf(source?.ssh?.port?.toString() ?: "22") }
    var user by rememberSaveable { mutableStateOf(source?.ssh?.username ?: "") }
    var pass by rememberSaveable { mutableStateOf(source?.ssh?.password ?: "") }
    var proxyHost by rememberSaveable { mutableStateOf(source?.transport?.httpProxy?.host ?: "") }
    var proxyPort by rememberSaveable { mutableStateOf(source?.transport?.httpProxy?.port?.toString() ?: "80") }
    var sni by rememberSaveable { mutableStateOf(source?.transport?.tls?.serverName ?: "") }
    var payloadEnabled by rememberSaveable { mutableStateOf(source?.transport?.payload?.enabled ?: false) }
    var payloadTemplate by rememberSaveable {
        mutableStateOf(
            source?.transport?.payload?.template
                ?: "GET / HTTP/1.1[crlf]Host: [sni][crlf]Connection: Upgrade[crlf]Upgrade: websocket[crlf][crlf]"
        )
    }
    var modeMenu by remember { mutableStateOf(false) }

    val usesProxy = mode.contains("http_proxy")
    val usesTls = mode.contains("tls")

    fun build(select: Boolean, restart: Boolean): SaveProfileRequest {
        val port = portText.toIntOrNull() ?: 22
        val proxyP = proxyPort.toIntOrNull() ?: 80
        return SaveProfileRequest(
            id = source?.id ?: "",
            name = name.trim().ifBlank { "SSHCustom Profile" },
            select = select,
            restart = restart,
            ssh = SshConfig(
                host = host.trim(),
                port = port,
                username = user.trim(),
                password = pass,
                authType = "password",
                fallbackIps = source?.ssh?.fallbackIps,
            ),
            transport = TransportConfig(
                mode = mode,
                chain = emptyList(),
                httpProxy = if (usesProxy) HttpProxyConfig(
                    host = proxyHost.trim().ifBlank { host.trim() },
                    port = proxyP,
                    connectMethod = "socket",
                ) else null,
                tls = if (usesTls) TlsConfig(
                    enabled = true,
                    serverName = sni.trim().ifBlank { host.trim() },
                    insecureSkipVerify = true,
                    alpn = listOf("http/1.1"),
                ) else null,
                payload = PayloadConfig(
                    enabled = payloadEnabled,
                    template = payloadTemplate,
                    sendTiming = if (usesProxy) "after_proxy_socket_before_ssh" else "before_ssh",
                    readResponse = true,
                    allowHttpStatus = listOf(101, 200, 204, 302),
                ),
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source == null) "New Profile" else "Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                ExposedDropdownMenuBox(expanded = modeMenu, onExpandedChange = { modeMenu = !modeMenu }) {
                    OutlinedTextField(
                        value = MODES.firstOrNull { it.first == mode }?.second ?: mode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    androidx.compose.material3.ExposedDropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        MODES.forEach { (id, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { mode = id; modeMenu = false })
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("SSH Host") }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                }

                if (usesProxy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = proxyHost, onValueChange = { proxyHost = it }, label = { Text("Proxy Host") }, singleLine = true, modifier = Modifier.weight(2f))
                        OutlinedTextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it.filter { ch -> ch.isDigit() }.take(5) },
                            label = { Text("Proxy Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (usesTls) {
                    OutlinedTextField(value = sni, onValueChange = { sni = it }, label = { Text("SNI Server Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Payload Injection", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Send payload before SSH or after the proxy socket, depending on the selected mode.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                    Switch(checked = payloadEnabled, onCheckedChange = { payloadEnabled = it })
                }
                if (payloadEnabled) {
                    OutlinedTextField(
                        value = payloadTemplate,
                        onValueChange = { payloadTemplate = it },
                        label = { Text("Payload Template") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                    )
                }
            }
        },
        // Two actions, mirroring the WebUI exactly:
        confirmButton = {
            Button(onClick = { onSave(build(select = true, restart = true)) }) {
                Text(stringResource(R.string.profile_save_use_restart))
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = { onSave(build(select = false, restart = false)) }) {
                Text(stringResource(R.string.profile_save))
            }
        },
    )
}

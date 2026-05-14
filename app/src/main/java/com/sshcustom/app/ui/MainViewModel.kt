package com.sshcustom.app.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshcustom.app.data.ApiClient
import com.sshcustom.app.data.ProfileSelectRequest
import com.sshcustom.app.data.ProfilesFile
import com.sshcustom.app.data.Repository
import com.sshcustom.app.data.RootClient
import com.sshcustom.app.data.SaveProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Single ViewModel for the whole UI. We keep one because the four tabs
 * share most of the underlying state (status, profiles) and a single
 * VM avoids the boilerplate of N tab-specific VMs all subscribing to
 * the same repository.
 */
class MainViewModel : ViewModel() {

    private val repo = Repository(viewModelScope)

    val connection = repo.connection
    val status = repo.status
    val profiles = repo.profiles
    val publicIp = repo.publicIp
    val autostart = repo.autostart

    private val _toast = MutableSharedFlow<String>()
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private val _rooted = MutableStateFlow<Boolean?>(null)
    val rooted: StateFlow<Boolean?> = _rooted.asStateFlow()

    init {
        repo.start()
        viewModelScope.launch {
            _rooted.value = RootClient.isRooted()
            repo.refreshProfiles()
            repo.refreshPublicIp(false)
            repo.refreshAutostart()
        }
    }

    override fun onCleared() {
        repo.stop()
        super.onCleared()
    }

    // ---- Lifecycle / control ----

    fun controlAction(action: String) {
        viewModelScope.launch {
            val r = ApiClient.control(action)
            if (r.isSuccess) {
                emitToast(when (action) {
                    "stop" -> "Stopping and cleaning rules…"
                    "restart" -> "Restarting tunnel…"
                    "start" -> "Starting tunnel…"
                    else -> "Action: $action"
                })
                return@launch
            }
            // API offline → fall back to root shell directly.
            when (val rr = RootClient.control(action)) {
                is RootClient.RootResult.Ok -> emitToast("Action sent via root: $action")
                is RootClient.RootResult.Failed -> emitToast("Control failed: ${rr.message}")
                RootClient.RootResult.Denied -> emitToast("Root access denied")
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            repo.refreshProfiles()
            repo.refreshPublicIp(true)
            repo.refreshAutostart()
        }
    }

    fun refreshPublicIp() {
        viewModelScope.launch { repo.refreshPublicIp(true) }
    }

    fun refreshProfiles() {
        viewModelScope.launch { repo.refreshProfiles() }
    }

    // ---- Profiles ----

    fun selectProfile(id: String, restart: Boolean) {
        viewModelScope.launch {
            val r = ApiClient.selectProfile(ProfileSelectRequest(id, restart))
            r.onSuccess {
                emitToast(if (restart) "Profile selected. Restarting…" else "Profile selected. Restart to apply.")
                repo.refreshProfiles()
            }.onFailure { emitToast("Select failed: ${it.message}") }
        }
    }

    fun saveProfile(req: SaveProfileRequest) {
        viewModelScope.launch {
            ApiClient.saveProfile(req)
                .onSuccess {
                    emitToast(if (req.restart) "Profile saved. Restarting…" else "Profile saved.")
                    repo.refreshProfiles()
                }
                .onFailure { emitToast("Profile save failed: ${it.message}") }
        }
    }

    // ---- Settings: DNS + hotspot ----

    fun applyNetwork(dnsMode: String, customServers: List<String>, hotspotEnabled: Boolean) {
        viewModelScope.launch {
            val patch = buildJsonObject {
                put("dns", buildJsonObject {
                    put("mode", dnsMode)
                    if (dnsMode == "custom") {
                        put("servers", buildJsonArray { customServers.forEach { add(it) } })
                    }
                })
                put("hotspot", buildJsonObject {
                    put("enabled", hotspotEnabled)
                    put("tcp", hotspotEnabled)
                    put("dns", false)
                })
                put("restart", true)
            }
            ApiClient.patchConfig(patch)
                .onSuccess { emitToast("Network settings saved. Restarting to apply…") }
                .onFailure { emitToast("Network save failed: ${it.message}") }
        }
    }

    // ---- Settings: autostart ----

    fun toggleAutostart(enabled: Boolean) {
        viewModelScope.launch {
            val ok = repo.setAutostart(enabled)
            if (!ok) emitToast("Autostart toggle failed")
            else emitToast(if (enabled) "Autostart enabled" else "Autostart disabled")
        }
    }

    // ---- Logs ----

    suspend fun fetchLog(kind: String): String =
        ApiClient.fetchLog(kind).getOrElse { "Log unavailable: ${it.message}" }

    fun clearLog(kind: String) {
        viewModelScope.launch {
            ApiClient.clearLog(kind)
                .onSuccess { emitToast("$kind log cleared.") }
                .onFailure { emitToast("Clear failed: ${it.message}") }
        }
    }

    // ---- Profile import/export ----

    /**
     * Write the current profiles list out to the user-chosen file via SAF.
     * We deliberately re-fetch from the daemon first so the export reflects
     * the live state on disk, not whatever the local StateFlow happened to
     * cache. Passwords are included because the export is the user's own
     * file — when they pick the location through SAF they are explicitly
     * agreeing to that disclosure.
     */
    fun exportProfiles(context: Context, target: Uri) {
        viewModelScope.launch {
            val r = ApiClient.profiles()
            r.onFailure {
                emitToast("Export failed: ${it.message}")
                return@launch
            }
            val pf = r.getOrNull() ?: return@launch
            withContext(Dispatchers.IO) {
                runCatching {
                    val payload = ApiClient.json.encodeToString(ProfilesFile.serializer(), pf)
                    context.contentResolver.openOutputStream(target)?.use { os ->
                        os.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open output stream")
                }.onSuccess {
                    emitToast("Profiles exported (${pf.profiles.size}).")
                }.onFailure {
                    emitToast("Export failed: ${it.message}")
                }
            }
        }
    }

    /**
     * Read a profiles JSON file the user picked, decode it, and POST each
     * profile to /api/v1/profile/save in turn. The selected_id from the
     * imported file is applied at the end so the user lands on the same
     * active profile as the source device.
     */
    fun importProfiles(context: Context, source: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                emitToast("Import failed: file is empty or unreadable")
                return@launch
            }
            val parsed = runCatching {
                ApiClient.json.decodeFromString(ProfilesFile.serializer(), text)
            }.getOrElse {
                emitToast("Import failed: not a valid profiles JSON")
                return@launch
            }
            var saved = 0
            for (p in parsed.profiles) {
                val req = SaveProfileRequest(
                    id = p.id,
                    name = p.name,
                    select = false,
                    restart = false,
                    ssh = p.ssh,
                    transport = p.transport,
                )
                ApiClient.saveProfile(req).onSuccess { saved += 1 }
            }
            if (parsed.selectedId.isNotBlank()) {
                ApiClient.selectProfile(ProfileSelectRequest(parsed.selectedId, restart = false))
            }
            repo.refreshProfiles()
            emitToast("Imported $saved/${parsed.profiles.size} profiles.")
        }
    }

    private suspend fun emitToast(message: String) {
        _toast.emit(message)
        Log.d("SshCustom", message)
    }
}

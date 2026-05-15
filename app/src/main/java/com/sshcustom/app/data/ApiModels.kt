package com.sshcustom.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Minimal API models — only what the foreground service and Quick Tile
 * need. All dashboard UI is handled by the WebView; these models exist
 * solely so the notification can show "Connected via <profile>".
 */
@Serializable
data class Envelope(
    @SerialName("api_version") val apiVersion: String = "v1",
    val ok: Boolean = false,
    val data: JsonElement? = null,
    val error: String? = null,
)

@Serializable
data class HealthData(
    val status: String? = null,
    val version: String? = null,
)

@Serializable
data class StatusData(
    val runtime: RuntimeState = RuntimeState(),
)

@Serializable
data class RuntimeState(
    val state: String = "",
    val running: Boolean = false,
    val connected: Boolean = false,
    @SerialName("ssh_authenticated") val sshAuthenticated: Boolean = false,
    @SerialName("selected_profile") val selectedProfile: String = "",
    @SerialName("interface") val iface: String = "",
)

@Serializable
data class ControlRequest(val action: String)

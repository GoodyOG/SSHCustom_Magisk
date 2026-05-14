package com.sshcustom.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Models that mirror the JSON contract documented in `docs/openapi.yaml`.
 *
 * Two design notes:
 *
 * - The daemon's `Envelope` is generic over `data`; we keep it as
 *   [JsonElement] here and let each call site deserialize its expected
 *   shape. This avoids defining a parallel sealed hierarchy that would
 *   need updating every time a new endpoint is added.
 * - Many fields are `String?` / `Int?` even when the daemon usually
 *   sends them populated. The daemon's State.Snapshot returns "" for
 *   uninitialized strings; treating them as nullable lets Compose decide
 *   how to render a missing value (`"--"`).
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
    val config: ConfigSummary? = null,
    val capabilities: List<Capability> = emptyList(),
    val paths: Paths = Paths(),
)

@Serializable
data class Paths(
    @SerialName("work_dir") val workDir: String = "",
    @SerialName("config_path") val configPath: String = "",
    @SerialName("profiles_path") val profilesPath: String = "",
    @SerialName("run_dir") val runDir: String = "",
    val webroot: String = "",
)

@Serializable
data class Capability(
    val name: String = "",
    val enabled: Boolean = false,
    val description: String = "",
)

/**
 * Mirror of the daemon's State.Snapshot map. Field names match the JSON
 * keys exactly — see internal/sshcustomd/main.go State.Snapshot.
 *
 * Anything that isn't always populated stays optional so a half-initialized
 * daemon (e.g. before the first metrics tick) doesn't break parsing.
 */
@Serializable
data class RuntimeState(
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("uptime_seconds") val uptimeSeconds: Long = 0,
    val state: String = "",
    val running: Boolean = false,
    val connected: Boolean = false,
    @SerialName("ssh_authenticated") val sshAuthenticated: Boolean = false,
    @SerialName("transport_ready") val transportReady: Boolean = false,
    val phase: String = "",
    val version: String = "",
    val goos: String = "",
    val goarch: String = "",
    @SerialName("selected_profile") val selectedProfile: String = "",
    @SerialName("selected_mode") val selectedMode: String = "",
    @SerialName("transport_chain") val transportChain: String = "",
    @SerialName("payload_enabled") val payloadEnabled: Boolean = false,
    @SerialName("last_error") val lastError: String = "",
    @SerialName("last_event") val lastEvent: String = "",
    val attempt: Int = 0,
    @SerialName("network_online") val networkOnline: Boolean = false,
    @SerialName("default_route") val defaultRoute: String = "",
    @SerialName("interface") val iface: String = "",
    val gateway: String = "",
    @SerialName("source_ip") val sourceIp: String = "",
    @SerialName("hotspot_enabled") val hotspotEnabled: Boolean = false,
    @SerialName("socks_enabled") val socksEnabled: Boolean = false,
    @SerialName("socks_addr") val socksAddr: String = "",
    @SerialName("socks_running") val socksRunning: Boolean = false,
    @SerialName("transparent_enabled") val transparentEnabled: Boolean = false,
    @SerialName("transparent_addr") val transparentAddr: String = "",
    @SerialName("transparent_running") val transparentRunning: Boolean = false,
    @SerialName("transparent_applied") val transparentApplied: Boolean = false,
    @SerialName("hotspot_running") val hotspotRunning: Boolean = false,
    @SerialName("cpu_percent") val cpuPercent: Double = 0.0,
    @SerialName("memory_rss_bytes") val memoryRssBytes: Long = 0,
    @SerialName("memory_rss_mb") val memoryRssMb: Double = 0.0,
    @SerialName("system_mem_used_percent") val systemMemUsedPercent: Double = 0.0,
    @SerialName("dns_mode") val dnsMode: String = "device",
    @SerialName("dns_servers") val dnsServers: List<String>? = null,
    @SerialName("pool_size") val poolSize: Int = 0,
    @SerialName("pool_healthy") val poolHealthy: Int = 0,
    @SerialName("pool_streams") val poolStreams: Int = 0,
    @SerialName("pool_max_streams") val poolMaxStreams: Int = 0,
    @SerialName("pool_reconnecting") val poolReconnecting: Int = 0,
    @SerialName("pool_last_error") val poolLastError: String = "",
    @SerialName("resolver_method") val resolverMethod: String = "",
    @SerialName("resolved_ips") val resolvedIps: List<String>? = null,
    @SerialName("remote_banner") val remoteBanner: String = "",
)

/**
 * Lightweight projection of the on-disk config that the daemon emits in
 * the /api/v1/status payload. The full Config (with all Performance
 * tuning knobs) is exposed by /api/v1/config; this struct is just the
 * subset the dashboard renders.
 */
@Serializable
data class ConfigSummary(
    val dns: DnsConfig? = null,
    val hotspot: HotspotConfig? = null,
    @SerialName("local_proxy") val localProxy: LocalProxyConfig? = null,
    @SerialName("transparent_proxy") val transparentProxy: TransparentConfig? = null,
)

@Serializable
data class DnsConfig(
    val mode: String = "device",
    val enabled: Boolean = false,
    val servers: List<String>? = null,
    @SerialName("timeout_seconds") val timeoutSeconds: Int = 4,
    val hijack: Boolean = false,
)

@Serializable
data class HotspotConfig(
    val enabled: Boolean = false,
    val tcp: Boolean = false,
    val dns: Boolean = false,
    val interfaces: List<String>? = null,
)

@Serializable
data class LocalProxyConfig(
    @SerialName("socks_enabled") val socksEnabled: Boolean = false,
    @SerialName("socks_host") val socksHost: String = "127.0.0.1",
    @SerialName("socks_port") val socksPort: Int = 1080,
)

@Serializable
data class TransparentConfig(
    val enabled: Boolean = false,
    @SerialName("tcp_port") val tcpPort: Int = 10810,
    @SerialName("chains_prefix") val chainsPrefix: String = "SSHC",
    @SerialName("ipv4_only") val ipv4Only: Boolean = true,
)

@Serializable
data class ProfilesFile(
    @SerialName("selected_id") val selectedId: String = "",
    val profiles: List<Profile> = emptyList(),
)

@Serializable
data class Profile(
    val id: String = "",
    val name: String = "",
    val selected: Boolean = false,
    val ssh: SshConfig = SshConfig(),
    val transport: TransportConfig = TransportConfig(),
)

@Serializable
data class SshConfig(
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val password: String = "",
    @SerialName("auth_type") val authType: String = "password",
    @SerialName("fallback_ips") val fallbackIps: List<String>? = null,
)

@Serializable
data class TransportConfig(
    val mode: String = "direct",
    val chain: List<String> = emptyList(),
    @SerialName("http_proxy") val httpProxy: HttpProxyConfig? = null,
    val tls: TlsConfig? = null,
    val payload: PayloadConfig = PayloadConfig(),
)

@Serializable
data class HttpProxyConfig(
    val host: String = "",
    val port: Int = 80,
    @SerialName("connect_method") val connectMethod: String = "socket",
)

@Serializable
data class TlsConfig(
    val enabled: Boolean = true,
    @SerialName("server_name") val serverName: String = "",
    @SerialName("insecure_skip_verify") val insecureSkipVerify: Boolean = true,
    val alpn: List<String> = listOf("http/1.1"),
)

@Serializable
data class PayloadConfig(
    val enabled: Boolean = false,
    val template: String = "",
    @SerialName("send_timing") val sendTiming: String = "before_ssh",
    @SerialName("read_response") val readResponse: Boolean = true,
    @SerialName("allow_http_status") val allowHttpStatus: List<Int> = listOf(101, 200, 204, 302),
)

@Serializable
data class PublicIpData(
    @SerialName("checked_at") val checkedAt: String = "",
    val provider: String = "",
    val tunnel: PublicIpDetails? = null,
)

@Serializable
data class PublicIpDetails(
    val ok: Boolean = false,
    val path: String = "",
    val ip: String = "",
    val country: String = "",
    val region: String = "",
    val city: String = "",
    val isp: String = "",
    val org: String = "",
    val asn: String = "",
    @SerialName("as_name") val asName: String = "",
    val timezone: String = "",
    @SerialName("latency_ms") val latencyMs: Long = 0,
    val cached: Boolean = false,
    val error: String = "",
)

@Serializable
data class ControlRequest(val action: String)

@Serializable
data class AutostartRequest(val enabled: Boolean)

@Serializable
data class AutostartResponse(val enabled: Boolean)

@Serializable
data class SaveProfileRequest(
    val id: String = "",
    val name: String = "",
    val select: Boolean = false,
    val restart: Boolean = false,
    val ssh: SshConfig = SshConfig(),
    val transport: TransportConfig = TransportConfig(),
)

@Serializable
data class ProfileSelectRequest(
    @SerialName("selected_id") val selectedId: String,
    val restart: Boolean = false,
)

/**
 * Used for the /api/v1/config PATCH body. We keep this an opaque
 * JsonObject because the daemon accepts a partial patch (DNS, hotspot,
 * etc.) and there is no value in modeling every nullable field here.
 */
@Serializable
data class ConfigPatch(val patch: JsonObject)

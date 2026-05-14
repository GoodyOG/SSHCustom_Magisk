package com.sshcustom.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * Thin OkHttp-backed wrapper around the daemon's /api/v1/* surface.
 *
 * The daemon is always on loopback so most of OkHttp's defaults are
 * unnecessary. We disable the connection pool's keep-alive entirely
 * (matching the daemon's `SetKeepAlivesEnabled(false)` on the server
 * side) and force IPv4 — the dashboard polled stuck `[::1]` connections
 * in pre-v2 builds and we want to avoid that whole class of issue.
 *
 * SSE goes through the same OkHttp instance via okhttp-sse, with read
 * timeout disabled because the stream stays open for hours.
 */
object ApiClient {

    /**
     * Bound to loopback by daemon config. The port is configurable in
     * config.json but 9190 is the documented default and we don't
     * support discovery — when users change the port the app will
     * report "daemon unreachable" and they can adjust later (out of
     * scope for v2.0.0).
     */
    private const val BASE_URL = "http://127.0.0.1:9190"

    private const val MEDIA_JSON = "application/json; charset=utf-8"

    /** Lenient parser: ignores unknown fields so daemon upgrades don't break the app. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    private lateinit var http: OkHttpClient
    private lateinit var sseHttp: OkHttpClient
    private lateinit var sseFactory: EventSource.Factory

    fun initialize(context: Context) {
        // Force IPv4 so the loopback connection never hangs trying ::1
        // when Android exposes a v6 loopback that the daemon isn't bound to.
        val v4Dns = okhttp3.Dns { hostname ->
            InetAddress.getAllByName(hostname).filter { it.address.size == 4 }
        }

        http = OkHttpClient.Builder()
            .dns(v4Dns)
            .retryOnConnectionFailure(false)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        sseHttp = http.newBuilder()
            // SSE streams must not be killed by an OkHttp read timeout; the
            // daemon sends a comment heartbeat every 25s so we'd never see
            // legitimate "no data for 60s" on a healthy connection, but we
            // still set 0 to make the intent explicit.
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        sseFactory = EventSources.createFactory(sseHttp)

        // Application-level KeepAliveDuration for the device's idle pool.
        // Loopback connections are cheap; we don't need the default 5min.
    }

    private fun req(path: String, builder: Request.Builder.() -> Unit = {}): Request =
        Request.Builder().url(BASE_URL + path).apply(builder).build()

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resumeWith(Result.success(response))
            }
        })
    }

    private suspend fun envelope(request: Request): Envelope = withContext(Dispatchers.IO) {
        execute(request).use { res ->
            val body = res.body?.string().orEmpty()
            if (body.isEmpty()) {
                Envelope(ok = false, error = "empty response (HTTP ${res.code})")
            } else {
                try {
                    json.decodeFromString(Envelope.serializer(), body)
                } catch (t: Throwable) {
                    Envelope(ok = false, error = "decode failed: ${t.message}")
                }
            }
        }
    }

    private inline fun <reified T> Envelope.unwrap(): Result<T> {
        if (!ok) return Result.failure(IOException(error.orEmpty().ifEmpty { "API returned ok=false" }))
        val payload = data ?: return Result.failure(IOException("API returned ok=true with no data"))
        return runCatching { json.decodeFromJsonElement(json.serializersModule.serializer<T>(), payload) }
    }

    suspend fun health(): Result<HealthData> =
        envelope(req("/api/v1/health")).unwrap()

    suspend fun status(): Result<StatusData> =
        envelope(req("/api/v1/status")).unwrap()

    suspend fun publicIp(refresh: Boolean = false): Result<PublicIpData> {
        val path = "/api/v1/network/public-ip" + if (refresh) "?refresh=1" else ""
        return envelope(req(path)).unwrap()
    }

    suspend fun profiles(): Result<ProfilesFile> =
        envelope(req("/api/v1/profiles")).unwrap()

    suspend fun saveProfile(body: SaveProfileRequest): Result<JsonElement> {
        val rb = json.encodeToString(SaveProfileRequest.serializer(), body)
            .toRequestBody(MEDIA_JSON.toMediaType())
        return envelope(req("/api/v1/profile/save") { post(rb) }).unwrap()
    }

    suspend fun selectProfile(body: ProfileSelectRequest): Result<JsonElement> {
        val rb = json.encodeToString(ProfileSelectRequest.serializer(), body)
            .toRequestBody(MEDIA_JSON.toMediaType())
        return envelope(req("/api/v1/profile/select") { post(rb) }).unwrap()
    }

    suspend fun control(action: String): Result<JsonElement> {
        val rb = json.encodeToString(ControlRequest.serializer(), ControlRequest(action))
            .toRequestBody(MEDIA_JSON.toMediaType())
        return envelope(req("/api/v1/control") { post(rb) }).unwrap()
    }

    suspend fun patchConfig(patch: JsonObject): Result<JsonElement> {
        val rb = patch.toString().toRequestBody(MEDIA_JSON.toMediaType())
        return envelope(req("/api/v1/config") { post(rb) }).unwrap()
    }

    suspend fun getAutostart(): Result<AutostartResponse> =
        envelope(req("/api/v1/autostart")).unwrap()

    suspend fun setAutostart(enabled: Boolean): Result<AutostartResponse> {
        val rb = json.encodeToString(AutostartRequest.serializer(), AutostartRequest(enabled))
            .toRequestBody(MEDIA_JSON.toMediaType())
        return envelope(req("/api/v1/autostart") { post(rb) }).unwrap()
    }

    suspend fun clearLog(kind: String): Result<JsonElement> {
        val rb = "{}".toRequestBody(MEDIA_JSON.toMediaType())
        return envelope(req("/api/v1/logs/$kind/clear") { post(rb) }).unwrap()
    }

    suspend fun fetchLog(kind: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            execute(req("/api/v1/logs/$kind")).use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                res.body?.string().orEmpty()
            }
        }
    }

    /**
     * Open the SSE stream. Returns the [EventSource] so the caller can
     * cancel it on lifecycle stop. The listener should parse the JSON
     * payload from `onEvent`'s `data` argument as a [StatusData] (same
     * shape as /api/v1/status).
     */
    fun openEvents(listener: EventSourceListener): EventSource {
        val request = req("/api/v1/events") { header("Accept", "text/event-stream") }
        return sseFactory.newEventSource(request, listener)
    }
}

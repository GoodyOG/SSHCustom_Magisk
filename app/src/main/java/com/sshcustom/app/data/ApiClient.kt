package com.sshcustom.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
 * Thin HTTP client for the daemon's loopback API. Only exposes the
 * endpoints the foreground service and Quick Tile need — the full
 * dashboard is handled by the WebView.
 */
object ApiClient {

    private const val BASE_URL = "http://127.0.0.1:9190"
    private const val MEDIA_JSON = "application/json; charset=utf-8"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var http: OkHttpClient? = null
    private var sseFactory: EventSource.Factory? = null

    fun initialize(context: Context) {
        val v4Dns = object : okhttp3.Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                InetAddress.getAllByName(hostname).filter { it.address.size == 4 }
        }

        val client = OkHttpClient.Builder()
            .dns(v4Dns)
            .retryOnConnectionFailure(false)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        http = client

        val sseClient = client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        sseFactory = EventSources.createFactory(sseClient)
    }

    private fun client(): OkHttpClient =
        http ?: throw IOException("ApiClient not initialized")

    private fun req(path: String, builder: Request.Builder.() -> Unit = {}): Request =
        Request.Builder().url(BASE_URL + path).apply(builder).build()

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = client().newCall(request)
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
                    Envelope(ok = false, error = "decode: ${t.message}")
                }
            }
        }
    }

    private inline fun <reified T> Envelope.unwrap(): Result<T> {
        if (!ok) return Result.failure(IOException(error.orEmpty().ifEmpty { "API error" }))
        val payload = data ?: return Result.failure(IOException("no data"))
        return runCatching { json.decodeFromJsonElement(json.serializersModule.serializer<T>(), payload) }
    }

    suspend fun health(): Result<HealthData> =
        runCatching { envelope(req("/api/v1/health")).unwrap<HealthData>().getOrThrow() }

    suspend fun status(): Result<StatusData> =
        runCatching { envelope(req("/api/v1/status")).unwrap<StatusData>().getOrThrow() }

    suspend fun control(action: String): Result<Unit> = runCatching {
        val rb = json.encodeToString(ControlRequest.serializer(), ControlRequest(action))
            .toRequestBody(MEDIA_JSON.toMediaType())
        val env = envelope(req("/api/v1/control") { post(rb) })
        if (!env.ok) throw IOException(env.error ?: "control failed")
    }

    /**
     * Open the SSE event stream for live status updates. Returns the
     * [EventSource] so the caller can cancel it. Safe to call even if
     * [initialize] hasn't run — returns null in that case.
     */
    fun openEvents(listener: EventSourceListener): EventSource? {
        val factory = sseFactory ?: return null
        val request = req("/api/v1/events") { header("Accept", "text/event-stream") }
        return factory.newEventSource(request, listener)
    }
}

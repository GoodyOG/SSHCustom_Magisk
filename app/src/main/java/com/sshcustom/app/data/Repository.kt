package com.sshcustom.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

/**
 * Single source of truth that the ViewModels observe.
 *
 * Two concerns here:
 *
 * - **Connection lifecycle.** [start] opens an SSE stream and falls
 *   back to polling /api/v1/status if the stream errors twice. The
 *   logic mirrors the WebUI so behavior is consistent across surfaces.
 * - **Side-channel state.** Profiles, public IP, and logs are not on
 *   the SSE channel because they are large or rarely updated. We
 *   expose them as separate StateFlows so each screen pulls only what
 *   it needs.
 */
class Repository(
    private val scope: CoroutineScope,
) {

    private val _connection = MutableStateFlow(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _status = MutableStateFlow<StatusData?>(null)
    val status: StateFlow<StatusData?> = _status.asStateFlow()

    private val _profiles = MutableStateFlow<ProfilesFile?>(null)
    val profiles: StateFlow<ProfilesFile?> = _profiles.asStateFlow()

    private val _publicIp = MutableStateFlow<PublicIpData?>(null)
    val publicIp: StateFlow<PublicIpData?> = _publicIp.asStateFlow()

    private val _autostart = MutableStateFlow<Boolean?>(null)
    val autostart: StateFlow<Boolean?> = _autostart.asStateFlow()

    private var sse: EventSource? = null
    private var pollJob: Job? = null
    private var sseFailures = 0

    enum class ConnectionState { Idle, Streaming, Polling, Offline }

    fun start() {
        if (sse != null || pollJob != null) return
        openSse()
    }

    fun stop() {
        sse?.cancel()
        sse = null
        pollJob?.cancel()
        pollJob = null
        _connection.value = ConnectionState.Idle
    }

    private fun openSse() {
        sse = ApiClient.openEvents(object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                sseFailures = 0
                _connection.value = ConnectionState.Streaming
                stopPolling()
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                // Only "status" frames currently. Future event types can be
                // added by the daemon without breaking this listener — the
                // type comparison below makes that explicit.
                if (type != "status") return
                runCatching {
                    ApiClient.json.decodeFromString(StatusData.serializer(), data)
                }.onSuccess { _status.value = it }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                sseFailures += 1
                sse = null
                _connection.value = ConnectionState.Offline
                if (sseFailures >= 2) {
                    // Give up on SSE; switch to polling for the rest of
                    // the session. EventSource will be re-opened on
                    // start() if the user navigates away and back.
                    startPolling()
                } else {
                    // Try once more after a short delay.
                    scope.launch(Dispatchers.IO) {
                        delay(2000)
                        if (sse == null && pollJob == null) openSse()
                    }
                }
            }
        })
    }

    private fun startPolling() {
        if (pollJob != null) return
        _connection.value = ConnectionState.Polling
        pollJob = scope.launch(Dispatchers.IO) {
            while (true) {
                ApiClient.status().onSuccess { _status.value = it }
                delay(3000)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    suspend fun refreshProfiles() {
        ApiClient.profiles().onSuccess { _profiles.value = it }
    }

    suspend fun refreshPublicIp(force: Boolean = false) {
        ApiClient.publicIp(force).onSuccess { _publicIp.value = it }
    }

    suspend fun refreshAutostart() {
        ApiClient.getAutostart().onSuccess { _autostart.value = it.enabled }
    }

    suspend fun setAutostart(enabled: Boolean): Boolean {
        val r = ApiClient.setAutostart(enabled)
        r.onSuccess { _autostart.value = it.enabled }
        return r.isSuccess
    }
}

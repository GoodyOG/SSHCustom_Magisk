package com.sshcustom.app.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sshcustom.app.R
import com.sshcustom.app.data.ApiClient
import com.sshcustom.app.data.RootClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings Tile that sits in the Android pull-down shade.
 *
 * Behavior:
 *   - Tile is **inactive** (gray) when the daemon is offline.
 *   - Tile is **active** (tinted) when the daemon is connected.
 *   - Tap toggles: inactive → start, active → stop. Both go through the
 *     daemon's /api/v1/control if reachable; otherwise we fall back to
 *     `/data/adb/sshcustom/sshcustom.sh` directly via libsu, so the tile
 *     keeps working even when the API is offline (e.g. right after a
 *     reboot, before the daemon has come up).
 *
 * The tile state is refreshed every time the shade opens (onStartListening)
 * by hitting /api/v1/health. We deliberately don't keep an SSE connection
 * here because TileService instances are short-lived and hooking them
 * into the foreground service's stream would be heavier than just
 * polling once per shade-open.
 */
class TunnelTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val nowActive = tile.state == Tile.STATE_ACTIVE
        val action = if (nowActive) "stop" else "start"
        scope.launch {
            // Optimistic: flip the icon to "busy" immediately so the tap
            // feels responsive, then reconcile with the daemon state once
            // the action lands.
            tile.state = Tile.STATE_UNAVAILABLE
            tile.updateTile()
            val viaApi = ApiClient.control(action).isSuccess
            if (!viaApi) {
                RootClient.control(action)
            }
            // Give the daemon a moment to actually transition before
            // re-querying.
            delay(800)
            refresh()
        }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        val tile = qsTile ?: return@withContext
        val ok = ApiClient.health().isSuccess
        tile.state = if (ok) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (ok) "Connected" else "Offline"
        }
        tile.label = "SSHCustom"
        tile.icon = Icon.createWithResource(this@TunnelTileService, R.drawable.ic_launcher_foreground)
        tile.updateTile()
    }
}

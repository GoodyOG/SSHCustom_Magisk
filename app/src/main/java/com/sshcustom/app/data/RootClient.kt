package com.sshcustom.app.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around libsu for the few operations the app needs to do
 * **outside** the daemon's HTTP API:
 *
 * - **Daemon control when the API is unreachable.** When the user opens
 *   the app for the first time after a reboot the daemon may not be up
 *   yet; pressing "Start" hits this path instead of /api/v1/control.
 * - **Read profiles directly from disk** so the Profiles tab works even
 *   when the daemon is offline (e.g. after a crash).
 *
 * Both paths require root because /data/adb/sshcustom is a Magisk-owned
 * directory only accessible to uid 0.
 *
 * If root is denied the methods return [RootResult.Denied]; the UI
 * surfaces that as a single-line banner instead of a dialog so it
 * matches the rest of the design language.
 */
object RootClient {

    sealed class RootResult<out T> {
        data class Ok<T>(val value: T) : RootResult<T>()
        data class Failed(val message: String) : RootResult<Nothing>()
        data object Denied : RootResult<Nothing>()
    }

    /**
     * Cached on first request. libsu's getCachedShell()/getShell() will
     * spin up a new su shell on demand so this is cheap to call repeatedly.
     */
    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        Shell.isAppGrantedRoot() ?: try {
            Shell.getShell().isRoot
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun runCommand(vararg cmd: String): RootResult<List<String>> = withContext(Dispatchers.IO) {
        if (!isRooted()) return@withContext RootResult.Denied
        val res = Shell.cmd(*cmd).exec()
        if (res.isSuccess) RootResult.Ok(res.out) else RootResult.Failed(res.err.joinToString("\n"))
    }

    /**
     * Schedules /data/adb/sshcustom/sshcustom.sh <action>. The script
     * itself backgrounds and returns quickly, but we still wrap with
     * the same 0.3 s sleep the daemon uses internally so callers can
     * trust the call returned before the daemon is killed.
     */
    suspend fun control(action: String): RootResult<Unit> {
        val script = "/data/adb/sshcustom/sshcustom.sh"
        return when (val r = runCommand("test -x $script && (sleep 0.3 && $script $action) >/dev/null 2>&1 &")) {
            is RootResult.Ok -> RootResult.Ok(Unit)
            is RootResult.Failed -> r
            RootResult.Denied -> RootResult.Denied
        }
    }

    /**
     * Read /data/adb/sshcustom/profiles.json directly. Used as a
     * fallback when the daemon HTTP API is offline.
     */
    suspend fun readProfilesFromDisk(): RootResult<String> = runCommand(
        "cat /data/adb/sshcustom/profiles.json"
    ).let { result ->
        when (result) {
            is RootResult.Ok -> RootResult.Ok(result.value.joinToString("\n"))
            is RootResult.Failed -> result
            RootResult.Denied -> RootResult.Denied
        }
    }
}

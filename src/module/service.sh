#!/system/bin/sh
# service.sh - SSHCustom v2.1.8 boot handler
#
# Boot flow:
#   1. Wait for sys.boot_completed=1 (Android userspace fully up).
#   2. Run boot-reset to clear any stale enabled/paused markers from a previous
#      session.
#   3. ALWAYS start the daemon process (WebUI always accessible after boot).
#   4. If the autostart marker exists, the daemon starts WITHOUT --idle flag
#      (tunnel connects immediately after connectivity). Without the marker,
#      the daemon starts with --idle (WebUI up, tunnel off — user starts from UI).
#
# The daemon is always alive when the module is enabled. The user controls
# the tunnel (start/stop/restart) from the WebUI at http://127.0.0.1:9190

WORK_DIR="/data/adb/sshcustom"
RUN_DIR="$WORK_DIR/run"
LOG="$RUN_DIR/boot.log"
AUTOSTART_MARKER="$RUN_DIR/autostart"

mkdir -p "$RUN_DIR"

has_route() {
  ip route get 1.1.1.1 >/dev/null 2>&1 && return 0
  ip route 2>/dev/null | grep -q '^default ' && return 0
  return 1
}

{
  echo "$(date '+%Y-%m-%d %H:%M:%S') boot service started (v2.1.8)"
  until [ "$(getprop sys.boot_completed 2>/dev/null)" = "1" ]; do sleep 3; done
  echo "$(date '+%Y-%m-%d %H:%M:%S') boot completed; resetting to clean state"
  [ -x "$WORK_DIR/sshcustom.sh" ] && "$WORK_DIR/sshcustom.sh" boot-reset

  # Wait for connectivity (30s cap) regardless of autostart — the daemon
  # needs network for the WebUI to report useful status even in idle mode.
  echo "$(date '+%Y-%m-%d %H:%M:%S') waiting for connectivity (30s cap)"
  i=0
  while [ "$i" -lt 30 ]; do
    if has_route; then
      echo "$(date '+%Y-%m-%d %H:%M:%S') route detected after ${i}s"
      break
    fi
    sleep 1
    i=$((i+1))
  done

  # Always start the daemon. If autostart is enabled, start with tunnel active.
  # Otherwise start in idle mode (WebUI accessible, tunnel off).
  if [ -f "$AUTOSTART_MARKER" ]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') autostart ON — starting daemon with tunnel"
    [ -x "$WORK_DIR/sshcustom.sh" ] && "$WORK_DIR/sshcustom.sh" start >> "$LOG" 2>&1 || \
      echo "$(date '+%Y-%m-%d %H:%M:%S') daemon start failed; watchdog will retry"
  else
    echo "$(date '+%Y-%m-%d %H:%M:%S') autostart OFF — starting daemon in idle mode (WebUI only)"
    [ -x "$WORK_DIR/sshcustom.sh" ] && "$WORK_DIR/sshcustom.sh" start-idle >> "$LOG" 2>&1 || \
      echo "$(date '+%Y-%m-%d %H:%M:%S') daemon idle start failed"
  fi
} >> "$LOG" 2>&1
exit 0

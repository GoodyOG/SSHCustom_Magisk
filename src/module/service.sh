#!/system/bin/sh
# service.sh - SSHCustom v2.2.0 boot handler
#
# Boot flow:
#   1. Wait for sys.boot_completed=1 (Android userspace fully up).
#   2. Run boot-reset to clear any stale enabled/paused markers from a previous
#      session. This prevents the daemon from coming up with rules from before
#      the reboot already in iptables.
#   3. ALWAYS start the daemon so the WebUI is accessible at 127.0.0.1:9190.
#      - If autostart marker exists: start with tunnel active (full mode).
#      - If autostart is off: start in idle mode (WebUI only, no tunnel).
#      The user can then start/stop/restart the tunnel from the WebUI.
#
# The connectivity wait (for autostart mode) avoids the "module starts before
# radio is up, fails, user thinks it's broken" failure pattern.

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
  echo "$(date '+%Y-%m-%d %H:%M:%S') boot service started (v2.2.0)"
  until [ "$(getprop sys.boot_completed 2>/dev/null)" = "1" ]; do sleep 3; done
  echo "$(date '+%Y-%m-%d %H:%M:%S') boot completed; resetting to stopped state"
  [ -x "$WORK_DIR/sshcustom.sh" ] && "$WORK_DIR/sshcustom.sh" boot-reset

  if [ -f "$AUTOSTART_MARKER" ]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') autostart enabled; waiting for connectivity (30s cap)"
    i=0
    while [ "$i" -lt 30 ]; do
      if has_route; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') route detected after ${i}s; starting daemon with tunnel"
        break
      fi
      sleep 1
      i=$((i+1))
    done
    if [ -x "$WORK_DIR/sshcustom.sh" ]; then
      "$WORK_DIR/sshcustom.sh" start >> "$LOG" 2>&1 || \
        echo "$(date '+%Y-%m-%d %H:%M:%S') autostart failed; watchdog will retry"
    fi
  else
    echo "$(date '+%Y-%m-%d %H:%M:%S') autostart disabled; starting daemon in idle mode (WebUI only)"
    if [ -x "$WORK_DIR/sshcustom.sh" ]; then
      "$WORK_DIR/sshcustom.sh" start-idle >> "$LOG" 2>&1 || \
        echo "$(date '+%Y-%m-%d %H:%M:%S') idle start failed"
    fi
  fi
} >> "$LOG" 2>&1
exit 0

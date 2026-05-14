#!/system/bin/sh
# service.sh - SSHCustom v2.0.0 boot handler
#
# Boot flow:
#   1. Wait for sys.boot_completed=1 (Android userspace fully up).
#   2. Run boot-reset to clear any stale enabled/paused markers from a previous
#      session. This prevents the daemon from coming up with rules from before
#      the reboot already in iptables.
#   3. If the autostart marker exists ($RUN_DIR/autostart, written by the
#      WebUI/app), wait for connectivity or 30 s — whichever comes first —
#      then call sshcustom.sh start. Without the marker, the module sits idle
#      and the user starts it via the action button.
#
# The connectivity wait avoids the "module starts before radio is up, fails,
# user thinks it's broken" failure pattern. ip route get is cheap and works
# even on toybox-only Androids; we cap the wait at 30 s so a permanently
# offline device still gets a single start attempt (which will fail-fast and
# the watchdog takes over from there).

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
  echo "$(date '+%Y-%m-%d %H:%M:%S') boot service started (v2.0.0)"
  until [ "$(getprop sys.boot_completed 2>/dev/null)" = "1" ]; do sleep 3; done
  echo "$(date '+%Y-%m-%d %H:%M:%S') boot completed; resetting to stopped state"
  [ -x "$WORK_DIR/sshcustom.sh" ] && "$WORK_DIR/sshcustom.sh" boot-reset

  if [ -f "$AUTOSTART_MARKER" ]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') autostart enabled; waiting for connectivity (30s cap)"
    i=0
    while [ "$i" -lt 30 ]; do
      if has_route; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') route detected after ${i}s; starting daemon"
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
    echo "$(date '+%Y-%m-%d %H:%M:%S') autostart disabled; module idle - open 127.0.0.1:9190 after starting"
  fi
} >> "$LOG" 2>&1
exit 0

#!/system/bin/sh
# service.sh - SSHCustom v1.0.0 boot handler
WORK_DIR="/data/adb/sshcustom"
RUN_DIR="$WORK_DIR/run"
LOG="$RUN_DIR/boot.log"
mkdir -p "$RUN_DIR"
{
  echo "$(date '+%Y-%m-%d %H:%M:%S') boot service started (v1.0.0)"
  until [ "$(getprop sys.boot_completed 2>/dev/null)" = "1" ]; do sleep 3; done
  echo "$(date '+%Y-%m-%d %H:%M:%S') boot completed; resetting to stopped state"
  [ -x "$WORK_DIR/sshcustom.sh" ] && "$WORK_DIR/sshcustom.sh" boot-reset
  echo "$(date '+%Y-%m-%d %H:%M:%S') ready - open 127.0.0.1:9190 after starting"
} >> "$LOG" 2>&1
exit 0

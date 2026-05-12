#!/system/bin/sh

RUN_DIR="/data/adb/sshcustom/run"
LOG="$RUN_DIR/net_clean.log"
mkdir -p "$RUN_DIR"

IPT="iptables"
IP6T="ip6tables"
CHAINS="SSHC_OUTPUT SSHC_PREROUTING SSHC_PROXY SSHC_DNS SSHC_HOTSPOT SSHC_HOTSPOT_DNS"
IFACES="wlan+ swlan+ ap+ rndis+ ncm+ bt-pan+"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG"; }
run() { "$@" >/dev/null 2>&1; }

clean_v4() {
  for C in $CHAINS; do
    run $IPT -t nat -D OUTPUT -p tcp -j "$C"
    run $IPT -t nat -D OUTPUT -j "$C"
    run $IPT -t nat -D PREROUTING -p tcp -j "$C"
    run $IPT -t nat -D PREROUTING -j "$C"
    for IF in $IFACES; do
      run $IPT -t nat -D PREROUTING -i "$IF" -p tcp -j "$C"
      run $IPT -t nat -D PREROUTING -i "$IF" -j "$C"
    done
  done
  for C in $CHAINS; do
    run $IPT -t nat -F "$C"
    run $IPT -t nat -X "$C"
  done
  run $IPT -D FORWARD -j ACCEPT
  run ip rule del fwmark 110 table 110
  run ip route flush table 110
}

clean_v6() {
  for C in $CHAINS; do
    run $IP6T -t nat -D OUTPUT -p tcp -j "$C"
    run $IP6T -t nat -D OUTPUT -j "$C"
    run $IP6T -t nat -D PREROUTING -p tcp -j "$C"
    run $IP6T -t nat -D PREROUTING -j "$C"
    for IF in $IFACES; do
      run $IP6T -t nat -D PREROUTING -i "$IF" -p tcp -j "$C"
      run $IP6T -t nat -D PREROUTING -i "$IF" -j "$C"
    done
  done
  for C in $CHAINS; do
    run $IP6T -t nat -F "$C"
    run $IP6T -t nat -X "$C"
  done
  run ip -6 rule del fwmark 110 table 110
  run ip -6 route flush table 110
}

log "clean start"
clean_v4
clean_v6
log "clean complete"
exit 0

#!/bin/bash
# WiFi orchestrator: scans on mlan0 (STA), runs portal on uap0 (AP).
# mlan0 is managed by NetworkManager; uap0 is NM-unmanaged (AP only).

set -euo pipefail

STA_IFACE="mlan0"
AP_IFACE="uap0"
AP_IP="192.168.4.1"
SCAN_CACHE="/run/wifi-scan-cache.json"
DNSMASQ_PID="/run/dnsmasq-captive.pid"
PORTAL_PID="/run/captive-portal.pid"
CONNECT_TIMEOUT=45

log() { echo "[wifi-setup] $*"; }

cleanup() {
    [ -f "$PORTAL_PID"  ] && kill "$(cat "$PORTAL_PID")"  2>/dev/null; rm -f "$PORTAL_PID"
    [ -f "$DNSMASQ_PID" ] && kill "$(cat "$DNSMASQ_PID")" 2>/dev/null; rm -f "$DNSMASQ_PID"
    killall hostapd 2>/dev/null || true
    ip addr flush dev "$AP_IFACE" 2>/dev/null || true
    ip link set "$AP_IFACE" down 2>/dev/null || true
}
trap cleanup EXIT

scan_networks() {
    log "Scanning for WiFi networks on $STA_IFACE..."
    nmcli dev wifi rescan ifname "$STA_IFACE" 2>/dev/null || true
    sleep 2
    nmcli -t -f SSID dev wifi list ifname "$STA_IFACE" 2>/dev/null \
        | grep -v '^--$' | grep -v '^$' | sort -u \
        | python3 -c "
import sys, json
ssids = [l.strip() for l in sys.stdin if l.strip()]
print(json.dumps(ssids))
" > "$SCAN_CACHE" 2>/dev/null || echo "[]" > "$SCAN_CACHE"
    log "Found $(python3 -c "import json; print(len(json.load(open('$SCAN_CACHE'))))" 2>/dev/null || echo 0) networks."
}

start_ap_mode() {
    log "Starting AP mode on $AP_IFACE (SSID: Gateway-Setup)..."
    ip link set "$AP_IFACE" up
    ip addr add "${AP_IP}/24" dev "$AP_IFACE"

    hostapd -B /etc/hostapd/hostapd.conf
    log "hostapd started."

    dnsmasq --conf-file=/etc/dnsmasq-captive.conf --pid-file="$DNSMASQ_PID"
    log "dnsmasq started."

    python3 /usr/bin/captive-portal &
    echo $! > "$PORTAL_PID"
    log "Captive portal at http://${AP_IP}/"

    wait "$(cat "$PORTAL_PID")" 2>/dev/null || true
    log "Captive portal exited — credentials saved."
}

# Scan first while mlan0 is in STA mode
scan_networks

# Already connected under whatever profile name (e.g. set up manually via
# `nmcli device wifi connect <SSID>`, which names the profile after the
# SSID, not any name this script controls) - nothing to do but watch it.
active_con="$(nmcli -t -f GENERAL.CONNECTION dev show "$STA_IFACE" 2>/dev/null | cut -d: -f2-)"
if [ -n "$active_con" ] && [ "$active_con" != "--" ]; then
    log "Already connected via profile '$active_con'."
    while nmcli -t -f GENERAL.STATE dev show "$STA_IFACE" 2>/dev/null \
            | grep -q "100 (connected)"; do
        sleep 15
    done
    log "WiFi connection lost."
    exit 1
fi

# Not connected - try any saved WiFi profile, regardless of its name.
saved_con="$(nmcli -t -f NAME,TYPE con show 2>/dev/null | awk -F: '$2 == "802-11-wireless" {print $1; exit}')"
if [ -n "$saved_con" ]; then
    log "Saved WiFi profile '$saved_con' found, connecting via NM..."
    if nmcli con up "$saved_con" 2>/dev/null; then
        log "WiFi connected."
        while nmcli -t -f GENERAL.STATE dev show "$STA_IFACE" 2>/dev/null \
                | grep -q "100 (connected)"; do
            sleep 15
        done
        log "WiFi connection lost."
        exit 1
    fi
    log "Failed to connect — falling back to captive portal."
fi

log "No WiFi credentials — starting captive portal."
start_ap_mode

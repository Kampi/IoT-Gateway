#!/bin/bash
# WiFi orchestrator: tries STA mode on boot; falls back to AP + captive portal
# when no credentials exist or connection fails.

set -euo pipefail

IFACE="wlan0"
AP_IP="192.168.4.1"
AP_NETWORK="192.168.4.0/24"
WPA_CONF="/etc/wpa_supplicant/wpa_supplicant-${IFACE}.conf"
SCAN_CACHE="/run/wifi-scan-cache.json"
CONNECT_TIMEOUT=45   # seconds to wait for STA association
DNSMASQ_PID="/run/dnsmasq-captive.pid"
HOSTAPD_PID="/run/hostapd-captive.pid"
PORTAL_PID="/run/captive-portal.pid"

log() { echo "[wifi-setup] $*"; }

cleanup() {
    log "Stopping AP mode services..."
    [ -f "$PORTAL_PID" ]  && kill "$(cat "$PORTAL_PID")"  2>/dev/null || true
    [ -f "$DNSMASQ_PID" ] && kill "$(cat "$DNSMASQ_PID")" 2>/dev/null || true
    [ -f "$HOSTAPD_PID" ] && kill "$(cat "$HOSTAPD_PID")" 2>/dev/null || true
    rm -f "$PORTAL_PID" "$DNSMASQ_PID" "$HOSTAPD_PID"
    ip addr flush dev "$IFACE" 2>/dev/null || true
}
trap cleanup EXIT

# Scan for nearby SSIDs and cache the result as a JSON array.
# Called before switching to AP mode so the interface is still in STA mode.
scan_networks() {
    log "Scanning for WiFi networks..."
    ip link set "$IFACE" up 2>/dev/null || true
    sleep 1
    # iw scan, extract non-empty SSIDs, deduplicate, emit JSON array
    iw dev "$IFACE" scan 2>/dev/null \
        | grep "SSID:" \
        | sed 's/.*SSID: //' \
        | grep -v '^\s*$' \
        | sort -u \
        | python3 -c "
import sys, json
ssids = [l.rstrip() for l in sys.stdin if l.strip()]
print(json.dumps(ssids))
" > "$SCAN_CACHE" 2>/dev/null \
        || echo "[]" > "$SCAN_CACHE"
    log "Found $(python3 -c "import json; print(len(json.load(open('$SCAN_CACHE'))))" 2>/dev/null || echo 0) networks."
}

# AP mode: hostapd + dnsmasq + captive portal web server
start_ap_mode() {
    log "Starting AP mode (SSID: Gateway-Setup)..."

    # Stop wpa-supplicant if running
    systemctl stop "wpa-supplicant@${IFACE}.service" 2>/dev/null || true
    killall wpa_supplicant 2>/dev/null || true
    sleep 1

    ip addr flush dev "$IFACE" 2>/dev/null || true
    ip link set "$IFACE" up
    ip addr add "${AP_IP}/24" dev "$IFACE"

    hostapd -B -P "$HOSTAPD_PID" /etc/hostapd/hostapd.conf
    log "hostapd started."

    dnsmasq --conf-file=/etc/dnsmasq-captive.conf --pid-file="$DNSMASQ_PID"
    log "dnsmasq started."

    python3 /usr/bin/captive-portal &
    echo $! > "$PORTAL_PID"
    log "Captive portal started on http://${AP_IP}/"

    # Wait for the portal to exit (it exits after credentials are saved)
    wait "$(cat "$PORTAL_PID")" 2>/dev/null || true
    log "Captive portal exited – credentials saved, restarting for STA mode."
    # EXIT trap fires here → cleanup() tears down AP mode
}

# STA mode: wait up to CONNECT_TIMEOUT seconds for wpa-supplicant association
wait_connected() {
    local elapsed=0
    while [ "$elapsed" -lt "$CONNECT_TIMEOUT" ]; do
        if iw dev "$IFACE" link 2>/dev/null | grep -q "Connected to"; then
            return 0
        fi
        sleep 2
        elapsed=$(( elapsed + 2 ))
    done
    return 1
}

# No credentials at all → go straight to AP mode
if [ ! -f "$WPA_CONF" ] || ! grep -q "^network=" "$WPA_CONF" 2>/dev/null; then
    log "No WiFi credentials found."
    scan_networks
    start_ap_mode
    return
fi

log "Credentials found, trying STA mode..."
systemctl start "wpa-supplicant@${IFACE}.service" 2>/dev/null || true

if wait_connected; then
    log "WiFi connected in STA mode."
    # Monitor the connection; exit when it drops so systemd restarts us
    while iw dev "$IFACE" link 2>/dev/null | grep -q "Connected to"; do
        sleep 15
    done
    log "WiFi disconnected."
    exit 1   # systemd will restart → retry STA or fall back to AP
fi

log "Could not connect within ${CONNECT_TIMEOUT}s."
scan_networks
start_ap_mode

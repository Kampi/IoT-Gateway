#!/usr/bin/env python3
"""
Captive portal web server for WiFi configuration.

Listens on port 80. All DNS queries are redirected to this host by dnsmasq,
so OS captive-portal detection (iOS, Android, Windows) triggers automatically
and opens a browser pointing here.

Flow:
  1. User connects to "Gateway-Setup" AP.
  2. OS detects captive portal → opens browser.
  3. User selects SSID and enters password → POST /connect.
  4. Credentials are written to wpa_supplicant config.
  5. This process exits → wifi-setup.sh detects exit → systemd restarts the
     wifi-setup service → tries STA mode with the new credentials.
"""

import json
import os
import sys
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

LISTEN_PORT = 80
STA_IFACE = "mlan0"
STA_CON_NAME = "wifi-client"
SCAN_CACHE = "/run/wifi-scan-cache.json"
COUNTRY = "DE"

# These paths are fetched by OSes to detect captive portals.
# Return a redirect so the browser navigates to our portal.
DETECTION_PATHS = {
    "/generate_204",           # Android / Chrome
    "/hotspot-detect.html",    # iOS / macOS
    "/library/test/success.html",
    "/connecttest.txt",        # Windows
    "/ncsi.txt",
    "/redirect",               # Firefox
    "/success.txt",
}

_HTML = """\
<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Gateway – WiFi Einrichtung</title>
<style>
*{{box-sizing:border-box;margin:0;padding:0}}
body{{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;
      background:#f0f4f8;min-height:100vh;display:flex;
      align-items:center;justify-content:center;padding:20px}}
.card{{background:#fff;border-radius:12px;box-shadow:0 4px 24px rgba(0,0,0,.12);
       padding:32px;width:100%;max-width:400px}}
h1{{font-size:1.3em;margin-bottom:6px;color:#1a1a2e}}
p.sub{{color:#666;font-size:.9em;margin-bottom:24px}}
label{{display:block;font-size:.85em;font-weight:600;
       color:#444;margin-bottom:4px;margin-top:14px}}
select,input[type=password]{{width:100%;padding:11px 12px;border:1.5px solid #d0d7de;
  border-radius:8px;font-size:1em;outline:none;transition:border .2s}}
select:focus,input:focus{{border-color:#0969da}}
button{{margin-top:20px;width:100%;padding:13px;background:#0969da;color:#fff;
        border:none;border-radius:8px;font-size:1em;font-weight:600;
        cursor:pointer;transition:background .2s}}
button:hover{{background:#0550ae}}
.msg{{margin-top:16px;padding:12px;border-radius:8px;font-size:.9em}}
.ok{{background:#dafbe1;color:#116329}}
.err{{background:#ffd8d3;color:#82071e}}
</style>
</head>
<body>
<div class="card">
  <h1>Gateway WiFi</h1>
  <p class="sub">Verbinde das Gateway mit deinem Netzwerk.</p>
  <form method="POST" action="/connect">
    <label for="ssid">WiFi Netzwerk</label>
    <select id="ssid" name="ssid">
      {options}
    </select>
    <label for="pw">Passwort</label>
    <input id="pw" type="password" name="password"
           placeholder="WiFi-Passwort" autocomplete="off" required>
    <button type="submit">Verbinden</button>
  </form>
  {message}
</div>
</body>
</html>
"""


def _ssid_options() -> str:
    try:
        with open(SCAN_CACHE) as f:
            ssids = json.load(f)
    except Exception:
        ssids = []
    if not ssids:
        return '<option value="">— Keine Netzwerke gefunden —</option>'
    return "\n      ".join(
        f'<option value="{s}">{s}</option>' for s in ssids
    )


def _render(message: str = "") -> bytes:
    return _HTML.format(options=_ssid_options(), message=message).encode()


def _save_credentials(ssid: str, password: str) -> None:
    import subprocess
    subprocess.run(["nmcli", "con", "delete", STA_CON_NAME],
                   capture_output=True)
    subprocess.run([
        "nmcli", "con", "add",
        "type", "wifi",
        "ifname", STA_IFACE,
        "con-name", STA_CON_NAME,
        "wifi.ssid", ssid,
        "wifi-sec.key-mgmt", "wpa-psk",
        "wifi-sec.psk", password,
        "ipv4.method", "auto",
        "ipv6.method", "disabled",
        "connection.autoconnect", "yes",
        "connection.autoconnect-priority", "20",
    ], check=True)


def _schedule_exit() -> None:
    """Exit 2 s after the response is sent so the browser can display it."""
    threading.Timer(2.0, os._exit, args=[0]).start()


class PortalHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):  # suppress access log
        pass

    def _send_html(self, body: bytes, status: int = 200) -> None:
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path in DETECTION_PATHS:
            # Captive-portal detection: redirect to portal root
            self.send_response(302)
            self.send_header("Location", "http://192.168.4.1/")
            self.end_headers()
            return
        self._send_html(_render())

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode(errors="replace")
        params = parse_qs(body)
        ssid = params.get("ssid", [""])[0].strip()
        password = params.get("password", [""])[0]

        if not ssid or not password:
            msg = '<p class="msg err">Bitte SSID und Passwort angeben.</p>'
            self._send_html(_render(msg))
            return

        try:
            _save_credentials(ssid, password)
            msg = (
                '<p class="msg ok">'
                f"Gespeichert! Das Gateway verbindet sich jetzt mit <b>{ssid}</b>. "
                "Du kannst dieses Netzwerk verlassen."
                "</p>"
            )
            self._send_html(_render(msg))
            _schedule_exit()
        except Exception as exc:
            msg = f'<p class="msg err">Fehler beim Speichern: {exc}</p>'
            self._send_html(_render(msg))


if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", LISTEN_PORT), PortalHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        sys.exit(0)

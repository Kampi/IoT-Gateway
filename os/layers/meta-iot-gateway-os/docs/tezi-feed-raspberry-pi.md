<!-- SPDX-License-Identifier: MIT -->
<!-- Copyright (c) 2026 Daniel Kampert -->

# Easy Installer feed on a Raspberry Pi

Flash GatewayOS with the [Toradex Easy Installer][tezi] (Tezi) over the
network: a Raspberry Pi next to the gateway serves the image over HTTP and
gives the Easy Installer its network (DHCP, DNS) on its Ethernet port. The Pi
can also load the Easy Installer into the Verdin over USB (recovery mode).

[tezi]: https://developer.toradex.com/easy-installer/toradex-easy-installer/

```text
build host ──(scp, LAN/Wi-Fi)──> Raspberry Pi ──(Ethernet eth0)──> Dahlia / gateway Ethernet
                                      └──────────(USB, recovery only)──> Dahlia USB-C (OTG)
```

Tested with a Raspberry Pi 4 (Debian 12 bookworm, NetworkManager), uplink via
Wi-Fi (`wlan0`), the gateway on `eth0`.

| Address | Purpose |
| --- | --- |
| `192.168.10.1/24` (Pi `eth0`) | network of the Easy Installer, DHCP `192.168.10.50–150` |
| `192.168.0.1/24` (Pi `eth0`) | reaches the flashed gateway on Dahlia (static `192.168.0.2`, see README "Networking") |

**Quick setup:** `scripts/raspberry-pi/setup-tezi-server.sh` in the project
repository does steps 1–4 (and with `--with-uuu` the `uuu` build of 6b) in one
go: copy it to the Pi and run `sudo ./setup-tezi-server.sh --with-uuu`. The
sections below describe the same steps manually.

---

## 1. Network on eth0

```bash
sudo nmcli con add type ethernet ifname eth0 con-name Local \
    ipv4.method manual ipv4.addresses "192.168.10.1/24,192.168.0.1/24" \
    ipv4.never-default yes ipv6.method link-local
```

No default route on `eth0`: the Pi keeps using `wlan0` for the internet.

## 2. DHCP and DNS (dnsmasq)

```bash
sudo apt install dnsmasq
sudo tee /etc/dnsmasq.d/tezi.conf <<'EOF'
# DHCP + DNS for the Toradex Easy Installer on eth0 only
interface=eth0
# also starts while eth0 has no address yet (boot), only binds eth0
bind-dynamic
dhcp-range=192.168.10.50,192.168.10.150,12h
# no default route for the clients, the feed is local
dhcp-option=option:router
dhcp-authoritative
log-dhcp
# The Easy Installer always asks its default feed tezi.toradex.com for
# image_list.json: answer with this Pi, then it lists the local images
# without any configuration on the device.
address=/tezi.toradex.com/192.168.10.1
EOF
sudo dnsmasq --test && sudo systemctl enable --now dnsmasq
```

Check: `sudo ss -ulnp | grep dnsmasq` shows port 53 only on `192.168.10.1`
(and localhost), never on the Wi-Fi address.

## 3. Web server (nginx) with the image

```bash
sudo apt install nginx      # serves /var/www/html on port 80
sudo chown "$USER" /var/www/html
```

Copy the Easy Installer image of a build (on the build host,
`os/build/deploy/images/verdin-imx8mm/`):

```bash
TAR=gateway-os-image-verdin-imx8mm.rootfs-<timestamp>-Tezi_<version>.tar
scp os/build/deploy/images/verdin-imx8mm/$TAR <user>@<pi>:/tmp/
ssh <user>@<pi> "tar -xf /tmp/$TAR -C /var/www/html && rm /tmp/$TAR"
```

`/var/www/html/image_list.json` lists the images (paths relative to the list):

```json
{
    "config_format": 1,
    "images": [
        "gateway-os-image-verdin-imx8mm.rootfs-<timestamp>-Tezi_<version>/image.json"
    ]
}
```

Check from the Pi: `curl -I http://192.168.10.1/image_list.json` (200). The
Easy Installer also asks for `/image_list_3rdparty.json`, the 404 for it is
harmless.

## 4. Zeroconf announcement (optional)

Second way for the Easy Installer to find the feed (avahi-daemon is part of
Raspberry Pi OS):

```bash
sudo tee /etc/avahi/services/tezi.service <<'EOF'
<?xml version="1.0" standalone="no"?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
  <name>GatewayOS Easy Installer feed (%h)</name>
  <service>
    <type>_tezi._tcp</type>
    <port>80</port>
    <txt-record>path=/image_list.json</txt-record>
  </service>
</service-group>
EOF
sudo systemctl restart avahi-daemon
```

## 5. Unattended installation (optional)

The gateway has no display. With `"autoinstall": true` in the image's
`image.json` the Easy Installer installs it as soon as it finds it (both
start methods below boot it with the `autoinstall` kernel argument):

```bash
cd /var/www/html/<image folder>
cp -n image.json image.json.orig
python3 -c "import json; d=json.load(open('image.json')); d['autoinstall']=True; json.dump(d, open('image.json','w'), indent=4)"
```

**Reset it after the installation** (`mv image.json.orig image.json`):
otherwise every later Easy Installer start on this network re-flashes the
gateway. Without autoinstall, use the Easy Installer over VNC
(`<Easy Installer IP>:5900`, the address is in `/var/lib/misc/dnsmasq.leases`).

Progress: `sudo tail -f /var/log/nginx/access.log` (the rootfs is fetched
once per slot, `RFS_A` and `RFS_B`) and `sudo journalctl -fu dnsmasq`.

---

## 6. Starting the Easy Installer on the gateway

### a) From a running gateway (no USB needed)

Works as long as GatewayOS still boots: put the Easy Installer on the eMMC
BOOT partition, U-Boot starts it instead of Linux at the next boot. The Easy
Installer runs from RAM and overwrites the whole eMMC.

```bash
# build host: boot.scr of the Easy Installer package with a devicetree it
# knows (gateway U-Boot: fdtfile=iot-gateway-imx8mm.dtb, not in tezi.itb).
# Verdin without Wi-Fi: imx8mm-verdin-nonwifi-dahlia.dtb
os/scripts/tezi-feed.sh tezi-emmc /tmp/tezi-emmc imx8mm-verdin-wifi-dahlia.dtb

# gateway: the 48 MB BOOT partition only fits tezi.itb (40 MB) without the
# kernel -> after this step GatewayOS no longer boots, only the Easy Installer.
GW=root@<gateway>
ssh $GW 'mkdir -p /root/boot-backup && cp -a /boot/* /root/boot-backup/ && rm /boot/*'
scp /tmp/tezi-emmc/* $GW:/boot/
ssh $GW 'sync && reboot'
```

`os/scripts/tezi-feed.sh` uses the Easy Installer package in
`os/Verdin-iMX8MM_ToradexEasyInstaller_*` (`TEZI_PKG` to override) and
`mkimage` from the build (`u-boot-tools-native`).

With the A/B layout the BOOT partition is not mounted (README "Updates
(RAUC)"): mount `/dev/mmcblk0p1` first. Usually an update is a RAUC bundle
anyway; the Easy Installer is only needed for a new layout or a broken eMMC.

### b) USB recovery mode (empty or broken eMMC)

Needs `uuu` ≥ 1.5.165 (Easy Installer 7.x). Debian bookworm ships 1.4.193,
which loads the first stage but times out on the second one
(`SDPV: write ... LIBUSB_ERROR_TIMEOUT`). Build the current version once:

```bash
sudo apt install git cmake g++ pkg-config libusb-1.0-0-dev libbz2-dev \
    libzstd-dev libssl-dev zlib1g-dev libtinyxml2-dev
git clone https://github.com/nxp-imx/mfgtools.git ~/src/mfgtools
cmake -S ~/src/mfgtools -B ~/src/mfgtools/build -DCMAKE_BUILD_TYPE=Release
cmake --build ~/src/mfgtools/build -j4     # -> ~/src/mfgtools/build/uuu/uuu
```

Copy `boot.scr`, `imx-boot-recoverytezi`, `overlays.txt`, `tezi.itb` and
`recovery/uuu.auto` of the Easy Installer package to the Pi (same layout),
then:

1. Remove **all** power from the Dahlia, including everything connected to
   its UARTs (see Troubleshooting).
2. Hold the recovery button, power on, release after 1–2 s.
3. `lsusb` on the Pi shows `1fc9:0134 NXP Semiconductors SE Blank M845S`.
4. `cd <package>/recovery && sudo ~/src/mfgtools/build/uuu/uuu uuu.auto`

The Easy Installer then boots, gets an address from dnsmasq and loads the feed.

---

## 7. After the installation

The gateway reboots into GatewayOS. On Dahlia it has `192.168.0.2` on
Ethernet (static, no DHCP), reachable from the Pi via `192.168.0.1`:

```bash
ssh-keygen -R 192.168.0.2          # new host key after every flash
ssh root@192.168.0.2 'rauc status; mount | grep overlay'
```

Wi-Fi: captive portal `Gateway-Setup` (README "Networking"). Data from the
old installation: restore a `gateway-backup` archive (README "Backup").

---

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `uuu`: first stage OK, then `SDPV: write ... LIBUSB_ERROR_TIMEOUT`; afterwards the Verdin stays `1b67:4fff Toradex USB download gadget` even when unplugged | The Verdin was not really off: a self-powered device on a UART of the Dahlia (USB-UART adapter on the debug UART, nRF5340-DK on UART_1) back-powers the module through TX/RX. Disconnect them (or their supply) before power-cycling into recovery. Also check `uuu` ≥ 1.5.165 |
| No USB device appears or disappears any more on the Pi, rebinding `xhci_hcd` fails with `Host halt failed, -110` | The VL805 USB controller of the Pi 4 hung on the stuck download gadget (same on Windows). Reboot the Pi |
| `dnsmasq: unknown interface eth0` at boot | `bind-interfaces` instead of `bind-dynamic` |
| Easy Installer stops at U-Boot `Could not find configuration node` | `boot.scr` without the `fdtfile` line (start from eMMC, 6a) |
| Easy Installer shows no image | `curl http://192.168.10.1/image_list.json`, nginx log; is `tezi.toradex.com` answered with `192.168.10.1` (`dig @192.168.10.1 tezi.toradex.com`)? |

# meta-iot-gateway-os

Yocto/OpenEmbedded layer for the IoT Gateway OS on the
[Toradex Verdin iMX8MM][som]: a headless, multi-protocol IoT gateway
(LoRaWAN, Zigbee, Wireless M-Bus, Bluetooth LE, Thread/Matter) that publishes
everything via MQTT to Home Assistant.

[som]: https://www.toradex.com/computer-on-modules/verdin-arm-family/nxp-imx-8m-mini-nano

- **Yocto release:** Scarthgap (5.0)
- **Distro:** `gateway-os` (`conf/distro/gateway-os.conf`)
- **Image:** `gateway-os-image` (`recipes-core/images/gateway-os-image.bb`)

---

## Table of Contents

- [meta-iot-gateway-os](#meta-iot-gateway-os)
  - [Building](#building)
  - [Configuration Variables](#configuration-variables)
  - [Board variants](#board-variants)
  - [Hardware](#hardware)
    - [SODIMM Pin Mapping](#sodimm-pin-mapping)
    - [Battery and sensors](#battery-and-sensors)
  - [Protocol Stack](#protocol-stack)
    - [LoRaWAN](#lorawan)
    - [Zigbee](#zigbee)
    - [Wireless M-Bus](#wireless-m-bus)
    - [Bluetooth LE](#bluetooth-le)
    - [Thread](#thread)
    - [MQTT](#mqtt)
  - [Networking](#networking)
  - [Security](#security)
  - [Logging](#logging)
  - [Backup](#backup)
  - [Maintainer](#maintainer)

---

## Building

Dependencies (all `scarthgap`): `openembedded-core`, `meta-arm`,
`meta-toradex-nxp`, `meta-toradex-demos`, `meta-security`.

```bash
bitbake-layers add-layer layers/meta-iot-gateway-os   # layer priority 6
source layers/poky/oe-init-build-env build
bitbake gateway-os-image
```

Artifacts: `build/tmp/deploy/images/verdin-imx8mm/`.

Example configuration (Dahlia dev setup, MQTT broker on the NAS):
`conf/templates/gateway-os/` (`local.conf.sample`, `bblayers.conf.sample`).
Create a new build directory from it with:

```bash
TEMPLATECONF=$PWD/layers/meta-iot-gateway-os/conf/templates/gateway-os \
    source layers/openembedded-core/oe-init-build-env build
```

The gateway settings are at the end of `local.conf`.

---

## Configuration Variables

Set in `build/conf/local.conf`. All MQTT clients default to the Mosquitto
broker on the gateway (`localhost`). If Home Assistant / ChirpStack use a
broker elsewhere, point **all** of them to it:

```bitbake
GATEWAY_BOARD           = "dahlia"
CHIRPSTACK_MQTT_SERVER  = "tcp://192.168.1.10:1883"
ZIGBEE2MQTT_MQTT_SERVER = "mqtt://192.168.1.10"
WMBUSMETERS_MQTT_SERVER = "192.168.1.10"
THEENGS_MQTT_HOST       = "192.168.1.10"
TELEMETRY_MQTT_HOST     = "192.168.1.10"
```

<!-- USER_CONFIG_START -->

| Variable | Default | Description | Recipe |
| --- | --- | --- | --- |
| `GATEWAY_BOARD` | `mainboard` | Target hardware: "mainboard" (IoT-Gateway PCB, see gateway/) or "dahlia" (Verdin Dahlia dev setup with nRF5340-DK as Thread RCP on UART_1) | `gateway-os.conf` |
| `CHIRPSTACK_MQTT_SERVER` | `tcp://localhost:1883` | MQTT broker URL for ChirpStack Gateway Bridge (e.g. tcp://192.168.1.10:1883) | `chirpstack-gateway-bridge_4.0.11.bb` |
| `LORA_SERVER_ADDRESS` | `localhost` | Address of the ChirpStack Gateway Bridge (Semtech UDP server) | `lora-packet-forwarder_2.1.0.bb` |
| `OTBR_INFRA_IFACE` | `mlan0` | Network interface connected to the backbone (IP) network with IPv6 | `ot-br-posix_%.bbappend` |
| `OTBR_INFRA_IPV6_ADDRESS` | `fd8f:e72d:8065:0:3f1e:f55:3464:19db/64` | Fixed IPv6 address/prefix length of the gateway on OTBR_INFRA_IFACE, next hop of the LAN router's route to OTBR_OMR_PREFIX (empty = none) | `ot-br-posix_%.bbappend` |
| `OTBR_RCP_DEVICE` | `/dev/thread` | Thread RCP device path (nRF5340 flashed with OpenThread RCP firmware, connected via UART_4) | `ot-br-posix_%.bbappend` |
| `THEENGS_MQTT_HOST` | `localhost` | MQTT broker host for Theengs Gateway (Bluetooth LE to MQTT, e.g. 192.168.1.10) | `theengs-gateway_1.8.0.bb` |
| `OTBR_OMR_PREFIX` | `fdf2:83ec:1caf:1::/64` | Fixed OMR (off-mesh routable) IPv6 prefix of the Thread network, e.g. "fdxx:xxxx:xxxx:1::/64" (random ULA, RFC 4193). Empty = border router picks a random one that changes with every new Thread network. A fixed prefix lets the LAN router keep one static route to the gateway. | `thread-network-init_1.0.bb` |
| `WMBUSMETERS_MQTT_SERVER` | `localhost` | MQTT broker hostname (or hostname:port) for wmbusmeters (e.g. 192.168.1.10 or 192.168.1.10:1883) | `wmbusmeters_1.18.0.bb` |
| `WMBUSMETERS_DEVICE` | `/dev/wmbus:im871a:t1` | wM-Bus receiver for wmbusmeters (device:type:link modes, e.g. /dev/wmbus:im871a:t1) | `wmbusmeters_1.18.0.bb` |
| `ZIGBEE2MQTT_MQTT_SERVER` | `mqtt://localhost` | MQTT broker URL for Zigbee2MQTT (e.g. mqtt://192.168.1.10) | `zigbee2mqtt_2.12.0.bb` |
| `ZIGBEE2MQTT_HA_BRIDGE_NAME` | `IoT-Gateway (Zigbee)` | Name of the Zigbee bridge device in Home Assistant, parent of all Zigbee devices | `zigbee2mqtt_2.12.0.bb` |
| `ZIGBEE2MQTT_USB_VID` | `10c4` | USB vendor ID of the Zigbee USB stick (Dahlia dev setup only) | `zigbee2mqtt_2.12.0.bb` |
| `ZIGBEE2MQTT_USB_PID` | `ea60` | USB product ID of the Zigbee USB stick (Dahlia dev setup only) | `zigbee2mqtt_2.12.0.bb` |
| `TELEMETRY_MQTT_HOST` | `localhost` | MQTT broker host for the gateway telemetry (Home Assistant device "IoT-Gateway") | `gateway-telemetry_1.0.bb` |
| `TELEMETRY_DEVICE_NAME` | `IoT-Gateway` | Name of the gateway device in Home Assistant (CPU, memory, disk, battery, uptime) | `gateway-telemetry_1.0.bb` |
| `TELEMETRY_LORA_DEVICE_NAME` | `IoT-Gateway (LoRa)` | Name of the LoRa gateway device in Home Assistant, parent of all LoRa sensors | `gateway-telemetry_1.0.bb` |
| `TELEMETRY_BLE_DEVICE_NAME` | `IoT-Gateway (Bluetooth)` | Name of the Bluetooth gateway device in Home Assistant, parent of all BLE sensors | `gateway-telemetry_1.0.bb` |
| `TELEMETRY_WMBUS_DEVICE_NAME` | `IoT-Gateway (wM-Bus)` | Name of the wM-Bus gateway device in Home Assistant (wmbusmeters status and version) | `gateway-telemetry_1.0.bb` |

<!-- USER_CONFIG_END -->

The table is generated: run `python3 scripts/update-vars-doc.py` after adding
a `# @USER_CONFIG: <description>` line directly above a `VAR ?= "default"`.

---

## Board variants

`GATEWAY_BOARD` adds the override `board-<name>`: recipes use
`VAR:board-dahlia = ...`, and files in `files/board-dahlia/` replace the
default ones.

| | `mainboard` (default) | `dahlia` (dev setup) |
| --- | --- | --- |
| Hardware | IoT-Gateway PCB (`gateway/`) | Verdin Dahlia + nRF5340-DK |
| Module power/reset/boot GPIOs | `module-power@<module>.service` drives them | no-op unit (dev kits have their own supply) |
| `end0` (Ethernet) | DHCP | static `192.168.0.2/24`, point-to-point link to the build host, no default route |
| Thread RCP | Fanstel BT40 on UART_4, `/dev/thread` | nRF5340-DK on UART_1, `/dev/thread` |
| Zigbee | CC2652P module on UART_2, `/dev/zigbee`, powered via `module-power@zigbee` | USB stick, `/dev/zigbee` by VID/PID (`ZIGBEE2MQTT_USB_VID`/`_PID`, default `10c4:ea60`). `zigbee2mqtt` starts when it is plugged in, skipped without it |
| LoRa | RAK5146 USB concentrator (`0483:5740`), `/dev/lora`. `lora-packet-forwarder` starts when it is plugged in | same |
| wM-Bus | iM871A on UART_1, `/dev/wmbus` | none, wmbusmeters disabled (UART_1 is the Thread RCP) |

| Verdin UART | `mainboard` (default) | `dahlia` (dev setup) |
| --- | --- | --- |
| UART_1 | wM-Bus | Thread RCP (Dahlia X20 pin 12/13, TxD/RxD only, 115200 baud) |
| UART_2 | Zigbee | unused |
| UART_3 | Linux console (`console=ttymxc0,115200`) | Linux console |
| UART_4 | Thread RCP (with RTS/CTS) | unused |

The services always use the same device names (`/dev/thread`, `/dev/zigbee`,
`/dev/lora`, `/dev/wmbus`); only the udev rules differ per board.

The nRF5340-DK's J-Link drives P1.00 through its virtual COM port, which
blocks the RCP's RxD on Dahlia. Disable it once with J-Link Commander
(`vcom disable`) and power-cycle the DK.

---

## Hardware

Toradex Verdin iMX8MM (NXP i.MX 8M Mini, Cortex-A53 quad-core, aarch64).
Custom devicetree: `recipes-kernel/linux/linux-toradex/iot-gateway-imx8mm.dts`
(unused SoM peripherals disabled), kernel fragment `atecc608b.cfg`
(I2C chardev, ATECC/SHA204A crypto, MAX17040 fuel gauge).

### SODIMM Pin Mapping

**Verdin UART_N labels do not match the NXP/Linux `&uartN` node numbers**
(see the comments in Toradex's `imx8mm-verdin.dtsi`):

| Verdin UART (schematic) | NXP/Linux devicetree node | `serial` alias | Linux device |
| --- | --- | --- | --- |
| UART_3 | `&uart1` | `serial0` | `/dev/ttymxc0` |
| UART_1 | `&uart2` | `serial1` | `/dev/ttymxc1` |
| UART_2 | `&uart3` | `serial2` | `/dev/ttymxc2` |
| UART_4 | `&uart4` | `serial3` | `/dev/ttymxc3` |

| Signal | SODIMM | iMX8MM pad | Linux device | Purpose |
| --- | --- | --- | --- | --- |
| UART_3_RX | 133 | SAI3_RXC | `/dev/ttymxc0` | Console (`&uart1`) |
| UART_3_TX | 135 | SAI3_RXD | `/dev/ttymxc0` | Console (`&uart1`) |
| UART_1_TX | 131 | SAI3_TXC | `/dev/ttymxc1` | wM-Bus transceiver (`&uart2`) |
| UART_1_RX | 129 | SAI3_TXFS | `/dev/ttymxc1` | wM-Bus transceiver (`&uart2`) |
| UART_2_TX | 139 | ECSPI1_MOSI | `/dev/ttymxc2` | CC2652P Zigbee adapter (`&uart3`) |
| UART_2_RX | 137 | ECSPI1_SCLK | `/dev/ttymxc2` | CC2652P Zigbee adapter (`&uart3`) |
| UART_2_CTS | 141 | ECSPI1_MISO | `/dev/ttymxc2` | pin-muxed but unused (`&uart3` deletes `uart-has-rtscts`) |
| UART_2_RTS | 143 | ECSPI1_SS0 | `/dev/ttymxc2` | pin-muxed but unused (`&uart3` deletes `uart-has-rtscts`) |
| UART_4_RX | 196 | ECSPI2_SCLK | `/dev/ttymxc3` | nRF5340 Thread RCP (`&uart4`), M4-reserved |
| UART_4_TX | 200 | ECSPI2_MOSI | `/dev/ttymxc3` | nRF5340 Thread RCP (`&uart4`), M4-reserved |
| UART_4_CTS | 198 | ECSPI2_MISO | `/dev/ttymxc3` | nRF5340 Thread RCP (`&uart4`), M4-reserved |
| UART_4_RTS | 202 | ECSPI2_SS0 | `/dev/ttymxc3` | nRF5340 Thread RCP (`&uart4`), M4-reserved |
| GPIO_1 | 206 | GPIO3_IO04 | `gpiochip2` line 4 | Ready-LED (gpio-leds, `/sys/class/leds/ready`) |
| GPIO_2 | 30 | GPIO4_IO25 | `gpiochip3` line 25 | LoRa power enable |
| GPIO_3 | 32 | GPIO4_IO24 | `gpiochip3` line 24 | Zigbee power enable |
| GPIO_4 | 34 | GPIO4_IO26 | `gpiochip3` line 26 | wM-Bus power enable |
| I2C_1_SCL | 14 | I2C4_SCL | `/dev/i2c-3` | ATECC608B + MAX17048 (`&i2c4`) |
| I2C_1_SDA | 12 | I2C4_SDA | `/dev/i2c-3` | ATECC608B + MAX17048 (`&i2c4`) |
| Battery-Charge-Enable | 42 | GPIO3_IO23 | `gpiochip2` line 23 | Battery charge enable |
| Battery-Charge | 44 | GPIO3_IO22 | `gpiochip2` line 22 | Battery charge status |
| Battery-Alert | 46 | GPIO3_IO24 | `gpiochip2` line 24 | Battery alert |
| Version-Bit0 | 212 | GPIO5_IO27 | `gpiochip4` line 27 | Version bit 0 (LSB) |
| Version-Bit1 | 216 | GPIO1_IO00 | `gpiochip0` line 0 | Version bit 1 |
| Version-Bit2 | 218 | GPIO1_IO11 | `gpiochip0` line 11 | Version bit 2 (MSB) |
| GPIO-Num0 | 17 | GPIO3_IO15 | `gpiochip2` line 15 | Bidirectional GPIO header |
| GPIO-Num1 | 19 | GPIO1_IO01 | `gpiochip0` line 1 | Bidirectional GPIO header |
| GPIO-Num2 | 21 | GPIO3_IO03 | `gpiochip2` line 3 | Bidirectional GPIO header |
| GPIO-Num3 | 91 | GPIO5_IO02 | `gpiochip4` line 2 | Bidirectional GPIO header |
| GPIO-Num4 | 93 | GPIO5_IO19 | `gpiochip4` line 19 | Bidirectional GPIO header |
| GPIO-Num5 | 94 | GPIO4_IO03 | `gpiochip3` line 3 | Bidirectional GPIO header |
| GPIO-Num6 | 95 | GPIO5_IO18 | `gpiochip4` line 18 | Bidirectional GPIO header |
| GPIO-Num7 | 96 | GPIO4_IO04 | `gpiochip3` line 4 | Bidirectional GPIO header |
| Zigbee-Reset | 104 | GPIO4_IO11 | `gpiochip3` line 11 | CC2652P reset |
| Zigbee-Boot | 106 | GPIO4_IO12 | `gpiochip3` line 12 | CC2652P bootloader select |
| wM-Bus-Reset | 102 | GPIO4_IO00 | `gpiochip3` line 0 | wM-Bus transceiver reset |
| Thread-Power-Enable | 36 | GPIO4_IO23 | `gpiochip3` line 23 | nRF5340 power enable |
| Thread-Reset | 108 | GPIO4_IO13 | `gpiochip3` line 13 | nRF5340 reset |
| Thread-Boot | 112 | GPIO4_IO14 | `gpiochip3` line 14 | nRF5340 bootloader select |
| SSD-Power-Enable | 187 | GPIO1_IO15 | `gpiochip0` line 15 | NVMe SSD power enable |
| PowerMux-Status | 210 | GPIO5_IO26 | `gpiochip4` line 26 | Power mux status |

GPIOs with a `gpio-line-names` label can be used by name with libgpiod
(`gpioget --by-name gpiochip3 lora-power-enable`). The ready-LED uses the LED
subsystem (`/sys/class/leds/ready/brightness`), set by
`gateway-ready-led.service` once the boot completes.

On the mainboard `uart4` (Thread RCP) is still `status = "disabled"`: UART_4 is
shared with the Cortex-M4. Enable it once the M4 firmware no longer claims it.

### Battery and sensors

| Device | Interface | Purpose | sysfs / named line |
| --- | --- | --- | --- |
| MAX17048 fuel gauge | `&i2c4` (`/dev/i2c-3`) `0x36` | State of charge | `/sys/class/power_supply/max17040-0/` |
| Charge enable | GPIO3_IO23 `battery-charge-enable` | Enable/disable charging | `gpioset gpiochip2 23=1` |
| Charge status | GPIO3_IO22 `battery-charge` | Charging active (input) | `gpioget gpiochip2 22` |
| Alert | GPIO3_IO24 `battery-alert` | Low-battery alert (input) | `gpioget gpiochip2 24` |

Fuel gauge MAX17048 (`max17040_battery`): `/sys/class/power_supply/max17040-0/{capacity,voltage_now}`.
On-module: INA219 supply current (`/sys/class/hwmon/hwmon*/curr1_input`),
ADS1015 ADC (`/sys/bus/iio/devices/iio:device0/in_voltage*_raw`).

---

## Protocol Stack

The services only use the device names below. Which hardware sits behind
them per board: see [Board variants](#board-variants).

### LoRaWAN

- **Hardware / device:** RAK5146 USB concentrator (SX1302), `/dev/lora`
- **Services:** `lora-packet-forwarder` (`/opt/lora-packet-forwarder/`, EU868)
  → `chirpstack-gateway-bridge` → MQTT
- Started by udev when the concentrator appears, stopped when it disappears.
  `gateway-telemetry` restarts the forwarder if the bridge stops publishing
  stats (`LORA_WATCHDOG_TIMEOUT`)

### Zigbee

- **Device:** `/dev/zigbee`, adapter `zstack` (EFR32 sticks such as the
  ZBDongle-E: `ember`)
- **Service:** `zigbee2mqtt`, data in `/var/lib/zigbee2mqtt/`, base topic
  `zigbee2mqtt`. On Dahlia started when the USB stick is plugged in

### Wireless M-Bus

- **Device:** `/dev/wmbus` (iM871A, mainboard only), mode `auto:t1`
- **Service:** `wmbusmeters`, config in `/etc/wmbusmeters.d/`, log in
  `/var/log/wmbusmeters/`

### Bluetooth LE

- **Hardware:** Wi-Fi/BT chip of the Verdin module (SDIO, `hci0`)
- **Service:** `theengs-gateway` 1.8.0 (Theengs Decoder 2.5.0) decodes BLE
  sensor advertisements, MQTT topics `home/TheengsGateway/BTtoMQTT/<MAC>`
- **Home Assistant:** every sensor is a device of its own under
  `IoT-Gateway (Bluetooth)`. iBeacons are filtered out
- Python dependencies missing in scarthgap: `recipes-devtools/python/`
- Test firmware: `firmware/ESP32_BLE` (BTHome v2)

### Thread

- **Hardware / device:** nRF5340 RCP (`firmware/nRF5340`), `/dev/thread`,
  Spinel/HDLC, 115200 baud
- **Border router:** `otbr-agent`, ot-br-posix `thread-reference-20250612`
  (announces the Thread role via mDNS, last version with Avahi support),
  `wpan0`, backbone `mlan0` with IPv6 (`OTBR_INFRA_IFACE`), TREL enabled,
  IPv6 forwarding on
- **Network start:** `thread-network-init` attaches the network on every
  `otbr-agent` start, sets the fixed OMR prefix and keeps a copy of the
  dataset in `/var/lib/gateway-os/thread-dataset.hex`. A missing dataset is
  restored from it; a new network is only formed on the very first start

```text
OTBR_AGENT_OPTS="-I wpan0 -B mlan0 spinel+hdlc+uart:///dev/thread?uart-baudrate=115200 trel://mlan0"
```

#### LAN route to the Thread network

Thread devices get addresses from the OMR prefix. Not every LAN host learns
the route from the gateway's Router Advertisements (Android does, Synology
DSM does not), so the LAN router needs one static IPv6 route. Both values
are fixed by variables, so the route stays valid when the Thread network is
recreated or the gateway is re-flashed:

| Route field (FritzBox: *Home Network → Network → Network Settings → IPv6 Routes*) | Value | Variable |
| --- | --- | --- |
| IPv6 network / prefix length | `fdf2:83ec:1caf:1::` / `64` | `OTBR_OMR_PREFIX` (`thread-network-init`) |
| Gateway | `fd8f:e72d:8065:0:3f1e:f55:3464:19db` | `OTBR_INFRA_IPV6_ADDRESS` (`ot-br-posix`), set on `mlan0` by a NetworkManager dispatcher script |

`OTBR_INFRA_IPV6_ADDRESS` must lie in the LAN's IPv6 prefix. With several
gateways in one LAN, give each its own OMR prefix and address. Check:
`ot-ctl br omrprefix` (fixed prefix as `Favored`), `ip -6 addr show mlan0`.

### MQTT

Local broker: Mosquitto (`recipes-connectivity/mosquitto/files/mosquitto.conf`).
`gateway-telemetry` publishes gateway status, service states and restart
buttons to Home Assistant via MQTT discovery.

---

## Networking

NetworkManager manages all interfaces (`systemd-networkd`/`resolved` are
removed).

| Interface | Configuration | Purpose |
| --- | --- | --- |
| `mlan0` (Wi-Fi) | profile created by `wifi-captive-portal` | LAN uplink, Thread backbone |
| `end0` (Ethernet) | `eth-dhcp`: DHCP plus static `192.168.0.2/24`. Dahlia: static only, no default route | wired LAN / direct PC connection |
| `uap0` | unmanaged, captive portal access point | Wi-Fi setup |
| `wpan0` | `otbr-agent` | Thread |

**Wi-Fi setup:** without stored credentials, or if the connection fails,
`wifi-captive-portal` opens the access point `Gateway-Setup` with a captive
portal to choose a network. **Direct Ethernet:** give the PC an address in
`192.168.0.0/24` and connect to `192.168.0.2`.

---

## Security

- **SSH:** public key only (`/etc/ssh/sshd_config.d/00-gateway-hardening.conf`,
  password and keyboard-interactive login disabled)
- **Secure element:** ATECC608B at `0x60` on `&i2c4` (`/dev/i2c-3`), used from
  userspace via `cryptoauthlib` 3.7.4

---

## Logging

- **Journal:** in RAM (`/run/log/journal`, 64 MB), archived every 15 min to
  eMMC `/var/lib/journal-archive/` (gzip, 256 MB ring buffer,
  `journal-sync.timer`). Read with `zcat /var/lib/journal-archive/<file>.log.gz`
- **`/var/log` is RAM**, every log has a size limit. `logrotate` runs hourly,
  services ship their rule in `/etc/logrotate.d/` (`copytruncate` for programs
  without a reopen signal):

| Log | Location | Limit |
| --- | --- | --- |
| systemd journal | `/run/log/journal` (RAM) | `RuntimeMaxUse=64M` (journald) |
| Journal archive | `/var/lib/journal-archive` (eMMC) | ring buffer, 256 MB (`journal-sync`) |
| syslog (`/var/log/messages`) | RAM | busybox `syslogd`, 200 KB + 1 rotation |
| wmbusmeters | `/var/log/wmbusmeters/` (RAM) | logrotate: 1 MB, 3 compressed generations |
| Zigbee2MQTT | `/var/lib/zigbee2mqtt/log/` | Zigbee2MQTT keeps the last 10 log directories |

---

## Backup

`gateway-backup.timer` (daily) packs `/var/lib/zigbee2mqtt/` and the Thread
state (`/var/lib/thread/*.data`, `/var/lib/gateway-os/thread-dataset.hex`)
and pushes it via `rsync`/ssh to `GATEWAY_BACKUP_REMOTE`
(`/etc/default/gateway-backup`). Without a destination the run is skipped.

Setup: after the first boot, `journalctl -u gateway-backup-keygen` shows the
device's public key (`/etc/gateway-backup/id_ed25519.pub`). Add it to the
backup host's `authorized_keys`, set `GATEWAY_BACKUP_REMOTE` (e.g.
`backup@host:/srv/gateway-backups/`) and test with
`systemctl start gateway-backup`.

Restore:

```bash
systemctl stop zigbee2mqtt otbr-agent
mkdir -p /tmp/restore && tar -xzf <timestamp>.tar.gz -C /tmp/restore
cp -a /tmp/restore/<timestamp>/zigbee2mqtt/. /var/lib/zigbee2mqtt/
cp -a /tmp/restore/<timestamp>/thread/thread-dataset.hex /var/lib/gateway-os/
systemctl start otbr-agent zigbee2mqtt
```

`thread-network-init` restores the Thread dataset if `otbr-agent` has none.
On a gateway that already runs another network, set it with
`ot-ctl dataset set active $(cat /var/lib/gateway-os/thread-dataset.hex)`
(between `ot-ctl thread stop` / `ifconfig down` and `ifconfig up` / `thread start`).

---

## Maintainer

- [Daniel Kampert](mailto:DanielKampert@kampis-elektroecke.de)

<!-- SPDX-License-Identifier: MIT -->
<!-- Copyright (c) 2026 Daniel Kampert -->

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
    - [UARTs](#uarts)
    - [Module power](#module-power)
    - [Battery and sensors](#battery-and-sensors)
  - [Protocol Stack](#protocol-stack)
    - [LoRaWAN](#lorawan)
    - [Zigbee](#zigbee)
    - [Wireless M-Bus](#wireless-m-bus)
    - [Bluetooth LE](#bluetooth-le)
    - [Thread](#thread)
    - [MQTT](#mqtt)
    - [CAN](#can)
    - [Radio channels](#radio-channels)
  - [Networking](#networking)
  - [Security](#security)
  - [Updates (RAUC)](#updates-rauc)
  - [System](#system)
  - [Watchdog](#watchdog)
  - [Logging](#logging)
  - [Backup](#backup)
  - [Maintainer](#maintainer)

---

## Building

Dependencies (all `scarthgap`): `openembedded-core`, `meta-arm`,
`meta-toradex-nxp`, `meta-toradex-demos`, `meta-security`, `meta-rauc`
(`os/local_manifest.xml`, fetched by `make repo`).

```bash
bitbake-layers add-layer layers/meta-iot-gateway-os   # layer priority 6
source layers/poky/oe-init-build-env build
bitbake gateway-os-image       # Easy Installer image (first flash)
bitbake gateway-os-bundle      # RAUC update bundle, see "Updates (RAUC)"
```

Flashing: Easy Installer image from a USB stick (`make tezi-usb USB=<mount point>`)
or over the network from a Raspberry Pi next to the gateway, see
[docs/tezi-feed-raspberry-pi.md](docs/tezi-feed-raspberry-pi.md) (DHCP/DNS,
nginx feed, USB recovery with `uuu`, starting the Easy Installer from a
running gateway).

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

Set in `build/conf/local.conf`. The gateway has no MQTT broker of its own,
only clients. Point **all** of them to the broker that Home Assistant /
ChirpStack use (the defaults `localhost` only work with a broker added to the
image):

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
| `GATEWAY_KEYS_DIR` | `${TOPDIR}/../keys` | Folder with the signing keys (rauc/, nrf5340/), default: public debug keys in os/keys | `gateway-os.conf` |
| `GATEWAY_ROOTFS_SLOT_SIZE` | `4096` | Size of each of the two rootfs slots in MiB (A/B updates with RAUC) | `gateway-os.conf` |
| `CHIRPSTACK_MQTT_SERVER` | `tcp://localhost:1883` | MQTT broker URL for ChirpStack Gateway Bridge (e.g. tcp://192.168.1.10:1883) | `chirpstack-gateway-bridge_4.0.11.bb` |
| `GATEWAY_CAN_INTERFACES` | `can0` | CAN interfaces brought up at boot (space separated, empty = none) | `gateway-can_1.0.bb` |
| `GATEWAY_CAN_BITRATE` | `500000` | CAN bitrate in bit/s | `gateway-can_1.0.bb` |
| `GATEWAY_CAN_RESTART_MS` | `100` | CAN automatic restart after bus-off in ms (0 = off) | `gateway-can_1.0.bb` |
| `LORA_SERVER_ADDRESS` | `localhost` | Address of the ChirpStack Gateway Bridge (Semtech UDP server) | `lora-packet-forwarder_2.1.0.bb` |
| `NRF5340_FW_SRC` | `${TOPDIR}/../../firmware/nRF5340` | nRF5340 firmware sources (west manifest app/west.yml, configs, overlays) | `nrf5340-rcp-firmware_2.7.0.bb` |
| `NRF5340_DFU_KEY` | `${GATEWAY_KEYS_DIR}/nrf5340/dfu_signing_key.pem` | MCUboot signing key of the nRF5340 (Ed25519, see os/keys/README.md) | `nrf5340-rcp-firmware_2.7.0.bb` |
| `GATEWAY_SSH_AUTHORIZED_KEYS` | `${GATEWAY_KEYS_DIR}/gateway_ed25519.pub` | Public SSH key(s) allowed to log in as root (authorized_keys format) | `openssh_%.bbappend` |
| `OTBR_INFRA_IFACE` | `mlan0` | Network interface connected to the backbone (IP) network with IPv6 | `ot-br-posix_%.bbappend` |
| `OTBR_INFRA_IPV6_ADDRESS` | `fd8f:e72d:8065:0:3f1e:f55:3464:19db/64` | Fixed IPv6 address/prefix length of the gateway on OTBR_INFRA_IFACE, next hop of the LAN router's route to OTBR_OMR_PREFIX (empty = none) | `ot-br-posix_%.bbappend` |
| `OTBR_RCP_DEVICE` | `/dev/thread` | Thread RCP device path (nRF5340 flashed with OpenThread RCP firmware, connected via UART_4) | `ot-br-posix_%.bbappend` |
| `THEENGS_MQTT_HOST` | `localhost` | MQTT broker host for Theengs Gateway (Bluetooth LE to MQTT, e.g. 192.168.1.10) | `theengs-gateway_1.8.0.bb` |
| `OTBR_OMR_PREFIX` | `fdf2:83ec:1caf:1::/64` | Fixed OMR (off-mesh routable) IPv6 prefix of the Thread network, e.g. "fdxx:xxxx:xxxx:1::/64" (random ULA, RFC 4193). Empty = border router picks a random one that changes with every new Thread network. A fixed prefix lets the LAN router keep one static route to the gateway. | `thread-network-init_1.0.bb` |
| `OTBR_CHANNEL` | `20` | Thread channel (11-26). A new network is formed on it; an existing network on another channel is moved there with a pending dataset (5 min delay, devices follow without re-commissioning). Empty = keep the channel of the network | `thread-network-init_1.0.bb` |
| `WMBUSMETERS_MQTT_SERVER` | `localhost` | MQTT broker hostname (or hostname:port) for wmbusmeters (e.g. 192.168.1.10 or 192.168.1.10:1883) | `wmbusmeters_1.18.0.bb` |
| `WMBUSMETERS_DEVICE` | `/dev/wmbus:im871a:t1` | wM-Bus receiver for wmbusmeters (device:type:link modes, e.g. /dev/wmbus:im871a:t1) | `wmbusmeters_1.18.0.bb` |
| `ZIGBEE2MQTT_MQTT_SERVER` | `mqtt://localhost` | MQTT broker URL for Zigbee2MQTT (e.g. mqtt://192.168.1.10) | `zigbee2mqtt_2.12.0.bb` |
| `ZIGBEE2MQTT_BASE_TOPIC` | `@GATEWAY_ID@/zigbee` | MQTT base topic of Zigbee2MQTT, unique per broker. @GATEWAY_ID@ = gateway ID from the module serial number (gateway-id) | `zigbee2mqtt_2.12.0.bb` |
| `ZIGBEE2MQTT_HA_BRIDGE_NAME` | `IoT-Gateway (Zigbee)` | Name of the Zigbee bridge device in Home Assistant, parent of all Zigbee devices | `zigbee2mqtt_2.12.0.bb` |
| `ZIGBEE2MQTT_CHANNEL` | `25` | Zigbee channel (11-26), set on every start and overrides configuration.yaml; a change moves the existing network. Empty = configuration.yaml / Zigbee2MQTT default (11) | `zigbee2mqtt_2.12.0.bb` |
| `ZIGBEE2MQTT_USB_VID` | `10c4` | USB vendor ID of the Zigbee USB stick (Dahlia dev setup only) | `zigbee2mqtt_2.12.0.bb` |
| `ZIGBEE2MQTT_USB_PID` | `ea60` | USB product ID of the Zigbee USB stick (Dahlia dev setup only) | `zigbee2mqtt_2.12.0.bb` |
| `RAUC_KEY_FILE` | `${GATEWAY_KEYS_DIR}/rauc/development-1.key.pem` | Private key that signs the RAUC bundles | `gateway-os-bundle.bb` |
| `RAUC_CERT_FILE` | `${GATEWAY_KEYS_DIR}/rauc/development-1.cert.pem` | Certificate of the RAUC bundle signing key | `gateway-os-bundle.bb` |
| `GATEWAY_BACKUP_REMOTE` | `` | rsync/ssh destination of the daily backup, e.g. "backup@192.168.1.10:/volume1/backups/gateway/" (empty = no backup) | `gateway-backup_1.0.bb` |
| `GATEWAY_HEALTH_UNITS` | `NetworkManager.service otbr-agent.service gateway-telemetry.service` | Units that must be active on the first boot of a new system, otherwise rollback to the previous slot | `gateway-health-check_1.0.bb` |
| `GATEWAY_HEALTH_DEVICE_UNITS` | `/dev/zigbee:zigbee2mqtt.service /dev/lora:lora-packet-forwarder.service` | "<device>:<unit>" pairs, unit only required while the device exists (USB peripherals) | `gateway-health-check_1.0.bb` |
| `GATEWAY_HEALTH_TIMEOUT` | `300` | Seconds the health check waits for the units before it rolls back | `gateway-health-check_1.0.bb` |
| `GATEWAY_ID_PREFIX` | `iot-gateway` | Prefix of the gateway ID (<prefix>-<module serial number>), used as hostname and e.g. in the Zigbee2MQTT base topic | `gateway-id_1.0.bb` |
| `POWER_BATTERY_LEVEL` | `1` | Level of powermux-status (SODIMM 210) on battery (TPS2121 ST via inverting BSS138: 1 = battery, 0 = mains) | `gateway-power-monitor_1.0.bb` |
| `POWER_SHUTDOWN_PERCENT` | `10` | Clean shutdown when the gateway runs on battery and the battery is at or below this charge in % | `gateway-power-monitor_1.0.bb` |
| `POWER_SHUTDOWN_DELAY` | `0` | Clean shutdown after this many seconds on battery, regardless of the charge (0 = off) | `gateway-power-monitor_1.0.bb` |
| `TELEMETRY_MQTT_HOST` | `localhost` | MQTT broker host for the gateway telemetry (Home Assistant device "IoT-Gateway") | `gateway-telemetry_1.0.bb` |
| `TELEMETRY_DEVICE_NAME` | `IoT-Gateway` | Name of the gateway device in Home Assistant (CPU, memory, disk, battery, uptime) | `gateway-telemetry_1.0.bb` |
| `TELEMETRY_LORA_DEVICE_NAME` | `IoT-Gateway (LoRa)` | Name of the LoRa gateway device in Home Assistant, parent of all LoRa sensors | `gateway-telemetry_1.0.bb` |
| `TELEMETRY_BLE_DEVICE_NAME` | `IoT-Gateway (Bluetooth)` | Name of the Bluetooth gateway device in Home Assistant, parent of all BLE sensors | `gateway-telemetry_1.0.bb` |
| `TELEMETRY_WMBUS_DEVICE_NAME` | `IoT-Gateway (wM-Bus)` | Name of the wM-Bus gateway device in Home Assistant (wmbusmeters status and version) | `gateway-telemetry_1.0.bb` |
| `TELEMETRY_INPUT_VOLTAGE_CHANNEL` | `3` | ads1015 channel of the input voltage divider (empty = no Input Voltage sensor) | `gateway-telemetry_1.0.bb` |
| `TELEMETRY_INPUT_VOLTAGE_DIVIDER` | `9.3333` | Ratio of the input voltage divider, (R_top + R_bottom) / R_bottom | `gateway-telemetry_1.0.bb` |
| `GATEWAY_UPDATE_SERVER` | `` | Update server (base URL, e.g. http://192.168.0.1), bundle expected at <server>/bundles/gateway-os-bundle-<MACHINE>.raucb; GATEWAY_UPDATE_URL overrides the full URL (both empty = no update entity) | `gateway-telemetry_1.0.bb` |
| `GATEWAY_UPDATE_CHECK_INTERVAL` | `3600` | Seconds between checks for a new bundle on the update server | `gateway-telemetry_1.0.bb` |
| `GATEWAY_UPDATE_REBOOT` | `1` | Reboot into the new system after an install from Home Assistant (1/0) | `gateway-telemetry_1.0.bb` |
| `RAUC_KEYRING_FILE_PATH` | `${GATEWAY_KEYS_DIR}/rauc/ca.cert.pem` | RAUC keyring (CA certificate) installed on the gateway | `rauc-conf.bbappend` |

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
| Module power/reset/boot GPIOs | `module-power@<module>.service` drives them for LoRa, Zigbee, Thread and wM-Bus ([Module power](#module-power)) | no-op unit (dev kits and USB sticks have their own supply) |
| `end0` (Ethernet) | DHCP | static `192.168.0.2/24`, point-to-point link to the build host, no default route |
| LoRa | RAK5146 USB concentrator (`0483:5740`), `/dev/lora`, powered via `module-power@lora`. `lora-packet-forwarder` starts when it enumerates | RAK5146 USB concentrator, own supply. `lora-packet-forwarder` starts when it is plugged in |
| Zigbee | CC2652P module on UART_3, `/dev/zigbee`, powered via `module-power@zigbee` | USB stick, `/dev/zigbee` by VID/PID (`ZIGBEE2MQTT_USB_VID`/`_PID`, default `10c4:ea60`), own supply. `zigbee2mqtt` starts when it is plugged in, skipped without it |
| Thread RCP | Fanstel BT40 (nRF5340) on UART_4, `/dev/thread`, powered via `module-power@thread` | nRF5340-DK on UART_1, `/dev/thread`, own supply |
| wM-Bus | iM871A on UART_2, `/dev/wmbus`, powered via `module-power@wmbus` | none, wmbusmeters disabled |
| Debug console | UART_1, header X901 (`ttymxc1`, U-Boot patched) | UART_3, FTDI on USB-C X18 (`ttymxc0`, Toradex default) |

UART assignment of both boards: see [UARTs](#uarts).

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
| UART_3_RX | 147 | SAI2_RXC | `/dev/ttymxc0` | CC2652P Zigbee adapter (`&uart1`) |
| UART_3_TX | 149 | SAI2_RXFS | `/dev/ttymxc0` | CC2652P Zigbee adapter (`&uart1`) |
| UART_1_TX | 131 | SAI3_TXC | `/dev/ttymxc1` | Debug console, header X901 (`&uart2`) |
| UART_1_RX | 129 | SAI3_TXFS | `/dev/ttymxc1` | Debug console, header X901 (`&uart2`) |
| UART_2_TX | 139 | ECSPI1_MOSI | `/dev/ttymxc2` | iM871A wM-Bus transceiver (`&uart3`) |
| UART_2_RX | 137 | ECSPI1_SCLK | `/dev/ttymxc2` | iM871A wM-Bus transceiver (`&uart3`) |
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

`uart4` (Thread RCP) is muxed to SODIMM 196/198/200/202 (Verdin SPI_1 pins,
with RTS/CTS) instead of the standard UART_4 pins 151/153. UART_4 is reserved
for the Cortex-M4 by default; the TF-A RDC configuration (`recipes-bsp/imx-atf`)
assigns it to the A53, no M4 firmware is used.

### UARTs

Both boards use the same devicetree; the role of each UART is set by udev
rules (`/dev/zigbee`, `/dev/wmbus`, `/dev/thread`) and the console by U-Boot
(`console=`). The SoM UART pins are 1.8 V; the level after each entry is
SoM side ↔ peripheral side.

| Verdin UART | i.MX / Linux | SODIMM TxD / RxD (RTS / CTS) | `mainboard` (default) | `dahlia` (dev setup) |
| --- | --- | --- | --- | --- |
| UART_1 | `uart2`, `/dev/ttymxc1` (`30890000`) | 131 / 129 (133 / 135, unused) | **Debug console**, header X901, 115200 8N1 — **1.8 V**, no level shifter: use a 1.8 V USB-UART adapter | **Thread RCP** (nRF5340-DK), X20 pin 13 TxD / 12 RxD, 115200, no flow control (RTS/CTS on X20 pin 14/15 not used) — **1.8 V**, no level shifter: the DK's nRF VDD must be 1.8 V |
| UART_2 | `uart3`, `/dev/ttymxc2` (`30880000`) | 139 / 137 (143 / 141, unused) | **wM-Bus** iM871A, `/dev/wmbus`, no flow control — **1.8 V ↔ 3.3 V** (TXB0102 IC1303, 3.3 V from the module LDO) | unused (X19 pin 15 TxD / 14 RxD, 16 RTS / 17 CTS) — **1.8 V** |
| UART_3 | `uart1`, `/dev/ttymxc0` (`30860000`) | 149 / 147 | **Zigbee** CC2652P, `/dev/zigbee`, 115200, no flow control — **1.8 V ↔ 3.3 V** (TXB0102 IC1102, 3.3 V from the module LDO) | **Debug console**, FTDI FT4232 on USB-C X18 (default assembly), 115200 8N1 — **1.8 V ↔ FTDI** via the level shifter on the Dahlia, host side USB |
| UART_4 | `uart4`, `/dev/ttymxc3` (`30a60000`) | 200 / 196 (202 / 198), muxed to the SPI_1 pins | **Thread RCP** Fanstel BT40 (nRF5340), `/dev/thread`, 115200, RTS/CTS — **1.8 V ↔ 3.3 V** (NXB0104 IC1204, 3.3 V from the module LDO) | unused (the standard UART_4 pins 151/153 to the FTDI are not muxed) |

**Debug console on the mainboard:** Verdin modules have their console on
UART_3, but the mainboard routes header X901 to UART_1 and the Zigbee module
to UART_3. With `GATEWAY_BOARD = "mainboard"` everything after the boot ROM
prints on UART_1:

| Stage | Change | Where |
| --- | --- | --- |
| TF-A (BL31) | `ATF_BOOT_UART_BASE = 0x30890000` | `recipes-bsp/imx-atf/imx-atf_%.bbappend` |
| U-Boot SPL + U-Boot | UART2 clock in SPL, `stdout-path = &uart2` and pre-relocation pinmux in the U-Boot devicetree, `console=ttymxc1` | `recipes-bsp/u-boot/u-boot-toradex/0001-verdin-imx8mm-debug-console-on-UART_1.patch` |
| Linux | `console=ttymxc1,115200` from U-Boot, getty on `ttymxc1` (`SERIAL_CONSOLES`) | `conf/distro/gateway-os.conf` |
| Peripherals | Zigbee on `ttymxc0`, wM-Bus on `ttymxc2` | udev rules `99-zigbee.rules`, `99-wmbus.rules` |

Limits:

- The Toradex Easy Installer and USB recovery bring their own U-Boot and
  kernel and print on UART_3, i.e. towards the Zigbee module: no output on
  X901 while flashing. The CC2652P is unpowered then (LDO enable pulled down)
  and isolated by its TXB0102, so this does no harm.
- A U-Boot environment saved before the switch keeps `console=ttymxc0`:
  `env default -a; saveenv` or `fw_setenv console ttymxc1`.
- The bootloader is not part of the RAUC bundle, it changes only with a full
  flash (Easy Installer).
- On updates of the Toradex BSP, check that the U-Boot patch still applies.

### Module power

The radio modules on the mainboard are switched by the SoM. Each has a
TLV75733 LDO (3.3 V) whose enable input has a 10 kΩ pull-down, so the module
is **off** until Linux drives the line. Reset and boot go through 74LVC1G07
open-drain buffers: SoM line low = asserted, high = released (module side
pulled up).

| Module | Power enable | Reset | Boot (bootloader) | Unit | Pulled in by |
| --- | --- | --- | --- | --- | --- |
| Zigbee (CC2652P) | SODIMM 32 `zigbee-power-enable` | 104 `zigbee-reset` | 106 `zigbee-boot` | `module-power@zigbee` | `zigbee2mqtt.service` (`Requires=`) |
| Thread (nRF5340, Fanstel BT40) | 36 `thread-power-enable` | 108 `thread-reset` | 112 `thread-boot` (MCUboot serial recovery) | `module-power@thread` | `otbr-agent.service` (drop-in `10-module-power.conf`) |
| wM-Bus (iM871A) | 34 `wmbus-power-enable` | 102 `wmbus-reset` | – | `module-power@wmbus` (drop-in `lines.conf`, no boot line) | `wmbusmeters.service` (`Requires=`) |
| LoRa (RAK5146, USB) | 30 `lora-power-enable` | 185 = Verdin `USB_2_EN`, driven high (released) by the kernel's USB_2 VBUS regulator once the USB host runs | – | `module-power@lora` (drop-in `lines.conf`, power only) | `multi-user.target` at boot: `lora-packet-forwarder` is started by udev when the concentrator enumerates |

`module-power@<module>.service` (`gateway-module-power`) runs
`gpioset <module>-power-enable=1 <module>-reset=1 <module>-boot=1` and holds
the lines while it runs (`MODULE_POWER_LINES` in a drop-in for other lines).
It has `StopWhenUnneeded=yes`: when the module's service stops, the lines
are released and the pull-downs switch the module off; the next start
power-cycles it. `module-power@lora` stays on as long as the system runs. The services wait briefly after power-up (Thread 2 s for
MCUboot + RCP, wM-Bus 1 s). `flash-nrf5340` stops `otbr-agent` and drives
`thread-boot` / `thread-reset` itself for serial recovery.

Check: `systemctl status module-power@thread`, `gpioinfo | grep -E
"power-enable|reset|boot"` (consumer `module-power-<module>`).

On Dahlia, `module-power@.service` is a no-op unit (`files/board-dahlia/`):
the dev kits and USB sticks have their own supply.

### Battery and sensors

| Device | Interface | Purpose | sysfs / named line |
| --- | --- | --- | --- |
| MAX17048 fuel gauge | `&i2c4` (`/dev/i2c-3`) `0x36` | State of charge | `/sys/class/power_supply/max17040-0/` |
| Charge enable | GPIO3_IO23 `battery-charge-enable` | Enable/disable charging | `gpioset gpiochip2 23=1` |
| Charge status | GPIO3_IO22 `battery-charge` | Charging active (input) | `gpioget gpiochip2 22` |
| Alert | GPIO3_IO24 `battery-alert` | Low-battery alert (input) | `gpioget gpiochip2 24` |

Fuel gauge MAX17048 (`max17040_battery`): `/sys/class/power_supply/max17040-0/{capacity,voltage_now}`.
On-module: INA219 supply monitor (`ina2xx`, hwmon `ina219`: `in1_input` bus
voltage mV, `curr1_input` mA, `power1_input` µW), ADS1015 ADC
(`/sys/bus/iio/devices/iio:device0/in_voltage*_raw`). `sensors` (lm-sensors)
shows all hwmon devices.

**Power source (mainboard):** two TPS2121 power muxes. Their ST output is
high for IN1 and low for IN2 (internal pull-up, TPS2121 datasheet) and
reaches the SoM through an inverting BSS138 each:

| Mux | IN1 / IN2 | SoM line | Level |
| --- | --- | --- | --- |
| IC601 | DC jack (X601) / PoE | SODIMM 38 `input-status` (GPIO4_IO27, R605 to +1V8) | **0 = DC, 1 = PoE** |
| IC704 | mains (LM62460 buck) / battery (ADP1614 boost) | SODIMM 210 `powermux-status` (GPIO5_IO26, R723 to +1V8) | **0 = mains, 1 = battery** (`POWER_BATTERY_LEVEL`) |

IC601's ST also has a 10k pull-down (R604), which forms a divider with the
internal 6–20k pull-up: ST must stay above the BSS138 threshold for IN1 (check
on the board). Home Assistant sensor "Power Source": `DC` / `PoE` / `Battery`
(`TELEMETRY_POWER_SOURCE_LINES`).

**Input voltage (mainboard):** `V_In` through R611 100k / R612 12k to ADC_1
(module ADC TLA2024, `ads1015`, `in_voltage3`): `V_In = ADC × 112/12`
(`TELEMETRY_INPUT_VOLTAGE_CHANNEL`, `TELEMETRY_INPUT_VOLTAGE_DIVIDER`), Home
Assistant sensor "Input Voltage". ADC_1 reads up to 1.8 V, i.e. `V_In` up to
about 16.8 V.

Dahlia: both sensors are off (`:board-dahlia` empty), the inputs are not
wired there.

`gateway-power-monitor` (mains / battery, not enabled on Dahlia: no TPS2121
there, SODIMM 210 floats with a pull-up):

- writes the source to `/run/gateway-power/state`, Home Assistant binary
  sensor "Mains Power"
- sends Home Assistant events "Power Event" (`on_battery`, `mains_restored`,
  `shutdown`) for automations, e.g. a notification
- shuts down cleanly on battery at `POWER_SHUTDOWN_PERCENT` (default 10 %)
  or after `POWER_SHUTDOWN_DELAY` seconds on battery (0 = off), also
  without MQTT

**Hardware version:** straps on SODIMM 212/216/218 (`version-bit0..2`,
bit 0 = LSB, pads with pull-down: open = 0), shown as "Hardware" in Home
Assistant. Dahlia: `GPIO_4` / `GPIO_5_R` / `GPIO_6_R` on extension header X20
pins 30 / 32 / 33 (R339/R340 assembled), open = Rev. 0, bridge to +1.8 V
(X20 pin 26) for other values. SODIMM 210 (power mux status) is `GPIO_3` on
X20 pin 29 with a pull-up, so `gateway-power-monitor` stays disabled there.

---

## Protocol Stack

The services only use the device names below. Which hardware sits behind
them per board: see [Board variants](#board-variants).

### LoRaWAN

- **Hardware / device:** RAK5146 USB concentrator (SX1302), `/dev/lora`
- **Power (mainboard):** `module-power@lora` switches the RAK5146 on and
  releases its reset/boot lines (power enable `lora-power-enable`, reset
  `USB_2_EN` via the kernel) at boot, before it enumerates on USB. Stopping
  the unit switches the module off again. On Dahlia a no-op ([Module power](#module-power))
- **Services:** `lora-packet-forwarder` (`/opt/lora-packet-forwarder/`, EU868)
  → `chirpstack-gateway-bridge` → MQTT
- Started by udev when the concentrator appears, stopped when it disappears.
  `gateway-telemetry` restarts the forwarder if the bridge stops publishing
  stats (`LORA_WATCHDOG_TIMEOUT`)

### Zigbee

- **Device:** `/dev/zigbee`, adapter `zstack` (EFR32 sticks such as the
  ZBDongle-E: `ember`)
- **Power (mainboard):** `module-power@zigbee` switches the CC2652P on and
  releases its reset/boot lines (power enable `zigbee-power-enable`, reset
  `zigbee-reset`, boot `zigbee-boot`) before `zigbee2mqtt` starts. Stopping
  the unit switches the module off again. On Dahlia a no-op ([Module power](#module-power))
- **Service:** `zigbee2mqtt`, data in `/var/lib/zigbee2mqtt/`. On Dahlia
  started when the USB stick is plugged in
- **Base topic:** `<gateway ID>/zigbee`, e.g. `iot-gateway-07219303/zigbee`
  (`ZIGBEE2MQTT_BASE_TOPIC`, default `@GATEWAY_ID@/zigbee`). Two Zigbee2MQTT
  instances with the same base topic on one broker delete each other's Home
  Assistant entities, the gateway ID keeps it unique without per-gateway
  settings. `zigbee2mqtt.service` fills in `@GATEWAY_ID@` at the first start
  (`/var/lib/zigbee2mqtt/configuration.yaml`)
- **Gateway ID:** `gateway-id.service` writes `/run/gateway-id.env` at boot:
  `GATEWAY_SERIAL` (serial number of the Verdin module from the devicetree,
  fallback `/etc/machine-id`) and `GATEWAY_ID=<GATEWAY_ID_PREFIX>-<serial>`.
  Services load it with `EnvironmentFile=/run/gateway-id.env`. It is also
  the hostname (`iot-gateway-07219303.local` via mDNS), as long as
  `/etc/hostname` still has the image default `iot-gateway`

### Wireless M-Bus

- **Device:** `/dev/wmbus` (iM871A, mainboard only), mode `auto:t1`
- **Power (mainboard):** `module-power@wmbus` switches the iM871A on and
  releases its reset/boot lines (power enable `wmbus-power-enable`, reset
  `wmbus-reset`) before `wmbusmeters` starts. Stopping the unit switches the
  module off again. On Dahlia a no-op ([Module power](#module-power))
- **Service:** `wmbusmeters`, config in `/etc/wmbusmeters.d/`, log in
  `/var/log/wmbusmeters/`

### Bluetooth LE

- **Hardware:** Wi-Fi/BT chip of the Verdin module (SDIO, `hci0`)
- **Service:** `theengs-gateway` 1.8.0 (Theengs Decoder 2.5.0) decodes BLE
  sensor advertisements, MQTT topics `home/TheengsGateway/BTtoMQTT/<MAC>`
- **Home Assistant:** every sensor is a device of its own under
  `IoT-Gateway (Bluetooth)`. iBeacons are filtered out
- Python dependencies missing in scarthgap: `recipes-devtools/python/`
- Test firmware: `firmware/device/ESP32_BLE` (BTHome v2)

### Thread

- **Hardware / device:** nRF5340 RCP (`firmware/nRF5340`), `/dev/thread`,
  Spinel/HDLC, 115200 baud
- **Power (mainboard):** `module-power@thread` switches the nRF5340 on and
  releases its reset/boot lines (power enable `thread-power-enable`, reset
  `thread-reset`, boot `thread-boot`) before `otbr-agent` starts. Stopping the
  unit switches the module off again. On Dahlia a no-op ([Module power](#module-power))
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

**RCP firmware** (`nrf5340-rcp-firmware`): built from `firmware/nRF5340`
with the nRF Connect SDK of `app/west.yml` (fetched once into `downloads/`
by `do_fetch_ncs`, with the Zephyr SDK ARM toolchain and a Python venv, no
local NCS needed), variant `dk` on Dahlia, `gateway` on the mainboard. Signed
with `NRF5340_DFU_KEY` (debug key `os/keys/nrf5340/dfu_signing_key.pem`).

| File on the gateway (`/usr/share/nrf5340-rcp/`) | Use |
| --- | --- |
| `rcp.signed.bin` | update over UART: `flash-nrf5340` |
| `netcore-merged.hex` | MCUboot + RCP for a full flash over SWD (first flash, new key) |
| `appcore-merged.hex` | Dahlia/DK only: app-core companion (releases the network core), SWD |

The same files are in `deploy/images/verdin-imx8mm/nrf5340-rcp/` for
flashing from a PC (`nrfjprog --program <file> --chiperase --verify`, net
core: `--coprocessor CP_NETWORK`).

`flash-nrf5340 [image]` stops `otbr-agent`, brings MCUboot into serial
recovery and uploads the image with `mcumgr` over `/dev/thread`, then starts
`otbr-agent` again:

- **mainboard:** recovery via the `thread-boot` / `thread-reset` GPIOs, no
  interaction
- **Dahlia:** connect P1.15 of the nRF5340-DK to GND and press RESET on the
  DK when asked (115200 baud)

MCUboot only accepts images signed with its own key: a nRF5340 flashed with
another key (e.g. the old `app/dfu_signing_key.pem`) needs one full flash of
`netcore-merged.hex` (and on the DK `appcore-merged.hex`) over SWD first.

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

No broker on the gateway: all services are clients of an external broker
(`*_MQTT_SERVER` / `*_MQTT_HOST`, see [Configuration Variables](#configuration-variables)).
`mosquitto-clients` (`mosquitto_pub`) is installed for wmbusmeters and tests.
`gateway-telemetry` publishes gateway status, service states and restart
buttons to Home Assistant via MQTT discovery:

| Home Assistant | Content |
| --- | --- |
| Firmware (device card) | `GatewayOS <DISTRO_VERSION> (<layer git revision>)` |
| Hardware (device card) | `Rev. <n>` from the version straps (Dahlia: X20 pins 30/32/33) |
| Serial number (device card) | serial number of the Verdin module (`GATEWAY_SERIAL`, gateway-id) |
| Diagnostic entities (one per line; the device card shows no line breaks inside a field) | Linux Kernel, Bootloader (`/proc/device-tree/chosen/u-boot,version`), GatewayOS Layer (`/etc/os-release`: `VERSION_ID` = `DISTRO_VERSION`, `BUILD_ID` = git revision of this layer), Hardware Version |
| Sensors | CPU temperature/load, memory, disk, uptime, battery (MAX17048), Supply Voltage/Current/Power (INA219), Input Voltage (ADC_1, mainboard) |
| Power Source | `DC` / `PoE` / `Battery` from the two TPS2121 power muxes (mainboard) |
| Mains Power, Power Event | power source and events of `gateway-power-monitor` (mainboard) |
| GatewayOS (update) | installed / latest version, install button (`GATEWAY_UPDATE_SERVER`, see Updates) |

---

### CAN

MCP2518FD on the Verdin module (`mcp251xfd`, `can0`). `can@can0.service`
(`gateway-can`) brings it up at boot with `GATEWAY_CAN_BITRATE` (default
500 kbit/s) and automatic restart after bus-off (`GATEWAY_CAN_RESTART_MS`),
`GATEWAY_CAN_INTERFACES` lists the interfaces. Tools: `can-utils`
(`candump can0`, `cansend can0 123#DEADBEEF`), `ip -details link show can0`.

---

### Radio channels

Four radios share the 2.4 GHz band (Wi-Fi, Bluetooth, Zigbee, Thread). The
channels are planned so that they do not overlap:

| Radio | Channel | Frequency | Set by |
|---|---|---|---|
| Wi-Fi (home network) | 1 | 2401–2423 MHz | access point (router) |
| Bluetooth LE | adaptive | 2402–2480 MHz | hops around busy channels by itself |
| Thread | 20 | 2450 MHz | `OTBR_CHANNEL` (`thread-network-init`) |
| Zigbee | 25 | 2475 MHz | `ZIGBEE2MQTT_CHANNEL` (`zigbee2mqtt`) |
| LoRaWAN | EU868 | 863–870 MHz | `global_conf.json` (no conflict with 2.4 GHz) |

802.15.4 channel *n* (Zigbee, Thread) is at 2405 + 5 × (*n* − 11) MHz, 2 MHz
wide. Wi-Fi channels are 20 MHz wide, so Wi-Fi 1 covers 802.15.4 channels
11–14, Wi-Fi 6 covers 16–19 and Wi-Fi 11 covers 21–24.

- **Zigbee 25** is one of the Zigbee Light Link channels (11, 15, 20, 25)
  that Hue, IKEA, Ledvance etc. use, every common device supports it. Avoid
  26: many devices send with reduced power there or do not support it.
- **Thread 20** sits between Wi-Fi 1 and Zigbee 25 with 25 MHz to each side.
- **Wi-Fi:** best on 5 GHz. On 2.4 GHz keep it on channel 1. With another
  Wi-Fi channel re-plan: Wi-Fi 6 → Thread 11, Zigbee 25; Wi-Fi 11 → Thread 11,
  Zigbee 15.
- **Other Zigbee networks** nearby (e.g. a second coordinator, a Hue bridge)
  should use a different channel than the gateway.

**Changing the channels:**

- `ZIGBEE2MQTT_CHANNEL` is passed to Zigbee2MQTT as
  `ZIGBEE2MQTT_CONFIG_ADVANCED_CHANNEL` on every start (overrides
  `configuration.yaml`, also on existing installations with configuration in
  `/data`). If the coordinator runs on another channel, zigbee-herdsman moves
  the network (`Mgmt_NWK_Update_req` broadcast). Mains-powered routers
  (bulbs, plugs) follow right away. Sleeping battery devices (buttons,
  sensors) often miss the broadcast: many find the network again with a
  rejoin when they wake up, some (older or Tuya/Aqara devices) have to be
  paired again. Change the channel once, when setting up the network.
- `OTBR_CHANNEL` is used when a new Thread network is formed. An existing
  network on another channel is moved by `thread-network-init` with a pending
  dataset (5 min delay), like "change channel" in Home Assistant. All Thread
  devices switch without being commissioned again. Check with
  `ot-ctl channel` and `ot-ctl dataset pending`.
- Empty variables keep the current channel.

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

- **Keys:** all keys in `os/keys/` (`GATEWAY_KEYS_DIR`,
  [os/keys/README.md](../../keys/README.md)): SSH root login, RAUC CA and
  signing key, nRF5340 MCUboot key. The keys in the repository are **public
  debug keys**: anybody with the repository can log in as root on a gateway
  built with them and sign updates it accepts. Gateways in the field need own
  keys outside git (`GATEWAY_KEYS_DIR` in `local.conf`)
- **SSH:** public key only (`/etc/ssh/sshd_config.d/00-gateway-hardening.conf`,
  password and keyboard-interactive login disabled), key from
  `GATEWAY_SSH_AUTHORIZED_KEYS` (default `os/keys/gateway_ed25519.pub`)
- **Secure element:** ATECC608B at `0x60` on `&i2c4` (`/dev/i2c-3`), used from
  userspace via `cryptoauthlib` 3.7.4

---

## Updates (RAUC)

A/B updates with [RAUC](https://rauc.io) (`meta-rauc`, `scarthgap`). The first
installation of this layout uses the Easy Installer
([docs/tezi-feed-raspberry-pi.md](docs/tezi-feed-raspberry-pi.md)). Each
rootfs slot contains its own kernel and devicetree (`/boot`), so a bundle
updates both. The layout is created by the Toradex Easy Installer image
(`gateway_tezi_ab_layout` in `gateway-os-image.bb`), switching from the old
single-rootfs layout needs one Easy Installer flash:

| Partition | Label | Content |
| --- | --- | --- |
| `mmcblk0p1` | `BOOT` | U-Boot boot script only (`recipes-bsp/u-boot/u-boot-distro-boot/boot.cmd.in`) |
| `mmcblk0p2` | `RFS_A` | rootfs slot A, `GATEWAY_ROOTFS_SLOT_SIZE` MiB |
| `mmcblk0p3` | `RFS_B` | rootfs slot B, same size, flashed with the same image |
| `mmcblk0p4` | `DATA` | rest of the eMMC, mounted on `/data` |

- **Persistent data:** `/sbin/preinit` (kernel argument `init=/sbin/preinit`,
  template `recipes-core/images/files/gateway-preinit.sh.in`) mounts `DATA`
  on `/data` and overlays `/etc`, `/var/lib`, `/home` and `/root` before systemd starts
  (the rootfs is read-only, see [System](#system))
  (`/data/overlay-<dir>/upper`). Wi-Fi and network settings, SSH host keys,
  `machine-id`, Zigbee and Thread networks, the backup key and the journal
  archive survive updates. Files from the new image appear unless they were
  changed on the gateway.
- **Slot selection:** U-Boot variables `BOOT_ORDER` (default `A B`),
  `BOOT_A_LEFT` / `BOOT_B_LEFT` (3 attempts each). Every boot takes one
  attempt, `rauc-mark-good.service` confirms the slot after a successful boot.
  A slot that fails 3 times is skipped, the gateway falls back to the other
  one. Environment: `/dev/mmcblk0boot0` (`/etc/fw_env.config`, `fw_printenv`).
- **Kernel:** `recipes-kernel/linux/linux-toradex/rauc.cfg` (overlayfs built
  in for preinit; loop, squashfs, dm-verity for bundles; nbd for streaming).
- **Watchdog:** a hanging kernel or systemd resets the SoC (see
  [Watchdog](#watchdog)) and counts as a failed attempt.
- **Health check:** on the first boot of a new bundle
  `gateway-health-check` waits for the important services before the slot is
  marked good, otherwise it rolls back to the previous slot right away.
- **From Home Assistant:** update entity "GatewayOS" with installed/latest
  version and install button (`GATEWAY_UPDATE_SERVER`).
- **Update process:** building, signing, installing (streaming over HTTP,
  from Home Assistant), health check, rollback, persistent data and keys:
  [docs/ota-rauc.md](docs/ota-rauc.md).
- **Bundle:** `bitbake gateway-os-bundle` →
  `deploy/images/verdin-imx8mm/gateway-os-bundle-verdin-imx8mm.raucb`
  (format `verity`, adaptive `block-hash-index`: streaming installs only
  download blocks not already on the gateway; compatible
  `iot-gateway-<GATEWAY_BOARD>`, so a Dahlia bundle is rejected by a
  mainboard gateway and vice versa).

```bash
scp gateway-os-bundle-verdin-imx8mm.raucb root@<gateway>:/data/
ssh root@<gateway> rauc install /data/gateway-os-bundle-verdin-imx8mm.raucb
ssh root@<gateway> reboot
ssh root@<gateway> rauc status        # booted slot, both slots "good"
```

**Signing keys** (`os/keys/rauc/`, public debug keys, see "Security"):
`ca.key.pem` / `ca.cert.pem` (CA, valid until 2046) and
`development-1.key.pem` / `development-1.cert.pem` (signing key issued by the
CA, `RAUC_KEY_FILE` / `RAUC_CERT_FILE`). The gateway only accepts bundles
signed by a key of this CA (`RAUC_KEYRING_FILE_PATH` → `/etc/rauc/ca.cert.pem`).

---

## System

- **Read-only rootfs** (`read-only-rootfs`): `/` is mounted read-only, the
  gateway writes only to `DATA` (overlays `/etc`, `/var/lib`, `/home`,
  `/root`) and tmpfs (`/run`, `/tmp`, `/var/volatile` with `/var/log`). A power
  loss cannot damage the system, every slot is exactly its image. Generated
  files go to `/run` (e.g. `/run/lora-packet-forwarder/global_conf.json`,
  `/run/gateway-id.env`). Changes outside the overlays need a new image
  (RAUC bundle).
- **zram swap** (`zram`, `/etc/default/zram`): compressed swap in RAM,
  50 % of the RAM, lz4. Buffer for memory peaks (Zigbee2MQTT/Node.js,
  Python), no swap writes to the eMMC. Check: `swapon`, `zramctl`.
- **Diagnostics:** `nvme-cli` / `smartmontools` (`nvme smart-log /dev/nvme0`,
  `smartctl -a /dev/nvme0`, NVMe temperature in hwmon), `mmc-utils`
  (`mmc extcsd read /dev/mmcblk0 | grep -i life` = eMMC wear), `sensors`,
  `can-utils`.

---

## Watchdog

systemd drives the i.MX watchdog (`imx2+ watchdog`, `/dev/watchdog0`):
`RuntimeWatchdogSec=30s`, `RebootWatchdogSec=5min`
(`recipes-core/systemd/systemd-conf/10-gateway-watchdog.conf`). If the kernel
or PID 1 hang for 30 s, the SoC resets. Check:
`systemctl show -p RuntimeWatchdogUSec`.

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

`gateway-backup.timer` (daily) packs all persistent data, the changes in the
`/etc`, `/var/lib`, `/home` and `/root` overlays on `DATA`
(`/data/overlay-<dir>/upper`): Wi-Fi and network settings, SSH host keys,
`machine-id` (identifies the gateway in Home Assistant), hostname,
Zigbee2MQTT (coordinator DB and network backup), Thread dataset and the
backup key. Not included: the journal archive. The archive
`<GATEWAY_ID>-<timestamp>.tar.gz` (e.g. `iot-gateway-07219303-20261114-000030.tar.gz`,
GNU tar with xattrs, a few MB) is pushed via `rsync`/ssh to
`GATEWAY_BACKUP_REMOTE` (`local.conf`, on the gateway
`/etc/default/gateway-backup`). Without a destination the run is skipped.
Old archives are not deleted on the backup host.

Setup:

1. Backup host (e.g. the NAS): user with SSH access and a target directory.
2. After the first boot, `journalctl -u gateway-backup-keygen` shows the
   gateway's public key (`/etc/gateway-backup/id_ed25519.pub`). Add it to the
   backup user's `authorized_keys`. A new Easy Installer flash creates a new
   key; restoring a backup brings the old one back.
3. `GATEWAY_BACKUP_REMOTE = "backup@192.168.178.103:/volume1/backups/gateway/"`
   in `local.conf` (or in `/etc/default/gateway-backup` on the gateway).
4. Test: `systemctl start gateway-backup`, `journalctl -u gateway-backup`.

Restore (same or replacement gateway, also after an Easy Installer flash):

```bash
scp iot-gateway-07219303-<timestamp>.tar.gz root@<gateway>:/data/restore.tar.gz
ssh root@<gateway> reboot
```

`/sbin/preinit` unpacks it before the overlays are mounted, replaces their
contents and renames the archive to `restore.tar.gz.done` (`.failed` if it
cannot be unpacked, the current data stay). The gateway then has the backed
up identity (`machine-id`, hostname, Zigbee2MQTT base topic), so Home
Assistant keeps its devices and entities.

`thread-network-init` restores the Thread dataset if `otbr-agent` has none.
On a gateway that already runs another network, set it with
`ot-ctl dataset set active $(cat /var/lib/gateway-os/thread-dataset.hex)`
(between `ot-ctl thread stop` / `ifconfig down` and `ifconfig up` / `thread start`).

---

## Maintainer

- [Daniel Kampert](mailto:DanielKampert@kampis-elektroecke.de)

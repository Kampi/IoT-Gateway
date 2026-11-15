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
    - [Battery and sensors](#battery-and-sensors)
  - [Protocol Stack](#protocol-stack)
    - [LoRaWAN](#lorawan)
    - [Zigbee](#zigbee)
    - [Wireless M-Bus](#wireless-m-bus)
    - [Bluetooth LE](#bluetooth-le)
    - [Thread](#thread)
    - [MQTT](#mqtt)
    - [CAN](#can)
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
| `WMBUSMETERS_MQTT_SERVER` | `localhost` | MQTT broker hostname (or hostname:port) for wmbusmeters (e.g. 192.168.1.10 or 192.168.1.10:1883) | `wmbusmeters_1.18.0.bb` |
| `WMBUSMETERS_DEVICE` | `/dev/wmbus:im871a:t1` | wM-Bus receiver for wmbusmeters (device:type:link modes, e.g. /dev/wmbus:im871a:t1) | `wmbusmeters_1.18.0.bb` |
| `ZIGBEE2MQTT_MQTT_SERVER` | `mqtt://localhost` | MQTT broker URL for Zigbee2MQTT (e.g. mqtt://192.168.1.10) | `zigbee2mqtt_2.12.0.bb` |
| `ZIGBEE2MQTT_BASE_TOPIC` | `@GATEWAY_ID@/zigbee` | MQTT base topic of Zigbee2MQTT, unique per broker. @GATEWAY_ID@ = gateway ID from the module serial number (gateway-id) | `zigbee2mqtt_2.12.0.bb` |
| `ZIGBEE2MQTT_HA_BRIDGE_NAME` | `IoT-Gateway (Zigbee)` | Name of the Zigbee bridge device in Home Assistant, parent of all Zigbee devices | `zigbee2mqtt_2.12.0.bb` |
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
- **Services:** `lora-packet-forwarder` (`/opt/lora-packet-forwarder/`, EU868)
  → `chirpstack-gateway-bridge` → MQTT
- Started by udev when the concentrator appears, stopped when it disappears.
  `gateway-telemetry` restarts the forwarder if the bridge stops publishing
  stats (`LORA_WATCHDOG_TIMEOUT`)

### Zigbee

- **Device:** `/dev/zigbee`, adapter `zstack` (EFR32 sticks such as the
  ZBDongle-E: `ember`)
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

# meta-iot-gateway-os

Yocto/OpenEmbedded layer for the IoT Gateway OS. Targets the
[Toradex Verdin iMX8MM][som] and provides a hardened, multi-protocol IoT
gateway image.

[som]: https://www.toradex.com/computer-on-modules/verdin-arm-family/nxp-imx-8m-mini-nano

- **Yocto release:** Scarthgap (5.0)
- **Distro:** `gateway-os`
- **Image:** `gateway-os-image`

---

## Table of Contents

- [meta-iot-gateway-os](#meta-iot-gateway-os)
  - [Table of Contents](#table-of-contents)
  - [Dependencies](#dependencies)
  - [Adding the Layer](#adding-the-layer)
  - [Building](#building)
  - [Configuration Variables](#configuration-variables)
  - [Hardware](#hardware)
    - [SODIMM Pin Mapping](#sodimm-pin-mapping)
    - [GPIO](#gpio)
    - [LED](#led)
    - [Battery Management](#battery-management)
    - [On-module Sensors](#on-module-sensors)
  - [Distro Configuration](#distro-configuration)
    - [DISTRO\_FEATURES](#distro_features)
    - [systemd](#systemd)
  - [Kernel](#kernel)
    - [Custom DTS](#custom-dts)
    - [Kernel Config Fragment](#kernel-config-fragment)
  - [Device Tree](#device-tree)
    - [Nodes enabled / added](#nodes-enabled--added)
    - [Nodes explicitly disabled](#nodes-explicitly-disabled)
  - [Image Contents](#image-contents)
    - [IMAGE\_FEATURES](#image_features)
    - [IMAGE\_INSTALL](#image_install)
  - [Protocol Stack](#protocol-stack)
    - [LoRaWAN - RAK5146 (USB, SX1302)](#lorawan---rak5146-usb-sx1302)
    - [Zigbee - CC2652P (UART\_2)](#zigbee---cc2652p-uart_2)
    - [Wireless M-Bus (UART\_1)](#wireless-m-bus-uart_1)
    - [Thread - nRF5340 RCP (UART\_4)](#thread---nrf5340-rcp-uart_4)
    - [MQTT](#mqtt)
  - [Security](#security)
    - [SSH - Certificate-only authentication](#ssh---certificate-only-authentication)
    - [Secure Element - ATECC608B](#secure-element---atecc608b)
    - [Battery Fuel Gauge - MAX17048](#battery-fuel-gauge---max17048)
  - [Networking](#networking)
  - [System Logging](#system-logging)
    - [Architecture](#architecture)
    - [How the sync works](#how-the-sync-works)
    - [Configuration](#configuration)
    - [Reading archived logs](#reading-archived-logs)
  - [Backup](#backup)
    - [How it works](#how-it-works)
    - [What's backed up / not backed up](#whats-backed-up--not-backed-up)
    - [Backup Configuration](#backup-configuration)
    - [First-time setup](#first-time-setup)
    - [Restoring](#restoring)
  - [Maintainer](#maintainer)

---

## Dependencies

| Layer | Branch | Purpose |
| --- | --- | --- |
| `openembedded-core` (poky) | scarthgap | Base layer |
| `meta-arm` | scarthgap | ARM toolchain |
| `meta-toradex-nxp` | scarthgap | Toradex BSP (iMX8MM) |
| `meta-toradex-demos` | scarthgap | Toradex demo configs |
| `meta-security` | scarthgap | Security hardening |

---

## Adding the Layer

```bash
bitbake-layers add-layer layers/meta-iot-gateway-os
```

The layer priority is **6** (`BBFILE_PRIORITY_meta-iot-gateway-os = "6"`).

---

## Building

```bash
source layers/poky/oe-init-build-env build
bitbake gateway-os-image
```

The resulting artifacts are located in `build/tmp/deploy/images/verdin-imx8mm/`.

---

## Configuration Variables

The following variables can be overridden in `build/conf/local.conf` to adapt
the image to the target infrastructure without modifying any recipe.

```bitbake
# Example local.conf entries
CHIRPSTACK_MQTT_SERVER  = "tcp://192.168.1.10:1883"
ZIGBEE2MQTT_MQTT_SERVER = "mqtt://192.168.1.10"
WMBUSMETERS_MQTT_SERVER = "192.168.1.10"
OTBR_INFRA_IFACE        = "end0"
OTBR_RCP_DEVICE         = "/dev/thread"
```

> **Updating this table:** run `python3 scripts/update-vars-doc.py` from the
> layer root after adding a new `# @USER_CONFIG: <description>` marker above
> any `VARIABLE ?= "default"` line in a recipe.

<!-- USER_CONFIG_START -->

| Variable | Default | Description | Recipe |
| --- | --- | --- | --- |
| `CHIRPSTACK_MQTT_SERVER` | `tcp://localhost:1883` | MQTT broker URL for ChirpStack Gateway Bridge (e.g. tcp://192.168.1.10:1883) | `chirpstack-gateway-bridge_4.0.11.bb` |
| `OTBR_INFRA_IFACE` | `end0` | Network interface connected to the backbone (IP) network | `ot-br-posix_%.bbappend` |
| `OTBR_RCP_DEVICE` | `/dev/thread` | Thread RCP device path (nRF5340 flashed with OpenThread RCP firmware, connected via UART_4) | `ot-br-posix_%.bbappend` |
| `WMBUSMETERS_MQTT_SERVER` | `localhost` | MQTT broker hostname (or hostname:port) for wmbusmeters (e.g. 192.168.1.10 or 192.168.1.10:1883) | `wmbusmeters_1.18.0.bb` |
| `ZIGBEE2MQTT_MQTT_SERVER` | `mqtt://localhost` | MQTT broker URL for Zigbee2MQTT (e.g. mqtt://192.168.1.10) | `zigbee2mqtt_2.12.0.bb` |

<!-- USER_CONFIG_END -->

---

## Hardware

**SoM**
Toradex Verdin iMX8MM (NXP i.MX 8M Mini, Cortex-A53 quad-core, aarch64)

### SODIMM Pin Mapping

**Verdin UART_N labels (Toradex schematic notation) do not match NXP/Linux's
`&uartN` devicetree node numbers.** Toradex's own base devicetree
(`imx8mm-verdin.dtsi`) documents the real mapping directly above each
`&uartN` node:

| Verdin UART (schematic) | NXP/Linux devicetree node | `serial` alias | Linux device |
| --- | --- | --- | --- |
| UART_3 | `&uart1` | `serial0` | `/dev/ttymxc0` |
| UART_1 | `&uart2` | `serial1` | `/dev/ttymxc1` |
| UART_2 | `&uart3` | `serial2` | `/dev/ttymxc2` |
| UART_4 | `&uart4` | `serial3` | `/dev/ttymxc3` |

Only UART_4 happens to line up by coincidence. `/dev/ttymxc<N>` comes from
the SoC-level `aliases { serialN = &uartX; }` node
(`imx8mm.dtsi`) - the driver assigns the minor number from the alias
index, independent of which UARTs are enabled.

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

### GPIO

GPIO lines with a `gpio-line-names` label in the DTS can be addressed by
name via `libgpiod`:

```bash
gpioget --by-name gpiochip3 lora-power-enable
gpioset gpiochip3 25=1   # LoRa power on
```

### LED

The ready-LED is managed by the kernel `gpio-leds` driver and is accessible
via the LED subsystem — not via `libgpiod`:

```bash
cat /sys/class/leds/ready/brightness   # 0 = off, 1 = on
echo 1 > /sys/class/leds/ready/brightness
```

The LED is driven by `gateway-ready-led.service` once the boot completes.

### Battery Management

| Device | Interface | Purpose | sysfs / named line |
| --- | --- | --- | --- |
| MAX17048 fuel gauge | i2c1 `0x36` | State of charge | `/sys/class/power_supply/max17040-0/` |
| Charge enable | GPIO3_IO23 `battery-charge-enable` | Enable/disable charging | `gpioset gpiochip2 23=1` |
| Charge status | GPIO3_IO22 `battery-charge` | Charging active (input) | `gpioget gpiochip2 22` |
| Alert | GPIO3_IO24 `battery-alert` | Low-battery alert (input) | `gpioget gpiochip2 24` |

```bash
# State of charge (percent)
cat /sys/class/power_supply/max17040-0/capacity
# Cell voltage (µV)
cat /sys/class/power_supply/max17040-0/voltage_now
```

### On-module Sensors

On two different on-SoM I2C buses - `verdin_som_adc` on `&i2c1` (`/dev/i2c-0`,
the SoM-internal bus that also carries the PMIC), `hwmon` on `&i2c4`
(`/dev/i2c-3`, the same bus as the carrier's ATECC608B/MAX17048 - see
"SODIMM Pin Mapping" for why `&i2c4` is "Verdin I2C_1" and `&i2c1` isn't):

**INA219 — Module supply current (`hwmon`)**

```bash
# Current into module VCC (mA), bus voltage (mV), power (µW)
cat /sys/class/hwmon/hwmon*/curr1_input
cat /sys/class/hwmon/hwmon*/in1_input
cat /sys/class/hwmon/hwmon*/power1_input
```

**ADS1015 — On-module ADC (`verdin_som_adc`)**

4 differential / 8 single-ended channels exposed via the IIO subsystem:

```bash
ls /sys/bus/iio/devices/iio\:device*/in_voltage*_raw
cat /sys/bus/iio/devices/iio\:device0/in_voltage4_raw  # ADC_4 single-ended
```

---

## Distro Configuration

`conf/distro/gateway-os.conf`

```text
DISTRO_VERSION  = "1.0.0"
INIT_MANAGER    = "systemd"
```

### DISTRO_FEATURES

| Added | Removed |
| --- | --- |
| `pam` | `x11` |
| `virtualization` | `wayland` |
| `wifi` | `3g` |
| `bluetooth` | `alsa` |
| `overlayfs` | `opengl` |
| | `vulkan` |

Multimedia (audio, display, GPU) is fully removed. Only headless operation.

### systemd

- Network management: `systemd-networkd`
- DNS resolution: `systemd-resolved`
- `VIRTUAL-RUNTIME_net_manager = "systemd"`

---

## Kernel

**Recipe:** `recipes-kernel/linux/linux-toradex_%.bbappend`

### Custom DTS

The file `linux-toradex/iot-gateway-imx8mm.dts` is injected into the kernel
source tree at build time via `do_configure:append()`:

```text
arch/arm64/boot/dts/freescale/iot-gateway-imx8mm.dts
```

The corresponding DTB is registered via:

```bitbake
KERNEL_DEVICETREE:append = " freescale/iot-gateway-imx8mm.dtb"
```

### Kernel Config Fragment

`linux-toradex/atecc608b.cfg` enables:

| Option | Purpose |
| --- | --- |
| `CONFIG_I2C_CHARDEV=y` | `/dev/i2c-X` character devices for cryptoauthlib |
| `CONFIG_CRYPTO_DEV_ATMEL_ECC=y` | ATECC508A/608A kernel crypto driver |
| `CONFIG_CRYPTO_DEV_ATMEL_SHA204A=y` | SHA204A kernel crypto driver |
| `CONFIG_BATTERY_MAX17040=y` | MAX17048 fuel gauge driver |

---

## Device Tree

`recipes-kernel/linux/linux-toradex/iot-gateway-imx8mm.dts`

Includes the Toradex base overlays:

- `imx8mm-verdin.dtsi`
- `imx8mm-verdin-wifi.dtsi`
- `imx8mm-verdin-dev.dtsi`

### Nodes enabled / added

| Node | Bus / Interface | Address | Purpose |
| --- | --- | --- | --- |
| `atecc608b` | i2c1 | `0x60` | Secure element (cryptoauthlib userspace) |
| `max17048` (fuel-gauge) | i2c1 | `0x36` | Battery fuel gauge (`max17040_battery`) |
| `hwmon` (INA219) | i2c4 | `0x40` | Current measurement into module VCC |
| `verdin_som_adc` (ADS1015) | i2c1 | `0x49` | On-module ADC channels |
| `gpio-leds` (led-ready) | GPIO3_IO4 | - | Ready-LED on SODIMM 206 |
| `uart1` | SODIMM 133/135 | - | Console (stdout-path) |
| `uart2` | SODIMM 129/131 | - | wmBus transceiver |
| `uart3` | SODIMM 137/139 | - | Zigbee adapter |

### Nodes explicitly disabled

| Node | Reason |
| --- | --- |
| `sai2` | SAI2 pins repurposed as power-enable GPIOs |
| `ecspi2` | ECSPI2 pins repurposed as UART_4 (Thread RCP) |
| `flexspi` | No QSPI flash on carrier |
| `usdhc2` | No SD card slot on carrier |
| `uart4` | Prepared for Thread RCP (nRF5340) but reserved for M4 core by default |
| `i2c2` | No devices on Verdin I2C_2_DSI |
| `i2c3` | No devices on Verdin I2C_4_CSI |
| `i2c4` | Carrier board I2C unused |
| `pwm1` / `pwm2` / `pwm3` | No PWM consumers |
| `sound_card` | No audio hardware |
| `nau8822_1a` | No audio codec |
| `lcdif` | No display |
| `backlight` | No display |
| `gpio_expander_21` | No GPIO expander on carrier |
| `lvds_ti_sn65dsi84` | No DSI display adapter |
| `hdmi_lontium_lt8912` | No HDMI adapter |
| `atmel_mxt_ts` | No touchscreen |
| `eeprom_display_adapter` | No display adapter EEPROM |
| `eeprom_carrier_board` | Carrier EEPROM not used |
| `hwmon_temp` | No temperature sensor on carrier |

---

## Image Contents

`recipes-core/images/gateway-os-image.bb`

### IMAGE_FEATURES

| Feature | Package |
| --- | --- |
| `ssh-server-openssh` | OpenSSH server |

### IMAGE_INSTALL

| Package | Version | Purpose |
| --- | --- | --- |
| `htop` | - | System monitor |
| `curl` | - | HTTP client |
| `tmux` | - | Terminal multiplexer |
| `nano` | - | Text editor |
| `i2c-tools` | - | I2C bus utilities (`i2cdetect`, `i2cget`, `i2cset`) |
| `libgpiod` | - | GPIO library |
| `libgpiod-tools` | - | GPIO CLI tools (`gpioget`, `gpioset`, `gpioinfo`) |
| `avahi-daemon` | - | mDNS/DNS-SD service discovery |
| `mosquitto` | - | MQTT broker |
| `mosquitto-clients` | - | MQTT CLI tools |
| `ot-br-posix` | 0.3.0+git | OpenThread Border Router (Thread ↔ IP) |
| `wmbusmeters` | 1.18.0 | Wireless M-Bus meter reader |
| `zigbee2mqtt` | 2.12.0 | Zigbee ↔ MQTT bridge |
| `lora-packet-forwarder` | 2.1.0 | LoRaWAN packet forwarder (SX1302/SX1303) |
| `chirpstack-gateway-bridge` | 4.0.11 | LoRaWAN gateway bridge to ChirpStack |
| `cryptoauthlib` | 3.7.4 | Microchip crypto library for ATECC608B |
| `gateway-logging` | 1.0 | RAM journal + eMMC ring buffer logging |
| `gateway-ready-led` | - | Drives ready-LED via systemd service |
| `networkmanager` | - | Network manager |
| `networkmanager-nmcli` | - | NetworkManager CLI |
| `wifi-captive-portal` | - | Wi-Fi provisioning captive portal |
| `iw` | - | Wireless configuration tool |
| `linux-firmware` | - | Firmware for on-module Wi-Fi/BT |
| `bluez5` | - | Bluetooth stack |
| `mcumgr` | git (apache/mynewt-mcumgr-cli) | MCUmgr/SMP CLI - MCUboot serial recovery/DFU for the nRF5340 (see `firmware/nRF5340/`) |

---

## Protocol Stack

### LoRaWAN - RAK5146 (USB, SX1302)

- **Hardware:** RAK5146 USB concentrator module (SX1302/SX1303)
- **Connection:** USB CDC-ACM - `VID:0483 PID:5740`
- **udev symlink:** `/dev/lora`
- **Config:** `global_conf.json.RAK-USB-EU868` (EU868 band plan)
- **Install path:** `/opt/lora-packet-forwarder/`
- **Service:** `lora-packet-forwarder.service` (autostart)

```text
SUBSYSTEM=="tty", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="5740", SYMLINK+="lora"
```

### Zigbee - CC2652P (UART_2)

- **Hardware:** CC2652P coordinator via Verdin UART_2 (NXP/Linux `&uart3` -
  Verdin's UART_N labels don't match NXP's `&uartN` node numbers, see
  "SODIMM Pin Mapping")
- **Connection:** `/dev/ttymxc2` (iMX8MM `uart3`, SODIMM 137/139)
- **udev symlink:** `/dev/zigbee`
- **Adapter type:** `zstack`
- **MQTT base topic:** `zigbee2mqtt`
- **MQTT broker:** `mqtt://localhost`
- **Service:** `zigbee2mqtt.service` (autostart, depends on `mosquitto.service`)
- **Data directory:** `/var/lib/zigbee2mqtt/`

```text
SUBSYSTEM=="tty", KERNELS=="30880000.serial", SYMLINK+="zigbee"
```

### Wireless M-Bus (UART_1)

- **Hardware:** wmBus transceiver via Verdin UART_1 (NXP/Linux `&uart2`)
- **Connection:** `/dev/ttymxc1` (iMX8MM `uart2`, SODIMM 129/131)
- **Tool:** `wmbusmeters` 1.18.0
- **Mode:** `auto:t1`
- **Log:** `/var/log/wmbusmeters/wmbusmeters.log`
- **Config dir:** `/etc/wmbusmeters.d/`
- **Service:** `wmbusmeters.service` (autostart)

### Thread - nRF5340 RCP (UART_4)

- **Hardware:** Nordic nRF5340 flashed with OpenThread RCP firmware
- **Connection:** UART_4, SODIMM 196/198/200/202 (via ECSPI2 pad mux, with RTS/CTS)
- **udev symlink:** `/dev/thread` (maps `30a60000.serial` → `/dev/thread`)
- **Protocol:** Spinel/HDLC over UART
- **Border Router daemon:** `otbr-agent` (ot-br-posix)
- **Thread interface:** `wpan0`
- **Backbone interface:** `end0` (configurable via `OTBR_INFRA_IFACE`)
- **Service:** `otbr-agent.service`

> **Note:** `uart4` is currently `status = "disabled"` because UART_4 is
> shared with the Cortex-M4 core. Enable it by setting `status = "okay"`
> once the M4 firmware no longer claims the UART.

```text
OTBR_AGENT_OPTS="-I wpan0 -B end0 spinel+hdlc+uart:///dev/thread trel://end0"
```

TREL (Thread Radio Encapsulation Link) is enabled for Thread-over-IP fallback.

### MQTT

- **Broker:** Mosquitto (localhost)
- **Config:** `recipes-connectivity/mosquitto/files/mosquitto.conf`

---

## Security

### SSH - Certificate-only authentication

`recipes-connectivity/openssh/openssh_%.bbappend` installs a drop-in into
`/etc/ssh/sshd_config.d/00-gateway-hardening.conf`:

```text
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AuthenticationMethods publickey
```

Password login is completely disabled. Only SSH public key authentication is accepted.

### Secure Element - ATECC608B

- **Device:** Microchip ATECC608B (I2C, address `0x60`)
- **Bus:** iMX8MM `i2c1` → `/dev/i2c-0`
- **Library:** `cryptoauthlib` 3.7.4 (CMake, shared library)
- **Interface:** userspace via `/dev/i2c-X` (no kernel crypto offload)
- **Library path:** `${libdir}/libcryptoauth.so`

Build options:

```cmake
-DATCA_HAL_I2C=ON
-DATCA_BUILD_SHARED_LIBS=ON
-DBUILD_TESTS=OFF
```

### Battery Fuel Gauge - MAX17048

- **Device:** Maxim MAX17048 (I2C, address `0x36`)
- **Bus:** iMX8MM `i2c1` (shared with ATECC608B)
- **Driver:** `max17040_battery` (`CONFIG_BATTERY_MAX17040=y`)
- **sysfs:** `/sys/class/power_supply/max17040-0/{voltage_now,capacity}`

---

## Networking

`recipes-core/systemd/systemd-conf_%.bbappend` installs a `systemd-networkd`
configuration for all wired Ethernet interfaces:

- **Primary:** DHCP (preferred when available, metric 10)
- **Fallback:** Static `192.168.0.2/24` (always active in parallel)

The static fallback allows direct PC-to-device Ethernet connection without a
DHCP server. Configure the PC with any address in `192.168.0.0/24`.

---

## System Logging

`recipes-core/gateway-logging/gateway-logging_1.0.bb`

### Architecture

```text
RAM (tmpfs) /run/log/journal/          <- journald writes here (volatile)
    RuntimeMaxUse     = 64 MB          <- RAM ring buffer limit
    RuntimeMaxFileSize =  8 MB
    RuntimeKeepFree   = 32 MB

         |  journal-sync.timer (every 15 min, first after 5 min)
         v

eMMC /var/log/journal-archive/         <- compressed archive files
    journal-YYYYMMDD-HHMMSS.log.gz
    Ring buffer limit: 256 MB          <- oldest files deleted when exceeded
```

### How the sync works

1. `journal-sync.timer` fires every 15 minutes (first time 5 minutes after boot)
2. `journal-sync.service` runs `/usr/libexec/gateway-os/journal-sync`
3. The script reads the cursor saved from the last run (`/var/log/journal-archive/.cursor`)
4. Only new journal entries since the last cursor are exported (`--after-cursor`)
5. Output is compressed with `gzip` and written as a timestamped file on eMMC
6. The new cursor position is saved for the next run
7. If the archive directory exceeds 256 MB, the oldest files are deleted

### Configuration

| Parameter | Value | Location |
| --- | --- | --- |
| RAM buffer size | 64 MB | `00-gateway-journald.conf` → `RuntimeMaxUse` |
| eMMC ring buffer | 256 MB | `journal-sync` → `MAX_MB` |
| Sync interval | 15 min | `journal-sync.timer` → `OnUnitActiveSec` |
| First sync after boot | 5 min | `journal-sync.timer` → `OnBootSec` |

### Reading archived logs

```bash
# List archive files
ls -lh /var/log/journal-archive/

# Read a specific archive
zcat /var/log/journal-archive/journal-20260621-120500.log.gz | less

# Live journal (RAM)
journalctl -f
```

## Backup

`recipes-core/gateway-backup/gateway-backup_1.0.bb`

### How it works

1. On first boot, `gateway-backup-keygen.service` generates a per-device
   ed25519 key at `/etc/gateway-backup/id_ed25519` (skipped on later boots
   once the key exists - `ConditionPathExists=!...`)
2. `gateway-backup.timer` fires daily (`RandomizedDelaySec=30m`), catching up
   a missed run after boot via `Persistent=true`
3. `gateway-backup.service` runs `/usr/libexec/gateway-os/gateway-backup.sh`
4. The script stages a snapshot of `/var/lib/zigbee2mqtt/` and the
   OpenThread dataset under `/var/lib/gateway-backup/<timestamp>/`
5. The snapshot is tarred (`<timestamp>.tar.gz`) and pushed via
   `rsync -e ssh` to `GATEWAY_BACKUP_REMOTE`
6. Local staging files are deleted after a successful transfer

### What's backed up / not backed up

| Included | Not included |
| --- | --- |
| `/var/lib/zigbee2mqtt/` (coordinator DB + network backup - re-pairing every Zigbee device otherwise) | ATECC608B secure element keys (hardware, non-extractable by design) |
| OpenThread dataset (`/tmp/0_*.data`, `.swap` - network key/PAN ID/channel for the Thread mesh, e.g. the Onvis plug) | mosquitto (`persistence false`, nothing to save) |
| | anything not explicitly listed above |

> The OpenThread dataset path assumes `otbr-agent`'s working directory
> resolves `tmp` (`OPENTHREAD_CONFIG_POSIX_SETTINGS_PATH`) to `/tmp`. Not yet
> verified whether `/tmp` is persistent or tmpfs on this image - that only
> matters for surviving a reboot (see [Thread - nRF5340 RCP](#thread---nrf5340-rcp-uart_4)
> / `thread-network-init`, which re-attaches on every boot regardless), not
> for this backup, which just needs the file to exist while the timer runs.
> Verify with `ls /tmp/0_*` after the Thread network has formed.

### Backup Configuration

| Parameter | Location |
| --- | --- |
| Remote destination | `/etc/default/gateway-backup` → `GATEWAY_BACKUP_REMOTE` (e.g. `backup@host:/srv/gateway-backups/`) |
| SSH key | `/etc/gateway-backup/id_ed25519` (generated on first boot, add the `.pub` to the remote host's `authorized_keys`) |
| Known hosts | `/etc/gateway-backup/known_hosts` (`StrictHostKeyChecking=accept-new` - trusted on first connect, verified after) |
| Schedule | Daily, `RandomizedDelaySec=30m` (`gateway-backup.timer`) |

### First-time setup

```bash
# 1. Let the gateway boot once so gateway-backup-keygen runs, then fetch the public key:
journalctl -u gateway-backup-keygen

# 2. Add it to the backup host:
ssh backup@host "mkdir -p ~/.ssh && echo '<pasted pubkey>' >> ~/.ssh/authorized_keys"

# 3. Set the destination:
echo 'GATEWAY_BACKUP_REMOTE="backup@host:/srv/gateway-backups/"' > /etc/default/gateway-backup

# 4. Trigger a manual run to verify:
systemctl start gateway-backup.service
journalctl -u gateway-backup.service
```

### Restoring

```bash
# On the backup host, list snapshots for this device:
ls /srv/gateway-backups/

# On the gateway (stop the affected services first):
systemctl stop zigbee2mqtt otbr-agent
tar -xzf <timestamp>.tar.gz -C /tmp/restore
cp -a /tmp/restore/<timestamp>/zigbee2mqtt/. /var/lib/zigbee2mqtt/
cp -a /tmp/restore/<timestamp>/0_*.data /tmp/   # OpenThread settings path
systemctl start otbr-agent zigbee2mqtt
```

## Maintainer

- [Daniel Kampert](mailto:DanielKampert@kampis-elektroecke.de)

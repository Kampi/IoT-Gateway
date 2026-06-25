# meta-iot-gateway-os

Yocto/OpenEmbedded layer for the IoT Gateway OS. Targets the
[Toradex Verdin iMX8MM](https://www.toradex.com/computer-on-modules/verdin-arm-family/nxp-imx-8m-mini-nano) and provides a hardened, multi-protocol IoT gateway image.

- **Yocto release:** Scarthgap (5.0)
- **Distro:** `gateway-os`
- **Image:** `gateway-os-image`
- **Maintainer:** Daniel Kampert <kontakt@daniel-kampert.de>

---

## Table of Contents

1. [Dependencies](#dependencies)
2. [Adding the Layer](#adding-the-layer)
3. [Building](#building)
4. [Configuration Variables](#configuration-variables)
5. [Hardware](#hardware)
6. [Distro Configuration](#distro-configuration)
7. [Kernel](#kernel)
8. [Device Tree](#device-tree)
9. [Image Contents](#image-contents)
10. [Protocol Stack](#protocol-stack)
11. [Security](#security)
12. [Networking](#networking)
13. [System Logging](#system-logging)

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
```

> **Updating this table:** run `python3 scripts/update-vars-doc.py` from the
> layer root after adding a new `# @USER_CONFIG: <description>` marker above
> any `VARIABLE ?= "default"` line in a recipe.

<!-- USER_CONFIG_START -->

| Variable | Default | Description | Recipe |
| --- | --- | --- | --- |
| `CHIRPSTACK_MQTT_SERVER` | `tcp://localhost:1883` | MQTT broker URL for ChirpStack Gateway Bridge (e.g. tcp://192.168.1.10:1883) | `chirpstack-gateway-bridge_4.0.11.bb` |
| `WMBUSMETERS_MQTT_SERVER` | `localhost` | MQTT broker hostname (or hostname:port) for wmbusmeters (e.g. 192.168.1.10 or 192.168.1.10:1883) | `wmbusmeters_1.18.0.bb` |
| `ZIGBEE2MQTT_MQTT_SERVER` | `mqtt://localhost` | MQTT broker URL for Zigbee2MQTT (e.g. mqtt://192.168.1.10) | `zigbee2mqtt_2.12.0.bb` |

<!-- USER_CONFIG_END -->

---

## Hardware

**SoM**  
Toradex Verdin iMX8MM (NXP i.MX 8M Mini, Cortex-A53 quad-core, aarch64)

### SODIMM Pin Mapping (relevant signals)

| Signal | SODIMM | iMX8MM pad | Linux device | Purpose |
| --- | --- | --- | --- | --- |
| I2C_1_SCL | 14 | I2C4_SCL | `/dev/i2c-3` | ATECC608B + MAX17048 |
| I2C_1_SDA | 12 | I2C4_SDA | `/dev/i2c-3` | ATECC608B + MAX17048 |
| UART_2_TX | 137 | UART3_TX | `/dev/ttymxc2` | CC2530 Zigbee adapter |
| UART_2_RX | 139 | UART3_RX | `/dev/ttymxc2` | CC2530 Zigbee adapter |
| CTRL_SLEEP_MOCI# | 256 | GPIO5_IO01 | — | Carrier board power rail (SoM output) |
| PCIE_1_RESET# | 244 | GPIO3_IO19 | — | RAK5146 reset (via `pcie0 reset-gpio`) |
| PCIE_1_CLK_P | 228 | PCIe SerDes | — | RAK5146 MCU reset (analog pad, not GPIO) |
| SODIMM 216 | 216 | GPIO1_IO00 | `gpiochip0` line 0 | Version bit 0 (carrier pull-up, SoM pull-down) |
| SODIMM 218 | 218 | GPIO1_IO11 | `gpiochip0` line 11 | Version bit 1 (carrier pull-up, SoM pull-down) |
| SODIMM 220 | 220 | GPIO1_IO08 | `gpiochip0` line 8 | Version bit 2 (carrier pull-up, SoM pull-down) |

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
- `imx8mm-verdin-nonwifi.dtsi`
- `imx8mm-verdin-dev.dtsi`

### Nodes enabled / added

| Node | Bus | Address | Driver | Purpose |
| --- | --- | --- | --- | --- |
| `atecc608b` | i2c4 | `0x60` | cryptoauthlib (userspace) | Matter Secure Element |
| `max17048` (fuel-gauge) | i2c4 | `0x36` | `max17040_battery` | Battery fuel gauge |

### Nodes explicitly disabled

| Node | Reason |
| --- | --- |
| `pcie0` | RAK5146 used via USB; PCIe bus not needed |
| `pcie_phy` | PCIe PHY clock not needed |
| `sound_card` | No audio hardware |
| `nau8822_1a` | No audio codec |
| `sai2` | No SAI audio |
| `lcdif` | No display |
| `backlight` | No display |
| `pwm1` | Display backlight PWM, not usable as GPIO |

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
| `htop` | — | System monitor |
| `curl` | — | HTTP client |
| `tmux` | — | Terminal multiplexer |
| `avahi-daemon` | — | mDNS/DNS-SD service discovery |
| `mosquitto` | — | MQTT broker |
| `mosquitto-clients` | — | MQTT CLI tools |
| `wmbusmeters` | 1.18.0 | Wireless M-Bus meter reader |
| `zigbee2mqtt` | 2.12.0 | Zigbee ↔ MQTT bridge |
| `lora-packet-forwarder` | 2.1.0 | LoRaWAN packet forwarder (SX1302/SX1303) |
| `cryptoauthlib` | 3.7.4 | Microchip crypto library for ATECC608B |
| `gateway-logging` | 1.0 | RAM journal + eMMC ring buffer logging |

---

## Protocol Stack

### LoRaWAN — RAK5146 (USB, SX1302)

- **Hardware:** RAK5146 USB concentrator module (SX1302/SX1303)
- **Connection:** USB CDC-ACM — `VID:0483 PID:5740`
- **udev symlink:** `/dev/lora`
- **Config:** `global_conf.json.RAK-USB-EU868` (EU868 band plan)
- **Install path:** `/opt/lora-packet-forwarder/`
- **Service:** `lora-packet-forwarder.service` (autostart)

```text
SUBSYSTEM=="tty", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="5740", SYMLINK+="lora"
```

### Zigbee — CC2530 (UART)

- **Hardware:** CC2530 coordinator via Verdin UART_2
- **Connection:** `/dev/ttymxc2` (iMX8MM `uart3`, platform address `0x30880000`)
- **udev symlink:** `/dev/zigbee`
- **Adapter type:** `zstack`
- **MQTT base topic:** `zigbee2mqtt`
- **MQTT broker:** `mqtt://localhost`
- **Service:** `zigbee2mqtt.service` (autostart, depends on `mosquitto.service`)
- **Data directory:** `/var/lib/zigbee2mqtt/`

```text
SUBSYSTEM=="tty", KERNELS=="30880000.serial", SYMLINK+="zigbee"
```

### Wireless M-Bus

- **Tool:** `wmbusmeters` 1.18.0
- **SDR backend:** `rtlsdr` 2.0.2
- **Mode:** `auto:t1`
- **Log:** `/var/log/wmbusmeters/wmbusmeters.log`
- **Config dir:** `/etc/wmbusmeters.d/`
- **Driver dir:** `/etc/wmbusmeters.drivers.d/`
- **Service:** `wmbusmeters.service` (autostart)

### MQTT

- **Broker:** Mosquitto (localhost)
- **Config:** `recipes-connectivity/mosquitto/files/mosquitto.conf`

---

## Security

### SSH — Certificate-only authentication

`recipes-connectivity/openssh/openssh_%.bbappend` installs a drop-in into
`/etc/ssh/sshd_config.d/00-gateway-hardening.conf`:

```text
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AuthenticationMethods publickey
```

Password login is completely disabled. Only SSH public key authentication is accepted.

### Matter — ATECC608B Secure Element

- **Device:** Microchip ATECC608B (I2C, address `0x60`)
- **Bus:** Verdin I2C_1 → iMX8MM `i2c4` → `/dev/i2c-3`
- **Library:** `cryptoauthlib` 3.7.4 (CMake, shared library)
- **Interface:** userspace via `/dev/i2c-X` (no kernel crypto offload)
- **Library path:** `${libdir}/libcryptoauth.so`

Build options:

```cmake
-DATCA_HAL_I2C=ON
-DATCA_BUILD_SHARED_LIBS=ON
-DBUILD_TESTS=OFF
```

### Battery Fuel Gauge — MAX17048

- **Device:** Maxim MAX17048 (I2C, address `0x36`)
- **Bus:** Verdin I2C_1 → iMX8MM `i2c4` (shared with ATECC608B)
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

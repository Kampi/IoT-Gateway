# IoT-Gateway

[![License](https://img.shields.io/badge/License-MIT%203.0-blue.svg)](https://opensource.org/license/mit/)
[![PCB](https://github.com/Kampi/IoT-Gateway/actions/workflows/hw-pcb.yaml/badge.svg)](https://github.com/Kampi/IoT-Gateway/actions/workflows/hw-pcb.yaml)
[![Documentation](https://img.shields.io/badge/Documentation-HTML-007ec6?longCache=true&style=flat&logo=asciidoctor&colorA=555555)](https://Kampi.github.io/IoT-Gateway/)

## Table of Contents

- [IoT-Gateway](#iot-gateway)
  - [Table of Contents](#table-of-contents)
  - [About](#about)
  - [Directory Breakdown](#directory-breakdown)
  - [Building the OS](#building-the-os)
  - [Wi-Fi Configuration (nmcli)](#wi-fi-configuration-nmcli)
  - [Easy Installer Server (Raspberry Pi)](#easy-installer-server-raspberry-pi)
  - [Firmware - nRF5340 (OpenThread RCP)](#firmware---nrf5340-openthread-rcp)
  - [Firmware - nRF54L15 (Matter test light)](#firmware---nrf54l15-matter-test-light)
  - [Firmware - ESP32 LoRa (LoRaWAN test sensor)](#firmware---esp32-lora-lorawan-test-sensor)
  - [Firmware - ESP32 BLE (BLE test sensor)](#firmware---esp32-ble-ble-test-sensor)
  - [Ressources](#ressources)
  - [Maintainer](#maintainer)

## About

...

It is built with **KiCad** and integrates a full **CI/CD pipeline** using **KiBot**, ensuring reproducible outputs for documentation, manufacturing, and releases.

Key features:

- Open-source hardware design
- Automated generation of Gerber, BoM, and documentation via KiBot
- Workflow stages (DRAFT -> PRELIMINARY -> CHECKED -> RELEASED) for structured releases
- Clear documentation and changelog to track progress
- Designed with collaboration and transparency in mind

Please check the [wiki](https://github.com/Kampi/IoT-Gateway/wiki) for more information about the project.

## Directory Breakdown

- **`.github`**: GitHub related files
- **`.gitlab`**: GitLab related files
- **`.gitignore`**: Ignored files list
- **`3d-print`**: Project related files for 3D printer
- **`cad`**: Project related CAD files
- **`firmware`**: Firmware directory
- **`gateway`**: KiCad project for the PCB
- **`scripts`**: Additional scripts for CI/CD etc.
- **`CHANGELOG.md`**: Version history
- **`LICENSE`**: Project license
- **`README.md`**: Project overview

## Building the OS

The gateway OS is built with Yocto/OpenEmbedded from the `os` directory using
the provided `Makefile`.

```bash
cd os

# One-time: install the Yocto/OpenEmbedded build dependencies on this host
make setup

# Clone the Toradex BSP layers (Scarthgap / 5.0) via `repo`
make repo

# Initialize the Yocto build environment
source layers/openembedded-core/oe-init-build-env build

# Build the gateway image
bitbake gateway-os-image
```

The resulting image is located in `build/tmp/deploy/images/verdin-imx8mm/`.
Use `make clean` to remove the `.repo`/`export` checkout and the `tmp`/`deploy`
build output.

See [os/layers/meta-iot-gateway-os/README.md](os/layers/meta-iot-gateway-os/README.md)
for layer details and configuration variables.

## Wi-Fi Configuration (nmcli)

The gateway image ships with NetworkManager and its `nmcli` CLI for Wi-Fi
provisioning.

```bash
# List available Wi-Fi networks
nmcli device wifi list

# Connect to a Wi-Fi network
nmcli device wifi connect "<SSID>" password "<PASSWORD>"

# Show configured/active connections
nmcli connection show

# Check Wi-Fi radio status
nmcli radio wifi
```

## Easy Installer Server (Raspberry Pi)

`scripts/raspberry-pi/setup-tezi-server.sh` turns a Raspberry Pi (Raspberry Pi
OS / Debian 12 with NetworkManager) into the network feed for the Toradex Easy
Installer and the server for RAUC OTA bundles: a static network on `eth0`
(no default route), dnsmasq (DHCP + DNS, answers `tezi.toradex.com` with the
Pi), nginx (`/var/www/html`, bundles in `bundles/`) and an avahi announcement.
`--with-uuu` also builds `uuu` for USB recovery. It can be run again at any
time.

```bash
# on the Pi (uplink via Wi-Fi, gateway on eth0)
sudo ./setup-tezi-server.sh --with-uuu
```

Settings such as `ETH_IFACE` or `TEZI_ADDRESS` via environment variables
(`--help`). See
[os/layers/meta-iot-gateway-os/docs/tezi-feed-raspberry-pi.md](os/layers/meta-iot-gateway-os/docs/tezi-feed-raspberry-pi.md)
for the manual steps, copying images and starting the Easy Installer.

## Firmware - nRF5340 (OpenThread RCP)

`firmware/nRF5340` runs Nordic's OpenThread coprocessor (RCP) sample on the
nRF5340's network core, talking Spinel over HDLC-over-UART to `otbr-agent`
on the Verdin (UART_4). The workspace is a self-contained `west` setup -
the nRF Connect SDK is fetched on demand and never vendored into git.

```bash
cd firmware/nRF5340

# One-time: fetch the nRF Connect SDK / Zephyr workspace
make setup

# Build for the stock nRF5340-DK and flash it
make build
make flash

# Once the carrier board's schematic is final and the pin overlay updated
make build TARGET=gateway
```

See [firmware/nRF5340/README.md](firmware/nRF5340/README.md) for the full
workspace layout, prerequisites, and board overlay details.

## Firmware - nRF54L15 (Matter test light)

`firmware/device/nRF54L15_Matter` turns an nRF54L15-DK into a Matter-over-Thread
light bulb that joins the gateway's Thread network and is added to Home
Assistant via Matter. It builds against an installed nRF Connect SDK
(`~/ncs/v3.3.4` by default).

```bash
cd firmware/device/nRF54L15_Matter
make build
make erase-flash
```

See [firmware/device/nRF54L15_Matter/README.md](firmware/device/nRF54L15_Matter/README.md)
for commissioning with Home Assistant.

## Firmware - ESP32 LoRa (LoRaWAN test sensor)

`firmware/device/ESP32_LoRa` turns an ESP-WROVER-KIT with a RAK3172 module
into a LoRaWAN sensor that joins ChirpStack via OTAA (EU868, class A) through
the gateway's RAK5146 concentrator and sends a random temperature every 20 s
(deep sleep in between). PlatformIO with ESP-IDF, RAK3172 driver as git
submodule.

```bash
cd firmware/device/ESP32_LoRa
git submodule update --init components/RAK3172
pio run -e debug -t upload --upload-port /dev/ttyUSB1
```

See [firmware/device/ESP32_LoRa/README.md](firmware/device/ESP32_LoRa/README.md)
for the wiring, the OTAA keys and the ChirpStack device profile.

## Firmware - ESP32 BLE (BLE test sensor)

`firmware/device/ESP32_BLE` turns an ESP-WROVER-KIT into a BLE temperature
and humidity sensor that broadcasts BTHome v2 advertisements every 10 s.
Theengs Gateway on the gateway decodes them and publishes the sensor to Home
Assistant via MQTT discovery. PlatformIO with ESP-IDF.

```bash
cd firmware/device/ESP32_BLE
pio run -e debug -t upload --upload-port /dev/ttyUSB1
```

See [firmware/device/ESP32_BLE/README.md](firmware/device/ESP32_BLE/README.md)
for the advertisement format.

## Ressources

- [KiBot Template](https://github.com/nguyen-v/KDT_Hierarchical_KiBot)
- [KiCad Project Template](https://github.com/Kampi/Template-Project)

## Maintainer

- [Daniel Kampert](mailto:DanielKampert@kampis-elektroecke.de)

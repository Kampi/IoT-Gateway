# IoT-Gateway

[![License](https://img.shields.io/badge/License-MIT%203.0-blue.svg)](https://opensource.org/license/mit/)
[![PCB](https://github.com/Kampi/IoT-Gateway/actions/workflows/pcb.yaml/badge.svg)](https://github.com/Kampi/IoT-Gateway/actions/workflows/pcb.yaml)
[![Documentation](https://img.shields.io/badge/Documentation-HTML-007ec6?longCache=true&style=flat&logo=asciidoctor&colorA=555555)](https://Kampi.github.io/IoT-Gateway/)

## Table of Contents

- [IoT-Gateway](#iot-gateway)
  - [Table of Contents](#table-of-contents)
  - [About](#about)
  - [Directory Breakdown](#directory-breakdown)
  - [Building the OS](#building-the-os)
  - [Wi-Fi Configuration (nmcli)](#wi-fi-configuration-nmcli)
  - [Firmware - nRF5340 (OpenThread RCP)](#firmware---nrf5340-openthread-rcp)
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

## Ressources

- [KiBot Template](https://github.com/nguyen-v/KDT_Hierarchical_KiBot)
- [KiCad Project Template](https://github.com/Kampi/Template-Project)

## Maintainer

- [Daniel Kampert](mailto:DanielKampert@kampis-elektroecke.de)

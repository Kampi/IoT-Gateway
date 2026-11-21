<!-- SPDX-License-Identifier: MIT -->
<!-- Copyright (c) 2026 Daniel Kampert -->

# ESP32_LoRa - LoRaWAN temperature sensor

Test device for the gateway's LoRaWAN path (RAK5146 concentrator,
`lora-packet-forwarder`, `chirpstack-gateway-bridge`, see
`os/layers/meta-iot-gateway-os/recipes-connectivity`). An ESP32 with a RAK3172
LoRa module joins a LoRaWAN network server (ChirpStack) via OTAA and sends a
random temperature value every 20 s.

```text
ESP32 + RAK3172 --LoRaWAN EU868--> RAK5146 (IoT-Gateway) --UDP--> chirpstack-gateway-bridge --MQTT--> ChirpStack --> Home Assistant
```

## Operation

- **First power-on:** initializes the RAK3172, sets up LoRaWAN (OTAA, class A,
  EU868) and joins the network (`LORAWAN_JOIN_ATTEMPTS`,
  `LORAWAN_MAX_JOIN_INTERVAL_S`).
- **Every wake-up from deep sleep (20 s):** wakes the module, checks the join
  status (rejoins only if it really lost the session, a rejoin costs a new
  DevNonce and ~30 s SF12 airtime), sends one uplink and prints a downlink if
  there is one. The driver state survives deep sleep in RTC memory.

| Uplink | Value |
| --- | --- |
| Port | 1 |
| Payload | JSON text, e.g. `{"temperature": 27.430000}` (random 20-40 °C) |
| Confirmed | no |

Decode it in ChirpStack with a codec that parses the JSON text.

## Hardware

ESP-WROVER-KIT (4 MB flash) with a RAK3172 module:

| RAK3172 | ESP32 | Setting |
| --- | --- | --- |
| UART | UART1 | `CONFIG_RAK3172_UART_PORT` (default 1) |
| TX → ESP32 RX | GPIO 13 | `CONFIG_RAK3172_UART_RX` |
| RX ← ESP32 TX | GPIO 14 | `CONFIG_RAK3172_UART_TX` |
| Baud rate | 115200 | `CONFIG_RAK3172_BAUD_*` (`sdkconfig.debug`) |

Driver: [ESP32-RAK3172](https://github.com/Kampi/ESP32-RAK3172) release
4.2.2 as git submodule in `components/RAK3172` (branch `master`), requires
esp-idf v6 (PlatformIO `espressif32` 8.0.0, esp-idf 6.1).

## Configuration

OTAA keys in `include/settings/LoRaWAN_Default.h` (big-endian), matching the
device in ChirpStack:

| Define | Purpose |
| --- | --- |
| `DEVEUI` | Device EUI |
| `APPEUI` | Join EUI (App EUI) |
| `APPKEY` | Application key |
| `LORAWAN_JOIN_ATTEMPTS` | join attempts per join (2) |
| `LORAWAN_MAX_JOIN_INTERVAL_S` | maximum time between join attempts in s (15) |

ChirpStack device profile: LoRaWAN 1.0.x, region EU868, class A, OTAA.

## Build and flash

PlatformIO with ESP-IDF (`platformio.ini`, environments `debug` and
`release`):

```bash
cd firmware/device/ESP32_LoRa
git submodule update --init components/RAK3172

pio run -e debug -t upload --upload-port /dev/ttyUSB1
pio device monitor --port /dev/ttyUSB1
```

`upload_port` / `monitor_port` in `platformio.ini` are set for Windows
(`COM22`); pass the port on the command line on Linux.

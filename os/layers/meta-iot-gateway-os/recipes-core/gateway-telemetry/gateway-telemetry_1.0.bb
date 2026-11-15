# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

SUMMARY = "Publish gateway telemetry to Home Assistant via MQTT discovery"
DESCRIPTION = "Publishes system telemetry (CPU load/temperature, memory, \
disk and battery state) to the local MQTT broker using Home Assistant's \
MQTT discovery convention, so the gateway is auto-created as a standalone \
device in Home Assistant on connect. Connection settings are in \
/etc/default/gateway-telemetry (MQTT_HOST, HA_DISCOVERY_PREFIX, ...)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = " \
    file://gateway-telemetry.py \
    file://gateway-telemetry.conf \
    file://services/gateway-telemetry.service \
"

inherit systemd

# @USER_CONFIG: MQTT broker host for the gateway telemetry (Home Assistant device "IoT-Gateway")
TELEMETRY_MQTT_HOST ?= "localhost"

# @USER_CONFIG: Name of the gateway device in Home Assistant (CPU, memory, disk, battery, uptime)
TELEMETRY_DEVICE_NAME ?= "IoT-Gateway"

# @USER_CONFIG: Name of the LoRa gateway device in Home Assistant, parent of all LoRa sensors
TELEMETRY_LORA_DEVICE_NAME ?= "IoT-Gateway (LoRa)"

# @USER_CONFIG: Name of the Bluetooth gateway device in Home Assistant, parent of all BLE sensors
TELEMETRY_BLE_DEVICE_NAME ?= "IoT-Gateway (Bluetooth)"

# @USER_CONFIG: Name of the wM-Bus gateway device in Home Assistant (wmbusmeters status and version)
TELEMETRY_WMBUS_DEVICE_NAME ?= "IoT-Gateway (wM-Bus)"

# Board revision straps SODIMM 212/216/218 (version-bit0..2, bit 0 = LSB),
# shown as "Hardware" in Home Assistant. Dahlia: GPIO_4/GPIO_5_R/GPIO_6_R on
# extension header X20 pins 30/32/33, open = 0 (pad pull-down), bridge to
# +1V8 (X20 pin 26) to set a revision. Empty = no hardware version.
TELEMETRY_HARDWARE_VERSION_LINES ?= "version-bit0 version-bit1 version-bit2"

# Supply source sensor "Power Source" (DC / PoE / Battery): status lines of
# the TPS2121 power muxes, SODIMM 210 powermux-status (1 = battery) and
# SODIMM 38 input-status (1 = PoE). Dahlia: not wired, no sensor.
TELEMETRY_POWER_SOURCE_LINES ?= "powermux-status input-status"
TELEMETRY_POWER_SOURCE_LINES:board-dahlia = ""

# Input voltage sensor "Input Voltage": module ADC (ads1015) channel and
# divider ratio. Mainboard: ADC_1 = in_voltage3, R611 100k / R612 12k.
# Dahlia: ADC_1 not wired, no sensor.
# @USER_CONFIG: ads1015 channel of the input voltage divider (empty = no Input Voltage sensor)
TELEMETRY_INPUT_VOLTAGE_CHANNEL ?= "3"
TELEMETRY_INPUT_VOLTAGE_CHANNEL:board-dahlia = ""
# @USER_CONFIG: Ratio of the input voltage divider, (R_top + R_bottom) / R_bottom
TELEMETRY_INPUT_VOLTAGE_DIVIDER ?= "9.3333"

# OTA updates from Home Assistant (update entity "GatewayOS", docs/ota-rauc.md)
# @USER_CONFIG: Update server (base URL, e.g. http://192.168.0.1), bundle expected at <server>/bundles/gateway-os-bundle-<MACHINE>.raucb; GATEWAY_UPDATE_URL overrides the full URL (both empty = no update entity)
GATEWAY_UPDATE_SERVER ?= ""
# URL of the RAUC bundle (HTTP, streamed), derived from GATEWAY_UPDATE_SERVER unless set
GATEWAY_UPDATE_URL ?= "${@'%s/bundles/gateway-os-bundle-%s.raucb' % (d.getVar('GATEWAY_UPDATE_SERVER').rstrip('/'), d.getVar('MACHINE')) if d.getVar('GATEWAY_UPDATE_SERVER') else ''}"
# @USER_CONFIG: Seconds between checks for a new bundle on the update server
GATEWAY_UPDATE_CHECK_INTERVAL ?= "3600"
# @USER_CONFIG: Reboot into the new system after an install from Home Assistant (1/0)
GATEWAY_UPDATE_REBOOT ?= "1"

# GATEWAY_BOARD changes the configuration
PACKAGE_ARCH = "${MACHINE_ARCH}"

RDEPENDS:${PN} = "python3-core python3-json python3-logging python3-paho-mqtt libgpiod-tools"

SYSTEMD_SERVICE:${PN} = "gateway-telemetry.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${libexecdir}/gateway-os
    install -m 0755 ${WORKDIR}/gateway-telemetry.py ${D}${libexecdir}/gateway-os/gateway-telemetry.py

    install -d ${D}${sysconfdir}/default
    sed -e 's|@TELEMETRY_MQTT_HOST@|${TELEMETRY_MQTT_HOST}|g' \
        -e 's|@TELEMETRY_DEVICE_NAME@|${TELEMETRY_DEVICE_NAME}|g' \
        -e 's|@TELEMETRY_LORA_DEVICE_NAME@|${TELEMETRY_LORA_DEVICE_NAME}|g' \
        -e 's|@TELEMETRY_BLE_DEVICE_NAME@|${TELEMETRY_BLE_DEVICE_NAME}|g' \
        -e 's|@TELEMETRY_WMBUS_DEVICE_NAME@|${TELEMETRY_WMBUS_DEVICE_NAME}|g' \
        -e 's|@TELEMETRY_HARDWARE_VERSION_LINES@|${TELEMETRY_HARDWARE_VERSION_LINES}|g' \
        -e 's|@TELEMETRY_POWER_SOURCE_LINES@|${TELEMETRY_POWER_SOURCE_LINES}|g' \
        -e 's|@TELEMETRY_INPUT_VOLTAGE_CHANNEL@|${TELEMETRY_INPUT_VOLTAGE_CHANNEL}|g' \
        -e 's|@TELEMETRY_INPUT_VOLTAGE_DIVIDER@|${TELEMETRY_INPUT_VOLTAGE_DIVIDER}|g' \
        -e 's|@GATEWAY_UPDATE_URL@|${GATEWAY_UPDATE_URL}|g' \
        -e 's|@GATEWAY_UPDATE_CHECK_INTERVAL@|${GATEWAY_UPDATE_CHECK_INTERVAL}|g' \
        -e 's|@GATEWAY_UPDATE_REBOOT@|${GATEWAY_UPDATE_REBOOT}|g' \
        ${WORKDIR}/gateway-telemetry.conf > ${D}${sysconfdir}/default/gateway-telemetry
    chmod 0644 ${D}${sysconfdir}/default/gateway-telemetry

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/gateway-telemetry.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${libexecdir}/gateway-os/gateway-telemetry.py \
    ${sysconfdir}/default/gateway-telemetry \
    ${systemd_system_unitdir}/gateway-telemetry.service \
"

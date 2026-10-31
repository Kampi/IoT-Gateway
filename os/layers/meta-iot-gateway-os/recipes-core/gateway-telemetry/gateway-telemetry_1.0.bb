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

RDEPENDS:${PN} = "python3-core python3-json python3-logging python3-paho-mqtt"

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

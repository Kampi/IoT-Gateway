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

RDEPENDS:${PN} = "python3-core python3-json python3-logging python3-paho-mqtt"

SYSTEMD_SERVICE:${PN} = "gateway-telemetry.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${libexecdir}/gateway-os
    install -m 0755 ${WORKDIR}/gateway-telemetry.py ${D}${libexecdir}/gateway-os/gateway-telemetry.py

    install -d ${D}${sysconfdir}/default
    install -m 0644 ${WORKDIR}/gateway-telemetry.conf ${D}${sysconfdir}/default/gateway-telemetry

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/gateway-telemetry.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${libexecdir}/gateway-os/gateway-telemetry.py \
    ${sysconfdir}/default/gateway-telemetry \
    ${systemd_system_unitdir}/gateway-telemetry.service \
"

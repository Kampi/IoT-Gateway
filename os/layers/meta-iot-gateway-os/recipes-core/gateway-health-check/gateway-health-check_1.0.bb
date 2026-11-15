# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

SUMMARY = "Health check of a new RAUC slot before it is marked good"
DESCRIPTION = "On the first boot of a newly installed bundle, waits for the \
required services and only then lets rauc-mark-good mark the slot good. \
Otherwise marks it bad and reboots into the previous slot (rollback)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://gateway-health-check \
    file://gateway-health-check.service \
    file://gateway-health-check.conf \
"

S = "${WORKDIR}"

inherit systemd

# @USER_CONFIG: Units that must be active on the first boot of a new system, otherwise rollback to the previous slot
GATEWAY_HEALTH_UNITS ?= "NetworkManager.service otbr-agent.service gateway-telemetry.service"
# @USER_CONFIG: "<device>:<unit>" pairs, unit only required while the device exists (USB peripherals)
GATEWAY_HEALTH_DEVICE_UNITS ?= "/dev/zigbee:zigbee2mqtt.service /dev/lora:lora-packet-forwarder.service"
# @USER_CONFIG: Seconds the health check waits for the units before it rolls back
GATEWAY_HEALTH_TIMEOUT ?= "300"

RDEPENDS:${PN} = "rauc python3-core python3-json python3-logging"

SYSTEMD_SERVICE:${PN} = "gateway-health-check.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${libexecdir}/gateway-os
    install -m 0755 ${WORKDIR}/gateway-health-check ${D}${libexecdir}/gateway-os/

    install -d ${D}${sysconfdir}/default
    sed -e 's|@GATEWAY_HEALTH_UNITS@|${GATEWAY_HEALTH_UNITS}|g' \
        -e 's|@GATEWAY_HEALTH_DEVICE_UNITS@|${GATEWAY_HEALTH_DEVICE_UNITS}|g' \
        -e 's|@GATEWAY_HEALTH_TIMEOUT@|${GATEWAY_HEALTH_TIMEOUT}|g' \
        ${WORKDIR}/gateway-health-check.conf > ${D}${sysconfdir}/default/gateway-health-check
    chmod 0644 ${D}${sysconfdir}/default/gateway-health-check

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/gateway-health-check.service ${D}${systemd_system_unitdir}/
}

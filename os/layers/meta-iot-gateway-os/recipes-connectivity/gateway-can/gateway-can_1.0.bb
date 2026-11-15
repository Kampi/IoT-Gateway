# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

SUMMARY = "Bring up the CAN interfaces at boot"
DESCRIPTION = "can@<interface>.service sets bitrate and restart time of a \
SocketCAN interface (MCP2518FD can0) and brings it up, with can-utils for \
candump/cansend."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://can@.service \
    file://gateway-can.conf \
"

S = "${WORKDIR}"

inherit systemd

# @USER_CONFIG: CAN interfaces brought up at boot (space separated, empty = none)
GATEWAY_CAN_INTERFACES ?= "can0"
# @USER_CONFIG: CAN bitrate in bit/s
GATEWAY_CAN_BITRATE ?= "500000"
# @USER_CONFIG: CAN automatic restart after bus-off in ms (0 = off)
GATEWAY_CAN_RESTART_MS ?= "100"

RDEPENDS:${PN} = "iproute2 can-utils"

SYSTEMD_SERVICE:${PN} = "${@' '.join('can@%s.service' % i for i in d.getVar('GATEWAY_CAN_INTERFACES').split())}"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/can@.service ${D}${systemd_system_unitdir}/

    install -d ${D}${sysconfdir}/default
    sed -e 's|@GATEWAY_CAN_BITRATE@|${GATEWAY_CAN_BITRATE}|g' \
        -e 's|@GATEWAY_CAN_RESTART_MS@|${GATEWAY_CAN_RESTART_MS}|g' \
        ${WORKDIR}/gateway-can.conf > ${D}${sysconfdir}/default/gateway-can
    chmod 0644 ${D}${sysconfdir}/default/gateway-can
}

FILES:${PN} = "${systemd_system_unitdir}/can@.service ${sysconfdir}/default/gateway-can"

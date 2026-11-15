# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

SUMMARY = "Unique gateway ID from the module serial number"
DESCRIPTION = "Writes GATEWAY_SERIAL and GATEWAY_ID (e.g. iot-gateway-07219303) \
to /run/gateway-id.env at boot and uses it as hostname. Services load it \
with EnvironmentFile= for names that must be unique per gateway, e.g. MQTT \
topics."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://gateway-id \
    file://gateway-id.service \
"

S = "${WORKDIR}"

inherit systemd allarch

# Must match the image hostname (hostname:pn-base-files in gateway-os.conf):
# only that default is replaced by the gateway ID.
# @USER_CONFIG: Prefix of the gateway ID (<prefix>-<module serial number>), used as hostname and e.g. in the Zigbee2MQTT base topic
GATEWAY_ID_PREFIX ?= "iot-gateway"

SYSTEMD_SERVICE:${PN} = "gateway-id.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${libexecdir}/gateway-os
    sed -e 's|@GATEWAY_ID_PREFIX@|${GATEWAY_ID_PREFIX}|g' \
        ${WORKDIR}/gateway-id > ${D}${libexecdir}/gateway-os/gateway-id
    chmod 0755 ${D}${libexecdir}/gateway-os/gateway-id

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/gateway-id.service ${D}${systemd_system_unitdir}/
}

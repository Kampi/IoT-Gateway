# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

SUMMARY = "Power on the radio modules (Zigbee, Thread, wM-Bus, LoRa)"
DESCRIPTION = "Template unit module-power@<name>.service drives the \
<name>-power-enable, <name>-reset and <name>-boot GPIOs so the module runs \
normally. Pulled in by zigbee2mqtt.service (zigbee), otbr-agent.service \
(thread) and wmbusmeters.service (wmbus) via Requires=. The USB LoRa \
concentrator (lora) is powered at boot, its service starts on enumeration."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = " \
    file://services/module-power@.service \
    file://services/module-power@wmbus.service.d/lines.conf \
    file://services/module-power@lora.service.d/lines.conf \
"

RDEPENDS:${PN} = "libgpiod-tools"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/module-power@.service ${D}${systemd_system_unitdir}/
    install -d ${D}${systemd_system_unitdir}/module-power@wmbus.service.d
    install -m 0644 ${WORKDIR}/services/module-power@wmbus.service.d/lines.conf \
        ${D}${systemd_system_unitdir}/module-power@wmbus.service.d/
    install -d ${D}${systemd_system_unitdir}/module-power@lora.service.d
    install -m 0644 ${WORKDIR}/services/module-power@lora.service.d/lines.conf \
        ${D}${systemd_system_unitdir}/module-power@lora.service.d/

    # LoRa: powered at boot (the template has no [Install] section)
    install -d ${D}${sysconfdir}/systemd/system/multi-user.target.wants
    ln -sf ${systemd_system_unitdir}/module-power@.service \
        ${D}${sysconfdir}/systemd/system/multi-user.target.wants/module-power@lora.service
}

FILES:${PN} = " \
    ${systemd_system_unitdir}/module-power@.service \
    ${systemd_system_unitdir}/module-power@wmbus.service.d/lines.conf \
    ${systemd_system_unitdir}/module-power@lora.service.d/lines.conf \
    ${sysconfdir}/systemd/system/multi-user.target.wants/module-power@lora.service \
"

SUMMARY = "Switch on the READY LED (SODIMM 206 / Verdin GPIO_1) once boot has completed"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = " \
    file://gateway-ready-led.sh \
    file://services/gateway-ready-led.service \
"

inherit systemd

SYSTEMD_SERVICE:${PN} = "gateway-ready-led.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${libexecdir}/gateway-os
    install -m 0755 ${WORKDIR}/gateway-ready-led.sh ${D}${libexecdir}/gateway-os/gateway-ready-led.sh

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/gateway-ready-led.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${libexecdir}/gateway-os/gateway-ready-led.sh \
    ${systemd_system_unitdir}/gateway-ready-led.service \
"

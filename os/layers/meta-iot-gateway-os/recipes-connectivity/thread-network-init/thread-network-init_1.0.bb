SUMMARY = "Form/attach the gateway's Thread network on every boot"
DESCRIPTION = "otbr-agent is built with OTBR_NO_AUTO_ATTACH, so it never \
brings the Thread interface up by itself. This service does that on every \
boot, and forms a new random Thread network the first time (when no \
active dataset exists yet)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = " \
    file://thread-network-init.sh \
    file://services/thread-network-init.service \
"

inherit systemd

RDEPENDS:${PN} = "ot-br-posix"

SYSTEMD_SERVICE:${PN} = "thread-network-init.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${libexecdir}/gateway-os
    install -m 0755 ${WORKDIR}/thread-network-init.sh ${D}${libexecdir}/gateway-os/thread-network-init.sh

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/thread-network-init.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${libexecdir}/gateway-os/thread-network-init.sh \
    ${systemd_system_unitdir}/thread-network-init.service \
"

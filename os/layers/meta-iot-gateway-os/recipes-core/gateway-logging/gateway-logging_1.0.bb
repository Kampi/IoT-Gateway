SUMMARY = "GatewayOS system logging: volatile journal in RAM with periodic eMMC ring buffer"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://00-gateway-journald.conf \
    file://journal-archive.conf \
    file://journal-sync \
    file://services/journal-sync.service \
    file://services/journal-sync.timer \
"

inherit systemd

SYSTEMD_SERVICE:${PN} = "journal-sync.timer"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    # journald drop-in: volatile storage in RAM
    install -d ${D}${sysconfdir}/systemd/journald.conf.d
    install -m 0644 ${WORKDIR}/00-gateway-journald.conf ${D}${sysconfdir}/systemd/journald.conf.d/

    # tmpfiles.d: create archive directory on eMMC at boot
    install -d ${D}${libdir}/tmpfiles.d
    install -m 0644 ${WORKDIR}/journal-archive.conf ${D}${libdir}/tmpfiles.d/

    # Sync script
    install -d ${D}${libexecdir}/gateway-os
    install -m 0755 ${WORKDIR}/journal-sync ${D}${libexecdir}/gateway-os/journal-sync

    # Systemd units
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/journal-sync.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${WORKDIR}/services/journal-sync.timer ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${sysconfdir}/systemd/journald.conf.d/00-gateway-journald.conf \
    ${libdir}/tmpfiles.d/journal-archive.conf \
    ${libexecdir}/gateway-os/journal-sync \
    ${systemd_system_unitdir}/journal-sync.service \
    ${systemd_system_unitdir}/journal-sync.timer \
"

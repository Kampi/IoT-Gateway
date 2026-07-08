SUMMARY = "Daily off-device backup of Zigbee and Thread state"
DESCRIPTION = "Tars up the zigbee2mqtt coordinator DB/backup and the \
OpenThread dataset and rsyncs them to a remote host over SSH, using a \
per-device key generated on first boot. Destination is set in \
/etc/default/gateway-backup (GATEWAY_BACKUP_REMOTE)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = " \
    file://gateway-backup.sh \
    file://gateway-backup-keygen.sh \
    file://gateway-backup.conf \
    file://services/gateway-backup.service \
    file://services/gateway-backup.timer \
    file://services/gateway-backup-keygen.service \
"

inherit systemd

RDEPENDS:${PN} = "rsync openssh-ssh openssh-keygen zigbee2mqtt ot-br-posix"

SYSTEMD_SERVICE:${PN} = "gateway-backup-keygen.service gateway-backup.service gateway-backup.timer"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${libexecdir}/gateway-os
    install -m 0755 ${WORKDIR}/gateway-backup.sh ${D}${libexecdir}/gateway-os/gateway-backup.sh
    install -m 0755 ${WORKDIR}/gateway-backup-keygen.sh ${D}${libexecdir}/gateway-os/gateway-backup-keygen.sh

    install -d ${D}${sysconfdir}/default
    install -m 0644 ${WORKDIR}/gateway-backup.conf ${D}${sysconfdir}/default/gateway-backup

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/gateway-backup.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${WORKDIR}/services/gateway-backup.timer ${D}${systemd_system_unitdir}/
    install -m 0644 ${WORKDIR}/services/gateway-backup-keygen.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${libexecdir}/gateway-os/gateway-backup.sh \
    ${libexecdir}/gateway-os/gateway-backup-keygen.sh \
    ${sysconfdir}/default/gateway-backup \
    ${systemd_system_unitdir}/gateway-backup.service \
    ${systemd_system_unitdir}/gateway-backup.timer \
    ${systemd_system_unitdir}/gateway-backup-keygen.service \
"

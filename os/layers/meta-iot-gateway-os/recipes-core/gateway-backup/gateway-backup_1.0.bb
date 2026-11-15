# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

SUMMARY = "Daily off-device backup of all persistent gateway data"
DESCRIPTION = "Tars up the /etc, /var/lib and /home overlays of the DATA \
partition (network settings, keys, Zigbee and Thread state, ...) and rsyncs \
them to a remote host over SSH, using a per-device key generated on first \
boot. Destination: GATEWAY_BACKUP_REMOTE (local.conf, on the gateway \
/etc/default/gateway-backup)."
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

# GNU tar: xattrs and whiteout device nodes of the overlays
RDEPENDS:${PN} = "rsync openssh-ssh openssh-keygen tar gzip gateway-id"

# @USER_CONFIG: rsync/ssh destination of the daily backup, e.g. "backup@192.168.1.10:/volume1/backups/gateway/" (empty = no backup)
GATEWAY_BACKUP_REMOTE ?= ""

SYSTEMD_SERVICE:${PN} = "gateway-backup-keygen.service gateway-backup.service gateway-backup.timer"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${libexecdir}/gateway-os
    install -m 0755 ${WORKDIR}/gateway-backup.sh ${D}${libexecdir}/gateway-os/gateway-backup.sh
    install -m 0755 ${WORKDIR}/gateway-backup-keygen.sh ${D}${libexecdir}/gateway-os/gateway-backup-keygen.sh

    install -d ${D}${sysconfdir}/default
    sed -e 's|@GATEWAY_BACKUP_REMOTE@|${GATEWAY_BACKUP_REMOTE}|g' \
        ${WORKDIR}/gateway-backup.conf > ${D}${sysconfdir}/default/gateway-backup
    chmod 0644 ${D}${sysconfdir}/default/gateway-backup

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

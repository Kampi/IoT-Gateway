# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI:append = " \
    file://ssh.conf \
"

# Root login key (public part), from GATEWAY_KEYS_DIR (os/keys/README.md)
# @USER_CONFIG: Public SSH key(s) allowed to log in as root (authorized_keys format)
GATEWAY_SSH_AUTHORIZED_KEYS ?= "${GATEWAY_KEYS_DIR}/gateway_ed25519.pub"
do_install[file-checksums] += "${GATEWAY_SSH_AUTHORIZED_KEYS}:True"

do_install:append() {
    install -d ${D}${sysconfdir}/ssh/sshd_config.d
    install -m 0600 ${WORKDIR}/ssh.conf ${D}${sysconfdir}/ssh/sshd_config.d/

    [ -f "${GATEWAY_SSH_AUTHORIZED_KEYS}" ] || bbfatal "SSH key ${GATEWAY_SSH_AUTHORIZED_KEYS} not found (GATEWAY_KEYS_DIR)"
    install -d -m 0700 ${D}${ROOT_HOME}/.ssh
    install -m 0600 ${GATEWAY_SSH_AUTHORIZED_KEYS} ${D}${ROOT_HOME}/.ssh/authorized_keys
}

FILES:${PN}-sshd += " \
    ${sysconfdir}/ssh/sshd_config.d/ssh.conf \
    ${ROOT_HOME}/.ssh/authorized_keys \
"

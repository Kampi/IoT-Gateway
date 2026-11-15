# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Bundles must carry the same compatible string (gateway-os-bundle.bb), so a
# Dahlia bundle cannot be installed on a mainboard gateway and vice versa.
RAUC_COMPATIBLE ?= "iot-gateway-${GATEWAY_BOARD}"

# Keyring: CA of the bundle signing keys, from GATEWAY_KEYS_DIR (os/keys/README.md)
# @USER_CONFIG: RAUC keyring (CA certificate) installed on the gateway
RAUC_KEYRING_FILE_PATH ?= "${GATEWAY_KEYS_DIR}/rauc/ca.cert.pem"
RAUC_KEYRING_URI = ""
do_install[file-checksums] += "${RAUC_KEYRING_FILE_PATH}:True"

do_install() {
    [ -f "${RAUC_KEYRING_FILE_PATH}" ] || bbfatal "RAUC keyring ${RAUC_KEYRING_FILE_PATH} not found (GATEWAY_KEYS_DIR)"
    install -d ${D}${sysconfdir}/rauc
    sed -e 's|@RAUC_COMPATIBLE@|${RAUC_COMPATIBLE}|g' ${WORKDIR}/system.conf > ${D}${sysconfdir}/rauc/system.conf
    chmod 0644 ${D}${sysconfdir}/rauc/system.conf
    install -m 0644 ${RAUC_KEYRING_FILE_PATH} ${D}${sysconfdir}/rauc/ca.cert.pem
}

# GATEWAY_BOARD changes the compatible string
PACKAGE_ARCH = "${MACHINE_ARCH}"

# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

# Compressed swap in RAM: size and algorithm (/etc/default/zram)
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
SRC_URI:append = " file://zram"

do_install:append() {
    install -d ${D}${sysconfdir}/default
    install -m 0644 ${WORKDIR}/zram ${D}${sysconfdir}/default/zram
}

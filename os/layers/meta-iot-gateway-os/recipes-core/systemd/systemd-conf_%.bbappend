# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

FILESEXTRAPATHS:prepend := "${THISDIR}/systemd-conf:"

# Replace OE-core's wired.network with a disabled version — NetworkManager
# manages all interfaces; systemd-networkd is not used in this image.
SRC_URI:append = " \
    file://wired.network \
    file://wireless.network \
    file://10-gateway-watchdog.conf \
"

do_install:append() {
    install -d ${D}${sysconfdir}/systemd/network/
    install -m 0644 ${WORKDIR}/wired.network ${D}${sysconfdir}/systemd/network/
    install -m 0644 ${WORKDIR}/wireless.network ${D}${sysconfdir}/systemd/network/

    # Hardware watchdog, see 10-gateway-watchdog.conf
    install -d ${D}${systemd_unitdir}/system.conf.d/
    install -m 0644 ${WORKDIR}/10-gateway-watchdog.conf ${D}${systemd_unitdir}/system.conf.d/
}

FILES:${PN} += " \
    ${sysconfdir}/systemd/network/wired.network \
    ${sysconfdir}/systemd/network/wireless.network \
    ${systemd_unitdir}/system.conf.d/10-gateway-watchdog.conf \
"

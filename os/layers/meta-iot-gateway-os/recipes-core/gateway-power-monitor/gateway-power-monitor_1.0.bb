# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

SUMMARY = "Mains/battery supervision with clean shutdown on low battery"
DESCRIPTION = "Reads the TPS2121 power mux status (SODIMM 210, powermux-status) \
and the MAX17048 fuel gauge: reports the power source to gateway-telemetry \
(Home Assistant), sends power events and shuts the gateway down cleanly on \
low battery. Mainboard only."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://gateway-power-monitor \
    file://gateway-power-monitor.service \
    file://gateway-power-monitor.conf \
"

S = "${WORKDIR}"

inherit systemd

# @USER_CONFIG: Level of powermux-status (SODIMM 210) on battery (TPS2121 ST via inverting BSS138: 1 = battery, 0 = mains)
POWER_BATTERY_LEVEL ?= "1"
# @USER_CONFIG: Clean shutdown when the gateway runs on battery and the battery is at or below this charge in %
POWER_SHUTDOWN_PERCENT ?= "10"
# @USER_CONFIG: Clean shutdown after this many seconds on battery, regardless of the charge (0 = off)
POWER_SHUTDOWN_DELAY ?= "0"

RDEPENDS:${PN} = "python3-core python3-json python3-logging python3-shell libgpiod-tools mosquitto-clients"

SYSTEMD_SERVICE:${PN} = "gateway-power-monitor.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"
# Dahlia dev setup: no TPS2121, powermux-status floats (pull-up = "battery")
SYSTEMD_AUTO_ENABLE:${PN}:board-dahlia = "disable"

do_install() {
    install -d ${D}${libexecdir}/gateway-os
    install -m 0755 ${WORKDIR}/gateway-power-monitor ${D}${libexecdir}/gateway-os/

    install -d ${D}${sysconfdir}/default
    sed -e 's|@POWER_BATTERY_LEVEL@|${POWER_BATTERY_LEVEL}|g' \
        -e 's|@POWER_SHUTDOWN_PERCENT@|${POWER_SHUTDOWN_PERCENT}|g' \
        -e 's|@POWER_SHUTDOWN_DELAY@|${POWER_SHUTDOWN_DELAY}|g' \
        ${WORKDIR}/gateway-power-monitor.conf > ${D}${sysconfdir}/default/gateway-power-monitor
    chmod 0644 ${D}${sysconfdir}/default/gateway-power-monitor

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/gateway-power-monitor.service ${D}${systemd_system_unitdir}/
}

# GATEWAY_BOARD selects whether the service is enabled
PACKAGE_ARCH = "${MACHINE_ARCH}"

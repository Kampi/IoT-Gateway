SUMMARY = "Power on the Zigbee/Thread modules before their services start"
DESCRIPTION = "Template unit module-power@<name>.service drives the \
<name>-power-enable, <name>-reset and <name>-boot GPIOs so the module runs \
normally. Pulled in by zigbee2mqtt.service (zigbee) and otbr-agent.service \
(thread) via Requires=."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = "file://services/module-power@.service"

RDEPENDS:${PN} = "libgpiod-tools"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/module-power@.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = "${systemd_system_unitdir}/module-power@.service"

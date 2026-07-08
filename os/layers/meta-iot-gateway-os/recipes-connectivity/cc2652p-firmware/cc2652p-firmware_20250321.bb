SUMMARY = "Z-Stack 3.x coordinator firmware for CC2652P"
DESCRIPTION = "Pre-compiled Z-Stack 3.x coordinator firmware for TI CC2652P \
used with Zigbee2MQTT (launchpad build, no external PA switch control needed). \
Flash via: flash-cc2652p write /usr/share/cc2652p/CC2652P_coordinator.hex"
HOMEPAGE = "https://github.com/Koenkk/Z-Stack-firmware"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "https://github.com/Koenkk/Z-Stack-firmware/releases/download/Z-Stack_3.x.0_coordinator_20250321/CC1352P2_CC2652P_launchpad_coordinator_20250321.zip;subdir=firmware"
SRC_URI[sha256sum] = "66f5d3d20c2ca5375133da6a47b959525e39d707736a4d1b01b44bd95b04273a"

S = "${WORKDIR}/firmware"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}/usr/share/cc2652p
    install -m 0644 ${S}/CC1352P2_CC2652P_launchpad_coordinator_20250321.hex \
        ${D}/usr/share/cc2652p/CC2652P_coordinator.hex
}

FILES:${PN} = "/usr/share/cc2652p"

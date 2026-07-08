SUMMARY = "Driver for RTL-SDR device maintained by REDHAWK SDR."
HOMEPAGE = "http://sdr.osmocom.org/trac/wiki/rtl-sdr"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

SRCREV = "619ac3186ea0ffc092615e1f59f7397e5e6f668c"
SRC_URI = "git://gitea.osmocom.org/sdr/rtl-sdr;protocol=https;branch=master"

S = "${WORKDIR}/git"

DEPENDS = "libusb1"

inherit cmake pkgconfig

RDEPENDS:${PN} = "libusb1"

SUMMARY = "The program acquires utility meter readings from wired m-bus or wireless wm-bus meters."
HOMEPAGE = "https://github.com/wmbusmeters/wmbusmeters"

LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-3.0-only;md5=c79ff39f19dfec6d293b95dea7b07891"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = " \
    git://github.com/wmbusmeters/wmbusmeters;protocol=https;branch=master \
    file://0001-set-libxml2-cxxflags-by-pkg-config.patch \
    file://services/wmbusmeters.service \
    file://wmbusmeters.conf \
"
SRCREV = "2f2720d9d8e043304a366d11d933be9d535e17a5"

S = "${WORKDIR}/git"

DEPENDS = "rtlsdr icu libxml2"
RDEPENDS:${PN} = "rtlsdr icu"

# The Makefile strips binaries itself; let Yocto handle stripping instead.
EXTRA_OEMAKE += "STRIP=true"
CXXFLAGS += "-DLIBXML_DOCB_ENABLED"

inherit pkgconfig systemd

do_install () {
    # Binary: /usr/bin/wmbusmeters
    install -d ${D}${bindir}
    install -m 0755 ${S}/build/wmbusmeters ${D}${bindir}/wmbusmeters

    # Daemon symlink: /usr/sbin/wmbusmetersd -> ../bin/wmbusmeters
    install -d ${D}${sbindir}
    ln -s ../bin/wmbusmeters ${D}${sbindir}/wmbusmetersd

    # Default configuration
    install -d ${D}${sysconfdir}/wmbusmeters.d
    install -d ${D}${sysconfdir}/wmbusmeters.drivers.d
    install -m 0644 ${WORKDIR}/wmbusmeters.conf ${D}${sysconfdir}/wmbusmeters.conf

    # systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/wmbusmeters.service ${D}${systemd_system_unitdir}/wmbusmeters.service
}

FILES:${PN} += " \
    ${sysconfdir}/wmbusmeters.conf \
    ${sysconfdir}/wmbusmeters.d \
    ${sysconfdir}/wmbusmeters.drivers.d \
    ${sbindir}/wmbusmetersd \
    ${systemd_system_unitdir}/wmbusmeters.service \
"

SYSTEMD_SERVICE:${PN} = "wmbusmeters.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

SUMMARY = "The program acquires utility meter readings from wired m-bus or wireless wm-bus meters."
HOMEPAGE = "https://github.com/wmbusmeters/wmbusmeters"
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-3.0-only;md5=c79ff39f19dfec6d293b95dea7b07891"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRCREV = "2f2720d9d8e043304a366d11d933be9d535e17a5"
SRC_URI = " \
    git://github.com/wmbusmeters/wmbusmeters;protocol=https;branch=master \
    file://0001-set-libxml2-cxxflags-by-pkg-config.patch \
    file://services/wmbusmeters.service \
    file://wmbusmeters.conf \
    file://99-wmbus.rules \
"

S = "${WORKDIR}/git"

DEPENDS = "rtlsdr icu libxml2 mosquitto"

inherit pkgconfig systemd

RDEPENDS:${PN} = "rtlsdr icu libmosquitto1"

# @USER_CONFIG: MQTT broker hostname (or hostname:port) for wmbusmeters (e.g. 192.168.1.10 or 192.168.1.10:1883)
WMBUSMETERS_MQTT_SERVER ?= "localhost"

# The Makefile strips binaries itself; let Yocto handle stripping instead.
EXTRA_OEMAKE += "STRIP=true"
CXXFLAGS += "-DLIBXML_DOCB_ENABLED"

SYSTEMD_SERVICE:${PN} = "wmbusmeters.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
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
    sed -i 's|@WMBUSMETERS_MQTT_SERVER@|${WMBUSMETERS_MQTT_SERVER}|g' ${D}${sysconfdir}/wmbusmeters.conf

    # systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/wmbusmeters.service ${D}${systemd_system_unitdir}/wmbusmeters.service

    # udev rule: /dev/wmbus symlink
    install -d ${D}${sysconfdir}/udev/rules.d
    install -m 0644 ${WORKDIR}/99-wmbus.rules ${D}${sysconfdir}/udev/rules.d/
}

FILES:${PN} += " \
    ${sysconfdir}/wmbusmeters.conf \
    ${sysconfdir}/wmbusmeters.d \
    ${sysconfdir}/wmbusmeters.drivers.d \
    ${sbindir}/wmbusmetersd \
    ${systemd_system_unitdir}/wmbusmeters.service \
    ${sysconfdir}/udev/rules.d/99-wmbus.rules \
"

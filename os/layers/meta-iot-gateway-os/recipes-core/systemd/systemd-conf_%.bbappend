# Replace OE-core's wired.network with one that configures DHCP and a static
# fallback address for direct Ethernet connections (PC-to-device without DHCP).
FILESEXTRAPATHS:prepend := "${THISDIR}/systemd-conf:"

SRC_URI:append = " file://wireless.network "

FILES:${PN} += "${sysconfdir}/systemd/network/wireless.network"

do_install:append() {
    install -d ${D}${sysconfdir}/systemd/network/
    install -m 0644 ${WORKDIR}/wireless.network ${D}${sysconfdir}/systemd/network/
}

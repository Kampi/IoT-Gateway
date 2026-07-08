FILESEXTRAPATHS:prepend := "${THISDIR}/systemd-conf:"

# Replace OE-core's wired.network with a disabled version — NetworkManager
# manages all interfaces; systemd-networkd is not used in this image.
SRC_URI:append = " \
    file://wired.network \
    file://wireless.network \
"

do_install:append() {
    install -d ${D}${sysconfdir}/systemd/network/
    install -m 0644 ${WORKDIR}/wired.network ${D}${sysconfdir}/systemd/network/
    install -m 0644 ${WORKDIR}/wireless.network ${D}${sysconfdir}/systemd/network/
}

FILES:${PN} += " \
    ${sysconfdir}/systemd/network/wired.network \
    ${sysconfdir}/systemd/network/wireless.network \
"

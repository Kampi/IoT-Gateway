FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += " \
    file://gateway.conf \
    file://eth-dhcp.nmconnection \
"

do_install:append() {
    install -d ${D}${sysconfdir}/NetworkManager/conf.d
    install -m 0644 ${WORKDIR}/gateway.conf ${D}${sysconfdir}/NetworkManager/conf.d/gateway.conf

    install -d ${D}${sysconfdir}/NetworkManager/system-connections
    install -m 0600 ${WORKDIR}/eth-dhcp.nmconnection ${D}${sysconfdir}/NetworkManager/system-connections/eth-dhcp.nmconnection
}

FILES:${PN} += " \
    ${sysconfdir}/NetworkManager/conf.d/gateway.conf \
    ${sysconfdir}/NetworkManager/system-connections/eth-dhcp.nmconnection \
"

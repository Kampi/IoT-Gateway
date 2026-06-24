FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://gateway.conf"

do_install:append() {
    install -d ${D}${sysconfdir}/NetworkManager/conf.d
    install -m 0644 ${WORKDIR}/gateway.conf ${D}${sysconfdir}/NetworkManager/conf.d/gateway.conf
}

FILES:${PN} += "${sysconfdir}/NetworkManager/conf.d/gateway.conf"

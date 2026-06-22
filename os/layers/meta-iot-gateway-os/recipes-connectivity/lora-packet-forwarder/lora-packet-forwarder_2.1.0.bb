SUMMARY = "LoRa Packet Forwarder for SX1302/SX1303 concentrators (USB and SPI)"
HOMEPAGE = "https://github.com/Lora-net/sx1302_hal"

LICENSE = "BSD-2-Clause"
LIC_FILES_CHKSUM = "file://LICENSE.TXT;md5=d2119120bd616e725f4580070bd9ee19"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRCREV = "4b42025d1751e04632c0b04160e0d29dbbb222a5"
SRC_URI = " \
    git://github.com/Lora-net/sx1302_hal.git;protocol=https;branch=master \
    file://global_conf.json.RAK-USB-EU868 \
    file://99-lora.rules \
    file://services/lora-packet-forwarder.service \
"

S = "${WORKDIR}/git"

LORA_INSTALL_DIR = "/opt/lora-packet-forwarder"

inherit systemd

do_configure:append() {
    sed -i 's|$(CC) -L$(LGW_PATH) -L$(LIB_PATH) $< $(OBJDIR)/jitqueue.o -o $@ $(LIBS)|$(CC) -L$(LGW_PATH) -L$(LIB_PATH) $(LDFLAGS) $< $(OBJDIR)/jitqueue.o -o $@ $(LIBS)|' \
        ${S}/packet_forwarder/Makefile
}

do_compile() {
    oe_runmake -C ${S} \
        CC="${CC}" \
        AR="${AR}" \
        ARCH="" \
        CROSS_COMPILE="" \
        CFLAGS="${CFLAGS} -I${S}/libloragw/inc -I${S}/libtools/inc -I${S}/packet_forwarder/inc" \
        LDFLAGS="${LDFLAGS}" \
        libtools libloragw packet_forwarder
}

do_install() {
    install -d ${D}${LORA_INSTALL_DIR}
    install -m 0755 ${S}/packet_forwarder/lora_pkt_fwd ${D}${LORA_INSTALL_DIR}/

    install -d ${D}${LORA_INSTALL_DIR}/config
    install -m 0644 ${WORKDIR}/global_conf.json.RAK-USB-EU868 ${D}${LORA_INSTALL_DIR}/config/

    install -d ${D}${sysconfdir}/udev/rules.d
    install -m 0644 ${WORKDIR}/99-lora.rules ${D}${sysconfdir}/udev/rules.d/

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/lora-packet-forwarder.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${LORA_INSTALL_DIR} \
    ${sysconfdir}/udev/rules.d/99-lora.rules \
    ${systemd_system_unitdir}/lora-packet-forwarder.service \
"

SYSTEMD_SERVICE:${PN} = "lora-packet-forwarder.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

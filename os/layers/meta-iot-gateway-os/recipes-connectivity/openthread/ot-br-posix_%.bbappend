FILESEXTRAPATHS:prepend := "${THISDIR}/ot-br-posix:"

# @USER_CONFIG: Network interface connected to the backbone (IP) network
OTBR_INFRA_IFACE ?= "end0"

# @USER_CONFIG: Thread RCP device path (nRF5340 flashed with OpenThread RCP firmware, connected via UART_4)
OTBR_RCP_DEVICE ?= "/dev/thread"

# Override infra interface (upstream defaults to eth0)
EXTRA_OECMAKE:remove = "-DOTBR_INFRA_IF_NAME=eth0"
EXTRA_OECMAKE:append = " -DOTBR_INFRA_IF_NAME=${OTBR_INFRA_IFACE}"

SRC_URI:append = " \
    file://otbr-agent-gateway.conf \
    file://99-thread.rules \
"

do_install:append() {
    install -d ${D}${sysconfdir}/default
    sed -e 's|@OTBR_INFRA_IFACE@|${OTBR_INFRA_IFACE}|g' \
        -e 's|@OTBR_RCP_DEVICE@|${OTBR_RCP_DEVICE}|g' \
        ${WORKDIR}/otbr-agent-gateway.conf > ${D}${sysconfdir}/default/otbr-agent

    install -d ${D}${nonarch_base_libdir}/udev/rules.d
    install -m 0644 ${WORKDIR}/99-thread.rules ${D}${nonarch_base_libdir}/udev/rules.d/99-thread.rules
}

FILES:${PN} += " \
    ${sysconfdir}/default/otbr-agent \
    ${nonarch_base_libdir}/udev/rules.d/99-thread.rules \
"

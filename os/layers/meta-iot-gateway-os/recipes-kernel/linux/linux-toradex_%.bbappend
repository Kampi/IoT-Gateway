FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI:append = " \
    file://iot-gateway-imx8mm.dts \
    file://wireless.cfg \
    file://peripherals.cfg \
    file://multimedia.cfg \
    file://networking.cfg \
"

KERNEL_DEVICETREE:append = " freescale/iot-gateway-imx8mm.dtb"

do_configure:append() {
    install -m 0644 ${WORKDIR}/iot-gateway-imx8mm.dts ${S}/arch/arm64/boot/dts/freescale/
    echo 'dtb-$(CONFIG_ARCH_MXC) += iot-gateway-imx8mm.dtb' >> ${S}/arch/arm64/boot/dts/freescale/Makefile
}
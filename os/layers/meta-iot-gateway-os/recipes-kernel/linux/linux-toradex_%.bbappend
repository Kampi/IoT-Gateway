FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI:append = " \
    file://iot-gateway-imx8mm.dts \
"

KERNEL_DEVICETREE = "freescale/iot-gateway-imx8mm.dtb"

do_configure:append() {
    install -m 0644 ${WORKDIR}/iot-gateway-imx8mm.dts ${S}/arch/arm64/boot/dts/freescale/
    echo 'dtb-$(CONFIG_ARCH_MXC) += iot-gateway-imx8mm.dtb' >> ${S}/arch/arm64/boot/dts/freescale/Makefile
}
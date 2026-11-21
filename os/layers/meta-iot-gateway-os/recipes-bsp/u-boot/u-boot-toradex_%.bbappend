# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

FILESEXTRAPATHS:prepend := "${THISDIR}/u-boot-toradex:"

# IoT-Gateway mainboard: debug console header X901 is on Verdin UART_1
# (i.MX UART2, /dev/ttymxc1), UART_3 is the Zigbee module (README "UARTs").
# SPL, U-Boot and the kernel console (console=ttymxc1) move to UART2.
SRC_URI:append:board-mainboard = " file://0001-verdin-imx8mm-debug-console-on-UART_1.patch"

do_configure:append() {
    # Remove existing fdtfile, if present
    sed -i '/"fdtfile=.*\\0" \\/d' ${S}/include/configs/verdin-imx8mm.h

    # Add custom fdtfile
    sed -i 's/\("fdt_board=.*\\0" \\\)/\0\n      "fdtfile=iot-gateway-imx8mm.dtb\\0" \\/' ${S}/include/configs/verdin-imx8mm.h
}
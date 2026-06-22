do_configure:append() {
    # Remove existing fdtfile, if present
    sed -i '/"fdtfile=.*\\0" \\/d' ${S}/include/configs/verdin-imx8mm.h

    # Add custom fdtfile
    sed -i 's/\("fdt_board=.*\\0" \\\)/\0\n      "fdtfile=iot-gateway-imx8mm.dtb\\0" \\/' ${S}/include/configs/verdin-imx8mm.h
}
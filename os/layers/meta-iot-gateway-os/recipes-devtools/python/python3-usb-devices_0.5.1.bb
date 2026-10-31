SUMMARY = "USB device enumeration helpers"
HOMEPAGE = "https://github.com/Bluetooth-Devices/usb-devices"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d8b5b84f8af09011094cd7c29a9f972c"

PYPI_PACKAGE = "usb_devices"
SRC_URI[sha256sum] = "8ef45c401490391a3651b3ba748eb213a654ac165be10a0786ab963668c49cc3"

inherit pypi python_poetry_core

RDEPENDS:${PN} += "python3-asyncio"

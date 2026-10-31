SUMMARY = "Tools to enumerate and find Bluetooth adapters"
HOMEPAGE = "https://github.com/Bluetooth-Devices/bluetooth-adapters"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=732518afbb3ba92dbf606e49f9045cc9"

# 0.20.2 is the newest release that still builds with poetry-core 1.x (scarthgap)
# and works with scarthgap's bleak 0.21 / dbus-fast 2.21.
PYPI_PACKAGE = "bluetooth_adapters"
SRC_URI[sha256sum] = "88bfba4ab8678c1bb3416851b3e6f497d4b8a368c8de1bc4e517743081ad2f55"

inherit pypi python_poetry_core

RDEPENDS:${PN} += " \
    python3-aiooui \
    python3-asyncio \
    python3-bleak \
    python3-dbus-fast \
    python3-uart-devices \
    python3-usb-devices \
"

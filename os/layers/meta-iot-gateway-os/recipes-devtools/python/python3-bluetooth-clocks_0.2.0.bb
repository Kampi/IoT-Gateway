SUMMARY = "Library to set the time of Bluetooth Low Energy devices"
HOMEPAGE = "https://github.com/koenvervloesem/bluetooth-clocks"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=f30b8a49b804a0dfcc15b1a24c98d15b"

SRC_URI[sha256sum] = "42bf2cec2cdafa0d435050c12f0cc192a42d59148badf96b18bfce7dcc06abbe"

inherit pypi python_setuptools_build_meta

DEPENDS += "python3-setuptools-scm-native"

RDEPENDS:${PN} += "python3-bleak"

SUMMARY = "Bluetooth numbers: company IDs, service and characteristic UUIDs"
HOMEPAGE = "https://github.com/koenvervloesem/bluetooth-numbers"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=8b4e412632e80a86e8d12472edd6ebae"

PYPI_PACKAGE = "bluetooth_numbers"
SRC_URI[sha256sum] = "ee447de6de3e1b4389aa60e32bd91e69f964995cd6726e0443f00726b2e3d6c4"

inherit pypi python_setuptools_build_meta

DEPENDS += "python3-setuptools-scm-native"

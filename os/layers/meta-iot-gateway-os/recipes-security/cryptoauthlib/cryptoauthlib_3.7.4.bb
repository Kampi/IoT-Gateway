SUMMARY = "Microchip CryptoAuthLib — authentication device library for ATECC608B"
HOMEPAGE = "https://github.com/MicrochipTech/cryptoauthlib"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://license.txt;md5=a3293b8b3c4eb1d8620d5febdf645f97"

PV = "3.7.4"
SRCREV = "d59e6a4866149a7ebbd8d50a3b4227bc3a83fdfb"
SRC_URI = "git://github.com/MicrochipTech/cryptoauthlib.git;protocol=https;nobranch=1;lfs=0"

S = "${WORKDIR}/git"

inherit cmake

EXTRA_OECMAKE = " \
    -DATCA_HAL_I2C=ON \
    -DATCA_BUILD_SHARED_LIBS=ON \
    -DBUILD_TESTS=OFF \
    -DATCA_PRINTF=OFF \
    -DATCA_STRICT_C99=ON \
"

FILES:${PN} = " \
    ${libdir}/libcryptoauth.so \
"

FILES:${PN}-dev = " \
    ${includedir}/cryptoauthlib \
    ${libdir}/cmake \
"

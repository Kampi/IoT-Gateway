SUMMARY = "Theengs Decoder - decodes BLE advertisements of many sensors"
HOMEPAGE = "https://decoder.theengs.io"
# TheengsDecoder itself is GPL-3.0 (no license file in the sdist), the bundled
# ArduinoJson is MIT.
LICENSE = "GPL-3.0-only & MIT"
LIC_FILES_CHKSUM = " \
    file://${COMMON_LICENSE_DIR}/GPL-3.0-only;md5=c79ff39f19dfec6d293b95dea7b07891 \
    file://src/arduino_json/LICENSE.md;md5=961cff0c711ece7652baadd2ae42eb34 \
"

SRC_URI[sha256sum] = "87f0cc1b289c8220263bb0ddcbe2eac146fc8f110360277c2ed0cba1e77f688f"

# Upstream builds the extension through scikit-build, which doesn't cross-compile
# cleanly. It is just two C++ files against the Python C API, so build it directly
# with the target toolchain instead.
inherit pypi python3native python3targetconfig

DEPENDS += "python3"

do_configure[noexec] = "1"

do_compile() {
    ${CXX} ${CXXFLAGS} ${LDFLAGS} -std=c++11 -shared -fPIC \
        -I${S}/src -I${S}/src/arduino_json/src \
        -I${STAGING_INCDIR}/${PYTHON_DIR} \
        ${S}/TheengsDecoder/_decoder.cpp ${S}/src/decoder.cpp \
        -o ${B}/_decoder.so
}

do_install() {
    install -d ${D}${PYTHON_SITEPACKAGES_DIR}/TheengsDecoder
    install -m 0644 ${S}/TheengsDecoder/__init__.py ${D}${PYTHON_SITEPACKAGES_DIR}/TheengsDecoder/
    install -m 0755 ${B}/_decoder.so ${D}${PYTHON_SITEPACKAGES_DIR}/TheengsDecoder/

    # Minimal package metadata, so importlib.metadata.version("TheengsDecoder")
    # works (used by "TheengsGateway --diagnose").
    install -d ${D}${PYTHON_SITEPACKAGES_DIR}/theengsdecoder-${PV}.dist-info
    printf 'Metadata-Version: 2.1\nName: TheengsDecoder\nVersion: ${PV}\n' \
        > ${D}${PYTHON_SITEPACKAGES_DIR}/theengsdecoder-${PV}.dist-info/METADATA
}

FILES:${PN} = " \
    ${PYTHON_SITEPACKAGES_DIR}/TheengsDecoder \
    ${PYTHON_SITEPACKAGES_DIR}/theengsdecoder-${PV}.dist-info \
"

RDEPENDS:${PN} += "python3-core"

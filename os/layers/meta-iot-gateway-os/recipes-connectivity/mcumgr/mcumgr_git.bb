SUMMARY = "mcumgr - CLI for the MCUmgr/SMP management protocol (MCUboot serial recovery, DFU)"
HOMEPAGE = "https://github.com/apache/mynewt-mcumgr-cli"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://src/${GO_IMPORT}/LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

PV = "0.1+git"
SRCREV = "5c56bd24066c780aad5836429bfa2ecc4f9a944c"
SRC_URI = " \
    git://github.com/apache/mynewt-mcumgr-cli.git;protocol=https;branch=master \
    file://vendor.tar.gz;subdir=${BPN}/src/${GO_IMPORT} \
"

# go.bbclass unpacks git source to ${WORKDIR}/${BPN}/src/${GO_IMPORT}/
S = "${WORKDIR}/${BPN}"

inherit go-mod

GO_IMPORT = "github.com/apache/mynewt-mcumgr-cli"

# Build directly from the mcumgr/ package, bypassing go.bbclass's
# GOPATH-style package listing which fails when the module root has no
# .go files of its own (see meta-iot-gateway-os's
# chirpstack-gateway-bridge recipe for the same pattern).
do_compile() {
    cd ${S}/src/${GO_IMPORT}
    mkdir -p ${B}/bin
    ${GO} build -v -trimpath -mod=vendor \
        -o ${B}/bin/${BPN} \
        ./mcumgr/
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/bin/${BPN} ${D}${bindir}/
}

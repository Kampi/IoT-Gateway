SUMMARY = "ChirpStack Gateway Bridge — Semtech UDP to MQTT bridge for ChirpStack"
HOMEPAGE = "https://www.chirpstack.io/docs/chirpstack-gateway-bridge/"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://src/${GO_IMPORT}/LICENSE;md5=bc4546f147d6f9892ca1b7d23bf41196"

PV = "4.0.11"
SRCREV = "6897fe56c2b8e631632fe2ed37e4d6dbd903f563"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = " \
    git://github.com/chirpstack/chirpstack-gateway-bridge.git;protocol=https;nobranch=1 \
    file://vendor.tar.gz;subdir=${BPN}/src/${GO_IMPORT} \
    file://chirpstack-gateway-bridge.toml \
    file://services/chirpstack-gateway-bridge.service \
"

# go.bbclass unpacks git source to ${WORKDIR}/${BPN}/src/${GO_IMPORT}/
S = "${WORKDIR}/${BPN}"

inherit go-mod systemd

GO_IMPORT = "github.com/chirpstack/chirpstack-gateway-bridge"

# @USER_CONFIG: MQTT broker URL for ChirpStack Gateway Bridge (e.g. tcp://192.168.1.10:1883)
CHIRPSTACK_MQTT_SERVER ?= "tcp://localhost:1883"

SYSTEMD_SERVICE:${PN} = "chirpstack-gateway-bridge.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

# Build directly from the module root, bypassing go.bbclass's GOPATH-style
# package listing which fails when the module root has no .go files.
do_compile() {
    cd ${S}/src/${GO_IMPORT}
    mkdir -p ${B}/bin
    ${GO} build -v -trimpath -buildmode=pie -mod=vendor \
        -o ${B}/bin/${BPN} \
        ./cmd/chirpstack-gateway-bridge/
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/bin/${BPN} ${D}${bindir}/

    install -d ${D}${sysconfdir}/chirpstack-gateway-bridge
    install -m 0644 ${WORKDIR}/chirpstack-gateway-bridge.toml ${D}${sysconfdir}/chirpstack-gateway-bridge/
    sed -i 's|@CHIRPSTACK_MQTT_SERVER@|${CHIRPSTACK_MQTT_SERVER}|g' ${D}${sysconfdir}/chirpstack-gateway-bridge/chirpstack-gateway-bridge.toml

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/chirpstack-gateway-bridge.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} += " \
    ${sysconfdir}/chirpstack-gateway-bridge/chirpstack-gateway-bridge.toml \
    ${systemd_system_unitdir}/chirpstack-gateway-bridge.service \
"

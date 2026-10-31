SUMMARY = "Theengs Gateway - Bluetooth Low Energy to MQTT"
DESCRIPTION = "Scans for BLE advertisements, decodes several hundred sensor types \
with Theengs Decoder and publishes them via MQTT, including Home Assistant \
MQTT discovery."
HOMEPAGE = "https://gateway.theengs.io"
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=1ebbd3e34237af26da5dc08a4e440464"

PYPI_PACKAGE = "theengsgateway"
SRC_URI[sha256sum] = "d4e562d43a8695508e92d3b7f88f786e68b22bbd841595163f057d75d49a3e97"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI += " \
    file://theengsgw.conf \
    file://services/theengs-gateway.service \
"

inherit pypi python_setuptools_build_meta systemd

DEPENDS += "python3-setuptools-scm-native"

# @USER_CONFIG: MQTT broker host for Theengs Gateway (Bluetooth LE to MQTT, e.g. 192.168.1.10)
THEENGS_MQTT_HOST ?= "localhost"

SYSTEMD_SERVICE:${PN} = "theengs-gateway.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install:append() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/theengs-gateway.service ${D}${systemd_system_unitdir}/

    # Theengs Gateway rewrites its configuration on every start, so it lives
    # in /var/lib instead of the (read-only) /etc.
    install -d ${D}${localstatedir}/lib/theengs-gateway
    sed -e 's|@THEENGS_MQTT_HOST@|${THEENGS_MQTT_HOST}|g' \
        ${WORKDIR}/theengsgw.conf > ${D}${localstatedir}/lib/theengs-gateway/theengsgw.conf
}

FILES:${PN} += " \
    ${systemd_system_unitdir}/theengs-gateway.service \
    ${localstatedir}/lib/theengs-gateway \
"

RDEPENDS:${PN} += " \
    bluez5 \
    python3-asyncio \
    python3-bleak \
    python3-bluetooth-adapters \
    python3-bluetooth-clocks \
    python3-bluetooth-numbers \
    python3-importlib-metadata \
    python3-json \
    python3-logging \
    python3-paho-mqtt \
    python3-pycryptodomex \
    python3-theengsdecoder \
"

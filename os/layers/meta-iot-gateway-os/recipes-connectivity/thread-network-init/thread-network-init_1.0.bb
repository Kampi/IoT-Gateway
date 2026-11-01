SUMMARY = "Form/attach the gateway's Thread network on every boot"
DESCRIPTION = "otbr-agent is built with OTBR_NO_AUTO_ATTACH, so it never \
brings the Thread interface up by itself. This service does that on every \
boot, and forms a new random Thread network the first time (when no \
active dataset exists yet)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = " \
    file://thread-network-init.sh \
    file://services/thread-network-init.service \
"

inherit systemd

# Default for both boards, matches the static IPv6 route on the FritzBox.
# Use a different random ULA per gateway if several run in the same LAN.
# @USER_CONFIG: Fixed OMR (off-mesh routable) IPv6 prefix of the Thread network, e.g. "fdxx:xxxx:xxxx:1::/64" (random ULA, RFC 4193). Empty = border router picks a random one that changes with every new Thread network. A fixed prefix lets the LAN router keep one static route to the gateway.
OTBR_OMR_PREFIX ?= "fdf2:83ec:1caf:1::/64"

RDEPENDS:${PN} = "ot-br-posix"

SYSTEMD_SERVICE:${PN} = "thread-network-init.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${libexecdir}/gateway-os
    sed -e 's|@OTBR_OMR_PREFIX@|${OTBR_OMR_PREFIX}|g' \
        ${WORKDIR}/thread-network-init.sh > ${D}${libexecdir}/gateway-os/thread-network-init.sh
    chmod 0755 ${D}${libexecdir}/gateway-os/thread-network-init.sh

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/thread-network-init.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${libexecdir}/gateway-os/thread-network-init.sh \
    ${systemd_system_unitdir}/thread-network-init.service \
"

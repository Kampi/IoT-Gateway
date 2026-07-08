SUMMARY = "WiFi captive portal for initial network configuration"
DESCRIPTION = "On boot, tries to connect to a configured WiFi network via NetworkManager. \
If no credentials are stored or the connection fails, raises an open \
Access Point on uap0 (Gateway-Setup) with a captive portal that lets the user \
select a network and enter the password. Credentials are saved as an NM connection profile."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

SRC_URI = " \
    file://wifi-setup.sh \
    file://captive-portal.py \
    file://hostapd.conf \
    file://dnsmasq-captive.conf \
    file://services/wifi-setup.service \
"

inherit systemd

RDEPENDS:${PN} = " \
    bash \
    hostapd \
    dnsmasq \
    networkmanager \
    networkmanager-nmcli \
    iw \
    python3-core \
    python3-netclient \
    python3-json \
"

SYSTEMD_SERVICE:${PN} = "wifi-setup.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/wifi-setup.sh ${D}${bindir}/wifi-setup
    install -m 0755 ${WORKDIR}/captive-portal.py ${D}${bindir}/captive-portal

    install -d ${D}${sysconfdir}/hostapd
    install -m 0644 ${WORKDIR}/hostapd.conf ${D}${sysconfdir}/hostapd/hostapd.conf

    install -m 0644 ${WORKDIR}/dnsmasq-captive.conf ${D}${sysconfdir}/dnsmasq-captive.conf

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/wifi-setup.service ${D}${systemd_system_unitdir}/wifi-setup.service
}

FILES:${PN} += " \
    ${bindir}/wifi-setup \
    ${bindir}/captive-portal \
    ${sysconfdir}/hostapd/hostapd.conf \
    ${sysconfdir}/dnsmasq-captive.conf \
    ${systemd_system_unitdir}/wifi-setup.service \
"

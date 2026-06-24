SUMMARY = "WiFi captive portal for initial network configuration"
DESCRIPTION = "On boot, tries to connect to a configured WiFi network. \
If no credentials are stored or the connection fails, raises an open \
Access Point (Gateway-Setup) with a captive portal that lets the user \
select a network and enter the password."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://wifi-setup.sh \
    file://captive-portal.py \
    file://hostapd.conf \
    file://dnsmasq-captive.conf \
    file://wpa_supplicant-mlan0.conf \
    file://services/wifi-setup.service \
"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:${THISDIR}:"

inherit systemd

# hostapd and dnsmasq are pulled in via RDEPENDS so they don't have to be
# listed separately in IMAGE_INSTALL
RDEPENDS:${PN} = " \
    bash \
    hostapd \
    dnsmasq \
    wpa-supplicant \
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

    # Empty wpa-supplicant config — populated at runtime by the captive portal
    install -d ${D}${sysconfdir}/wpa_supplicant
    install -m 0600 ${WORKDIR}/wpa_supplicant-mlan0.conf ${D}${sysconfdir}/wpa_supplicant/wpa_supplicant-mlan0.conf

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/services/wifi-setup.service ${D}${systemd_system_unitdir}/wifi-setup.service
}

FILES:${PN} += " \
    ${bindir}/wifi-setup \
    ${bindir}/captive-portal \
    ${sysconfdir}/hostapd/hostapd.conf \
    ${sysconfdir}/dnsmasq-captive.conf \
    ${sysconfdir}/wpa_supplicant/wpa_supplicant-mlan0.conf \
    ${systemd_system_unitdir}/wifi-setup.service \
"

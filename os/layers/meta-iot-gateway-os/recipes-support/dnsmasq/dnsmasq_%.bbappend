# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

# dnsmasq is only used by wifi-captive-portal, which starts its own instance
# for the setup access point (/etc/dnsmasq-captive.conf). The system service
# would occupy ports 53/67 and make that instance fail ("Address already in
# use"), so the captive portal never came up.
SYSTEMD_AUTO_ENABLE:${PN} = "disable"

# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

# GatewayOS boot script: A/B slot selection for RAUC, kernel and devicetree
# from the selected rootfs slot (u-boot-distro-boot/boot.cmd.in replaces the
# Toradex script). It never loads devicetree overlays (none are shipped, see
# gateway-os-image.bb).
FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

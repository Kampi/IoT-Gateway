# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Location of the U-Boot environment for fw_printenv/fw_setenv and RAUC.
# meta-toradex-bsp-common installs a fw_env.config with /dev/emmc-boot0, a
# symlink from Toradex udev rules that are not part of this image. This one
# uses the kernel device name and replaces it (installed after it, see
# do_install[postfuncs]).
SRC_URI:append:class-target = " file://fw_env.config.gateway"

gateway_fw_env_config() {
    install -d ${D}${sysconfdir}
    install -m 0644 ${WORKDIR}/fw_env.config.gateway ${D}${sysconfdir}/fw_env.config
}
do_install[postfuncs] += "${@'gateway_fw_env_config' if d.getVar('CLASSOVERRIDE') == 'class-target' else ''}"

PACKAGE_ARCH:class-target = "${MACHINE_ARCH}"

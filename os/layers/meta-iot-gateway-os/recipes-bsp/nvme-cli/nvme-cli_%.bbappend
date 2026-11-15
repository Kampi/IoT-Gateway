# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

# The rootfs is read-only, packages cannot run postinst scripts on the target.
# nvme-cli's only creates /etc/nvme/hostnqn and hostid (NVMe over Fabrics):
# done at boot by nvme-hostnqn.service instead (/etc is a writable overlay).
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
SRC_URI:append = " file://nvme-hostnqn.service"

inherit systemd
SYSTEMD_SERVICE:${PN} += "nvme-hostnqn.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

python () {
    d.delVar('pkg_postinst_ontarget:' + d.getVar('PN'))
}

do_install:append() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/nvme-hostnqn.service ${D}${systemd_system_unitdir}/
}

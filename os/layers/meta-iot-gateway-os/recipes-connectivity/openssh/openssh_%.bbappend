FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI:append = " \
    file://ssh.conf \
    file://authorized_keys \
"

do_install:append() {
    install -d ${D}${sysconfdir}/ssh/sshd_config.d
    install -m 0600 ${WORKDIR}/ssh.conf ${D}${sysconfdir}/ssh/sshd_config.d/

    install -d -m 0700 ${D}${ROOT_HOME}/.ssh
    install -m 0600 ${WORKDIR}/authorized_keys ${D}${ROOT_HOME}/.ssh/authorized_keys
}

FILES:${PN}-sshd += " \
    ${sysconfdir}/ssh/sshd_config.d/ssh.conf \
    ${ROOT_HOME}/.ssh/authorized_keys \
"

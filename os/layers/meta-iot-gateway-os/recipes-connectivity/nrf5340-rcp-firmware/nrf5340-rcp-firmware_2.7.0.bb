# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

SUMMARY = "OpenThread RCP firmware for the nRF5340 (Thread border router coprocessor)"
DESCRIPTION = "Builds firmware/nRF5340 (nRF Connect SDK ot_coprocessor sample, \
MCUboot single-slot with serial recovery) for the nRF5340 network core and \
installs it with flash-nrf5340, which writes it over the RCP UART \
(/dev/thread, mcumgr) without SWD."
HOMEPAGE = "https://github.com/nrfconnect/sdk-nrf"

# nRF Connect SDK (Nordic 5-Clause), Zephyr + MCUboot (Apache-2.0), OpenThread (BSD-3-Clause)
# (copied from the NCS workspace into ${S}/licenses by do_unpack)
LICENSE = "Nordic-5-Clause & Apache-2.0 & BSD-3-Clause"
NO_GENERIC_LICENSE[Nordic-5-Clause] = "licenses/nrf.LICENSE"
LIC_FILES_CHKSUM = " \
    file://licenses/nrf.LICENSE;md5=245f42254fe833f001b81f6f6d677151 \
    file://licenses/zephyr.LICENSE;md5=fa818a259cbed7ce8bc2a22d35a464fc \
    file://licenses/mcuboot.LICENSE;md5=b6ee33f1d12a5e6ee3de1e82fb51eeb8 \
    file://licenses/openthread.LICENSE;md5=543b6fe90ec5901a683320a36390c65f \
"

# ---------------------------------------------------------------------------
# Sources
#
# - firmware/nRF5340/app of this repository (west manifest, configs, overlays)
# - nRF Connect SDK: west workspace from app/west.yml, limited to the projects
#   the RCP build needs (NRF5340_NCS_PROJECTS), shallow clones. Fetched once
#   into DL_DIR by do_fetch_ncs (network) together with a Python venv for
#   west/imgtool, reused by later builds.
# - Zephyr SDK: minimal package + ARM toolchain only (regular fetcher)
# ---------------------------------------------------------------------------

# @USER_CONFIG: nRF5340 firmware sources (west manifest app/west.yml, configs, overlays)
NRF5340_FW_SRC ?= "${TOPDIR}/../../firmware/nRF5340"
# @USER_CONFIG: MCUboot signing key of the nRF5340 (Ed25519, see os/keys/README.md)
NRF5340_DFU_KEY ?= "${GATEWAY_KEYS_DIR}/nrf5340/dfu_signing_key.pem"

# Firmware variant: "dk" = nRF5340-DK (Dahlia dev setup, plus the app-core
# companion image), "gateway" = module on the IoT-Gateway mainboard
NRF5340_TARGET ?= "gateway"
NRF5340_TARGET:board-dahlia ?= "dk"
NRF5340_BOARD ?= "nrf5340dk/nrf5340/cpunet"

# UART of MCUboot's serial recovery (same UART as Spinel, same settings as
# app/boards/<target>.overlay)
NRF5340_DFU_DEVICE ?= "/dev/thread"
NRF5340_DFU_BAUD ?= "1000000"
NRF5340_DFU_BAUD:board-dahlia ?= "115200"

# nRF Connect SDK projects of the west workspace (west manifest.project-filter).
# sdk-nrf-802154 is not public and not needed (802.15.4 driver from hal_nordic).
NRF5340_NCS_PROJECTS ?= "sdk-nrf zephyr mcuboot nrfxlib hal_nordic cmsis openthread mbedtls oberon-psa-crypto zcbor tinycrypt segger picolibc"
# Python packages for west, Zephyr's build scripts and MCUboot's imgtool
# (zephyr/scripts/requirements-base.txt, nrf/scripts/requirements-build.txt)
NRF5340_PYTHON_REQUIREMENTS ?= "west pyelftools PyYAML pykwalify packaging progress psutil pyserial requests anytree intelhex cbor2 ecdsa cryptography>=42.0.4 imagesize zcbor~=0.8.0 nrf-regtool~=5.3.2 click jsonschema"
NRF5340_NCS_CACHE ?= "${DL_DIR}/nrf5340-ncs"

ZEPHYR_SDK_VERSION = "0.16.5-1"
ZEPHYR_SDK_URL = "https://github.com/zephyrproject-rtos/sdk-ng/releases/download/v${ZEPHYR_SDK_VERSION}"
SRC_URI = " \
    ${ZEPHYR_SDK_URL}/zephyr-sdk-${ZEPHYR_SDK_VERSION}_linux-x86_64_minimal.tar.xz;name=sdk;subdir=sdk \
    ${ZEPHYR_SDK_URL}/toolchain_linux-x86_64_arm-zephyr-eabi.tar.xz;name=arm;subdir=sdk/zephyr-sdk-${ZEPHYR_SDK_VERSION};downloadfilename=zephyr-sdk-${ZEPHYR_SDK_VERSION}_toolchain_linux-x86_64_arm-zephyr-eabi.tar.xz \
    file://flash-nrf5340 \
"
SRC_URI[sdk.sha256sum] = "f23836a1d35800ae7d98073f8f7c79166deeed753b5af927ef778764592d9675"
SRC_URI[arm.sha256sum] = "d6455a2fd4e0bc03091f255fb8f21853f76dd2a19f7d57cd038ead594d9d76e8"

S = "${WORKDIR}"
B = "${WORKDIR}/build"

# The Zephyr SDK toolchain in SRC_URI is the x86_64 Linux build
python __anonymous () {
    if d.getVar('BUILD_ARCH') != 'x86_64':
        raise bb.parse.SkipRecipe("nrf5340-rcp-firmware needs an x86_64 build host (Zephyr SDK toolchain)")
}
DEPENDS = "cmake-native ninja-native dtc-native"
RDEPENDS:${PN} = "mcumgr libgpiod-tools"

inherit deploy

# Cache directory of the NCS workspace + venv: one per manifest, project list
# and Python requirements
def nrf5340_ncs_key(d):
    import hashlib, os
    manifest = os.path.join(d.getVar('NRF5340_FW_SRC'), 'app', 'west.yml')
    data = open(manifest).read() if os.path.exists(manifest) else ''
    data += '\n' + d.getVar('NRF5340_NCS_PROJECTS') + '\n' + d.getVar('NRF5340_PYTHON_REQUIREMENTS')
    return hashlib.sha256(data.encode()).hexdigest()[:16]

NRF5340_NCS_DIR = "${NRF5340_NCS_CACHE}/${@nrf5340_ncs_key(d)}"

python do_fetch_ncs() {
    import os, shutil, subprocess

    src = d.getVar('NRF5340_FW_SRC')
    ncs = d.getVar('NRF5340_NCS_DIR')
    ws = os.path.join(ncs, 'ws')
    venv = os.path.join(ncs, 'venv')
    if not os.path.exists(os.path.join(src, 'app', 'west.yml')):
        bb.fatal("nRF5340 firmware sources not found: %s (NRF5340_FW_SRC)" % src)

    env = dict(bb.fetch2.get_fetcher_environment(d))
    env.update({
        'HOME': os.path.join(ncs, 'home'),
        'PATH': os.path.join(venv, 'bin') + ':' + d.getVar('HOSTTOOLS_DIR'),
        'PIP_NO_CACHE_DIR': '1',
        'PIP_DISABLE_PIP_VERSION_CHECK': '1',
        'GIT_TERMINAL_PROMPT': '0',
    })

    def run(cmd, cwd=None):
        bb.note("nrf5340-ncs: " + " ".join(cmd))
        r = subprocess.run(cmd, cwd=cwd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if r.returncode != 0:
            bb.fatal("%s failed:\n%s" % (" ".join(cmd), r.stdout[-4000:]))

    bb.utils.mkdirhier(d.getVar('NRF5340_NCS_CACHE'))
    lf = bb.utils.lockfile(ncs + '.lock')
    try:
        if os.path.exists(os.path.join(ncs, '.done')):
            bb.note("nrf5340-ncs: using %s" % ncs)
            return
        if os.path.exists(ncs):
            shutil.rmtree(ncs)
        bb.utils.mkdirhier(os.path.join(ws, 'app'))
        bb.utils.mkdirhier(env['HOME'])
        shutil.copy(os.path.join(src, 'app', 'west.yml'), os.path.join(ws, 'app', 'west.yml'))

        run(['python3', '-m', 'venv', venv])
        run([os.path.join(venv, 'bin', 'pip'), 'install', '--upgrade', 'pip'])
        run([os.path.join(venv, 'bin', 'pip'), 'install'] + d.getVar('NRF5340_PYTHON_REQUIREMENTS').split())

        flt = '-.*,' + ','.join('+' + p for p in d.getVar('NRF5340_NCS_PROJECTS').split())
        run(['west', 'init', '-l', 'app'], cwd=ws)
        run(['west', 'config', 'manifest.project-filter', '--', flt], cwd=ws)
        run(['west', 'update', '--narrow', '-o=--depth=1'], cwd=ws)

        open(os.path.join(ncs, '.done'), 'w').close()
    finally:
        bb.utils.unlockfile(lf)
}
do_fetch_ncs[network] = "1"
do_fetch_ncs[vardeps] += "NRF5340_NCS_PROJECTS NRF5340_PYTHON_REQUIREMENTS"
do_fetch_ncs[file-checksums] += "${NRF5340_FW_SRC}/app/west.yml:True"
addtask fetch_ncs after do_fetch before do_unpack

nrf5340_copy_licenses() {
    ws=${NRF5340_NCS_DIR}/ws
    mkdir -p ${S}/licenses
    cp ${ws}/nrf/LICENSE ${S}/licenses/nrf.LICENSE
    cp ${ws}/zephyr/LICENSE ${S}/licenses/zephyr.LICENSE
    cp ${ws}/bootloader/mcuboot/LICENSE ${S}/licenses/mcuboot.LICENSE
    cp ${ws}/modules/lib/openthread/LICENSE ${S}/licenses/openthread.LICENSE
}
do_unpack[postfuncs] += "nrf5340_copy_licenses"

do_configure() {
    # App sources from the repository (configs, overlays, heartbeat module)
    rm -rf ${WORKDIR}/app
    cp -r ${NRF5340_FW_SRC}/app ${WORKDIR}/app
    rm -f ${WORKDIR}/app/dfu_signing_key.pem
}
do_configure[file-checksums] += "${NRF5340_FW_SRC}/app:True"

do_compile() {
    ncs=${NRF5340_NCS_DIR}
    [ -e "${ncs}/.done" ] || bbfatal "nRF Connect SDK cache ${ncs} missing, run: bitbake ${PN} -c fetch_ncs -f"
    [ -f "${NRF5340_DFU_KEY}" ] || bbfatal "MCUboot signing key ${NRF5340_DFU_KEY} not found (NRF5340_DFU_KEY, os/keys/README.md)"

    app=${WORKDIR}/app
    case "${NRF5340_TARGET}" in
        dk)      ov=nrf5340dk_nrf5340_cpunet ;;
        gateway) ov=gateway ;;
        *)       bbfatal "NRF5340_TARGET must be dk or gateway" ;;
    esac

    # Clean environment: no OE cross compiler settings, private HOME (the NCS
    # build would otherwise pick up an NCS toolchain registered in the user's
    # ~/.cmake/packages), Zephyr SDK from SRC_URI.
    run_west() {
        (cd ${ncs}/ws && env -i \
            HOME=${WORKDIR}/home \
            PATH=${ncs}/venv/bin:${STAGING_BINDIR_NATIVE}:${HOSTTOOLS_DIR} \
            ZEPHYR_SDK_INSTALL_DIR=${WORKDIR}/sdk/zephyr-sdk-${ZEPHYR_SDK_VERSION} \
            ZEPHYR_TOOLCHAIN_VARIANT=zephyr \
            west "$@")
    }
    mkdir -p ${WORKDIR}/home

    run_west build -b ${NRF5340_BOARD} -d ${B}/netcore -s nrf/samples/openthread/coprocessor --sysbuild -p always -- \
        -DEXTRA_CONF_FILE="${app}/prj.conf ${app}/boards/${ov}.conf" \
        -DEXTRA_DTC_OVERLAY_FILE="${app}/boards/${ov}.overlay ${app}/boards/partitions.overlay" \
        -DZEPHYR_EXTRA_MODULES=${app}/heartbeat_led \
        -DSB_CONF_FILE=${app}/sysbuild.conf \
        -DSB_CONFIG_BOOT_SIGNATURE_KEY_FILE=\"${NRF5340_DFU_KEY}\" \
        -Dmcuboot_EXTRA_CONF_FILE=${app}/mcuboot.conf \
        -Dmcuboot_EXTRA_DTC_OVERLAY_FILE="${app}/boards/${ov}.overlay ${app}/boards/mcuboot-${ov}.overlay ${app}/boards/partitions.overlay"

    if [ "${NRF5340_TARGET}" = "dk" ]; then
        # App core of the DK: releases the network core and forwards its pins
        run_west build -b nrf5340dk/nrf5340/cpuapp -d ${B}/appcore -s nrf/samples/nrf5340/empty_app_core -p always -- \
            -DEXTRA_DTC_OVERLAY_FILE=${app}/boards/empty_app_core-nrf5340dk.overlay
    fi
}
do_compile[file-checksums] += "${NRF5340_DFU_KEY}:True"

NRF5340_FW_DIR = "${datadir}/nrf5340-rcp"

do_install() {
    install -d ${D}${NRF5340_FW_DIR}
    # Signed RCP image for MCUboot serial recovery (flash-nrf5340, mcumgr)
    install -m 0644 ${B}/netcore/coprocessor/zephyr/zephyr.signed.bin ${D}${NRF5340_FW_DIR}/rcp.signed.bin
    # MCUboot + RCP for a full flash over SWD (first flash, new key)
    install -m 0644 ${B}/netcore/merged.hex ${D}${NRF5340_FW_DIR}/netcore-merged.hex
    if [ "${NRF5340_TARGET}" = "dk" ]; then
        install -m 0644 ${B}/appcore/merged.hex ${D}${NRF5340_FW_DIR}/appcore-merged.hex
    fi

    install -d ${D}${bindir}
    sed -e 's|@NRF5340_FW_DIR@|${NRF5340_FW_DIR}|g' \
        -e 's|@NRF5340_DFU_DEVICE@|${NRF5340_DFU_DEVICE}|g' \
        -e 's|@NRF5340_DFU_BAUD@|${NRF5340_DFU_BAUD}|g' \
        -e 's|@GATEWAY_BOARD@|${GATEWAY_BOARD}|g' \
        ${WORKDIR}/flash-nrf5340 > ${D}${bindir}/flash-nrf5340
    chmod 0755 ${D}${bindir}/flash-nrf5340
}

# Also next to the images, for a full flash over SWD from a PC
do_deploy() {
    install -d ${DEPLOYDIR}/nrf5340-rcp
    cp ${D}${NRF5340_FW_DIR}/* ${DEPLOYDIR}/nrf5340-rcp/
}
addtask deploy after do_install before do_build

FILES:${PN} = "${NRF5340_FW_DIR} ${bindir}/flash-nrf5340"
# Firmware images for another CPU, not for the target's architecture
INSANE_SKIP:${PN} += "arch"
PACKAGE_ARCH = "${MACHINE_ARCH}"

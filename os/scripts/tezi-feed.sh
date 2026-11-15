#!/bin/bash
# Serves the latest GatewayOS Easy Installer image as a network feed for the
# Toradex Easy Installer (Tezi), or copies it to a USB stick.
#
#   tezi-feed.sh serve [URL]   unpack + write image_list.json + HTTP server
#   tezi-feed.sh usb <dir>     unpack the image onto a mounted USB stick / SD card
#   tezi-feed.sh tezi-emmc <dir> [fdtfile]
#                              files to start the Easy Installer from the eMMC
#                              BOOT partition of a running gateway (no USB
#                              recovery), see layers/meta-iot-gateway-os/docs/tezi-feed-raspberry-pi.md
#
# Feed: in the Easy Installer open "Feeds", add <URL>/image_list.json
# (default URL http://192.168.0.1:8080, TEZI_FEED_URL / TEZI_FEED_PORT).
# The Easy Installer needs network access via DHCP on its Ethernet port.
set -euo pipefail

OS_DIR=$(cd "$(dirname "$0")/.." && pwd)
MACHINE=${MACHINE:-verdin-imx8mm}
DEPLOY=${OS_DIR}/build/deploy/images/${MACHINE}
FEED_DIR=${OS_DIR}/export/tezi-feed
PORT=${TEZI_FEED_PORT:-8080}

# Newest Easy Installer tarball of gateway-os-image
tarball=$(ls -t "${DEPLOY}"/gateway-os-image-*-Tezi_*.tar 2>/dev/null | sed -n 1p || true)
if [ -z "${tarball}" ]; then
    echo "No Easy Installer image in ${DEPLOY} - run 'bitbake gateway-os-image' first" >&2
    exit 1
fi
# Folder inside the tarball = its file name (image_type_tezi.bbclass)
name=$(basename "${tarball}" .tar)

unpack() {
    local dest=$1
    rm -rf "${dest:?}/${name}"
    mkdir -p "${dest}"
    # --no-same-owner: FAT sticks have no owners
    tar -xf "${tarball}" -C "${dest}" --no-same-owner
    echo "Unpacked ${name} to ${dest}"
}

# Easy Installer from the eMMC BOOT partition: boot.scr of the Toradex Easy
# Installer package with fdtfile set to a Toradex carrier board (the gateway
# U-Boot uses fdtfile=iot-gateway-imx8mm.dtb, which tezi.itb does not know).
tezi_emmc() {
    local dest=$1 fdt=${2:-imx8mm-verdin-wifi-dahlia.dtb}
    local pkg=${TEZI_PKG:-$(ls -d "${OS_DIR}"/Verdin-iMX8MM_ToradexEasyInstaller_*/ 2>/dev/null | sed -n 1p)}
    local tools=${OS_DIR}/build/tmp/sysroots-components/x86_64/u-boot-tools-native/usr/bin
    [ -f "${pkg}/tezi.itb" ] || { echo "Toradex Easy Installer package not found (TEZI_PKG)" >&2; exit 1; }
    [ -x "${tools}/mkimage" ] || { echo "mkimage not found in ${tools} (build u-boot-tools-native)" >&2; exit 1; }
    mkdir -p "${dest}"
    {
        echo "# GatewayOS: Easy Installer from the eMMC BOOT partition"
        echo "env set fdtfile ${fdt}"
        # Script payload: skip the 64 byte image header and the 8 byte length table
        tail -c +73 "${pkg}/boot.scr" | tr -d '\000'
    } > "${dest}/boot.cmd"
    "${tools}/mkimage" -A arm64 -T script -C none -n "Tezi from eMMC" -d "${dest}/boot.cmd" "${dest}/boot.scr" >/dev/null
    rm -f "${dest}/boot.cmd"
    cp "${pkg}/tezi.itb" "${pkg}/overlays.txt" "${dest}/"
    echo "Easy Installer files for the BOOT partition (fdtfile ${fdt}) in ${dest}:"
    ls -la "${dest}"
}

case "${1:-serve}" in
serve)
    url=${2:-${TEZI_FEED_URL:-http://192.168.0.1:${PORT}}}
    rm -rf "${FEED_DIR}"
    unpack "${FEED_DIR}"
    cat > "${FEED_DIR}/image_list.json" <<EOF
{
    "config_format": 1,
    "images": [
        "${url}/${name}/image.json"
    ]
}
EOF
    echo
    echo "Feed:   ${url}/image_list.json  (Easy Installer: Feeds -> add this URL)"
    echo "Server: 0.0.0.0:${PORT}, Ctrl+C to stop"
    exec python3 -m http.server "${PORT}" --bind 0.0.0.0 --directory "${FEED_DIR}"
    ;;
usb)
    dest=${2:?usage: $0 usb <mount point of the USB stick>}
    [ -d "${dest}" ] || { echo "${dest} is not a directory" >&2; exit 1; }
    unpack "${dest}"
    sync
    echo "Done. Easy Installer: plug in the stick, the image shows up in the list."
    ;;
tezi-emmc)
    tezi_emmc "${2:?usage: $0 tezi-emmc <dir> [fdtfile]}" "${3:-}"
    ;;
*)
    echo "usage: $0 serve [URL] | usb <dir> | tezi-emmc <dir> [fdtfile]" >&2
    exit 1
    ;;
esac

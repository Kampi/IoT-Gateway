# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

SUMMARY = "GatewayOS Production Image"
LICENSE = "MIT"

inherit core-image

KERNEL_DEVICETREE = "freescale/iot-gateway-imx8mm.dtb"

TEZI_EXTERNAL_KERNEL_DEVICETREE = ""
TEZI_EXTERNAL_KERNEL_DEVICETREE_BOOT = ""

# ---------------------------------------------------------------------------
# A/B updates with RAUC (see README "Updates")
#
# eMMC layout (Toradex Easy Installer, gateway_tezi_ab_layout below):
#   mmcblk0p1  BOOT   FAT, only the U-Boot boot script (slot selection)
#   mmcblk0p2  RFS_A  rootfs slot A, GATEWAY_ROOTFS_SLOT_SIZE MiB
#   mmcblk0p3  RFS_B  rootfs slot B, GATEWAY_ROOTFS_SLOT_SIZE MiB
#   mmcblk0p4  DATA   rest of the eMMC, /data (overlays for /etc, /var/lib, /home)
# Each slot boots its own kernel and devicetree from /boot, so a RAUC bundle
# (gateway-os-bundle) updates kernel and rootfs together.
# ---------------------------------------------------------------------------

# BOOT only holds boot.scr (machine config: IMAGE_BOOT_FILES:append), no
# kernel, devicetree or overlays.
IMAGE_BOOT_FILES = ""

# ext4 image of the rootfs for the RAUC bundle. Its size must be a multiple of
# 4 KiB for adaptive updates (block-hash-index), IMAGE_ROOTFS_ALIGNMENT is in KiB.
IMAGE_FSTYPES:append = " ext4"
IMAGE_ROOTFS_ALIGNMENT = "4"

IMAGE_FEATURES += " \
    ssh-server-openssh \
    overlayfs-etc \
    read-only-rootfs \
"

# Read-only rootfs: everything the gateway writes goes to DATA (overlays of
# /etc, /var/lib, /home, /root) or to tmpfs (/run, /tmp, /var/volatile). A
# power loss cannot damage the system, every slot is exactly its image.
#
# /sbin/preinit (kernel argument init=/sbin/preinit, see boot.cmd.in) mounts
# DATA on /data and overlays /etc, /var/lib, /home and /root before systemd
# starts. It must not remount / read-write: /data and the overlay targets
# exist in the image (gateway_data_mountpoint).
OVERLAYFS_ETC_MOUNT_POINT = "/data"
OVERLAYFS_ETC_DEVICE = "/dev/mmcblk0p4"
OVERLAYFS_ETC_FSTYPE = "ext4"
OVERLAYFS_ETC_MOUNT_OPTIONS = "defaults,noatime"
# Keep /sbin/init (and allow package-management): preinit is a separate file
OVERLAYFS_ETC_USE_ORIG_INIT_NAME = "0"
OVERLAYFS_ETC_INIT_TEMPLATE = "${THISDIR}/files/gateway-preinit.sh.in"
OVERLAYFS_ETC_CREATE_MOUNT_DIRS = "0"

gateway_data_mountpoint() {
    install -d ${IMAGE_ROOTFS}/data ${IMAGE_ROOTFS}/home ${IMAGE_ROOTFS}${ROOT_HOME}
}
ROOTFS_POSTPROCESS_COMMAND += "gateway_data_mountpoint"
do_rootfs[file-checksums] += "${THISDIR}/files/gateway-preinit.sh.in:True"

# Kernel and devicetree in the rootfs (/boot), loaded by U-Boot from the slot.
# The kernel package installs /boot/Image.gz as a symlink to the versioned
# file; replace it by the file itself, so U-Boot does not depend on ext4
# symlink support.
gateway_boot_files() {
    kernel=${IMAGE_ROOTFS}/boot/${KERNEL_IMAGETYPE}
    if [ -L "${kernel}" ]; then
        target=$(readlink "${kernel}")
        mv -f "${IMAGE_ROOTFS}/boot/$(basename "${target}")" "${kernel}"
    fi
    [ -f "${kernel}" ] || bbfatal "Kernel ${KERNEL_IMAGETYPE} missing in /boot of the rootfs"
    [ -f "${IMAGE_ROOTFS}/boot/iot-gateway-imx8mm.dtb" ] || bbfatal "iot-gateway-imx8mm.dtb missing in /boot of the rootfs"
}
ROOTFS_POSTPROCESS_COMMAND += "gateway_boot_files"

# Rewrites the eMMC layout of the Easy Installer image (image.json, written by
# rootfs_tezi_run_json of image_type_tezi.bbclass): BOOT, two rootfs slots
# with the same content (both bootable) and a DATA partition.
python gateway_tezi_ab_layout() {
    import copy, json, os

    json_file = os.path.join(d.getVar('IMGDEPLOYDIR'), "image-%s.json" % d.getVar('IMAGE_BASENAME'))
    slot_size = int(d.getVar('GATEWAY_ROOTFS_SLOT_SIZE'))

    with open(json_file) as f:
        data = json.load(f)

    emmc = next(b for b in data["blockdevs"] if b["name"] == d.getVar('EMMCDEV'))
    boot = [p for p in emmc["partitions"] if p["content"].get("label") == "BOOT"]
    rootfs = [p for p in emmc["partitions"] if p["content"].get("label") == d.getVar('TEZI_ROOT_LABEL')]
    if len(boot) != 1 or len(rootfs) != 1:
        bb.fatal("Unexpected Easy Installer partition layout in %s" % json_file)

    used = float(rootfs[0]["content"]["uncompressed_size"])
    if used > slot_size * 0.8:
        bb.fatal("rootfs (%d MiB) does not fit into GATEWAY_ROOTFS_SLOT_SIZE (%d MiB) with 20 %% headroom" % (used, slot_size))

    slots = []
    for name in ("A", "B"):
        slot = copy.deepcopy(rootfs[0])
        slot["partition_size_nominal"] = slot_size
        slot["want_maximised"] = False
        slot["content"]["label"] = "RFS_" + name
        slots.append(slot)

    datapart = {
        "partition_size_nominal": 512,
        "want_maximised": True,
        "content": {
            "label": "DATA",
            "filesystem_type": "ext4",
            "mkfs_options": "-E nodiscard"
        }
    }

    # Partition numbers are fixed: rauc system.conf, boot.cmd.in, OVERLAYFS_ETC_DEVICE
    emmc["partitions"] = boot + slots + [datapart]

    with open(json_file, "w") as f:
        json.dump(data, f, indent=4)
    bb.note("Easy Installer layout: BOOT, RFS_A, RFS_B (%d MiB each), DATA" % slot_size)
}
gateway_tezi_ab_layout[vardeps] += "GATEWAY_ROOTFS_SLOT_SIZE"
TEZI_IMAGE_TEZIIMG_PREFUNCS = "rootfs_tezi_run_json gateway_tezi_ab_layout"

# Custom services
IMAGE_INSTALL:append = " \
    thread-network-init \
    wifi-captive-portal \
    gateway-logging \
    gateway-ready-led \
    gateway-backup \
    gateway-telemetry \
    gateway-id \
    nrf5340-rcp-firmware \
    gateway-power-monitor \
    gateway-can \
    zram \
"

# Diagnostics: CAN, NVMe SSD, eMMC health, hardware sensors
IMAGE_INSTALL:append = " \
    can-utils \
    nvme-cli \
    smartmontools \
    mmc-utils \
    lmsensors-sensors \
"

# A/B updates
IMAGE_INSTALL:append = " \
    rauc \
    rauc-mark-good \
    gateway-health-check \
    e2fsprogs-resize2fs \
    libubootenv-bin \
    kernel-image \
    kernel-devicetree \
"

IMAGE_INSTALL:append = " \
    htop \
    curl \
    tmux \
    avahi-daemon \
    mosquitto-clients \
    ot-br-posix \
    wmbusmeters \
    zigbee2mqtt \
    theengs-gateway \
    lora-packet-forwarder \
    chirpstack-gateway-bridge \
    cryptoauthlib \
    networkmanager \
    networkmanager-nmcli \
    iw \
    linux-firmware \
    bluez5 \
    libgpiod \
    libgpiod-tools \
    i2c-tools \
    nano \
    mcumgr \
"

# Python packages
IMAGE_INSTALL:append = " \
    python3 \
    python3-requests \
    python3-paho-mqtt \
"

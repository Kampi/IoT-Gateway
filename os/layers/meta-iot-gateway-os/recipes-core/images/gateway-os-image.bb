SUMMARY = "GatewayOS Production Image"
LICENSE = "MIT"

inherit core-image

KERNEL_DEVICETREE = "freescale/iot-gateway-imx8mm.dtb"

TEZI_EXTERNAL_KERNEL_DEVICETREE = ""
TEZI_EXTERNAL_KERNEL_DEVICETREE_BOOT = ""

# TODO: We need to remove the loading of "overlays.txt" in the boot.scr file
#IMAGE_BOOT_FILES:remove = "overlays/*;overlays/"
#IMAGE_BOOT_FILES:remove = "overlays.txt"

IMAGE_FEATURES += " \
    ssh-server-openssh \
"

IMAGE_INSTALL:append = " \
    htop \
    curl \
    tmux \
    avahi-daemon \
    mosquitto \
    mosquitto-clients \
    ot-br-posix \
    thread-network-init \
    wmbusmeters \
    zigbee2mqtt \
    lora-packet-forwarder \
    chirpstack-gateway-bridge \
    cryptoauthlib \
    gateway-logging \
    gateway-ready-led \
    gateway-backup \
    networkmanager \
    networkmanager-nmcli \
    wifi-captive-portal \
    iw \
    linux-firmware \
    bluez5 \
    libgpiod \
    libgpiod-tools \
    i2c-tools \
    nano \
    mcumgr \
"
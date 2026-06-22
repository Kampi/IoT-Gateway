SUMMARY = "GatewayOS Production Image"
LICENSE = "MIT"

inherit core-image

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
    wmbusmeters \
    zigbee2mqtt \
    lora-packet-forwarder \
    chirpstack-gateway-bridge \
    cryptoauthlib \
    gateway-logging \
    wpa-supplicant \
    iw \
    linux-firmware \
    bluez5 \
    wifi-captive-portal \
"

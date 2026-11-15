# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

SUMMARY = "GatewayOS RAUC update bundle"
DESCRIPTION = "Signed RAUC bundle with the rootfs of gateway-os-image (kernel \
and devicetree included). Install on the gateway with \
'rauc install <bundle>.raucb', it is written to the inactive slot."
LICENSE = "MIT"

inherit bundle

RAUC_BUNDLE_COMPATIBLE = "iot-gateway-${GATEWAY_BOARD}"
RAUC_BUNDLE_VERSION = "${DISTRO_VERSION}"
RAUC_BUNDLE_DESCRIPTION = "GatewayOS ${DISTRO_VERSION} (${GATEWAY_BOARD})"
RAUC_BUNDLE_FORMAT = "verity"

RAUC_BUNDLE_SLOTS = "rootfs"
RAUC_SLOT_rootfs = "gateway-os-image"
RAUC_SLOT_rootfs[fstype] = "ext4"
# Adaptive updates (streaming): the gateway only downloads the 4 KiB blocks of
# the image that are not already in one of its slots (hash index per slot in
# /data/rauc). The first update into a slot still downloads everything.
RAUC_SLOT_rootfs[adaptive] = "block-hash-index"

# Signing key and certificate, issued by the CA in the keyring
# (RAUC_KEYRING_FILE_PATH), from GATEWAY_KEYS_DIR (os/keys/README.md).
# @USER_CONFIG: Private key that signs the RAUC bundles
RAUC_KEY_FILE ?= "${GATEWAY_KEYS_DIR}/rauc/development-1.key.pem"
# @USER_CONFIG: Certificate of the RAUC bundle signing key
RAUC_CERT_FILE ?= "${GATEWAY_KEYS_DIR}/rauc/development-1.cert.pem"

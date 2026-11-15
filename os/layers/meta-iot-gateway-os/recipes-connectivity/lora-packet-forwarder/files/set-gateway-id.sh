#!/bin/sh
# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

# Writes the packet forwarder configuration to /run/lora-packet-forwarder/
# (the rootfs is read-only): the template from the image with the LoRa
# gateway EUI-64 derived from the Ethernet MAC address.
# EUI-64 format: first 3 MAC bytes + FFFE + last 3 MAC bytes.

IFACE="end0"
TEMPLATE="/opt/lora-packet-forwarder/config/global_conf.json"
RUNDIR="/run/lora-packet-forwarder"
CONFIG="${RUNDIR}/global_conf.json"

mkdir -p "${RUNDIR}"
cp "${TEMPLATE}" "${CONFIG}.tmp"

MAC=$(cat /sys/class/net/${IFACE}/address 2>/dev/null)
if [ -z "$MAC" ]; then
    echo "set-gateway-id: interface ${IFACE} not found, keeping default gateway_ID" >&2
else
    # Remove colons and uppercase
    HEX=$(echo "$MAC" | tr -d ':' | tr 'a-f' 'A-F')
    # Build EUI-64: insert FFFE between OUI (3 bytes) and NIC (3 bytes)
    EUI64="${HEX%??????}FFFE${HEX#??????}"
    sed -i "s/\"gateway_ID\": \"[^\"]*\"/\"gateway_ID\": \"${EUI64}\"/" "${CONFIG}.tmp"
    echo "set-gateway-id: gateway_ID set to ${EUI64} (from ${IFACE} MAC ${MAC})"
fi

mv "${CONFIG}.tmp" "${CONFIG}"

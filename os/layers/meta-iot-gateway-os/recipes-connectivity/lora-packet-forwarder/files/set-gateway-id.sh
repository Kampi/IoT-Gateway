#!/bin/sh
# Derives the LoRa gateway EUI-64 ID from the Ethernet MAC address and writes
# it into global_conf.json before the packet forwarder starts.
# EUI-64 format: first 3 MAC bytes + FFFE + last 3 MAC bytes.

IFACE="end0"
CONFIG="/opt/lora-packet-forwarder/config/global_conf.json"

MAC=$(cat /sys/class/net/${IFACE}/address 2>/dev/null)
if [ -z "$MAC" ]; then
    echo "set-gateway-id: interface ${IFACE} not found, keeping default gateway_ID" >&2
    exit 0
fi

# Remove colons and uppercase
HEX=$(echo "$MAC" | tr -d ':' | tr 'a-f' 'A-F')

# Build EUI-64: insert FFFE between OUI (3 bytes) and NIC (3 bytes)
EUI64="${HEX%??????}FFFE${HEX#??????}"

sed -i "s/\"gateway_ID\": \"[^\"]*\"/\"gateway_ID\": \"${EUI64}\"/" "$CONFIG"
echo "set-gateway-id: gateway_ID set to ${EUI64} (from ${IFACE} MAC ${MAC})"

#!/bin/sh
set -e

CONF=/etc/default/gateway-backup
[ -r "${CONF}" ] && . "${CONF}"

: "${GATEWAY_BACKUP_REMOTE:?set GATEWAY_BACKUP_REMOTE in /etc/default/gateway-backup, e.g. user@host:/path/}"

KEY_DIR=/etc/gateway-backup
SSH_KEY=${KEY_DIR}/id_ed25519
KNOWN_HOSTS=${KEY_DIR}/known_hosts
STAGE_ROOT=/var/lib/gateway-backup
STAMP=$(date +%Y%m%d-%H%M%S)
STAGE=${STAGE_ROOT}/${STAMP}

mkdir -p "${STAGE}"

# Zigbee coordinator DB + network backup - without this every Zigbee device
# needs re-pairing after a re-flash/replacement.
[ -d /var/lib/zigbee2mqtt ] && cp -a /var/lib/zigbee2mqtt "${STAGE}/zigbee2mqtt"

# OpenThread dataset (network key/PAN ID/channel - what the Onvis plug and
# any other Thread device is joined to). Path is
# OPENTHREAD_CONFIG_POSIX_SETTINGS_PATH="tmp" resolved against otbr-agent's
# working directory (unset -> "/"), see thread-network-init for the same
# assumption. Glob because the filename embeds the RCP's EUI64.
for f in /tmp/0_*.data /tmp/0_*.swap; do
    [ -e "${f}" ] && cp -a "${f}" "${STAGE}/"
done

tar -C "${STAGE_ROOT}" -czf "${STAGE}.tar.gz" "${STAMP}"
rm -rf "${STAGE}"

rsync -a \
    -e "ssh -i ${SSH_KEY} -o UserKnownHostsFile=${KNOWN_HOSTS} -o StrictHostKeyChecking=accept-new" \
    "${STAGE}.tar.gz" "${GATEWAY_BACKUP_REMOTE}"

rm -f "${STAGE}.tar.gz"

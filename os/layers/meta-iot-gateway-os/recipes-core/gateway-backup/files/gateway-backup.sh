#!/bin/sh
set -e

CONF=/etc/default/gateway-backup
[ -r "${CONF}" ] && . "${CONF}"

# No destination configured (e.g. Dahlia dev setup): nothing to do. Not an
# error, otherwise the unit shows up as failed every day.
if [ -z "${GATEWAY_BACKUP_REMOTE}" ]; then
    echo "GATEWAY_BACKUP_REMOTE not set in ${CONF} - skipping backup"
    exit 0
fi

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
# any other Thread device is joined to): otbr-agent's settings in
# /var/lib/thread (filename embeds the RCP's EUI64) plus the plain dataset
# copy kept by thread-network-init.sh (restore: ot-ctl dataset set active).
mkdir -p "${STAGE}/thread"
for f in /var/lib/thread/*.data /var/lib/gateway-os/thread-dataset.hex; do
    [ -e "${f}" ] && cp -a "${f}" "${STAGE}/thread/"
done

tar -C "${STAGE_ROOT}" -czf "${STAGE}.tar.gz" "${STAMP}"
rm -rf "${STAGE}"

rsync -a \
    -e "ssh -i ${SSH_KEY} -o UserKnownHostsFile=${KNOWN_HOSTS} -o StrictHostKeyChecking=accept-new" \
    "${STAGE}.tar.gz" "${GATEWAY_BACKUP_REMOTE}"

rm -f "${STAGE}.tar.gz"

#!/bin/sh
# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

# Daily off-device backup of everything the gateway stores persistently and
# rsync to GATEWAY_BACKUP_REMOTE over SSH.
#
# With the A/B layout (RAUC) all persistent data are the changes in the
# overlays of /etc, /var/lib, /home and /root on the DATA partition
# (/data/overlay-<dir>/upper, see gateway-preinit.sh.in): Wi-Fi and network
# settings, SSH host keys, machine-id, Zigbee2MQTT (coordinator DB/backup),
# Thread dataset, this backup key, ... The archive contains these upper
# directories, so a restore (/data/restore.tar.gz, applied by /sbin/preinit at
# the next boot) brings the gateway back to exactly this state - also on a
# replacement gateway.
#
# Archive: <GATEWAY_ID>-<timestamp>.tar.gz, e.g. iot-gateway-07219303-20261113-000030.tar.gz
set -e

CONF=/etc/default/gateway-backup
[ -r "${CONF}" ] && . "${CONF}"

# No destination configured: nothing to do. Not an error, otherwise the unit
# shows up as failed every day.
if [ -z "${GATEWAY_BACKUP_REMOTE}" ]; then
    echo "GATEWAY_BACKUP_REMOTE not set in ${CONF} - skipping backup"
    exit 0
fi

[ -r /run/gateway-id.env ] && . /run/gateway-id.env
NAME=${GATEWAY_ID:-$(hostname)}

KEY_DIR=/etc/gateway-backup
SSH_KEY=${KEY_DIR}/id_ed25519
KNOWN_HOSTS=${KEY_DIR}/known_hosts
DATA=/data
STAGE_ROOT=/var/volatile/gateway-backup
ARCHIVE=${STAGE_ROOT}/${NAME}-$(date +%Y%m%d-%H%M%S).tar.gz

mkdir -p "${STAGE_ROOT}"
trap 'rm -f "${ARCHIVE}"' EXIT

dirs=""
for d in overlay-etc overlay-var-lib overlay-home overlay-root; do
    [ -d "${DATA}/${d}/upper" ] && dirs="${dirs} ${d}/upper"
done
if [ -z "${dirs}" ]; then
    echo "No overlays in ${DATA} (old single-rootfs layout?) - nothing to back up" >&2
    exit 1
fi

# GNU tar with xattrs: overlayfs marks deleted files with whiteout device
# nodes and replaced directories with trusted.overlay.* attributes.
# Not needed for a restore: the journal archive (only logs, up to 256 MB) and
# leftovers of the old staging directory.
# shellcheck disable=SC2086
tar -C "${DATA}" -czf "${ARCHIVE}" \
    --xattrs --xattrs-include='trusted.*' --numeric-owner \
    --exclude="overlay-var-lib/upper/journal-archive" \
    --exclude="overlay-var-lib/upper/gateway-backup" \
    ${dirs}

rsync -a \
    -e "ssh -i ${SSH_KEY} -o UserKnownHostsFile=${KNOWN_HOSTS} -o StrictHostKeyChecking=accept-new" \
    "${ARCHIVE}" "${GATEWAY_BACKUP_REMOTE}"

echo "Backup $(basename "${ARCHIVE}") ($(du -k "${ARCHIVE}" | cut -f1) KB) uploaded to ${GATEWAY_BACKUP_REMOTE}"

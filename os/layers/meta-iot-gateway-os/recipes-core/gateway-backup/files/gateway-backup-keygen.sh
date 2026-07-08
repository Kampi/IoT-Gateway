#!/bin/sh
set -e

KEY_DIR=/etc/gateway-backup
KEY=${KEY_DIR}/id_ed25519

# Per-device key, generated once on first boot - never baked into the image,
# so every gateway has its own identity towards the backup host. The public
# half still needs to be added to the backup host's authorized_keys by hand;
# it's printed to the journal below for that purpose.
mkdir -p "${KEY_DIR}"
ssh-keygen -t ed25519 -N "" -C "gateway-backup" -f "${KEY}"
touch "${KEY_DIR}/known_hosts"

echo "gateway-backup: new SSH key generated, add this to the backup host's authorized_keys:"
cat "${KEY}.pub"

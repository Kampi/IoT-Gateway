#!/bin/sh
set -e

# otbr-agent is built with -DOTBR_NO_AUTO_ATTACH=1 (see ot-br-posix_git.bb),
# so it never brings the Thread interface up on its own - every boot needs
# an explicit "ifconfig up" + "thread start", not just the first one.
# Only "dataset init new" (which generates a random network key/PAN ID/
# channel) must happen exactly once, on the very first boot that has no
# active dataset yet - re-running it would rotate the network key and
# knock every already-commissioned Thread device (e.g. the Onvis plug) off
# the mesh.

# otbr-agent's D-Bus control socket isn't necessarily ready the instant the
# process starts.
i=0
while ! ot-ctl state >/dev/null 2>&1 && [ "${i}" -lt 30 ]; do
    sleep 1
    i=$((i + 1))
done

# otbr-agent not reachable (e.g. no response from the RCP): give up for now,
# systemd restarts this script later. Without this check the dataset test
# below would also fail and we'd try to form a new network for nothing.
if ! ot-ctl state >/dev/null 2>&1; then
    echo "otbr-agent not reachable - retrying later" >&2
    exit 1
fi

# Copy of the active dataset, written after every successful start. Used to
# restore the network instead of forming a new one if otbr-agent comes up
# without its dataset (seen right after an otbr-agent restart and after an
# ot-br-posix upgrade).
DATASET_BACKUP=/var/lib/gateway-os/thread-dataset.hex

# ot-ctl exits with 0 even on errors ("Error 23: NotFound" when there is no
# active dataset), so check for an actual hex-encoded dataset instead.
# Newer ot-ctl versions end lines with CRLF - strip the CR, otherwise the
# check never matches and a new network is formed on every start.
active_dataset() {
    ot-ctl dataset active -x 2>/dev/null | tr -d '\r' | grep -E '^[0-9a-fA-F]+$'
}
has_dataset() {
    active_dataset >/dev/null
}

# otbr-agent can answer "state" before it has restored its settings, so give
# it time before concluding that there is no dataset.
i=0
while ! has_dataset && [ "${i}" -lt 20 ]; do
    sleep 1
    i=$((i + 1))
done

if ! has_dataset; then
    if [ -s "${DATASET_BACKUP}" ]; then
        echo "No active Thread dataset - restoring ${DATASET_BACKUP}"
        ot-ctl dataset set active "$(tr -cd '0-9a-fA-F' < "${DATASET_BACKUP}")"
    else
        echo "No active Thread dataset and no backup - forming a new network"
        ot-ctl dataset init new
        ot-ctl dataset commit active
    fi
fi

# Fixed OMR prefix (OTBR_OMR_PREFIX, see thread-network-init_1.0.bb). By
# default the border router picks a random OMR prefix that changes whenever
# the Thread network is recreated, which breaks static IPv6 routes on the LAN
# router (e.g. the FritzBox route Home Assistant needs to reach Thread
# devices). Not persisted by otbr-agent, so it is set on every start.
OMR_PREFIX="@OTBR_OMR_PREFIX@"
if [ -n "${OMR_PREFIX}" ]; then
    ot-ctl br omrconfig custom "${OMR_PREFIX}" med
fi

case "$(ot-ctl state)" in
    *leader*|*router*|*child*)
        ;;
    *)
        ot-ctl ifconfig up
        ot-ctl thread start
        ;;
esac

# Keep the backup in sync (e.g. after HA recreated the network via REST).
mkdir -p "$(dirname "${DATASET_BACKUP}")"
if has_dataset; then
    active_dataset > "${DATASET_BACKUP}.tmp"
    chmod 600 "${DATASET_BACKUP}.tmp"
    mv "${DATASET_BACKUP}.tmp" "${DATASET_BACKUP}"
fi

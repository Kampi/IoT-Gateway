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

if ! ot-ctl dataset active >/dev/null 2>&1; then
    echo "No active Thread dataset found - forming a new network"
    ot-ctl dataset init new
    ot-ctl dataset commit active
fi

case "$(ot-ctl state)" in
    *leader*|*router*|*child*)
        ;;
    *)
        ot-ctl ifconfig up
        ot-ctl thread start
        ;;
esac

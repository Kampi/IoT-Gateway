#!/bin/sh
set -e

LED=/sys/class/leds/ready/brightness

# The gpio-leds driver probes very early, but wait briefly in case it hasn't yet.
i=0
while [ ! -w "${LED}" ] && [ "${i}" -lt 50 ]; do
    sleep 0.1
    i=$((i + 1))
done

echo 1 > "${LED}"

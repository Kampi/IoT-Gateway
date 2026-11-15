#!/bin/sh
# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

#
# Toradex Easy Installer: runs after the image has been written.
# Arguments: PRODUCT_ID BOARD_REV SERIAL IMAGE_FOLDER
#
# GatewayOS: reset into the new system. The gateway is headless, nobody
# presses "Reboot" in the Easy Installer GUI.
#
# The reset runs detached (setsid, own session) and delayed, so the Easy
# Installer can finish (status, logs) and does not take it down with this
# script. If reboot is missing or hangs, the kernel resets via sysrq.

sync

RESET='
    sleep 10
    sync
    reboot -f
    sleep 5
    echo 1 > /proc/sys/kernel/sysrq
    echo b > /proc/sysrq-trigger
'

if command -v setsid > /dev/null 2>&1; then
    setsid sh -c "${RESET}" < /dev/null > /dev/null 2>&1 &
else
    nohup sh -c "${RESET}" < /dev/null > /dev/null 2>&1 &
fi

exit 0

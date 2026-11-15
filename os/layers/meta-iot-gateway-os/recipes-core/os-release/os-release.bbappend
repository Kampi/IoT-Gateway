# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

# /etc/os-release: VERSION_ID = DISTRO_VERSION (GatewayOS version), BUILD_ID =
# git revision of meta-iot-gateway-os ("<describe>" or "<hash>-dirty"), shown
# by gateway-telemetry as "GatewayOS Layer" in Home Assistant.
GATEWAY_LAYER_DIR := "${@os.path.abspath(os.path.join(d.getVar('THISDIR'), '..', '..'))}"

def gateway_layer_revision(d):
    import subprocess
    try:
        return subprocess.check_output(
            ['git', '-C', d.getVar('GATEWAY_LAYER_DIR'), 'describe', '--always', '--dirty', '--abbrev=10'],
            stderr=subprocess.DEVNULL, text=True).strip()
    except Exception:
        return 'unknown'

BUILD_ID = "${@gateway_layer_revision(d)}"
OS_RELEASE_FIELDS:append = " BUILD_ID"

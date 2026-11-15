#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Daniel Kampert

import argparse
import glob
import json
import logging
import os
import re
import signal
import subprocess
import sys
import threading
import time
import paho.mqtt.client as mqtt

log = logging.getLogger("gateway-telemetry")

DEVICE_MANUFACTURER = "Daniel Kampert"
DEVICE_MODEL = "IoT Gateway (Verdin iMX8MM)"

# Restart buttons in Home Assistant: key -> (button name, systemd units).
# Only these units can be restarted via MQTT. A button is only offered if its
# units are installed.
RESTART_BUTTONS = {
    "lora": ("Restart LoRa", ["lora-packet-forwarder.service", "chirpstack-gateway-bridge.service"]),
    "bluetooth": ("Restart Bluetooth", ["theengs-gateway.service"]),
    "zigbee": ("Restart Zigbee2MQTT", ["zigbee2mqtt.service"]),
    "wmbus": ("Restart wM-Bus", ["wmbusmeters.service"]),
    # thread-network-init.service follows via PartOf= (re-attaches the network
    # and sets the fixed OMR prefix), see recipes-connectivity/thread-network-init
    "thread": ("Restart Thread", ["otbr-agent.service"]),
}

# Status sensors in Home Assistant: key -> (name, systemd units). A service
# counts as running if all its units are active. Only offered if installed.
SERVICE_STATUS = {
    "lora": ("LoRa", ["lora-packet-forwarder.service", "chirpstack-gateway-bridge.service"]),
    "bluetooth": ("Bluetooth", ["theengs-gateway.service"]),
    "zigbee": ("Zigbee", ["zigbee2mqtt.service"]),
    "wmbus": ("wM-Bus", ["wmbusmeters.service"]),
    "thread": ("Thread", ["otbr-agent.service"]),
}

# Services that only count as running while their receiver is in use: the
# daemon keeps running without it and just retries (e.g. wmbusmeters).
RECEIVER_DEVICES = {
    "wmbus": ("wmbusmeters.service", "/dev/wmbus"),
}

# LoRa only counts as running while the gateway bridge publishes stats for this
# gateway (packet forwarder sends them every 30 s). A hung concentrator keeps
# both services "active" but stops the stats - and ChirpStack shows the
# gateway offline.
LORA_STATS_TIMEOUT_S = 90

SYSTEMD_UNIT_DIRS = ("/etc/systemd/system", "/usr/lib/systemd/system", "/lib/systemd/system")


def unit_installed(unit):
    """Installed and not disabled/masked. A disabled service (e.g. wmbusmeters
    on the Dahlia dev setup, where its UART carries the Thread RCP) gets no
    status sensor, restart button or gateway device - pressing "restart"
    would start it."""
    if not any(os.path.exists(os.path.join(d, unit)) for d in SYSTEMD_UNIT_DIRS):
        return False
    state = subprocess.run(["systemctl", "is-enabled", unit], capture_output=True, text=True).stdout.strip()
    return state not in ("disabled", "masked", "masked-runtime")


# A unit only counts as running once it has been active without interruption
# for this long. A crash-looping service (e.g. Zigbee2MQTT without adapter:
# ~30 s active, 10 s restart delay) would otherwise show up as running.
STABLE_RUNNING_S = 60


def unit_active_seconds(unit):
    """Seconds the unit has been active without interruption, None if not active."""
    out = subprocess.run(
        ["systemctl", "show", unit, "-p", "ActiveState", "-p", "ActiveEnterTimestampMonotonic"],
        capture_output=True, text=True,
    ).stdout
    props = dict(line.split("=", 1) for line in out.splitlines() if "=" in line)
    if props.get("ActiveState") != "active":
        return None
    since_us = int(props.get("ActiveEnterTimestampMonotonic", "0") or 0)
    return time.monotonic() - since_us / 1e6


def unit_active(unit):
    out = subprocess.run(
        ["systemctl", "show", unit, "-p", "ActiveState", "-p", "ActiveEnterTimestampMonotonic"],
        capture_output=True, text=True,
    ).stdout
    props = dict(line.split("=", 1) for line in out.splitlines() if "=" in line)
    if props.get("ActiveState") != "active":
        return False
    # Both use CLOCK_MONOTONIC, systemd in microseconds.
    since_us = int(props.get("ActiveEnterTimestampMonotonic", "0") or 0)
    return (time.monotonic() - since_us / 1e6) >= STABLE_RUNNING_S


def unit_holds_device(unit, device):
    """True if the unit's main process has the device open."""
    pid = subprocess.run(["systemctl", "show", unit, "-p", "MainPID", "--value"],
                         capture_output=True, text=True).stdout.strip()
    if not pid or pid == "0":
        return False
    target = os.path.realpath(device)
    try:
        fds = os.listdir(f"/proc/{pid}/fd")
    except OSError:
        return False
    for fd in fds:
        try:
            if os.path.realpath(f"/proc/{pid}/fd/{fd}") == target:
                return True
        except OSError:
            continue
    return False


def receiver_in_use(unit, device):
    # Without a receiver the daemon still opens the device briefly to probe
    # for it. Only a device that stays open counts.
    if not unit_holds_device(unit, device):
        return False
    time.sleep(1.5)
    return unit_holds_device(unit, device)


def service_running(key, units):
    if not all(unit_active(u) for u in units):
        return False
    if key in RECEIVER_DEVICES:
        return receiver_in_use(*RECEIVER_DEVICES[key])
    return True


def read_wmbusmeters_version():
    # "wmbusmeters: 1.18.0 with local changes" -> "1.18.0"
    try:
        out = subprocess.run(["wmbusmeters", "--version"], capture_output=True, text=True, timeout=5).stdout
    except (OSError, subprocess.SubprocessError):
        return None
    match = re.search(r"\d+\.\d+(\.\d+)?", out)
    return match.group(0) if match else None


def read_bridge_version():
    try:
        out = subprocess.run(["chirpstack-gateway-bridge", "version"], capture_output=True, text=True, timeout=5).stdout
    except (OSError, subprocess.SubprocessError):
        return None
    return out.strip() or None


def read_lora_gateway_id():
    # Set from the Ethernet MAC by lora-gateway-id.service
    try:
        with open("/run/lora-packet-forwarder/global_conf.json") as f:
            return json.load(f)["gateway_conf"]["gateway_ID"].lower()
    except (OSError, ValueError, KeyError):
        return None


THEENGS_CONFIG = "/var/lib/theengs-gateway/theengsgw.conf"


def read_theengs_device_id():
    # Theengs links every BLE sensor to "via_device": <discovery_device_name>
    try:
        with open(THEENGS_CONFIG) as f:
            return json.load(f).get("discovery_device_name") or "TheengsGateway"
    except (OSError, ValueError):
        return None


def read_theengs_version():
    paths = glob.glob("/usr/lib/python3*/site-packages/theengsgateway-*.dist-info")
    return paths[0].rsplit("theengsgateway-", 1)[1][: -len(".dist-info")] if paths else None


def read_machine_id():
    with open("/etc/machine-id") as f:
        return f.read().strip()


def read_int(path):
    with open(path) as f:
        return int(f.read().strip())


def find_battery_capacity_path():
    # Path depends on which power_supply name the max17048 fuel-gauge driver
    # registers under, so discover it instead of hardcoding it.
    paths = glob.glob("/sys/class/power_supply/*/capacity")
    return paths[0] if paths else None


def read_kernel_version():
    return os.uname().release


def read_bootloader_version():
    # Set by U-Boot in the devicetree it passes to the kernel
    try:
        with open("/proc/device-tree/chosen/u-boot,version") as f:
            return "U-Boot " + f.read().strip("\0\n ")
    except OSError:
        return None


def read_os_release():
    values = {}
    try:
        with open("/etc/os-release") as f:
            for line in f:
                key, _, value = line.strip().partition("=")
                values[key] = value.strip('"')
    except OSError:
        pass
    return values


def read_layer_version():
    # DISTRO_VERSION + git revision of meta-iot-gateway-os (os-release bbappend)
    release = read_os_release()
    version = release.get("VERSION_ID")
    if not version:
        return None
    build = release.get("BUILD_ID")
    return f"{version} ({build})" if build else version


def read_hardware_version():
    # Board revision strapped on SODIMM 212/216/218 (version-bit0..2,
    # bit 0 = LSB; Dahlia: X20 pins 30/32/33). Empty HARDWARE_VERSION_LINES = none.
    lines = os.environ.get("HARDWARE_VERSION_LINES", "").split()
    if not lines:
        return None
    try:
        out = subprocess.run(["gpioget", "--numeric"] + lines, capture_output=True, text=True, timeout=5)
        bits = [int(b) for b in out.stdout.split()]
    except (OSError, ValueError, subprocess.SubprocessError):
        return None
    if out.returncode != 0 or len(bits) != len(lines):
        return None
    return str(sum(bit << i for i, bit in enumerate(bits)))


def read_gateway_serial():
    # Module serial number, from gateway-id.service (/run/gateway-id.env)
    try:
        with open("/run/gateway-id.env") as f:
            for line in f:
                key, _, value = line.strip().partition("=")
                if key == "GATEWAY_SERIAL":
                    return value
    except OSError:
        pass
    return None


# Version entities (diagnostic): key -> (name, read function)
VERSION_INFO = {
    "kernel_version": ("Linux Kernel", read_kernel_version),
    "bootloader_version": ("Bootloader", read_bootloader_version),
    "layer_version": ("GatewayOS Layer", read_layer_version),
    "hardware_version": ("Hardware Version", read_hardware_version),
}


# OTA updates from Home Assistant (MQTT update entity "GatewayOS"): bundle URL
# (rauc install streams it), check interval, reboot after a successful install.
UPDATE_URL = os.environ.get("UPDATE_URL", "")
UPDATE_CHECK_INTERVAL = int(os.environ.get("UPDATE_CHECK_INTERVAL", "3600") or 3600)
UPDATE_REBOOT = os.environ.get("UPDATE_REBOOT", "1") == "1"


def rauc_json(args, timeout=120):
    out = subprocess.run(["rauc", *args, "--output-format=json"], capture_output=True, text=True, timeout=timeout)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip().splitlines()[-1] if out.stderr.strip() else "rauc failed")
    return json.loads(out.stdout)


def bundle_version(bundle):
    version = bundle.get("version") or "?"
    build = bundle.get("build")
    return f"{version} ({build})" if build else version


def read_installed_bundle():
    """Bundle of the booted slot (rauc status), fallback /etc/os-release for a
    slot written by the Easy Installer (no bundle information)."""
    try:
        status = rauc_json(["status", "--detailed"], timeout=60)
        for entry in status.get("slots", []):
            for _, slot in entry.items():
                if slot.get("bootname") == status.get("booted"):
                    bundle = slot.get("slot_status", {}).get("bundle", {})
                    if bundle.get("version"):
                        return bundle
    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as exc:
        log.warning("rauc status failed: %s", exc)
    release = read_os_release()
    return {"version": release.get("VERSION_ID", "?"), "build": release.get("BUILD_ID"), "hash": None}


# Power source written by gateway-power-monitor (mainboard): "mains" / "battery"
POWER_STATE_FILE = "/run/gateway-power/state"


def read_power_source():
    try:
        with open(POWER_STATE_FILE) as f:
            return f.read().strip()
    except OSError:
        return None


def gpio_values(lines):
    out = subprocess.run(["gpioget", "--numeric"] + lines, capture_output=True, text=True, timeout=5)
    if out.returncode != 0:
        raise OSError(out.stderr.strip() or "gpioget failed")
    return [int(v) for v in out.stdout.split()]


def read_supply_source():
    """Mainboard supply: TPS2121 IC704 (mains / battery, POWER_SOURCE_LINES
    first line, 1 = battery) and IC601 (DC jack / PoE, second line, 1 = PoE)."""
    lines = os.environ.get("POWER_SOURCE_LINES", "").split()
    if len(lines) != 2:
        return None
    try:
        battery, poe = gpio_values(lines)
    except (OSError, ValueError, subprocess.SubprocessError):
        return None
    if battery:
        return "Battery"
    return "PoE" if poe else "DC"


def find_iio(name):
    for path in sorted(glob.glob("/sys/bus/iio/devices/iio:device*")):
        try:
            with open(os.path.join(path, "name")) as f:
                if f.read().strip() == name:
                    return path
        except OSError:
            continue
    return None


def find_hwmon(name):
    # hwmon numbering depends on probe order, so look the device up by name.
    for path in sorted(glob.glob("/sys/class/hwmon/hwmon*")):
        try:
            with open(os.path.join(path, "name")) as f:
                if f.read().strip() == name:
                    return path
        except OSError:
            continue
    return None


def read_cpu_temp_c():
    return read_int("/sys/class/thermal/thermal_zone0/temp") / 1000.0


def read_cpu_load_percent():
    with open("/proc/loadavg") as f:
        load1 = float(f.read().split()[0])
    return min(load1 / os.cpu_count() * 100.0, 100.0)


def read_mem_used_percent():
    values = {}
    with open("/proc/meminfo") as f:
        for line in f:
            key, _, rest = line.partition(":")
            if key in ("MemTotal", "MemAvailable"):
                values[key] = int(rest.split()[0])
    total = values["MemTotal"]
    available = values["MemAvailable"]
    return (total - available) / total * 100.0


def read_disk_used_percent(path="/"):
    stat = os.statvfs(path)
    return (stat.f_blocks - stat.f_bfree) / stat.f_blocks * 100.0


def read_uptime_seconds():
    with open("/proc/uptime") as f:
        return float(f.read().split()[0])


class Sensor:
    def __init__(self, key, name, unit, read_fn, device_class=None, precision=1):
        self.key = key
        self.name = name
        self.unit = unit
        self.read_fn = read_fn
        self.device_class = device_class
        self.precision = precision

    def read(self):
        return round(self.read_fn(), self.precision)


def build_sensors():
    sensors = [
        Sensor("cpu_temp", "CPU Temperature", "°C", read_cpu_temp_c, device_class="temperature"),
        Sensor("cpu_load", "CPU Load", "%", read_cpu_load_percent),
        Sensor("mem_used", "Memory Used", "%", read_mem_used_percent),
        Sensor("disk_used", "Disk Used", "%", read_disk_used_percent),
        Sensor("uptime", "Uptime", "s", read_uptime_seconds, device_class="duration", precision=0),
    ]

    # INA219 power monitor (ina2xx hwmon driver): bus voltage in mV (in1),
    # current in mA (curr1), power in uW (power1)
    ina = find_hwmon("ina219")
    if ina:
        sensors += [
            Sensor("supply_voltage", "Supply Voltage", "V",
                   lambda: read_int(os.path.join(ina, "in1_input")) / 1000.0, device_class="voltage", precision=2),
            Sensor("supply_current", "Supply Current", "mA",
                   lambda: read_int(os.path.join(ina, "curr1_input")), device_class="current", precision=0),
            Sensor("supply_power", "Supply Power", "W",
                   lambda: read_int(os.path.join(ina, "power1_input")) / 1000000.0, device_class="power", precision=2),
        ]
    else:
        log.info("No INA219 power monitor found under /sys/class/hwmon, skipping supply sensors")

    # Input voltage: ADC channel of the module ADC (TLA2024, ads1015 driver)
    # behind a resistor divider (mainboard: ADC_1 = in_voltage3, 100k/12k)
    channel = os.environ.get("INPUT_VOLTAGE_CHANNEL", "")
    adc = find_iio("ads1015") if channel else None
    if adc:
        divider = float(os.environ.get("INPUT_VOLTAGE_DIVIDER", "1"))
        raw = os.path.join(adc, f"in_voltage{channel}_raw")
        scale = os.path.join(adc, f"in_voltage{channel}_scale")

        def read_input_voltage():
            with open(raw) as f_raw, open(scale) as f_scale:
                return int(f_raw.read()) * float(f_scale.read()) * divider / 1000.0

        sensors.append(Sensor("input_voltage", "Input Voltage", "V", read_input_voltage,
                              device_class="voltage", precision=2))
    elif channel:
        log.info("No ads1015 ADC found under /sys/bus/iio, skipping input voltage sensor")

    battery_path = find_battery_capacity_path()
    if battery_path:
        sensors.append(
            Sensor("battery", "Battery", "%", lambda: read_int(battery_path), device_class="battery", precision=0)
        )
    else:
        log.info("No battery fuel gauge found under /sys/class/power_supply, skipping battery sensor")

    return sensors


class GatewayTelemetry:
    def __init__(self, args):
        self.args = args
        self.device_id = read_machine_id()
        self.state_topic = f"gateway/{self.device_id}/telemetry/state"
        self.availability_topic = f"gateway/{self.device_id}/telemetry/availability"
        self.restart_topic = f"gateway/{self.device_id}/command/restart"
        self.service_topic = f"gateway/{self.device_id}/service"
        self.power_topic = f"gateway/{self.device_id}/power"
        self.status_services = {
            key: units for key, (_, units) in SERVICE_STATUS.items() if all(unit_installed(u) for u in units)
        }
        self.sensors = build_sensors()
        self.versions = {key: fn() for key, (_, fn) in VERSION_INFO.items()}
        self.info_topic = f"gateway/{self.device_id}/info"
        self.update_topic = f"gateway/{self.device_id}/update"
        self.update_command_topic = f"{self.update_topic}/install"
        self.update_lock = threading.Lock()
        self.update_state = {"title": "GatewayOS", "in_progress": False}
        self.update_installing = False
        self.update_last_check = None
        self.lora_gateway_id = read_lora_gateway_id()
        self.lora_stats_topic = f"+/gateway/{self.lora_gateway_id}/event/stats" if self.lora_gateway_id else None
        self.lora_last_stats = None
        self.connected_since = None

        self.client = mqtt.Client(
            mqtt.CallbackAPIVersion.VERSION2,
            client_id=f"gateway-telemetry-{self.device_id}",
        )
        if args.username:
            self.client.username_pw_set(args.username, args.password)

        # Retained LWT so Home Assistant immediately reflects the gateway
        # going offline (e.g. power loss) without waiting on a timeout.
        self.client.will_set(self.availability_topic, payload="offline", qos=1, retain=True)
        self.client.on_connect = self.on_connect
        self.client.on_disconnect = self.on_disconnect
        self.client.on_message = self.on_message

    def device_info(self):
        # Device card in Home Assistant: one line each for Firmware (GatewayOS
        # layer), Hardware (board revision) and Serial number
        # (module). It shows no line breaks inside a field, so kernel and
        # bootloader are diagnostic entities only (VERSION_INFO).
        v = self.versions
        info = {
            "identifiers": [self.device_id],
            "name": self.args.device_name,
            "manufacturer": DEVICE_MANUFACTURER,
            "model": DEVICE_MODEL,
        }
        if v["layer_version"]:
            info["sw_version"] = f"GatewayOS {v['layer_version']}"
        if v["hardware_version"] is not None:
            info["hw_version"] = f"Rev. {v['hardware_version']}"
        serial = read_gateway_serial()
        if serial:
            info["serial_number"] = serial
        return info

    def publish_versions(self):
        device = self.device_info()
        for key, (name, _) in VERSION_INFO.items():
            topic = f"{self.args.discovery_prefix}/sensor/{self.device_id}/{key}/config"
            value = self.versions[key]
            if value is None:
                self.client.publish(topic, "", qos=1, retain=True)
                continue
            payload = {
                "name": name,
                "unique_id": f"{self.device_id}_{key}",
                "state_topic": f"{self.info_topic}/{key}",
                "availability_topic": self.availability_topic,
                "entity_category": "diagnostic",
                "icon": "mdi:information-outline",
                "device": device,
            }
            self.client.publish(topic, json.dumps(payload), qos=1, retain=True)
            self.client.publish(f"{self.info_topic}/{key}", value, qos=1, retain=True)

    def publish_discovery(self):
        device = self.device_info()
        for sensor in self.sensors:
            topic = f"{self.args.discovery_prefix}/sensor/{self.device_id}/{sensor.key}/config"
            payload = {
                "name": sensor.name,
                "unique_id": f"{self.device_id}_{sensor.key}",
                "state_topic": self.state_topic,
                "availability_topic": self.availability_topic,
                "value_template": f"{{{{ value_json.{sensor.key} }}}}",
                "unit_of_measurement": sensor.unit,
                "state_class": "measurement",
                "device": device,
            }

            if sensor.device_class:
                payload["device_class"] = sensor.device_class
            # Retained: a client connecting later (e.g. HA restart) still
            # sees the config without us having to republish it.
            self.client.publish(topic, json.dumps(payload), qos=1, retain=True)
        log.info("Published discovery config for %d sensors (device_id=%s)", len(self.sensors), self.device_id)

        for key, (name, units) in SERVICE_STATUS.items():
            topic = f"{self.args.discovery_prefix}/binary_sensor/{self.device_id}/service_{key}/config"
            if key not in self.status_services:
                # Not installed (any more): remove a sensor announced earlier.
                self.client.publish(topic, "", qos=1, retain=True)
                continue
            payload = {
                "name": name,
                "unique_id": f"{self.device_id}_service_{key}",
                "state_topic": f"{self.service_topic}/{key}",
                "availability_topic": self.availability_topic,
                "device_class": "running",
                "entity_category": "diagnostic",
                "device": device,
            }
            self.client.publish(topic, json.dumps(payload), qos=1, retain=True)

        # Mains/battery (gateway-power-monitor): binary sensor + events
        # (on_battery, mains_restored, shutdown) for Home Assistant automations
        power = [
            (f"{self.args.discovery_prefix}/binary_sensor/{self.device_id}/mains_power/config", {
                "name": "Mains Power",
                "unique_id": f"{self.device_id}_mains_power",
                "state_topic": f"{self.power_topic}/mains",
                "availability_topic": self.availability_topic,
                "device_class": "power",
                "device": device,
            }),
            (f"{self.args.discovery_prefix}/event/{self.device_id}/power_event/config", {
                "name": "Power Event",
                "unique_id": f"{self.device_id}_power_event",
                "state_topic": f"{self.power_topic}/event",
                "event_types": ["on_battery", "mains_restored", "shutdown"],
                "device": device,
            }),
        ]
        monitored = os.path.exists(POWER_STATE_FILE)
        topic = f"{self.args.discovery_prefix}/sensor/{self.device_id}/power_source/config"
        if read_supply_source():
            self.client.publish(topic, json.dumps({
                "name": "Power Source",
                "unique_id": f"{self.device_id}_power_source",
                "state_topic": f"{self.power_topic}/source",
                "availability_topic": self.availability_topic,
                "device_class": "enum",
                "options": ["DC", "PoE", "Battery"],
                "icon": "mdi:power-plug",
                "device": device,
            }), qos=1, retain=True)
        else:
            self.client.publish(topic, "", qos=1, retain=True)
        for topic, payload in power:
            self.client.publish(topic, json.dumps(payload) if monitored else "", qos=1, retain=True)

        # OTA update entity (UPDATE_URL): install button + progress in Home Assistant
        topic = f"{self.args.discovery_prefix}/update/{self.device_id}/os_update/config"
        if UPDATE_URL:
            self.client.publish(topic, json.dumps({
                "name": "GatewayOS",
                "unique_id": f"{self.device_id}_os_update",
                "device_class": "firmware",
                "state_topic": self.update_topic,
                "command_topic": self.update_command_topic,
                "payload_install": "install",
                "availability_topic": self.availability_topic,
                "device": device,
            }), qos=1, retain=True)
        else:
            self.client.publish(topic, "", qos=1, retain=True)

        for key, (name, units) in RESTART_BUTTONS.items():
            topic = f"{self.args.discovery_prefix}/button/{self.device_id}/restart_{key}/config"
            if not all(unit_installed(u) for u in units):
                # Not installed (any more): remove a button announced earlier.
                self.client.publish(topic, "", qos=1, retain=True)
                continue
            payload = {
                "name": name,
                "unique_id": f"{self.device_id}_restart_{key}",
                "command_topic": self.restart_topic,
                "payload_press": key,
                "availability_topic": self.availability_topic,
                "device_class": "restart",
                "entity_category": "config",
                "device": device,
            }
            self.client.publish(topic, json.dumps(payload), qos=1, retain=True)

    def radio_gateways(self):
        """Gateway devices per radio, linked to this device ("via_device"), each
        with connection state (service running, see service_running) and
        version - same style as the Zigbee2MQTT bridge, which Zigbee2MQTT
        announces itself. Discovery topics and unique IDs are the ones used
        before (Node-RED flow, Theengs announce script), so Home Assistant
        keeps the existing devices and entities."""
        prefix = self.args.discovery_prefix
        gateways = []

        gw_id = read_lora_gateway_id()
        uid = f"chirpstack_gw_{gw_id}"
        gateways.append({
            "key": "lora",
            "installed": "lora" in self.status_services and gw_id is not None,
            "device": {"identifiers": [uid], "name": self.args.lora_device_name, "manufacturer": "ChirpStack",
                       "model": "LoRaWAN Gateway", "serial_number": gw_id},
            "version": read_bridge_version,
            "icon": "mdi:radio-tower",
            "status": (f"{prefix}/binary_sensor/{uid}_status/config", f"{uid}_status"),
            "version_cfg": (f"{prefix}/sensor/{uid}_version/config", f"{uid}_version"),
        })

        ble_id = read_theengs_device_id()
        slug = re.sub(r"[^a-z0-9]+", "_", (ble_id or "").lower()).strip("_")
        gateways.append({
            "key": "bluetooth",
            "installed": "bluetooth" in self.status_services and ble_id is not None,
            "device": {"identifiers": [ble_id], "name": self.args.ble_device_name, "manufacturer": "Theengs",
                       "model": "Theengs Gateway (Bluetooth LE to MQTT)"},
            "version": read_theengs_version,
            "icon": "mdi:bluetooth",
            "status": (f"{prefix}/binary_sensor/{slug}_status/config", f"{slug}_status"),
            "version_cfg": (f"{prefix}/sensor/{slug}_version/config", f"{slug}_version"),
        })

        uid = f"{self.device_id}_wmbus"
        gateways.append({
            "key": "wmbus",
            "installed": "wmbus" in self.status_services,
            "device": {"identifiers": [uid], "name": self.args.wmbus_device_name, "manufacturer": "wmbusmeters",
                       "model": "Wireless M-Bus receiver"},
            "version": read_wmbusmeters_version,
            "icon": "mdi:meter-electric",
            "status": (f"{prefix}/binary_sensor/{uid}/status/config", f"{uid}_status"),
            "version_cfg": (f"{prefix}/sensor/{uid}/version/config", f"{uid}_version"),
        })
        return gateways

    def gateway_topic(self, key, what):
        return f"gateway/{self.device_id}/{key}/{what}"

    def publish_radio_gateways(self):
        for gw in self.radio_gateways():
            status_topic, status_uid = gw["status"]
            version_topic, version_uid = gw["version_cfg"]
            if not gw["installed"]:
                # Not installed (any more): remove a device announced earlier.
                self.client.publish(status_topic, "", qos=1, retain=True)
                self.client.publish(version_topic, "", qos=1, retain=True)
                continue

            version = gw["version"]()
            device = {**gw["device"], "via_device": self.device_id}
            if version:
                device["sw_version"] = version

            entities = [
                (status_topic, {
                    "name": "Connection state",
                    "unique_id": status_uid,
                    "object_id": status_uid,
                    "state_topic": self.gateway_topic(gw["key"], "status"),
                    "device_class": "connectivity",
                }),
                (version_topic, {
                    "name": "Version",
                    "unique_id": version_uid,
                    "object_id": version_uid,
                    "state_topic": self.gateway_topic(gw["key"], "version"),
                    "icon": gw["icon"],
                }),
            ]
            for topic, entity in entities:
                payload = {**entity, "entity_category": "diagnostic", "device": device}
                self.client.publish(topic, json.dumps(payload), qos=1, retain=True)

            if version:
                self.client.publish(self.gateway_topic(gw["key"], "version"), version, qos=1, retain=True)
        log.info("Published radio gateway devices")

    def publish_state(self):
        state = {}
        for sensor in self.sensors:
            try:
                state[sensor.key] = sensor.read()
            except OSError as exc:
                log.warning("Failed to read sensor '%s': %s", sensor.key, exc)
        self.client.publish(self.state_topic, json.dumps(state), qos=1, retain=False)

        supply = read_supply_source()
        if supply:
            self.client.publish(f"{self.power_topic}/source", supply, qos=1, retain=True)

        source = read_power_source()
        if source:
            self.client.publish(f"{self.power_topic}/mains", "ON" if source == "mains" else "OFF",
                                qos=1, retain=True)

        running = {key: service_running(key, units) for key, units in self.status_services.items()}
        if "lora" in running:
            fresh = self.lora_last_stats is not None and time.monotonic() - self.lora_last_stats < LORA_STATS_TIMEOUT_S
            running["lora"] = running["lora"] and fresh
            self.lora_watchdog()
        for key, value in running.items():
            self.client.publish(f"{self.service_topic}/{key}", "ON" if value else "OFF", qos=1, retain=True)

        # Connection state of the radio gateway devices
        for key in ("lora", "bluetooth", "wmbus"):
            if key in running:
                self.client.publish(self.gateway_topic(key, "status"), "ON" if running[key] else "OFF",
                                    qos=1, retain=True)

    def lora_watchdog(self):
        """Restart the packet forwarder if the concentrator hangs: the forwarder
        keeps running but stops sending stats (ChirpStack: gateway offline).
        Only acts while we are connected to the broker long enough to have
        seen stats - otherwise missing stats say nothing about the gateway.
        Rate limit: the forwarder must have run for the full timeout again."""
        timeout = self.args.lora_watchdog_timeout
        if timeout <= 0 or self.connected_since is None:
            return
        now = time.monotonic()
        if now - self.connected_since < timeout:
            return
        if self.lora_last_stats is not None and now - self.lora_last_stats < timeout:
            return
        running_for = unit_active_seconds("lora-packet-forwarder.service")
        if running_for is None or running_for < timeout:
            return
        last = "never" if self.lora_last_stats is None else f"{now - self.lora_last_stats:.0f} s ago"
        log.warning("LoRa watchdog: no gateway stats for %d s (last: %s), restarting lora-packet-forwarder",
                    timeout, last)
        result = subprocess.run(["systemctl", "restart", "--no-block", "lora-packet-forwarder.service"],
                                capture_output=True, text=True)
        if result.returncode != 0:
            log.error("LoRa watchdog: restart failed: %s", result.stderr.strip())

    def on_connect(self, client, userdata, flags, reason_code, properties):
        if reason_code != 0:
            log.error("Failed to connect to MQTT broker: %s", reason_code)
            return
        log.info("Connected to MQTT broker at %s:%d", self.args.host, self.args.port)
        self.connected_since = time.monotonic()
        # Device is (re-)created/discovered in Home Assistant right here,
        # on every successful connection.
        client.publish(self.availability_topic, "online", qos=1, retain=True)
        client.subscribe(self.restart_topic, qos=1)
        if UPDATE_URL:
            client.subscribe(self.update_command_topic, qos=1)
            self.check_update_async()
        if self.lora_stats_topic:
            client.subscribe(self.lora_stats_topic, qos=0)
        self.publish_discovery()
        self.publish_versions()
        self.publish_radio_gateways()
        self.publish_state()

    def on_message(self, client, userdata, msg):
        if self.lora_stats_topic and mqtt.topic_matches_sub(self.lora_stats_topic, msg.topic):
            self.lora_last_stats = time.monotonic()
            return
        if msg.topic == self.update_command_topic:
            if msg.payload.decode(errors="replace").strip() == "install":
                self.install_update_async()
            return
        if msg.topic != self.restart_topic:
            return
        key = msg.payload.decode(errors="replace").strip()
        if key not in RESTART_BUTTONS:
            log.warning("Ignoring restart request for unknown service '%s'", key)
            return
        units = RESTART_BUTTONS[key][1]
        log.info("Restart requested via MQTT: %s", " ".join(units))
        # --no-block: don't stall the MQTT loop while the units restart.
        result = subprocess.run(["systemctl", "restart", "--no-block", *units], capture_output=True, text=True)
        if result.returncode != 0:
            log.error("Restart of %s failed: %s", " ".join(units), result.stderr.strip())

    # ---- OTA update (RAUC) -------------------------------------------------

    def publish_update_state(self, **changes):
        with self.update_lock:
            self.update_state.update(changes)
            payload = json.dumps(self.update_state)
        self.client.publish(self.update_topic, payload, qos=1, retain=True)

    def check_update(self):
        installed = read_installed_bundle()
        state = {"installed_version": bundle_version(installed)}
        try:
            remote = rauc_json(["info", UPDATE_URL], timeout=120)
            if remote.get("hash") and remote.get("hash") == installed.get("hash"):
                state["latest_version"] = state["installed_version"]
            else:
                state["latest_version"] = bundle_version(remote)
            state["release_summary"] = f"Bundle {UPDATE_URL} ({remote.get('compatible', '?')})"
        except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as exc:
            log.warning("Update check failed: %s", exc)
            state["latest_version"] = state["installed_version"]
            state["release_summary"] = f"Update check failed: {exc}"
        self.update_last_check = time.monotonic()
        if not self.update_installing:
            self.publish_update_state(**state)

    def check_update_async(self):
        threading.Thread(target=self.check_update, daemon=True).start()

    def install_update(self):
        log.info("Installing update from %s", UPDATE_URL)
        self.publish_update_state(in_progress=True, update_percentage=0, release_summary="Installing")
        proc = subprocess.Popen(["rauc", "install", UPDATE_URL], stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, bufsize=1)
        output, line = [], ""
        while True:
            ch = proc.stdout.read(1)
            if not ch:
                break
            if ch in "\r\n":
                match = re.match(r"\s*(\d+)%", line)
                if match:
                    self.publish_update_state(update_percentage=int(match.group(1)))
                if line.strip():
                    output.append(line.strip())
                line = ""
            else:
                line += ch
        rc = proc.wait()
        self.update_installing = False
        if rc == 0:
            log.info("Update installed%s", ", rebooting" if UPDATE_REBOOT else "")
            self.publish_update_state(in_progress=False, update_percentage=None,
                                      release_summary="Installed, rebooting" if UPDATE_REBOOT
                                      else "Installed, active after the next reboot")
            if UPDATE_REBOOT:
                time.sleep(5)
                subprocess.run(["systemctl", "reboot"])
        else:
            error = output[-1] if output else f"rauc install exit code {rc}"
            log.error("Update failed: %s", error)
            self.publish_update_state(in_progress=False, update_percentage=None,
                                      release_summary=f"Update failed: {error}")

    def install_update_async(self):
        if self.update_installing:
            log.warning("Update already running")
            return
        self.update_installing = True
        threading.Thread(target=self.install_update, daemon=True).start()

    def on_disconnect(self, client, userdata, disconnect_flags, reason_code, properties):
        log.warning("Disconnected from MQTT broker: %s", reason_code)
        self.connected_since = None

    def run(self):
        self.client.connect(self.args.host, self.args.port, keepalive=self.args.timeout)
        self.client.loop_start()

        def shutdown(signum, frame):
            self.client.publish(self.availability_topic, "offline", qos=1, retain=True)
            self.client.disconnect()
            self.client.loop_stop()
            sys.exit(0)

        signal.signal(signal.SIGTERM, shutdown)
        signal.signal(signal.SIGINT, shutdown)

        while True:
            time.sleep(self.args.interval)
            self.publish_state()
            if UPDATE_URL and not self.update_installing and (
                    self.update_last_check is None or time.monotonic() - self.update_last_check >= UPDATE_CHECK_INTERVAL):
                self.update_last_check = time.monotonic()
                self.check_update_async()

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(message)s", stream=sys.stdout)

    parser = argparse.ArgumentParser(description="Publish gateway telemetry to Home Assistant via MQTT discovery")
    parser.add_argument("--host", default=os.environ.get("MQTT_HOST", "localhost"), help="MQTT broker host")
    parser.add_argument("--port", type=int, default=int(os.environ.get("MQTT_PORT", "1883")), help="MQTT broker port")
    parser.add_argument("--username", default=os.environ.get("MQTT_USERNAME") or None, help="MQTT username")
    parser.add_argument("--password", default=os.environ.get("MQTT_PASSWORD") or None, help="MQTT password")
    parser.add_argument("--timeout", type=int, default=60, help="MQTT keepalive interval in seconds")
    parser.add_argument(
        "--interval",
        type=int,
        default=int(os.environ.get("TELEMETRY_INTERVAL", "30")),
        help="Telemetry publish interval in seconds",
    )
    parser.add_argument(
        "--discovery-prefix",
        default=os.environ.get("HA_DISCOVERY_PREFIX", "homeassistant"),
        help="Home Assistant MQTT discovery prefix",
    )
    parser.add_argument(
        "--device-name",
        default=os.environ.get("GATEWAY_NAME", "IoT Gateway"),
        help="Friendly device name shown in Home Assistant",
    )

    parser.add_argument(
        "--lora-watchdog-timeout",
        type=int,
        default=int(os.environ.get("LORA_WATCHDOG_TIMEOUT", "300")),
        help="Restart the LoRa packet forwarder after this many seconds without gateway stats (0 = off)",
    )
    parser.add_argument(
        "--lora-device-name",
        default=os.environ.get("LORA_DEVICE_NAME", "IoT-Gateway (LoRa)"),
        help="Name of the LoRa gateway device shown in Home Assistant",
    )
    parser.add_argument(
        "--ble-device-name",
        default=os.environ.get("BLE_DEVICE_NAME", "IoT-Gateway (Bluetooth)"),
        help="Name of the Bluetooth gateway device shown in Home Assistant",
    )
    parser.add_argument(
        "--wmbus-device-name",
        default=os.environ.get("WMBUS_DEVICE_NAME", "IoT-Gateway (wM-Bus)"),
        help="Name of the wM-Bus gateway device shown in Home Assistant",
    )

    GatewayTelemetry(parser.parse_args()).run()

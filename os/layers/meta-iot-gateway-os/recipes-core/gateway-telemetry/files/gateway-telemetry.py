#!/usr/bin/env python3
import argparse
import glob
import json
import logging
import os
import signal
import sys
import time
import paho.mqtt.client as mqtt

log = logging.getLogger("gateway-telemetry")

DEVICE_MANUFACTURER = "Daniel Kampert"
DEVICE_MODEL = "IoT Gateway (Verdin iMX8MM)"


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
        self.sensors = build_sensors()

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

    def device_info(self):
        return {
            "identifiers": [self.device_id],
            "name": self.args.device_name,
            "manufacturer": DEVICE_MANUFACTURER,
            "model": DEVICE_MODEL,
            "sw_version": os.uname().release,
        }

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

    def publish_state(self):
        state = {}
        for sensor in self.sensors:
            try:
                state[sensor.key] = sensor.read()
            except OSError as exc:
                log.warning("Failed to read sensor '%s': %s", sensor.key, exc)
        self.client.publish(self.state_topic, json.dumps(state), qos=1, retain=False)

    def on_connect(self, client, userdata, flags, reason_code, properties):
        if reason_code != 0:
            log.error("Failed to connect to MQTT broker: %s", reason_code)
            return
        log.info("Connected to MQTT broker at %s:%d", self.args.host, self.args.port)
        # Device is (re-)created/discovered in Home Assistant right here,
        # on every successful connection.
        client.publish(self.availability_topic, "online", qos=1, retain=True)
        self.publish_discovery()
        self.publish_state()

    def on_disconnect(self, client, userdata, disconnect_flags, reason_code, properties):
        log.warning("Disconnected from MQTT broker: %s", reason_code)

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

    GatewayTelemetry(parser.parse_args()).run()

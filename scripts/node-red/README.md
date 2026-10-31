# ChirpStack → Home Assistant (Node-RED)

Generic Node-RED flow that turns ChirpStack v4 uplinks into Home Assistant
entities via MQTT discovery. It runs on the NAS Node-RED instance, not on the
gateway. New devices show up in Home Assistant automatically on their first
uplink, with no per-device configuration.

```text
ChirpStack ──application/+/device/+/event/up─────┐
Gateway Bridge ──<region>/gateway/+/state/conn──┼─► Node-RED ─┬─► homeassistant/<component>/<id>/config  (discovery, retained)
Gateway Bridge ──<region>/gateway/+/event/stats─┘             ├─► chirpstack2ha/<devEui>/<field>         (state, retained)
                                                              └─► chirpstack2ha/gateway/<id>/status      (ON/OFF, retained)
```

## Requirements

- Binary payloads need a payload codec in the ChirpStack device profile, so
  uplinks contain the decoded `object`. Devices that send plain JSON text
  (e.g. `{"temperature": 22.18}`) work without a codec. Anything else only
  produces RSSI/SNR entities.
- Home Assistant's MQTT integration uses the same broker as ChirpStack
  (`192.168.178.103:1883`) with discovery enabled (default prefix `homeassistant`).

## Import

Node-RED → Menu → Import → select `chirpstack-to-homeassistant.json` → Deploy.
Adjust the broker config node if the broker address or credentials change.

## What it creates

- One HA device per LoRaWAN device (name, profile, application, DevEUI).
- One entity per decoded field. Nested objects are flattened (`ext.temperature`
  → `ext_temperature`), arrays are skipped.
  - numbers → `sensor`; unit and device class are guessed from the field name
    (`temp`, `hum`, `bat`, `co2`, `press`, `lux`, `pm2_5`, `power`, …)
  - booleans → `binary_sensor`
  - strings → `sensor` without unit
- RSSI and SNR of the best receiving gateway as diagnostic entities.
- One HA device per LoRa gateway (from the ChirpStack Gateway Bridge connection
  state, `<region>/gateway/<id>/state/conn`) with an online/offline status.
  Every LoRa device is linked to the gateway that received it first, so HA
  lists it under the gateway's "Connected devices". Gateway display names are
  set in `GATEWAY_NAMES` in the "Gateway → HA device + status" node; unknown
  gateways are called `LoRa Gateway <id>`. The firmware version shown in HA is
  `gateway_bridge_version` from the bridge's static metadata
  (`[meta_data.static]`, sent with every `event/stats` message).

## Per-device tuning (ChirpStack device tags)

| Tag | Example | Effect |
| --- | --- | --- |
| `ha_ignore` | `true` | Skip the device completely |
| `ha_skip` | `raw,counter` | Skip these fields (flattened names) |
| `ha_expire_after` | `7200` | Entities become unavailable after N seconds without data |
| `ha_unit_<field>` | `ha_unit_temp` = `°F` | Override the unit |
| `ha_class_<field>` | `ha_class_level` = `distance` | Override the HA device class |

## Removing a device

Discovery messages are retained. To remove a device's entities from Home
Assistant, delete it in HA (MQTT device page → Delete) or publish an empty
retained message to its `homeassistant/.../config` topics.

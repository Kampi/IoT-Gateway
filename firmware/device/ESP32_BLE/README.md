# ESP32_BLE - BLE temperature sensor simulator

Test firmware for the gateway's Bluetooth path (Theengs Gateway, see
`os/layers/meta-iot-gateway-os/recipes-connectivity/theengs-gateway`). It
broadcasts random temperature and humidity values every 10 s as **BTHome v2**
advertisements.

```text
ESP32 --BLE advertisement--> Theengs Gateway (IoT-Gateway) --MQTT--> Home Assistant
```

## Format

Non-connectable advertising, device name `BTH01`, service data for UUID
`0xFCD2` (BTHome v2, unencrypted):

| Byte | Content |
| --- | --- |
| `0x40` | BTHome v2 device info |
| `0x00` + id | packet id |
| `0x01` + % | battery (fixed 100 %) |
| `0x02` + int16 | temperature, 0.01 °C |
| `0x03` + int16 | humidity, 0.01 % |
| `0x0C` + int16 | voltage, 0.001 V (fixed 3.3 V) |

Theengs Decoder only decodes this layout for device names starting with
`BTH01`, so it shows up as model **`THB1/BTH01/TH05F`**. Home Assistant creates
the sensors automatically via MQTT discovery. Its native BTHome integration
understands the packets as well.

The values follow a random walk (temperature 15-30 °C, humidity 30-70 %).

## Build and flash

Same hardware as `ESP32_LoRa` (ESP-WROVER-KIT), PlatformIO with ESP-IDF:

```bash
pio run -e debug -t upload --upload-port /dev/ttyUSB1
pio device monitor --port /dev/ttyUSB1
```

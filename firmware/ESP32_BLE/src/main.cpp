/*
 * BLE temperature sensor simulator for the IoT-Gateway.
 *
 * Broadcasts random temperature/humidity values as BTHome v2 advertisements
 * (open standard, service UUID 0xFCD2). The payload layout and the device name
 * prefix "BTH01" match a decoder that Theengs Decoder ships, so the gateway's
 * Theengs Gateway decodes the values and creates the sensors in Home Assistant
 * via MQTT discovery. Home Assistant's own BTHome integration understands the
 * packets as well.
 *
 * Non-connectable advertising only, no GATT server.
 */

#include <cmath>
#include <cstring>

#include <esp_log.h>
#include <esp_random.h>
#include <nvs_flash.h>

#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

#include <host/ble_hs.h>
#include <nimble/nimble_port.h>
#include <nimble/nimble_port_freertos.h>

static const char *TAG = "main";

// Theengs only decodes this BTHome layout for devices whose name starts with
// "BTH01" (see THB1_json.h in Theengs Decoder). Keep it short: flags + name +
// service data must fit into the 31 byte advertising payload.
static const char *DEVICE_NAME = "BTH01";

// How often new values are generated and advertised.
static constexpr uint32_t UPDATE_INTERVAL_MS = 10000;

// Advertising interval in units of 0.625 ms: 1600 = 1 s.
static constexpr uint16_t ADV_INTERVAL = 1600;

static uint8_t _OwnAddrType;
static uint8_t _PacketId;

// Random walk around a start value, so the values look like a real sensor.
static float _Temperature = 22.0f;
static float _Humidity = 50.0f;

/** @brief      Random value in the range [-Range, +Range].
 */
static float RandomStep(float Range)
{
    return ((static_cast<float>(esp_random() % 2001) / 1000.0f) - 1.0f) * Range;
}

static float Clamp(float Value, float Min, float Max)
{
    return (Value < Min) ? Min : ((Value > Max) ? Max : Value);
}

static void PutInt16(uint8_t *p_Buffer, int16_t Value)
{
    p_Buffer[0] = static_cast<uint8_t>(Value & 0xFF);
    p_Buffer[1] = static_cast<uint8_t>((Value >> 8) & 0xFF);
}

/** @brief      (Re)start advertising with the current values.
 */
static void StartAdvertising(void)
{
    // BTHome v2 service data, layout as expected by Theengs (14 bytes after the UUID):
    //  0xFCD2 (UUID, little endian)
    //  0x40         device info: BTHome v2, not encrypted, regular interval
    //  0x00 <id>    packet id (lets receivers drop duplicates)
    //  0x01 <%>     battery
    //  0x02 <int16> temperature, 0.01 °C
    //  0x03 <int16> humidity, 0.01 %
    //  0x0C <int16> voltage, 0.001 V
    uint8_t ServiceData[16] = {
        0xD2, 0xFC,
        0x40,
        0x00, _PacketId,
        0x01, 100,
        0x02, 0x00, 0x00,
        0x03, 0x00, 0x00,
        0x0C, 0x00, 0x00,
    };

    PutInt16(&ServiceData[8], static_cast<int16_t>(lroundf(_Temperature * 100.0f)));
    PutInt16(&ServiceData[11], static_cast<int16_t>(lroundf(_Humidity * 100.0f)));
    PutInt16(&ServiceData[14], 3300);

    struct ble_hs_adv_fields Fields;
    memset(&Fields, 0, sizeof(Fields));
    Fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    Fields.name = reinterpret_cast<const uint8_t *>(DEVICE_NAME);
    Fields.name_len = strlen(DEVICE_NAME);
    Fields.name_is_complete = 1;
    Fields.svc_data_uuid16 = ServiceData;
    Fields.svc_data_uuid16_len = sizeof(ServiceData);

    // Advertising data can't be changed reliably while advertising.
    if (ble_gap_adv_active()) {
        ble_gap_adv_stop();
    }

    int Error = ble_gap_adv_set_fields(&Fields);
    if (Error != 0) {
        ESP_LOGE(TAG, "Cannot set advertising data! Error: %d", Error);
        return;
    }

    struct ble_gap_adv_params Params;
    memset(&Params, 0, sizeof(Params));
    Params.conn_mode = BLE_GAP_CONN_MODE_NON;
    Params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    Params.itvl_min = ADV_INTERVAL;
    Params.itvl_max = ADV_INTERVAL;

    Error = ble_gap_adv_start(_OwnAddrType, NULL, BLE_HS_FOREVER, &Params, NULL, NULL);
    if (Error != 0) {
        ESP_LOGE(TAG, "Cannot start advertising! Error: %d", Error);
    }
}

/** @brief      Generates new values and updates the advertisement periodically.
 */
static void SensorTask(void *p_Parameter)
{
    while (true) {
        _Temperature = Clamp(_Temperature + RandomStep(0.3f), 15.0f, 30.0f);
        _Humidity = Clamp(_Humidity + RandomStep(1.0f), 30.0f, 70.0f);
        _PacketId++;

        ESP_LOGI(TAG, "Advertising #%u: %.2f °C, %.2f %%", _PacketId, _Temperature, _Humidity);
        StartAdvertising();

        vTaskDelay(pdMS_TO_TICKS(UPDATE_INTERVAL_MS));
    }
}

/** @brief      Called by NimBLE once host and controller are in sync.
 */
static void OnSync(void)
{
    if (ble_hs_id_infer_auto(0, &_OwnAddrType) != 0) {
        ESP_LOGE(TAG, "Cannot determine the Bluetooth address type!");
        return;
    }

    uint8_t Address[6] = {0};
    ble_hs_id_copy_addr(_OwnAddrType, Address, NULL);
    ESP_LOGI(TAG, "Bluetooth address: %02X:%02X:%02X:%02X:%02X:%02X",
             Address[5], Address[4], Address[3], Address[2], Address[1], Address[0]);

    xTaskCreate(SensorTask, "sensorTask", 4096, NULL, 5, NULL);
}

static void OnReset(int Reason)
{
    ESP_LOGW(TAG, "NimBLE host reset! Reason: %d", Reason);
}

static void HostTask(void *p_Parameter)
{
    // Runs until nimble_port_stop() is called.
    nimble_port_run();
    nimble_port_freertos_deinit();
}

extern "C" void app_main(void)
{
    ESP_LOGI(TAG, "Starting BLE sensor simulator...");

    // NVS is required by the Bluetooth controller (PHY calibration data).
    esp_err_t Error = nvs_flash_init();
    if ((Error == ESP_ERR_NVS_NO_FREE_PAGES) || (Error == ESP_ERR_NVS_NEW_VERSION_FOUND)) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        Error = nvs_flash_init();
    }
    ESP_ERROR_CHECK(Error);

    ESP_ERROR_CHECK(nimble_port_init());

    ble_hs_cfg.sync_cb = OnSync;
    ble_hs_cfg.reset_cb = OnReset;

    nimble_port_freertos_init(HostTask);
}

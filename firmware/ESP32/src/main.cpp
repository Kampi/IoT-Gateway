#include <esp_log.h>
#include <esp_task_wdt.h>
#include <esp32/rom/rtc.h>
#include <esp_sleep.h>
#include <esp_wifi.h>
#include <esp_random.h>

#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/event_groups.h>

#include "rak3172.h"

#include "settings/LoRaWAN_Default.h"

static RTC_NOINIT_ATTR RAK3172_t _Device;

static StackType_t _applicationStack[8192];

static StaticTask_t _applicationBuffer;

static TaskHandle_t _applicationHandle;

static const char *TAG                          = "main";

static void applicationTask(void *p_Parameter)
{
    if (rtc_get_reset_reason(0) == POWERON_RESET) {
        RAK3172_Error_t Error;
        RAK3172_Info_t Info;

        _Device = RAK3172_DEFAULT_CONFIG(CONFIG_RAK3172_UART_PORT, CONFIG_RAK3172_UART_RX, CONFIG_RAK3172_UART_TX,
                                         CONFIG_RAK3172_UART_BAUD);
        _Device.Info = &Info;

        Error = RAK3172_Init(_Device);
        if (Error != RAK3172_ERR_OK) {
            ESP_LOGE(TAG, "Cannot initialize RAK3172! Error: 0x%04X", static_cast<unsigned int>(Error));
        }

        ESP_LOGI(TAG, "Firmware: %s", Info.Firmware.c_str());
        ESP_LOGI(TAG, "Serial number: %s", Info.Serial.c_str());
        ESP_LOGI(TAG, "Current mode: %u", _Device.Mode);

        Error = RAK3172_LoRaWAN_Init(_Device, 16, RAK_JOIN_OTAA, DEVEUI, APPEUI, APPKEY, RAK_CLASS_A, RAK_BAND_EU868,
                                     RAK_SUB_BAND_NONE);
        if (Error != RAK3172_ERR_OK) {
            ESP_LOGE(TAG, "Cannot initialize RAK3172 LoRaWAN! Error: 0x%04X", static_cast<unsigned int>(Error));
        }

        Error = RAK3172_LoRaWAN_StartJoin(_Device, LORAWAN_JOIN_ATTEMPTS, RAK3172_NO_TIMEOUT, true, false,
                                          LORAWAN_MAX_JOIN_INTERVAL_S);
        if (Error != RAK3172_ERR_OK) {
            ESP_LOGE(TAG, "Cannot join network! Error: 0x%04X", static_cast<unsigned int>(Error));
        }

        if (RAK3172_LoRaWAN_isJoined(_Device, true)) {
            ESP_LOGI(TAG, "Joined...");
        }

#ifdef CONFIG_RAK3172_USE_AUTO_LP
        Error = RAK3172_SetPwrMode(_Device, RAK_PWRMODE_STOP2);
        Error |= RAK3172_EnableAutoLowPower(_Device, true);
        if (Error != RAK3172_ERR_OK) {
            ESP_LOGE(TAG, "Cannot enable automatic low power mode! Error: 0x%04X", static_cast<unsigned int>(Error));
        }
#endif
    } else if ((rtc_get_reset_reason(0) == DEEPSLEEP_RESET) || (rtc_get_reset_reason(1) == DEEPSLEEP_RESET)) {
        RAK3172_Error_t Error;

        Error = RAK3172_WakeUp(_Device);
        if (Error != RAK3172_ERR_OK) {
            ESP_LOGW(TAG, "Wake up failed! Error: 0x%04X", static_cast<unsigned int>(Error));
        }

        // The module needs a moment after AT+SLEEP. If the first join status query fails, the
        // device would rejoin on every wake up (new DevNonce, ~30 s SF12 airtime). Retry instead.
        bool isJoined = false;
        uint8_t Attempt = 0;
        while ((isJoined == false) && (Attempt < 3)) {
            if (Attempt > 0) {
                vTaskDelay(100 / portTICK_PERIOD_MS);
            }

            isJoined = RAK3172_LoRaWAN_isJoined(_Device);
            Attempt++;
        }

        if (isJoined && (Attempt > 1)) {
            ESP_LOGW(TAG, "Join status read after %u attempts", static_cast<unsigned int>(Attempt));
        }

        if (isJoined == false) {
            ESP_LOGI(TAG, "Not joined. Rejoin...");

            Error = RAK3172_LoRaWAN_StartJoin(_Device, LORAWAN_JOIN_ATTEMPTS, RAK3172_NO_TIMEOUT, true, false,
                                              LORAWAN_MAX_JOIN_INTERVAL_S);
            if (Error != RAK3172_ERR_OK) {
                ESP_LOGE(TAG, "Cannot join network! Error: 0x%04X", static_cast<unsigned int>(Error));
            }
        }

        if (RAK3172_LoRaWAN_isJoined(_Device, true)) {
            std::string Payload;
            float Temperature;

            ESP_LOGI(TAG, "Joined...");

            Temperature = 20.0f + (static_cast<float>(esp_random() % 2001) / 100.0f);
            Payload = "{\"temperature\": " + std::to_string(Temperature) + "}";
            ESP_LOGI(TAG, "Sending temperature: %s °C", Payload.c_str());

#ifdef CONFIG_RAK3172_USE_AUTO_LP
            Error = RAK3172_LoRaWAN_Transmit(_Device, 1, Payload.c_str(), Payload.size(), false, 0, false);
#else
            Error = RAK3172_LoRaWAN_Transmit(_Device, 1, Payload.c_str(), Payload.size(), false, 0);
#endif

            if (Error == RAK3172_ERR_INVALID_RESPONSE) {
                ESP_LOGE(TAG, "Cannot transmit message! Error: 0x%04X", static_cast<unsigned int>(Error));
            } else {
                RAK3172_Rx_t Message;

                ESP_LOGI(TAG, "Message transmitted...");
                Error = RAK3172_LoRaWAN_Receive(_Device, &Message);
                if (Error != RAK3172_ERR_OK) {
                    ESP_LOGE(TAG, "Cannot receive message! Error: 0x%04X", static_cast<unsigned int>(Error));
                } else {
                    ESP_LOGI(TAG, " RSSI: %i", Message.RSSI);
                    ESP_LOGI(TAG, " SNR: %i", Message.SNR);
                    ESP_LOGI(TAG, " Port: %u", Message.Port);
                    ESP_LOGI(TAG, " Payload: %s", Message.Payload.c_str());
                }
            }
        }
    }

#ifdef CONFIG_RAK3172_USE_RUI3
#ifndef CONFIG_RAK3172_USE_AUTO_LP
    RAK3172_Sleep(_Device);
#endif
#endif

    // Prepare the driver for entering sleep mode.
    RAK3172_Deinit(_Device);

    // Disable all wakeup sources.
    esp_sleep_disable_wakeup_source(ESP_SLEEP_WAKEUP_ALL);

    // Sleep for 20 seconds.
    esp_sleep_enable_timer_wakeup(static_cast<uint64_t>(20) * 1000000ULL);

    // Disable the WiFi interface.
    esp_wifi_disconnect();
    esp_wifi_stop();
    vTaskDelay(100 / portTICK_PERIOD_MS);
    esp_wifi_deinit();

    esp_deep_sleep_start();
}

extern "C" void app_main(void)
{
    ESP_LOGI(TAG, "Starting application...");

    _applicationHandle = xTaskCreateStatic(applicationTask, "applicationTask", sizeof(_applicationStack), NULL, 1,
                                           _applicationStack, &_applicationBuffer);
    if (_applicationHandle == NULL) {
        ESP_LOGE(TAG, "    Unable to create application task!");

        esp_restart();
    }
}
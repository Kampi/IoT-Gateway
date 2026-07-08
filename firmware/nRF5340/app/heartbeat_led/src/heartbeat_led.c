#include <zephyr/kernel.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/init.h>

#define HEARTBEAT_LED_NODE 		DT_ALIAS(led0)
#define HEARTBEAT_PERIOD 		K_SECONDS(1)

static const struct gpio_dt_spec heartbeat_led = GPIO_DT_SPEC_GET(HEARTBEAT_LED_NODE, gpios);

static void heartbeat_led_expire(struct k_timer *timer_id)
{
	gpio_pin_toggle_dt(&heartbeat_led);
}

K_TIMER_DEFINE(heartbeat_led_timer, heartbeat_led_expire, NULL);

static int heartbeat_led_init(void)
{
	int ret;

	if (!gpio_is_ready_dt(&heartbeat_led)) {
		return -ENODEV;
	}

	ret = gpio_pin_configure_dt(&heartbeat_led, GPIO_OUTPUT_INACTIVE);
	if (ret < 0) {
		return ret;
	}

	k_timer_start(&heartbeat_led_timer, HEARTBEAT_PERIOD, HEARTBEAT_PERIOD);

	return 0;
}

SYS_INIT(heartbeat_led_init, APPLICATION, CONFIG_APPLICATION_INIT_PRIORITY);

#ifndef LORAWAN_DEFAULT_H_
#define LORAWAN_DEFAULT_H_

#include <stdint.h>

/** @brief  The APPEUI is in big-endian.
 */
static const uint8_t APPEUI[8] = { 0x18, 0x26, 0x8a, 0x12, 0x5a, 0xaf, 0xa2, 0x95 };

/** @brief  The DEVEUI is in big-endian.
 */
static const uint8_t DEVEUI[8] = { 0x79, 0xfb, 0x8b, 0x19, 0x4e, 0x47, 0x0f, 0xcc };

/** @brief  The APPKEY is in big-endian.
 */
static const uint8_t APPKEY[16] = { 0xdf, 0xc4, 0x58, 0x88, 0xa5, 0x0b, 0x4b, 0x3e, 0xeb, 0xda, 0x66, 0xe0, 0x49, 0x66, 0x12, 0x90 };

/** @brief Default JOIN attempts when using LoRaWAN mode.
 */
#define LORAWAN_JOIN_ATTEMPTS           2

/** @brief Maximum JOIN interval when using LoRaWAN mode.
 */
#define LORAWAN_MAX_JOIN_INTERVAL_S     15

/** @brief Default transmission attempts when using LoRaWAN mode.
 */
#define LORAWAN_TX_ATTEMPTS             5

#endif /* LORAWAN_DEFAULT_H_ */
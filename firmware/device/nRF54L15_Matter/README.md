# nRF54L15_Matter - Matter-over-Thread light bulb

Test device for the gateway's Thread border router. An nRF54L15-DK acts as a
Matter **On/Off + dimmable light** that joins the Thread network formed by the
gateway (nRF5340-DK as RCP, `otbr-agent`) and is controlled from Home
Assistant through Matter.

```text
Phone (BLE commissioning) --> nRF54L15-DK
nRF54L15-DK --Thread--> nRF5340-DK (RCP) --Spinel--> otbr-agent (IoT-Gateway) --IPv6--> Home Assistant Matter server
```

The application is a copy of Nordic's `nrf/samples/matter/light_bulb` from
nRF Connect SDK v3.3.4, reduced to the nRF54L15-DK (no AWS IoT integration,
no other boards). Product name: `IoT-Gateway Light`.

## Build and flash

Matter needs the full nRF Connect SDK, so this project builds against an
existing SDK install instead of its own west workspace:

| Variable | Default | Purpose |
| --- | --- | --- |
| `NCS_DIR` | `~/ncs/v3.3.4` | nRF Connect SDK install |
| `BOARD` | `nrf54l15dk/nrf54l15/cpuapp` | Zephyr board |
| `VARIANT` | `debug` | `release` uses `prj_release.conf` (no logs/shell) |
| `SNR` | - | J-Link serial of the DK, required with several probes |

```bash
cd firmware/nRF54L15_Matter
cp toolchain.mk.example toolchain.mk   # set NCS_TC (toolchain for NCS_DIR) and SNR
make build          # ~3-4 min clean build
make erase-flash    # first flash, wipes fabric + Thread credentials
make flash          # later flashes: app image only, keeps commissioning
make qrcode         # prints the onboarding QR payload / manual code
make console        # debug log + Matter shell on VCOM1 (/dev/ttyACM1)
```

> When the gateway's nRF5340-DK (PCA10095) is also connected via USB, always
> set `SNR` to the nRF54L15-DK (PCA10156, `nrfutil device list`). Otherwise
> `west flash` refuses to guess, or with an explicit wrong serial overwrites
> the RCP.

## Onboarding data

Factory data is generated during the build with the Matter test values
(`build/app/zephyr/factory_data.{txt,png}`):

| | |
| --- | --- |
| QR code | `MT:6FCJ142C00KA0648G00` |
| Manual pairing code | `34970112332` |
| Passcode / discriminator | `20202021` / `3840` |
| Vendor ID / product ID | `0xFFF1` (test vendor) / `0x8005` |

A scannable QR code: `build/app/zephyr/factory_data.png`, or
<https://project-chip.github.io/connectedhomeip/qrcode.html?data=MT%3A6FCJ142C00KA0648G00>.

Controllers show the device as uncertified (test vendor ID). That is expected.

## Buttons and LEDs (nRF54L15-DK)

| Element | Function |
| --- | --- |
| LED 0 | Status: short flash = waiting for commissioning, fast blink = BLE connection, solid = commissioned |
| LED 1 | The light (PWM, dimmable). Blinks on Matter *Identify* |
| Button 0 | < 3 s: restart BLE advertising. > 3 s: factory reset |
| Button 1 | Toggle the light locally |

BLE advertising starts automatically after boot and runs for 60 minutes.

## Commissioning with Home Assistant

Matter-over-Thread only works if the phone hands the **gateway's** Thread
credentials to the device. This is the most common reason commissioning fails.

1. Home Assistant: add the **Open Thread Border Router** integration with the
   gateway's LAN address, `http://<gateway-lan-ip>:8081` (not the build-host
   link `192.168.0.2`, the HA host cannot reach it).
2. Home Assistant: *Settings → Devices & services → Thread → Configure*. The
   network the gateway is running (`ot-ctl networkname`, e.g.
   `ha-thread-62d4`) must be the **preferred network**, with the border
   router listed under it. Delete stale networks nobody runs anymore.
3. **Push the credentials to the phone (Android):** in the companion app's own
   settings, *Settings → Companion app → Troubleshooting → **Sync Thread
   credentials***. Only this button copies HA's preferred network into Google
   Play Services. See the note below.
4. Put the device in pairing mode, then *Settings → Devices & services → Add
   device → Add Matter device* in the companion app and scan the QR code. The
   phone commissions over BLE, the device joins Thread, then the phone hands
   it to HA's Matter server.
5. Check: LED 0 is solid, the debug console shows `[DL]OpenThread ... attached`,
   and the device is listed as a child or router on the gateway
   (`ot-ctl child table` / `ot-ctl router table`, or `/node` on the REST API).

> **Thread credentials on Android, the trap we hit:**
>
> - Google Play Services shows "Your device needs a Thread border router"
>   whenever the phone has **no Thread credentials stored**, even if the
>   border router is reachable and valid.
> - The networks listed as "not available" under *Android Settings → Google →
>   … → Thread networks* ("Other") are border routers the phone *sees* via
>   mDNS but has no credentials for. That is not a reachability problem.
> - The credentials button in HA's Thread web page (in the app's web view)
>   goes the **other way** (phone → HA). With an empty phone it reports
>   "no credentials to import".
> - Only *Companion app → Troubleshooting → Sync Thread credentials* (step 3)
>   copies HA → phone. It always copies HA's **preferred** network, so step 2
>   must be right first. Re-run it whenever the Thread network is recreated
>   (e.g. by "Reset border router" in HA).
>
> Debugging: with USB debugging on, `adb logcat | grep GmsThreadNetwork` shows
> what Google sees, e.g. `Found valid Border Agent mDNS service` (border
> router OK) and `No preferred Thread network credentials found` (phone has
> no credentials).
>
> The LAN router needs a static IPv6 route to the gateway's fixed OMR prefix
> (Dahlia setup: `fdf2:83ec:1caf:1::/64`), see
> [scripts/home-assistant/README.md](../../scripts/home-assistant/README.md).

### Without a phone (on-network commissioning)

Home Assistant's Matter server has no Bluetooth ("Bluetooth commissioning not
available"). Instead, give the DK the gateway's Thread dataset over the debug
console, so it joins Thread uncommissioned and is reachable via IP:

```bash
# on the build host: active dataset as hex TLVs (contains the network key!)
curl -s -H 'Accept: text/plain' http://<gateway>:8081/node/dataset/active
```

```text
ot thread stop
ot ifconfig down
ot dataset set active <hex>
ot ifconfig up
ot thread start
```

The DK then registers `_matterc._udp` with the gateway's SRP server
(`ot-ctl srp server service` on the gateway) and Home Assistant can commission
it over the network with the manual code `34970112332`.

This needs border routing and the SRP server running on the gateway
(`ot-ctl br state` and `ot-ctl srp server state` = `running`), which requires
IPv6 on otbr-agent's infra interface (`-B`). On the Dahlia setup that is
`mlan0`, not `end0`.

### Troubleshooting (found during first bring-up)

| Symptom | Cause / fix |
| --- | --- |
| `ot-ctl br state` = `stopped`, SRP server `disabled` | otbr-agent's infra interface (`-B`) has no IPv6. Use the LAN interface (`mlan0` on the Dahlia). |
| Device found via mDNS, but no packets reach `wpan0` | IPv6 forwarding off on the gateway (`net.ipv6.conf.all.forwarding=1`). |
| LAN hosts cannot reach the OMR prefix (`fdf2:83ec:1caf:1::/64`) | Host ignores route info in RAs (e.g. Synology). Add a static IPv6 route on the router: OMR prefix via the gateway's LAN address. Keep the prefix fixed with `OTBR_OMR_PREFIX`. |
| Matter server: `Cannot assign requested address` | Matter server container has no IPv6 (e.g. IPv4-only macvlan). Run it with `network_mode: host` and `--primary-interface <lan-if>`. |

To start over: hold Button 0 > 3 s (factory reset), or `make erase-flash`, and
remove the device in Home Assistant.

## Debug console

`make console` (115200 baud, VCOM1). Useful commands:

```text
matter onboardingcodes ble     # QR / manual code
matter device factoryreset
ot state                       # disabled / detached / child / router / leader
ot dataset active
ot neighbor table
```

`ot state` stays `disabled` until a commissioner has passed the Thread
dataset. After commissioning, `ot dataset active` must show the gateway's
network name, channel and PAN ID.

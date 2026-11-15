<!-- SPDX-License-Identifier: MIT -->
<!-- Copyright (c) 2026 Daniel Kampert -->

# OTA updates with RAUC

GatewayOS updates the whole system (rootfs, kernel, devicetree) over the
network with [RAUC](https://rauc.io): a signed bundle is written to the slot
that is not running, the gateway boots it once on trial and keeps it only if
it comes up. Settings and data on the `DATA` partition are not touched.

The first installation of the A/B layout needs the Toradex Easy Installer
([tezi-feed-raspberry-pi.md](tezi-feed-raspberry-pi.md)), every later update
is an OTA update.

---

## Overview

```text
build host                              gateway
──────────                              ─────────────────────────────────────────
bitbake gateway-os-bundle               rauc install <bundle | URL>
  └─ .raucb (signed, verity) ──HTTP/scp──>  ├─ check signature (CA /etc/rauc/ca.cert.pem)
                                            ├─ check compatible (iot-gateway-<board>)
                                            ├─ write rootfs to inactive slot (A → B, B → A)
                                            └─ U-Boot: BOOT_ORDER = "<new> <old>"
                                        reboot
                                          └─ U-Boot boots the new slot (3 attempts)
                                               └─ rauc-mark-good.service → slot "good"
```

| eMMC partition | Content | Updated by |
| --- | --- | --- |
| `mmcblk0boot0` | U-Boot (`imx-boot`), U-Boot environment | Easy Installer only |
| `mmcblk0p1` `BOOT` | boot script (slot selection) | Easy Installer only |
| `mmcblk0p2` `RFS_A` | rootfs slot A incl. `/boot` (kernel, devicetree) | RAUC |
| `mmcblk0p3` `RFS_B` | rootfs slot B | RAUC |
| `mmcblk0p4` `DATA` | `/data`: overlays of `/etc`, `/var/lib`, `/home`, `/root`, RAUC state | never (only by the gateway itself) |

Configuration: `recipes-core/rauc/files/system.conf` (slots, bootloader
`uboot`, keyring), bundle recipe `recipes-core/bundles/gateway-os-bundle.bb`,
boot script `recipes-bsp/u-boot/u-boot-distro-boot/boot.cmd.in`.

---

## 1. Build a bundle

1. Set the new version in `conf/distro/gateway-os.conf` (`DISTRO_VERSION`).
   It becomes the bundle version (`rauc info`, `rauc status`) and the
   Easy Installer image version. RAUC does not block older versions, so the
   version is only for tracking.
2. Build:

   ```bash
   source layers/openembedded-core/oe-init-build-env build
   bitbake gateway-os-bundle
   ```

   Result: `build/deploy/images/verdin-imx8mm/gateway-os-bundle-verdin-imx8mm.raucb`
   (link to the file with the build timestamp).
3. Check:

   ```bash
   rauc info --keyring=keys/rauc/ca.cert.pem \
       build/deploy/images/verdin-imx8mm/gateway-os-bundle-verdin-imx8mm.raucb
   ```

   (`rauc` from the build: `build/tmp/sysroots-components/x86_64/rauc-native/usr/bin/rauc`)

| Bundle property | Value |
| --- | --- |
| Format | `verity` (dm-verity, needed for streaming, checked block by block) |
| Adaptive | `block-hash-index`: when streaming, the gateway only downloads the 4 KiB blocks it does not already have in one of its slots (hash index per slot under `/data/rauc`). The first update into a slot downloads everything, later ones mostly the changed files |
| Compatible | `iot-gateway-<GATEWAY_BOARD>`: a Dahlia bundle is rejected by a mainboard gateway and vice versa |
| Content | ext4 image of `gateway-os-image` (`rootfs` slot class) |
| Signature | `RAUC_KEY_FILE` / `RAUC_CERT_FILE` (default `os/keys/rauc/development-1.*`, public debug key) |

---

## 2. Install

### a) Streaming over HTTP (recommended)

The gateway reads the bundle directly from a web server, nothing is stored on
the gateway. Any server with HTTP range requests works (nginx, Apache,
`python3 -m http.server` does **not** support ranges).

```bash
# web server, e.g. the Raspberry Pi of the Easy Installer feed
scp build/deploy/images/verdin-imx8mm/gateway-os-bundle-verdin-imx8mm.raucb \
    <user>@<server>:/var/www/html/bundles/

# gateway
rauc install http://<server>/bundles/gateway-os-bundle-verdin-imx8mm.raucb
```

Dahlia setup with the Raspberry Pi: `http://192.168.0.1/bundles/...`
(gateway Ethernet `192.168.0.2`) or the Pi's LAN/Wi-Fi address.

### b) Local file

Needs free space for the whole bundle (~800 MB) on `/data`:

```bash
scp build/deploy/images/verdin-imx8mm/gateway-os-bundle-verdin-imx8mm.raucb root@<gateway>:/data/
ssh root@<gateway> 'rauc install /data/gateway-os-bundle-verdin-imx8mm.raucb && rm /data/gateway-os-bundle-verdin-imx8mm.raucb'
```

`rauc install` prints the progress and ends with
`Installing ... succeeded`. Nothing changes on the running system until the
reboot.

---

## 3. Activate

```bash
ssh root@<gateway> reboot
```

1. U-Boot starts the new slot first (`BOOT_ORDER`), its attempt counter
   (`BOOT_<slot>_LEFT`) goes from 3 to 2.
2. `/sbin/preinit` mounts `DATA` and the overlays, systemd starts.
3. `gateway-health-check.service` (before `boot-complete.target`) checks the
   new system, see "Health check" below.
4. Only after a passed check `rauc-mark-good.service` (requires
   `boot-complete.target`) marks the slot `good` and resets the counter to 3.

Check after the reboot:

```bash
rauc status                        # Booted from: rootfs.<n> (new slot), both "good"
fw_printenv BOOT_ORDER BOOT_A_LEFT BOOT_B_LEFT
cat /etc/os-release                # new version
systemctl --failed
```

---

### Health check

`gateway-health-check` runs on every boot, but only checks a slot whose
bundle (hash) has not been verified yet, i.e. the first boot after an
update. It waits up to `GATEWAY_HEALTH_TIMEOUT` seconds (default 300) until
these units have been active for 30 s without interruption:

- `GATEWAY_HEALTH_UNITS` (default `NetworkManager`, `otbr-agent`,
  `gateway-telemetry`)
- `GATEWAY_HEALTH_DEVICE_UNITS`: units of USB peripherals that are plugged
  in (default `/dev/zigbee` → `zigbee2mqtt`, `/dev/lora` →
  `lora-packet-forwarder`)

Passed: the bundle hash is recorded in `/data/gateway-health/<A|B>`, the slot
is marked good. Failed: `rauc status mark-bad booted` and reboot, U-Boot
starts the previous slot (rollback). The check never rolls back when the
other slot is not `good` (no boot loop). A verified bundle is never checked
again, so a missing peripheral or a broker outage later cannot roll back a
system that has worked. Log: `journalctl -u gateway-health-check`.

---

## 4. Updates from Home Assistant

With `GATEWAY_UPDATE_SERVER` (base URL of the update server, e.g.
`http://192.168.0.1` for the Raspberry Pi feed server) `gateway-telemetry` uses
the bundle `<server>/bundles/gateway-os-bundle-<MACHINE>.raucb` and adds the update entity **GatewayOS** to the
"IoT-Gateway" device:

- **Installed version:** bundle version and build of the booted slot
  (`rauc status --detailed`), e.g. `1.0.0 (20261114194300)`; a slot written by
  the Easy Installer shows `/etc/os-release`.
- **Latest version:** `rauc info <URL>` at start and every
  `GATEWAY_UPDATE_CHECK_INTERVAL` seconds (default 3600). Reads only the
  manifest, no download. Same bundle hash as the booted slot = up to date.
- **Install:** the install button in Home Assistant runs
  `rauc install <URL>` (streaming, adaptive), shows the progress in % and
  reboots afterwards (`GATEWAY_UPDATE_REBOOT`, default 1). The health check
  then decides about good / rollback.

New update for all gateways: build the bundle, copy it to the URL (keep the
file name), Home Assistant shows it within the check interval.

---

## 5. Rollback

**Automatic:** a failed health check marks the new slot bad and reboots
into the previous one right away. A slot that does not even get there
(kernel panic, hang → watchdog reset after 30 s, boot loop) loses one attempt
per boot; after 3 failed attempts U-Boot skips it and boots the previous slot
again.
`rauc status` then shows the new slot with attempts used up, the gateway runs
the old system.

**Manual**, e.g. the new version boots but a service misbehaves:

```bash
rauc status mark-active other      # previous slot first in BOOT_ORDER
reboot
```

The previous slot still contains the last version until the next
`rauc install` overwrites it.

**Mark a slot as bad** (never boot it again until the next install into it):

```bash
rauc status mark-bad booted        # or: mark-bad other
```

---

## 6. Persistent data during updates

The rootfs is read-only. `/etc`, `/var/lib`, `/home` and `/root` are overlays: the lower layer is the rootfs of
the booted slot, the upper layer `/data/overlay-<dir>/upper` holds every file
changed on the gateway.

- **Kept:** everything written on the gateway: Wi-Fi/network settings, SSH
  host keys, `machine-id` (Home Assistant identity), hostname, Zigbee2MQTT
  database and configuration, Thread dataset, backup key, journal archive.
- **Updated:** files of the new image that were not changed on the gateway,
  e.g. systemd units, udev rules, `/etc/default/*`, scripts in `/usr`.
- **Not updated:** files of the new image that exist in the upper layer. A
  changed default in the image (e.g. in `/etc/default/gateway-telemetry`)
  does not reach a gateway that has its own copy of the file. Zigbee2MQTT
  always has its own `/var/lib/zigbee2mqtt/configuration.yaml` (it rewrites
  the file at runtime).

  Reset a file to the image version: `rm /data/overlay-etc/upper/<path>` and
  reboot (do not edit the upper directories while they are mounted, the
  change may not show up until the next boot).
- Migrations that cannot be expressed as files of the image (e.g. a new
  required option in an existing config) need a script in the new image that
  updates the persistent copy at boot.

Before larger updates: run a backup (`systemctl start gateway-backup`, README
"Backup").

---

## 7. Keys

All keys are in `os/keys/` (`GATEWAY_KEYS_DIR`, [os/keys/README.md](../../../keys/README.md)).
The ones in the repository are **public debug keys**: anybody with the
repository can sign bundles a gateway built with them accepts. Gateways in
the field need their own keys outside git (`GATEWAY_KEYS_DIR`).

| File (`os/keys/rauc/`) | Purpose |
| --- | --- |
| `ca.key.pem` | CA private key: issues signing certificates. Keep offline |
| `ca.cert.pem` | CA certificate = keyring on the gateway (`RAUC_KEYRING_FILE_PATH` → `/etc/rauc/ca.cert.pem`), valid until 2046 |
| `development-1.key.pem` / `.cert.pem` | signing key + certificate used by `bitbake gateway-os-bundle` |

- The gateway accepts every bundle signed by a certificate of this CA. With
  own keys: losing the CA key and the signing key means no more OTA updates,
  only the Easy Installer. Back them up offline.
- New signing key (e.g. per person or CI), issued by the same CA, gateways
  need no change:

  ```bash
  cd os/keys/rauc
  openssl req -newkey rsa:4096 -nodes -keyout release-1.key.pem -out release-1.csr.pem \
      -subj "/O=IoT-Gateway/CN=IoT-Gateway RAUC Release-1"
  openssl x509 -req -in release-1.csr.pem -CA ca.cert.pem -CAkey ca.key.pem -CAcreateserial \
      -out release-1.cert.pem -days 3650 -extfile openssl.cnf -extensions v3_leaf
  ```

  and set `RAUC_KEY_FILE` / `RAUC_CERT_FILE` in `local.conf`
  (run in the keys folder, `GATEWAY_KEYS_DIR`).
- Replacing the CA itself needs a bundle that installs the new keyring
  (`ca.cert.pem`), signed with the old CA, before bundles of the new CA are
  accepted.

---

## 8. Limits

- U-Boot, its environment and the boot script are not part of the bundle.
  Changes there (e.g. `boot.cmd.in`, `imx-boot`) need the Easy Installer.
- Changes of the partition layout (`GATEWAY_ROOTFS_SLOT_SIZE`) need the Easy
  Installer, `DATA` is formatted then (backup/restore, README "Backup").
- The rootfs must fit into a slot (`GATEWAY_ROOTFS_SLOT_SIZE`, the build fails
  above 80 %). RAUC grows the ext4 file system to the slot size after writing
  (`resize=true`).

---

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `Compatible mismatch` | bundle of another board (`GATEWAY_BOARD`) |
| `signature verification failed: ... unsuitable certificate purpose` | signing certificate with `extendedKeyUsage = codeSigning` only: RAUC checks the S/MIME signing purpose (no `check-purpose` in `system.conf`). Issue it without EKU (`keys/rauc/openssl.cnf`, `v3_leaf`) |
| `Failed to run resize2fs` | the **running** system has no `resize2fs` (`resize=true` in `system.conf`); `e2fsprogs-resize2fs` is part of the image, older images: copy it to `/usr/local/sbin` once |
| `signature verification failed` / `unable to get local issuer certificate` | bundle signed with a key of another CA, or keyring on the gateway replaced |
| `Failed mounting bundle: ... verity` / `squashfs` | kernel without `rauc.cfg` options (dm-verity, squashfs, loop) |
| streaming: `Failed to start nbd` / HTTP errors | server without range requests, or not reachable from the gateway |
| `fw_setenv`/`Failed to set bootloader` | `/etc/fw_env.config` must point to `/dev/mmcblk0boot0 -0x2200 0x2000` |
| gateway always back on the old slot after an update | new slot did not reach `rauc-mark-good`: serial console (U-Boot prints the selected slot, preinit its mounts), `journalctl -b -1` is not available (journal in RAM) |
| update does not change a config file | file has a copy in `/data/overlay-*/upper`, see "Persistent data" |

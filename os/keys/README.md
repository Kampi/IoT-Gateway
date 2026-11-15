# Keys

All keys of the GatewayOS build in one place (`GATEWAY_KEYS_DIR`, default
`os/keys`).

> **All keys in this folder are public DEBUG keys.** They are in git so every
> checkout can build, sign and log in out of the box. Anybody with this
> repository can log in as root on a gateway built with them and sign RAUC
> bundles and nRF5340 images it accepts. Use your own keys for gateways in the
> field (see below).

| File | Used by | In git |
| --- | --- | --- |
| `gateway_ed25519` / `.ppk` (PuTTY) | SSH login as root on the gateway (`ssh -i os/keys/gateway_ed25519 root@<gateway>`) | yes (debug) |
| `gateway_ed25519.pub` | `/root/.ssh/authorized_keys` of the image (`openssh` bbappend, `GATEWAY_SSH_AUTHORIZED_KEYS`) | yes (debug) |
| `rauc/ca.cert.pem` | RAUC keyring on the gateway (`/etc/rauc/ca.cert.pem`, `rauc-conf`): bundles must be signed by this CA | yes (debug) |
| `rauc/ca.key.pem` | RAUC CA private key, issues signing certificates | yes (debug) |
| `rauc/development-1.key.pem` / `.cert.pem` | signs the RAUC bundles (`gateway-os-bundle`, `RAUC_KEY_FILE` / `RAUC_CERT_FILE`) | yes (debug) |
| `rauc/openssl.cnf` | certificate extensions for new signing keys (`docs/ota-rauc.md`, "Keys") | yes |
| `nrf5340/dfu_signing_key.pem` | Ed25519 key of MCUboot on the nRF5340 Thread RCP: MCUboot only boots images signed with it (`nrf5340-rcp-firmware`, `NRF5340_DFU_KEY`) | yes (debug) |
| `nrf5340/dfu_signing_key.pub.pem` | public part (reference only, MCUboot embeds it at build time) | yes |

## Own keys (production)

Keep them outside git and point the build to them in `local.conf`:

```bitbake
GATEWAY_KEYS_DIR = "/secure/gateway-keys"     # same layout as this folder
```

or per key: `GATEWAY_SSH_AUTHORIZED_KEYS`, `RAUC_KEY_FILE`, `RAUC_CERT_FILE`,
`RAUC_KEYRING_FILE_PATH`, `NRF5340_DFU_KEY`.

Create them:

```bash
# SSH root login
ssh-keygen -t ed25519 -N "" -C "gateway-root" -f gateway_ed25519

# RAUC CA + signing key (openssl.cnf from rauc/)
openssl req -x509 -newkey rsa:4096 -nodes -keyout rauc/ca.key.pem -out rauc/ca.cert.pem \
    -days 7300 -config rauc/openssl.cnf -extensions v3_ca
openssl req -newkey rsa:4096 -nodes -keyout rauc/development-1.key.pem -out /tmp/dev.csr \
    -subj "/O=IoT-Gateway/CN=IoT-Gateway RAUC Development-1"
openssl x509 -req -in /tmp/dev.csr -CA rauc/ca.cert.pem -CAkey rauc/ca.key.pem -CAcreateserial \
    -out rauc/development-1.cert.pem -days 3650 -extfile rauc/openssl.cnf -extensions v3_leaf

# nRF5340 MCUboot key
openssl genpkey -algorithm ed25519 -out nrf5340/dfu_signing_key.pem
```

Changing keys on existing devices:

- **RAUC:** the gateway only accepts bundles of the CA in its keyring. Switch
  with one bundle that contains the new `ca.cert.pem` but is still signed with
  the old key, or flash with the Easy Installer.
- **nRF5340:** MCUboot only accepts images signed with its own key. A new key
  needs one full flash of MCUboot + firmware over SWD (J-Link), after that
  `flash-nrf5340` works again.

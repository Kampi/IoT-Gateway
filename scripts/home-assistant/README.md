# Home Assistant + Matter server (Docker)

Template [docker-compose.yml](docker-compose.yml) for Home Assistant with the
Matter server, so Matter-over-Thread devices behind the IoT-Gateway's Thread
border router can be commissioned and controlled.

```text
Home Assistant --ws:5580--> Matter server --IPv6/LAN--> IoT-Gateway (otbr-agent) --Thread--> device
```

## Setup

```bash
cp .env.example .env        # set MATTER_PRIMARY_INTERFACE
docker compose up -d --remove-orphans
```

Then in Home Assistant: *Settings → Devices & services → Add integration →
Matter*, URL `ws://localhost:5580/ws`.

After the first start the Matter server log must show:

```text
Using '<your interface>' as primary interface (for link-local addresses)
```

and **no** `Failed to advertise records ... Cannot assign requested address`.

## Values to adjust

| Where | Value | How to find it |
| --- | --- | --- |
| `.env` | `MATTER_PRIMARY_INTERFACE` | `ip -br addr` on the Docker host: the interface with the LAN IPv4 + IPv6 addresses (Synology: `ovs_ethX`) |
| `docker-compose.yml` | `./config`, `./matter/data` | Storage paths on the host. Keep existing paths when migrating, the Matter fabric lives in `./matter/data` |
| `docker-compose.yml` | `docker.sock` mount | Optional, remove if no integration needs it |

## Network prerequisites

The Docker host must be able to reach the Thread network's OMR prefix via
the gateway. Linux hosts normally learn this route from the gateway's Router
Advertisements, but many (e.g. Synology DSM) ignore route information in RAs.
Then add a static route on the router:

| Field | Value | How to find it |
| --- | --- | --- |
| Network / prefix length | the fixed OMR prefix, Dahlia setup: `fdf2:83ec:1caf:1::` / `64` | `OTBR_OMR_PREFIX` in the layer, on the gateway `ot-ctl br omrprefix` (line `Favored:`) |
| Gateway | the gateway's fixed IPv6 on the LAN interface, `fd8f:e72d:8065:0:3f1e:f55:3464:19db` | `OTBR_INFRA_IPV6_ADDRESS` in the layer, on the gateway `ip -6 addr show mlan0` |

FritzBox: *Home Network → Network → Network Settings → IPv6 Routes → New
IPv6 Route*.

The gateway sets a fixed OMR prefix (`OTBR_OMR_PREFIX`, see the Thread
section of the `meta-iot-gateway-os` README), so the route stays valid when
the Thread network is recreated or the gateway is re-flashed. Without it the
border router picks a random prefix per network and the route must follow.

On the gateway itself, otbr-agent must use the LAN interface (`-B mlan0` in
`/etc/default/otbr-agent`) and IPv6 forwarding must be on
(`/etc/sysctl.d/60-otbr-ipv6-forwarding.conf`). The `meta-iot-gateway-os`
layer sets both.

## Checks

| Check | Expected |
| --- | --- |
| Gateway: `ot-ctl br state`, `ot-ctl srp server state` | `running`, `running` |
| Gateway: `sysctl net.ipv6.conf.all.forwarding` | `1` |
| Docker host: `ping -6 <Thread device OMR address>` | replies |
| Matter server log on commissioning | no `Cannot assign requested address` |

## Why not macvlan / bridge

A macvlan network with only an IPv4 subnet gives the container no usable IPv6
address, and bridge networks block mDNS. Both break Matter. Host networking
also makes the extra socat proxy for port 5580 unnecessary.

## Commissioning with an Android phone

The Matter server here has no Bluetooth, so new devices are added through the
companion app. The phone first needs HA's Thread credentials:

1. *Settings → Devices & services → Thread → Configure*: the network the
   gateway runs must be the **preferred network**.
2. Companion app: *Settings → Companion app → Troubleshooting → **Sync Thread
   credentials***. This is the only button that copies HA → phone. The
   credentials button on HA's Thread web page goes phone → HA and reports
   "no credentials to import" on a phone without credentials.
3. *Add device → Add Matter device* in the companion app.

If Google says "Your device needs a Thread border router", the phone has no
Thread credentials (step 2 missing, or HA's preferred network is a stale one).
Check with `adb logcat | grep GmsThreadNetwork`. After the Thread network is
recreated, repeat step 2. The router route only needs updating if no fixed
OMR prefix is configured.

Bluetooth commissioning through the Matter server is not set up here (needs
BlueZ, an adapter and `--bluetooth-adapter`). Use the Home Assistant
companion app on a phone, or join the device to Thread first and commission
on-network, see
[firmware/nRF54L15_Matter/README.md](../../firmware/nRF54L15_Matter/README.md).

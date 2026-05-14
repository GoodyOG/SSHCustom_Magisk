# SSHCustom-Magisk

Magisk / KernelSU module that turns a rooted Android phone into an SSH-tunnel
router. Bundles a self-contained Go daemon, a local WebUI, a native Android
companion app, and the iptables glue that makes it all transparent.

[![Build SSHCustom](https://github.com/GoodyOG/SSHCustom_Magisk/actions/workflows/build.yml/badge.svg)](https://github.com/GoodyOG/SSHCustom_Magisk/actions/workflows/build.yml)
[![License: Apache-2.0 (module + daemon)](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![License: GPL-3.0 (companion app)](https://img.shields.io/badge/app%20license-GPL--3.0-orange.svg)](app/LICENSE)
[![Latest release](https://img.shields.io/github/v/release/GoodyOG/SSHCustom_Magisk?display_name=tag&sort=semver)](https://github.com/GoodyOG/SSHCustom_Magisk/releases/latest)

## What this gives you

- **A SOCKS5 proxy** at `127.0.0.1:1080` and a **transparent TCP listener**
  at `0.0.0.0:10810` that funnel app traffic through your SSH server.
- **iptables rules** that redirect outgoing TCP from the device (and
  optionally from tethered hotspot clients) into the transparent listener,
  so apps don't need to know anything about a proxy.
- **Pluggable carriers** — direct, HTTP-CONNECT proxy, TLS/SNI, payload
  injection, or any combination — for SSH endpoints behind aggressive ISPs.
- **A local dashboard** at `http://127.0.0.1:9190/` for profile management,
  network controls, and live diagnostics.
- **A native Android companion app** with Material You theming, a Quick
  Settings Tile, persistent notification, and on-boot autostart.

It runs on rooted Android (Magisk **or** KernelSU) on `arm64-v8a` and
`armeabi-v7a` devices.

## Repository layout

```text
cmd/sshcustomd/                 Go daemon entry point
internal/                       Daemon packages (config, state, api,
                                sshpool, transport, proxy, iptables,
                                dns, metrics, version, webui)
internal/apiv1/contracts.go     Stable JSON envelope contracts
third_party/golang.org/x/crypto Vendored x/crypto fork (see PATCHES.md)
src/module/                     Magisk/KernelSU module template
  ├─ module.prop, customize.sh, service.sh, action.sh, uninstall.sh
  ├─ META-INF/com/google/android/{update-binary,updater-script}
  ├─ bin/{arm,arm64}/sshcustomd     Prebuilt static binaries
  ├─ config/{config.json, profiles.json}
  ├─ scripts/{sshcustom.sh, sshcustom_watchdog.sh, net_clean.sh}
  └─ webroot/{index.html, favicon.svg}
app/                            Native Android companion (Compose + libsu)
docs/openapi.yaml               Daemon API specification
scripts/package_module.py       Reproducible ZIP packager
build.sh                        Cross-compile + validate + package
.github/workflows/build.yml     CI (module ZIP + signed APK + release)
VERSION                         Single source of truth for the version
```

## Quick install

1. Download the latest release from
   [Releases](https://github.com/GoodyOG/SSHCustom_Magisk/releases/latest).
   Two artifacts: `SSHCustom-Magisk-vX.Y.Z.zip` (the module flashable) and
   `app-release.apk` (the companion app).
2. Flash the ZIP through the **Magisk** or **KernelSU** app, reboot.
3. Open the module in Magisk/KernelSU and tap the **action button** to
   start the tunnel. State chips (○ stopped, 🟡 starting, ● running)
   update live in the module description.
4. Install the companion APK if you want a native UI; otherwise visit
   `http://127.0.0.1:9190/` in any browser on the phone.
5. Edit or import a profile, then **Save, Use & Restart** to bring up the
   tunnel. Apps' TCP traffic now exits via your SSH server.

## How it fits together

```text
   ┌─────────┐                  ┌──────────────────┐
   │ Apps    │  TCP             │ /api/v1/* + SSE  │
   │         │ ──┐              │ /favicon.svg     │
   └─────────┘   │ iptables     │ /                │ ◄── browser / app
                 │ REDIRECT     └──────────┬───────┘
                 ▼                          │
   ┌──────────────────────────┐             │
   │ Transparent listener     │             │
   │ 0.0.0.0:10810            │             │
   │   SO_ORIGINAL_DST=80     │             │
   │   pool.Dial(target)      │             │
   └────────────┬─────────────┘             │
                │                            │
                ▼                            ▼
   ┌──────────────────────────────────────────────┐
   │ SSH connection pool (sshcustomd)             │
   │  ▸ pluggable carrier chain:                  │
   │      tcp → [http_proxy] → [tls] → [payload]  │
   │  ▸ Android-aware DNS (see internal/dnsx)     │
   │  ▸ circuit breaker, route-debounced reconnect│
   └──────────────────────────────────────────────┘
                │
                ▼
            SSH server  ─►  internet
```

The Go daemon handles SOCKS5, transparent TCP, the connection pool, the
HTTP API, and the SSE event stream. iptables glue lives in
`internal/iptables` and is invoked at start time and torn down at stop
time. Tethered hotspot clients are routed through the same chain via a
`PREROUTING` hook, so a laptop tethered to the phone shares the SSH exit
IP without extra config.

For a much deeper code tour, see [`docs/PHASE2_TRANSPORT_PROBE.md`](docs/PHASE2_TRANSPORT_PROBE.md).

## Companion app

The native Android app is GPL-3.0 (because it inherits patterns from
KernelSU-Next). It does not run the tunnel itself — it talks to the
daemon's `/api/v1/*` surface and uses libsu to invoke the module's
control scripts when the daemon is offline.

Highlights:

- **Material You dynamic colours** on Android 12+, fixed palette below.
- **Foreground service** that consumes the daemon's SSE event stream and
  keeps a persistent notification accurate.
- **Quick Settings Tile** for one-tap toggle from the system shade.
- **Boot receiver** that auto-launches the foreground service on boot
  when autostart is enabled.
- **Profile import/export** via the Storage Access Framework.

App-specific build notes are in [`app/README.md`](app/README.md).

## API

The daemon exposes a small REST + SSE surface bound to loopback at
`127.0.0.1:9190`. Full schema in
[`docs/openapi.yaml`](docs/openapi.yaml). Highlights:

| Method | Path                              | Purpose                              |
|--------|-----------------------------------|--------------------------------------|
| GET    | `/api/v1/health`                  | Liveness probe                       |
| GET    | `/api/v1/status`                  | Runtime + config + paths snapshot    |
| GET    | `/api/v1/events`                  | SSE stream of status updates         |
| GET    | `/api/v1/diagnostics`             | Pool stats, last events, route info  |
| GET    | `/api/v1/network/public-ip`       | Tunnel public-IP geo lookup          |
| GET    | `/api/v1/profiles`                | List profiles                        |
| POST   | `/api/v1/profile/save`            | Create/update a profile              |
| POST   | `/api/v1/profile/select`          | Switch the active profile            |
| GET    | `/api/v1/config`                  | Read runtime config                  |
| POST   | `/api/v1/config`                  | Patch runtime config (JSON merge)    |
| POST   | `/api/v1/control`                 | `start` / `stop` / `restart` / `clean` |
| GET    | `/api/v1/autostart`               | Read autostart flag                  |
| POST   | `/api/v1/autostart`               | Set autostart flag                   |
| GET    | `/api/v1/logs/{kind}`             | Tail core / control / action log     |
| POST   | `/api/v1/logs/{kind}/clear`       | Truncate log + write audit line      |

All JSON responses are wrapped in a stable envelope:

```json
{ "api_version": "v1", "ok": true, "data": { ... } }
```

The API has no authentication. It binds to loopback by design — the
threat model assumes a single user on a personal rooted device. If you
need it externally, run a TLS proxy in front of it and add auth there.

## Build from source

You need Go 1.23+, Python 3, and (for the app) Java 17 and Android SDK 35.

### Module + daemon

```bash
./build.sh
```

This validates the bundled `config.json` / `profiles.json`, cross-compiles
the daemon for `linux/arm64` and `linux/arm` (CGO disabled, statically
linked), copies the binaries into `src/module/bin/`, and packages a
deterministic ZIP into `dist/`.

### Companion app

```bash
./gradlew :app:assembleDebug
```

`app/build/outputs/apk/debug/app-debug.apk` is the result. To produce a
signed release APK locally, see the signing notes in `app/README.md`.

### Reproducible builds

- The module ZIP is byte-reproducible across machines: the packager
  (`scripts/package_module.py`) forces a fixed file timestamp and
  deterministic POSIX perms.
- The daemon binary is built with `-trimpath -buildvcs=false
  -ldflags="-s -w -buildid="`; CGO is off.

## Versioning

Single source of truth in [`VERSION`](VERSION). On bump, the build script
flows the value into `module.prop`, the daemon's `version.Version`, the
CI artifact names, and the app's `versionName` / `versionCode`. Just edit
`VERSION` and re-run `./build.sh` (or push to `main` and let CI handle it).

## Releases

Pushing a `v*` tag (e.g. `v2.0.0`) on `main` triggers a CI release that
attaches both the module ZIP and the signed APK to the GitHub Release.
See [`CHANGELOG.md`](CHANGELOG.md) for what changed.

## Compatibility & defaults

- **IPv4 only.** IPv6 is intentionally out of scope. If your network gives
  you IPv6 routes, apps may bypass the tunnel — disable IPv6 on the
  device or rely on the SSH server being IPv4-reachable.
- **Permissive SSH host keys and TLS verification** by default. This
  matches HTTP Injector / HTTP Custom behaviour and is what most
  payload-based ISP-bypass workflows expect. The companion app surfaces
  these as advanced toggles.
- **DNS modes:** `device` (the Android resolver path), `google`,
  `cloudflare`, or `custom`. UDP DNS hijacking is **not** supported on
  this rebuild.
- **Hotspot tethering** is on by default; tethered clients share the
  tunnel exit IP via a `PREROUTING` hook on `wlan+`, `rndis+`, `ncm+`,
  `bt-pan+`, etc.

## Licensing

- The module + Go daemon are licensed under the [Apache License
  2.0](LICENSE).
- The companion Android app is licensed under
  [GPL-3.0](app/LICENSE) because it incorporates patterns from
  KernelSU-Next.
- Third-party attributions are in [`NOTICE`](NOTICE).

## Contributing

Pull requests are welcome. Two ground rules:

1. Keep the `/api/v1/*` envelope shape stable — adding fields is fine,
   renaming or removing them is not.
2. Don't commit the gradle wrapper jar. Android Studio fetches it on
   import.

If you're touching the daemon, run `go test ./...` and `./build.sh`
before opening a PR. If you're touching the app, run `./gradlew
:app:assembleDebug`.

## Security

The dashboard binds to loopback and there's no authentication. Anyone
local to the phone with the right port can read profiles or change DNS.
On a personal device, that's fine. Don't expose port 9190 to your LAN
without putting a TLS-and-auth proxy in front.

SSH passwords are stored in plain text in `/data/adb/sshcustom/profiles.json`
(mode `0600`). On a rooted device anything else with root can read them
— acknowledged, not encrypted. If that's a concern for your threat model
you should use SSH keys (and the daemon supports them through the same
profile shape).

## Credits

- **GoodyOG** — author and maintainer.
- **KernelSU-Next** team — Compose UI patterns and libsu wiring approach.
- **The Go x/crypto authors** — vendored and lightly patched for Dropbear
  compatibility (see [`third_party/PATCHES.md`](third_party/PATCHES.md)).

Have fun.

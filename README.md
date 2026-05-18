# SSHCustom-Magisk

Magisk / KernelSU module that routes Android TCP traffic through an SSH tunnel with transparent proxying.

[![Build](https://github.com/GoodyOG/SSHCustom-Magisk/actions/workflows/build.yml/badge.svg)](https://github.com/GoodyOG/SSHCustom-Magisk/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/GoodyOG/SSHCustom-Magisk?sort=semver)](https://github.com/GoodyOG/SSHCustom-Magisk/releases/latest)

## Screenshots

<p align="center">
  <img src="docs/screenshot_home.png" width="22%" alt="Home">
  <img src="docs/screenshot_runtime.png" width="22%" alt="Runtime">
  <img src="docs/screenshot_profiles.png" width="22%" alt="Profiles">
  <img src="docs/screenshot_settings.png" width="22%" alt="Settings">
</p>

## Features

- **SSH connection pool** — 4 parallel SSH sessions for faster throughput and resilience
- **SOCKS5 proxy** + transparent TCP listener with iptables redirect
- **Pluggable transport** — direct SSH, HTTP proxy, TLS/SNI, payload injection
- **Hotspot tethering** — shares the tunnel with connected clients
- **Local dashboard** at `http://127.0.0.1:9190/` with real-time updates
- **Autostart on boot** with connectivity-aware delay
- **Real-time status** via Server-Sent Events + polling

Runs on rooted Android (Magisk or KernelSU), `arm64-v8a` and `armeabi-v7a`.

## Install

1. Download `SSHCustom-Magisk-vX.Y.Z.zip` from [Releases](https://github.com/GoodyOG/SSHCustom-Magisk/releases/latest).
2. Flash the ZIP via Magisk/KernelSU, reboot.
3. The daemon starts automatically — the WebUI is always accessible after boot.
4. Open the WebUI, configure a SSH profile, and tap **Start Tunnel** to connect.

**Accessing the WebUI:**

- **KernelSU / KSU-Next** — open the module WebUI directly from the manager.
- **WebUI-X Portable** — install [WebUI-X Portable](https://github.com/MMRLApp/WebUI-X-Portable) (also available on [Google Play](https://play.google.com/store/apps/details?id=com.dergoogler.mmrl.wx)). SSHCustom-Magisk appears in its module list with full edge-to-edge support. You can also **add a home screen shortcut** for instant access from the app's module list.
- **Magisk** — install [KsuWebUI Standalone](https://github.com/KOWX712/KsuWebUIStandalone/releases), grant it root access, then open SSHCustom's WebUI from within it.
- **Browser** — navigate to `http://127.0.0.1:9190/` on the device.

## Build from source

Requires Go 1.23+ and Python 3:

```bash
./build.sh
```

Output: `dist/SSHCustom-Magisk-v*.zip`

## API

REST + SSE on `127.0.0.1:9190`. Full spec in [`docs/openapi.yaml`](docs/openapi.yaml).

## Versioning

Single source of truth in [`VERSION`](VERSION). Bump it and push a `v*` tag — CI handles the rest.

## License

Licensed under the [Apache License 2.0](LICENSE).


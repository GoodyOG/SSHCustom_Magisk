# SSHCustom-Magisk

SSHCustom-Magisk is a Magisk and KernelSU module for routing Android TCP traffic through an SSH tunnel with root-level transparent proxying. It is built around a compact Go daemon, module control scripts, and a local WebUI for profile management, runtime visibility, and network controls.

The project is being rebuilt toward a production-grade module: clean source layout, repeatable builds, high-throughput SSH transport, a Specter-style Material WebUI, and a stable root-control path for a future companion app. Runtime folders intentionally stay lowercase `sshcustom`.

## Core Goals

- High-throughput SSH tunneling with payload, HTTP proxy, TLS/SNI, and direct transport modes.
- IPv4-only transparent TCP proxying for now, using iptables REDIRECT.
- Hotspot TCP sharing enabled by default, with WebUI controls.
- Device DNS as the default resolver, with optional Google DNS and Cloudflare DNS profiles.
- Maximum compatibility for SSH host keys, TLS/SNI, payload-based networks, and legacy profile behavior.
- Local API and WebUI available while the module runtime is enabled.
- Magisk and KernelSU support.
- A clean repository containing source, module files, and release binaries only where they are intentionally shipped.

## Module Layout

```text
cmd/sshcustomd/        Go daemon source
src/module/            Magisk/KernelSU module template
src/module/bin/        Shipped ARM binaries
src/module/config/     Default config and sample profiles
src/module/scripts/    Runtime control and cleanup scripts
src/module/webroot/    Built local WebUI
docs/                  Screenshots and technical notes
dist/                  Generated build output
```

`dist/` is generated output. It should not be treated as source.

## Runtime Flow

1. Magisk or KernelSU installs the module files.
2. `customize.sh` prepares `/data/adb/sshcustom`, copies the daemon, scripts, config, profiles, and WebUI.
3. The module action script starts or stops the runtime.
4. `sshcustom.sh` validates config, starts `sshcustomd`, applies cleanup when stopping, and maintains module state.
5. `sshcustomd` authenticates SSH, maintains an adaptive SSH connection pool, starts SOCKS5, starts transparent TCP proxying, and exposes the local API/WebUI.
6. iptables redirects local Android TCP traffic into the daemon, while SSH endpoint traffic and internal module ports are bypassed to avoid loops.
7. Hotspot TCP forwarding is enabled when configured, allowing tethered clients to share the tunnel path.

## WebUI Direction

The WebUI is being rebuilt as a Specter-style Material interface:

- Home dashboard with tunnel state, selected profile, throughput/runtime metrics, and public IP details.
- Public IP panel showing IP address, location, ISP, and ASN.
- Profile manager for SSH, payload, HTTP proxy, TLS/SNI, fallback IPs, and quick probe results.
- Network page for transparent proxy, SOCKS5, hotspot sharing, and DNS mode.
- Runtime page for core logs, control logs, connection pool state, route changes, reconnect events, memory, CPU, goroutines, and recent errors.
- Settings page for theme, compatibility flags, update details, and future root app integration.

The shipped dashboard lives in `src/module/webroot` and now follows the Specter-style layout: top app bar, Material-style cards, list containers, tonal controls, switches, chips, and bottom floating pill navigation.

## DNS Policy

Default DNS mode is `device`.

Planned selectable modes:

- `device`: use the Android/device resolver path.
- `google`: use Google DNS resolvers.
- `cloudflare`: use Cloudflare DNS resolvers.
- `custom`: optional future mode for user-provided resolver IPs.

DNS hijacking and UDP proxying are intentionally out of scope for the current IPv4 TCP rebuild. The first production target is reliable SSH endpoint resolution and clear WebUI control over resolver choice.

The current DNS setting affects SSHCustom endpoint resolution first: SSH host, HTTP proxy host, transport probes, and module network checks. Android app DNS and hotspot client DNS remain device-controlled for this rebuild.

## Throughput Policy

The daemon is optimized for speed and compatibility:

- Static Go binaries for Android ARM64 and ARMv7.
- SSH connection pooling to reduce per-stream setup cost.
- Large copy buffers for high-throughput streams.
- Patched SSH channel behavior for Dropbear compatibility and larger receive tolerance.
- Circuit-breaker reconnect behavior to avoid endless tight retry loops.
- Tunable pool size, stream idle timeout, acquire timeout, connect timeout, and keepalive interval.

The rebuild should prioritize measurable throughput and stability over adding broad proxy modes too early.

## API Policy

The local API is intended to be available only while the module runtime is enabled. It currently binds to:

```text
http://127.0.0.1:9190/
```

The API is open by design for the current module workflow. Future work should keep endpoints stable enough for a root companion app to control the module through root-side scripts and runtime state files.

Stable API work starts under `/api/v1`. Legacy `/api/*` routes remain available for the current WebUI while the Specter-style UI is rebuilt.

Initial v1 routes:

```text
GET        /api/v1/health
GET        /api/v1/status
GET        /api/v1/diagnostics
GET        /api/v1/network/public-ip
GET        /api/v1/config
POST/PATCH /api/v1/config
GET        /api/v1/profiles
GET        /api/v1/profile/current
POST       /api/v1/profile/select
POST       /api/v1/profile/save
POST       /api/v1/control
GET        /api/v1/logs/core
GET        /api/v1/logs/control
```

`/api/v1/network/public-ip` returns both direct device-route public IP details and tunnel-route public IP details when SOCKS/SSH is available.

## Compatibility Policy

The module favors compatibility:

- SSH host key verification remains permissive by default.
- TLS verification remains permissive by default for payload/SNI carrier workflows.
- IPv4 remains the production target for the current rebuild.
- Magisk and KernelSU are first-class targets.

Security-sensitive defaults should be clearly labeled in the WebUI instead of silently changed.

## Build

Go is required for daemon builds. The installed toolchain should be recent enough to build the module targets.

```bash
./build.sh
```

The build should:

1. Validate bundled config and profiles.
2. Cross-compile `sshcustomd` for Linux ARM64 and ARMv7.
3. Copy release binaries into `src/module/bin`.
4. Build the WebUI into `src/module/webroot`.
5. Package the module zip into `dist/` with Unix permission bits preserved.

Windows host builds are not the runtime target. Android/Linux ARM builds are the important release outputs.

## Install

1. Build or download a release zip.
2. Install the zip through Magisk or KernelSU.
3. Start the module with the module action button.
4. Open the local dashboard:

```text
http://127.0.0.1:9190/
```

5. Edit or import an SSH profile, probe the connection, and start the tunnel.

## Runtime Paths

```text
/data/adb/sshcustom/                  Runtime work directory
/data/adb/sshcustom/sshcustomd        Daemon binary
/data/adb/sshcustom/config.json       Runtime config
/data/adb/sshcustom/profiles.json     User profiles
/data/adb/sshcustom/run/              Logs and state
/data/adb/sshcustom/webroot/          Local WebUI
```

## Logs

Important runtime logs:

```text
/data/adb/sshcustom/run/core.log
/data/adb/sshcustom/run/control.log
/data/adb/sshcustom/run/action.log
/data/adb/sshcustom/run/watchdog.log
/data/adb/sshcustom/run/net_clean.log
```

The rebuilt WebUI should expose useful runtime details without requiring users to inspect files manually.

## Production Rebuild Roadmap

1. Clean repository layout, generated files, dependency layout, and build artifacts.
2. Split the daemon into focused packages for config, API, SSH transport, proxy listeners, iptables, metrics, and runtime state.
3. Stabilize API contracts for the WebUI and future root app.
4. Add DNS mode selection while keeping device DNS as default.
5. Add public IP details to the Home page.
6. Replace the current single-file dashboard with a Specter-style Material WebUI source build.
7. Improve runtime diagnostics and core log visibility.
8. Tune throughput with benchmarks and device-side observations.
9. Harden package scripts for Magisk and KernelSU installs.
10. Ship release candidates with repeatable build output and clear changelogs.

## Status

This repository is in active rebuild planning. The current daemon already provides the core tunnel path, but the codebase still needs cleanup, WebUI replacement, API polish, and production packaging work before it should be treated as deployment-grade.

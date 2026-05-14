# SSHCustom Companion App

Native Android app for the SSHCustom-Magisk module. Talks to the Go daemon
on `127.0.0.1:9190` over the documented `/api/v1/*` surface (see
[`../docs/openapi.yaml`](../docs/openapi.yaml)).

Licensed under [GPL-3.0](LICENSE) because it incorporates patterns from
KernelSU-Next.

## Building locally

You need:

- **Java 17** (`temurin` works; Android Gradle Plugin 8.x requires 17).
- **Android SDK 35** with build-tools 35.0.0 and platform 35.
- **Gradle 8.10.2** (matched by CI; newer minor versions are fine).

```bash
./gradlew :app:assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. It's
~21 MB unminified.

For a signed release APK locally, you have two options:

### Option A — environment variables (matches CI)

```bash
export KEYSTORE_BASE64="$(base64 -w0 < /path/to/your/release.jks)"
export KEYSTORE_PASSWORD="..."
export KEY_ALIAS="..."
export KEY_PASSWORD="..."

./gradlew :app:assembleRelease
```

The build script decodes the keystore on the fly into
`app/build/ci-release.jks` and signs the APK with it. This is exactly
what GitHub Actions does — the four secrets live in the repo's Actions
secrets.

### Option B — `keystore.properties` (developer convenience)

Create a file `keystore.properties` at the repo root:

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`keystore.properties` is gitignored. The path is resolved relative to the
repo root.

If neither path is configured, the release build falls back to the
Android debug signing config so you still get an installable APK — just
not one that can update over an existing release-signed install.

## Project structure

```
ui/
  MainActivity          single-activity Compose host
  MainViewModel         single VM that the four tabs share
  screens/
    AppRoot             bottom-nav scaffold
    HomeScreen          status hero + 4 stat cards (mirrors WebUI home)
    ProfilesScreen      list + Compose profile editor dialog +
                        SAF-based JSON import/export
    RuntimeScreen       diagnostics + paths + WebView log viewer
    SettingsScreen      DNS / hotspot / autostart / about
data/
  ApiClient             OkHttp + okhttp-sse wrapper around /api/v1
  ApiModels             @Serializable data classes mirroring the JSON
  Repository            SSE stream + polling fallback + state flows
  RootClient            libsu wrapper for daemon control when API is offline
service/
  TunnelMonitorService  foreground service: SSE consumer +
                        live persistent notification
  TunnelTileService     Quick Settings tile (one-tap toggle)
  BootReceiver          launches TunnelMonitorService on boot when
                        autostart is enabled
```

## Architecture choices

**Single ViewModel.** Four flat tabs share the same status snapshot.
Splitting into per-tab VMs would multiply boilerplate (each one would
still need a shared `Repository`) without buying any decoupling.

**OkHttp + kotlinx-serialization, no Retrofit / Hilt.** OkHttp +
serialization handles 14 endpoints with ~150 lines of client code;
Retrofit would add a code-generation step and extra runtime weight for
no real benefit. Hilt would pull in KSP and a half-second of build time
to manage exactly two singletons (`Repository`, `ApiClient`) that are
already trivially constructable.

**WebView for the logs screen, native everywhere else.** Logs are plain
text and identical regardless of how you view them — pointing a WebView
at `/api/v1/logs/{kind}` gets us the same view in the app, in Chrome,
and in KernelSU-Next's "open module WebUI" feature, with one source of
truth. Home / Profiles / Settings get full native Compose with
Material You.

**libsu for root, optional.** When the daemon is offline (e.g. before
first start), the app reads `profiles.json` directly via libsu. When
the daemon is up, it hits the API. If the user denies root, the app
gracefully degrades to read-only API access — they can still see status
and read profiles, just not start/stop the tunnel from the app.

## SSE → notification flow

```
TunnelMonitorService.onCreate
   └─ ApiClient.openEvents(listener)
          ▼
       SSE stream / 25 s heartbeat
          ▼
    listener.onEvent("status", payload)
          ▼
    Repository._status emits StatusData
          ▼
    Service rebuilds Notification
       title  = stringResource(stateLabel)
       text   = currentProfile + tunnelIp
       small icon tint = state colour
          ▼
    NotificationManager.notify(NOTIF_ID, ...)
```

A single `NotificationChannel` (`monitor`) is registered at
`Application.onCreate`. The service is `foregroundServiceType="dataSync"`
because it's exclusively passing API responses through to a UI surface.

## Quick Settings Tile

`TunnelTileService` is registered with the `android.service.quicksettings`
permission and surfaces in the system shade dropdown. Tapping it calls
`ApiClient.control("start" | "stop")`. The tile updates its label and
icon from the same `Repository.status` flow the home screen uses.

## Permissions

The app requests:

- `INTERNET` — talk to `127.0.0.1:9190` (and only that).
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — the monitor
  service.
- `POST_NOTIFICATIONS` (Android 13+) — for the persistent notification.
- `RECEIVE_BOOT_COMPLETED` — boot receiver.

It does **not** request `INTERNET` for arbitrary domains, location,
storage, contacts, or anything else. Networking is loopback-only.

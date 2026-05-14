# SSHCustom Companion App

Native Android app for the SSHCustom-Magisk module. Talks to the Go daemon
on `127.0.0.1:9190` over the documented `/api/v1/*` surface (see
`../docs/openapi.yaml`).

## Status

PR 4 lays out the project structure: Gradle 8.10 + AGP 8.7 + Kotlin 2.0 +
Jetpack Compose + Material 3 dynamic colors. All four tabs (Home, Profiles,
Runtime, Settings) are implemented and wired to the daemon API. The
foreground service is a skeleton; the Quick Settings Tile, theme bridge
to the WebView, and signed CI release land in PR 5.

## Building locally

The Android SDK is **not** required to read or review the source — every
file is plain Kotlin/XML. To actually compile:

1. Open the repository root in Android Studio Hedgehog or newer. Studio
   will resolve `gradle/wrapper/gradle-wrapper.jar` for you on first
   import (the binary is intentionally not committed; it's downloaded
   on demand by the wrapper script).
2. Or from the command line:
   ```
   ./gradlew :app:assembleDebug
   ```
3. The debug APK lands in `app/build/outputs/apk/debug/`.

## Architecture

```
ui/
  MainActivity          single-activity Compose host
  MainViewModel         single VM that the four tabs share
  screens/
    AppRoot             bottom-nav scaffold
    HomeScreen          status hero + 4 stat cards (mirrors WebUI home)
    ProfilesScreen      list + Compose profile editor dialog
    RuntimeScreen       diagnostics + paths + WebView log viewer
    SettingsScreen      DNS / hotspot / autostart / about
data/
  ApiClient             OkHttp + okhttp-sse wrapper around /api/v1
  ApiModels             @Serializable data classes mirroring the JSON
  Repository            SSE stream + polling fallback + state flows
  RootClient            libsu wrapper for daemon control when API is offline
service/
  TunnelMonitorService  foreground service skeleton (full impl in PR 5)
```

## Why a single ViewModel

The app has four flat tabs sharing the same status snapshot. Splitting
into per-tab VMs would multiply boilerplate (each one would still need a
shared `Repository`) without buying any decoupling. If a future feature
introduces deep navigation (e.g. profile detail screens) we can split
then.

## Why no Retrofit / Hilt

OkHttp + kotlinx-serialization handles 14 endpoints with ~150 lines of
client code; Retrofit would add a code-generation step and extra runtime
weight for no real benefit. Hilt would pull in KSP and a half-second of
build time to manage exactly two singletons (`Repository`, `ApiClient`)
that are already trivially constructable. Both were considered and
rejected.

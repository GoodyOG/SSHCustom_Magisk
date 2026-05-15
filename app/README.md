# SSHCustom Companion App

Native Android app for the SSHCustom-Magisk module. Communicates with the Go daemon at `127.0.0.1:9190` via `/api/v1/*`.

## Building

Needs Java 17 + Android SDK 35.

```bash
./gradlew :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

For signed release builds, set these environment variables (same as CI):

```bash
export KEYSTORE_BASE64="$(base64 -w0 < release.jks)"
export KEYSTORE_PASSWORD="..."
export KEY_ALIAS="..."
export KEY_PASSWORD="..."
./gradlew :app:assembleRelease
```

Or create `keystore.properties` at the repo root (gitignored).

## Architecture

- **Single ViewModel** shared across 4 tabs (Home, Profiles, Runtime, Settings)
- **OkHttp + kotlinx-serialization** — no Retrofit/Hilt needed for 14 endpoints
- **libsu** for root shell when daemon is offline
- **Foreground service** consumes SSE for live notification updates
- **Quick Settings Tile** for one-tap toggle
- **WebView** for the logs screen (reuses daemon's HTML)

## License

[Apache-2.0](LICENSE)

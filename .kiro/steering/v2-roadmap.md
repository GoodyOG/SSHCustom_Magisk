# SSHCustom-Magisk v2.0.0 Roadmap (Auto-loaded)

This file is read by Kiro at the start of every session in this workspace.
It exists so the v2.0.0 rebuild can continue cleanly across sessions even
if the previous one ended due to a context limit. Update it when decisions
or status change.

## Owner

- GitHub: `GoodyOG`
- Display name on About screens and credits: **GoodyOG** (matches GitHub).
- Repo: stays at `SSHCustom_Magisk` (no rename, decided 2026-05).

## Locked decisions

| Topic | Decision |
|---|---|
| Version | v2.0.0 |
| Module/daemon license | Apache-2.0 |
| App license | GPL-3.0 (required by KSU-Next code reuse) |
| App architecture | Hybrid: native Jetpack Compose for Home/Profiles/Settings, WebView for Logs (loads `/` from `127.0.0.1:9190`) |
| Min Android | API 26 (Android 8). Material You dynamic colors on 12+, fixed palette below. |
| App package id | `com.sshcustom.app` |
| App launcher name | SSHCustom |
| Distribution | GitHub Releases only, signed APK; keystore generated fresh in CI Secrets |
| WebUI tabs | Home / Profiles / Runtime / Settings (Network knobs live in Settings) |
| Profile editor | No `fallback_ips` field. Two buttons: Save / Save, Use & Restart. Existing `fallback_ips` on saved profiles preserved |
| Home cards | Tunnel Exit IP + local Device IP (no external lookup for device side). Tunnel side uses ip-api.com via SOCKS5 |
| Clear Logs | Truncates on disk via API, with confirm dialog |
| Autostart | Marker file `<run_dir>/autostart`, written via `/api/v1/autostart`, read by `service.sh` at boot |
| Boot delay | After `sys.boot_completed=1`, wait up to 30s for connectivity before starting |
| Update channel | Single `update.json` on `main` |
| API surface | `/api/v1/*` only. Legacy `/api/*` removed in v2 |
| SSE | `/api/v1/events` pushes `event: status` on every state mutation; 25s ping heartbeat |
| Logo | Abstract creative direction (Kiro to design at PR 6) |
| KSU-Next manifest | Module is openable from the KSU-Next module WebUI feature |
| Quick Settings Tile | Added in PR 5 for one-tap toggle |
| Diagnostics export | Skipped per owner decision |
| App reads/writes profiles when daemon is stopped | Yes, via libsu (root) |

## Risk-by-design (intentionally not addressed)

- Profile passwords are stored plaintext in `profiles.json` (file mode 0600).
  No encryption-at-rest. Owner accepts this on a personal rooted device.
- API has no auth token. CORS is wildcard. Bound to loopback. Owner accepts.

## Milestone status

- [x] **PR 1** merged: WebUI 4 tabs, Device IP fix, Clear Logs
- [x] **PR 2** merged: VERSION file, package split (`internal/{dnsx,iptables,metrics,version,webui}`),
      embed.FS WebUI, drop legacy `/api/*`, unit tests, dead `fwmark` cleanup
- [x] **PR 3** merged: SSE `/api/v1/events`, `/api/v1/autostart`, boot-delayed `service.sh`,
      `docs/openapi.yaml`
- [x] **PR 4** merged: Companion Android app skeleton — Gradle 8.10 + AGP 8.7 +
      Kotlin 2.0 + Compose. Four tabs (Home/Profiles/Runtime/Settings) wired to
      `/api/v1/*` with SSE + polling fallback. libsu for root daemon control.
      WebView log viewer. CI builds APK alongside module ZIP.
- [x] **PR 5** merged: App polish — Quick Settings Tile, foreground
      notification driven by SSE, profile import/export via SAF, signed
      release APK via repository secrets in CI. Keystore generated and
      uploaded as 4 Actions secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
      `KEY_ALIAS`, `KEY_PASSWORD`) plus a private gist backup.
- [ ] **PR 6** in progress: README rewrite, LICENSE files (Apache-2.0 +
      GPL-3.0), NOTICE, CHANGELOG, abstract logo asset (vector launcher
      + monochrome themed-icon variant + WebUI favicon), v2.0.0 git tag +
      GitHub Release with module ZIP and signed APK

## File layout reference

```
cmd/sshcustomd/         Go daemon, single package
internal/apiv1/         Stable JSON envelope contracts
internal/dnsx/          Android-specific DNS resolver (smart resolveHost)
internal/iptables/      Transparent TCP rule install/cleanup
internal/metrics/       /proc CPU/RSS sampler
internal/version/       Version string injected via -ldflags from VERSION file
internal/webui/         embed.FS handler, disk override wins
src/module/             Magisk/KernelSU module template that gets zipped
  bin/{arm,arm64}/      Prebuilt daemon binaries (committed)
  scripts/              sshcustom.sh control plane, watchdog, net_clean
  webroot/index.html    Single-file Material You-styled dashboard (≈1100 lines)
  service.sh            Boot handler with autostart marker support
docs/openapi.yaml       Single source of truth for the v1 API
app/                    Android companion app (PR 4+)
build.sh                Cross-compiles arm/arm64 + builds host validator + packages ZIP
scripts/package_module.py Deterministic ZIP packager (preserves +x perms)
.github/workflows/      CI: Go build + APK build (PR 4)
VERSION                 Single source for version (build.sh + module.prop + ldflags)
```

## Build verification commands

```bash
# Daemon: must be clean before any commit
go build ./...
go vet ./...
go test ./...

# Module ZIP end-to-end (uses VERSION file)
./build.sh

# Smoke-test daemon endpoints (use a non-default port to avoid clashes)
mkdir -p /tmp/sshc-test/run
# write minimal config.json + profiles.json with api.port=19191, then:
nohup ./dist/sshcustomd-host run -c /tmp/sshc-test/config.json \
  -p /tmp/sshc-test/profiles.json -w /tmp/sshc-test &
curl -s http://127.0.0.1:19191/api/v1/health
curl -s http://127.0.0.1:19191/api/v1/autostart
timeout 3 curl -s -N http://127.0.0.1:19191/api/v1/events | head -3
```

## How to resume after a session loss

1. Read this file and the merged-PR list above to learn the locked decisions.
2. `git status` and `git log --oneline -10` to see uncommitted work and the
   current commit graph.
3. `git diff --stat` to see what was in flight.
4. Run the verification commands above. If they pass, the working tree is
   in a known-good state; if not, fix that first.
5. Pick up from the first unchecked milestone above. The owner has already
   said "continue automatically — I will be back when you finish."

## Sandbox notes for the agent

- The gateway exposes `github_push_to_remote` and `github_create_pull_request`
  but no "merge PR" tool. Use the fast-forward pattern:
  ```
  git checkout main
  git merge --ff-only <branch>
  ```
  then `github_push_to_remote` for `main`. GitHub auto-closes the PR.
- The sandbox cannot install pip packages from the open internet. Verify
  YAML structure with `awk` / `grep`, not pyyaml.
- `fs_write` only writes inside the workspace. To write under `/tmp` use
  `python3 -c '...'` from `execute_bash` instead.
- `cwd` arg to `execute_bash` must be inside the workspace. Use absolute
  paths in the command itself when targeting `/tmp`.
- `control_bash_process` works for true daemons but `get_process_output`
  may fail to read; prefer `nohup ... &` from `execute_bash` for short
  smoke tests, capturing stdout to a file.

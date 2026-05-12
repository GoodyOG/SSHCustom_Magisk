#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION="${VERSION:-1.0.0}"
DIST="$ROOT/dist"
MODULE="$ROOT/src/module"
ARM64_BIN="$MODULE/bin/arm64/sshcustomd"
ARMV7_BIN="$MODULE/bin/arm/sshcustomd"
HOST_BIN="$DIST/sshcustomd-host"
ZIP_OUT="$DIST/SSHCustom-Magisk-v${VERSION}.zip"

mkdir -p "$DIST" "$(dirname "$ARM64_BIN")" "$(dirname "$ARMV7_BIN")"
export GOFLAGS="${GOFLAGS:--mod=mod}"

echo "==> Go toolchain"
go version

echo "==> Building host validation binary"
CGO_ENABLED=0 go build \
  -trimpath \
  -buildvcs=false \
  -ldflags="-s -w -buildid=" \
  -o "$HOST_BIN" \
  ./cmd/sshcustomd/

echo "==> Validating bundled config/profile JSON"
"$HOST_BIN" validate -c "$MODULE/config/config.json" -p "$MODULE/config/profiles.json"

echo "==> Building Android/Linux ARM64 daemon"
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build \
  -trimpath \
  -buildvcs=false \
  -ldflags="-s -w -buildid=" \
  -o "$ARM64_BIN" \
  ./cmd/sshcustomd/

echo "==> Building Android/Linux ARMv7 daemon"
GOOS=linux GOARCH=arm GOARM=7 CGO_ENABLED=0 go build \
  -trimpath \
  -buildvcs=false \
  -ldflags="-s -w -buildid=" \
  -o "$ARMV7_BIN" \
  ./cmd/sshcustomd/

echo "==> Packaging Magisk module"
python3 "$ROOT/scripts/package_module.py" "$MODULE" "$ZIP_OUT"

echo "$ZIP_OUT"

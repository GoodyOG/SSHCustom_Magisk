#!/usr/bin/env bash
# Generate a fresh release keystore for signing the SSHCustom companion APK.
#
# Run this ONCE on a trusted local machine. Back up both the keystore file
# and the four secrets you set below — losing them means future updates
# can't be installed over an existing install (Android requires the same
# signing identity for app updates).
#
# After running, the script prints four values you must add to your repo's
# GitHub Secrets at https://github.com/<owner>/<repo>/settings/secrets/actions:
#
#   KEYSTORE_BASE64    — the keystore file, base64-encoded
#   KEYSTORE_PASSWORD  — the store password
#   KEY_ALIAS          — the alias inside the keystore
#   KEY_PASSWORD       — the per-key password (we use the same as store)
#
# Usage:
#   ./scripts/generate-release-keystore.sh

set -euo pipefail

KEYSTORE_PATH="${1:-./sshcustom-release.jks}"
ALIAS="${KEY_ALIAS:-sshcustom}"
DNAME="${DNAME:-CN=SSHCustom Release, OU=GoodyOG, O=GoodyOG, L=GitHub, ST=NA, C=NA}"

if [[ -e "$KEYSTORE_PATH" ]]; then
  echo "Refusing to overwrite existing keystore at $KEYSTORE_PATH" >&2
  echo "Move or delete it first if you really want a new one." >&2
  exit 1
fi

# Generate a strong random password if KEYSTORE_PASSWORD isn't supplied.
PASS="${KEYSTORE_PASSWORD:-$(LC_ALL=C tr -dc 'A-Za-z0-9!#%*+-' </dev/urandom | head -c 40)}"

# RSA 4096, valid 100 years. Same as Magisk Manager's release keystore policy.
keytool -genkeypair -v \
  -keystore "$KEYSTORE_PATH" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$PASS" \
  -keypass "$PASS" \
  -dname "$DNAME" \
  -storetype PKCS12

echo
echo "================================================================"
echo "Keystore written to: $KEYSTORE_PATH"
echo
echo "Add the following four values to GitHub Secrets:"
echo
echo "  KEYSTORE_BASE64:"
base64 < "$KEYSTORE_PATH" | tr -d '\n'
echo
echo
echo "  KEYSTORE_PASSWORD: $PASS"
echo "  KEY_ALIAS:         $ALIAS"
echo "  KEY_PASSWORD:      $PASS"
echo
echo "BACK UP $KEYSTORE_PATH AND THESE PASSWORDS OFF-MACHINE NOW."
echo "Losing them means you cannot publish updates to the same app id."
echo "================================================================"

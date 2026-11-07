#!/usr/bin/env bash
# Release build for Theta Meishi Hatch, signed with the Theta Hatch series key (never committed):
# pass SIGNING_FILE / SIGNING_PASS_FILE (keystore password), and the key's alias in
# ANDROID_KEY_ALIAS and its password in SIGNING_KEY_PASS_FILE (the same as the keystore's when
# not given). See secrets/README.md in Theta.Hatch.Series.
set -euo pipefail
cd "$(dirname "$0")"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_DIR" ]]; then echo 'Set ANDROID_SDK_ROOT to your Android SDK directory.' >&2; exit 1; fi
BUILD_TOOLS="$SDK_DIR/build-tools/35.0.0"
SIGNING_FILE="${SIGNING_FILE:-../private-signing/meishiclaude-release.jks}"
SIGNING_PASS_FILE="${SIGNING_PASS_FILE:-../private-signing/keystore-password.txt}"
SIGNING_KEY_PASS_FILE="${SIGNING_KEY_PASS_FILE:-$SIGNING_PASS_FILE}"
if [[ -z "${ANDROID_KEY_ALIAS:-}" ]]; then echo 'Set ANDROID_KEY_ALIAS to the signing key alias.' >&2; exit 1; fi
# apksigner reads a second line for the key when both passwords point at one file: only a separate file is passed.
KEY_PASS=()
if [[ "$SIGNING_KEY_PASS_FILE" != "$SIGNING_PASS_FILE" ]]; then KEY_PASS=(--key-pass "file:$SIGNING_KEY_PASS_FILE"); fi
VERSION="$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle)"
OUT="build/ThetaMeishiHatch-$VERSION.apk" # the one APK of a Release (series spec §6.1)
export ANDROID_SDK_ROOT="$SDK_DIR"
./gradlew :app:assembleRelease --no-daemon
mkdir -p build
"$BUILD_TOOLS/zipalign" -f -P 16 4 app/build/outputs/apk/release/app-release-unsigned.apk build/aligned.apk
"$BUILD_TOOLS/apksigner" sign --ks "$SIGNING_FILE" --ks-key-alias "$ANDROID_KEY_ALIAS" --ks-pass "file:$SIGNING_PASS_FILE" "${KEY_PASS[@]}" --out "$OUT" build/aligned.apk
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$OUT"
echo "Built $OUT"

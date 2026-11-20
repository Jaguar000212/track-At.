#!/usr/bin/env bash
# Key material comes only from the selected channel's existing repository secrets.
set -euo pipefail
for name in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  if [[ -z "${!name:-}" ]]; then echo "Missing required channel signing secret: $name" >&2; exit 1; fi
done
umask 077
key="$RUNNER_TEMP/track-at-signing.jks"
trap 'rm -f "$key"' EXIT
printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$key"
mkdir -p dist
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk "$RUNNER_TEMP/aligned.apk"
"$APKSIGNER" sign --ks "$key" --ks-key-alias "$KEY_ALIAS" --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD --out "dist/track-at-$CHANNEL.apk" "$RUNNER_TEMP/aligned.apk"


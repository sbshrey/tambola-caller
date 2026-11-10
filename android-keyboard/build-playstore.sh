#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ -z "${TAMBOLA_KEYSTORE_PATH:-}" || ! -r "$TAMBOLA_KEYSTORE_PATH" ]]; then
  echo "Set TAMBOLA_KEYSTORE_PATH to the release keystore." >&2
  exit 2
fi

if [[ -z "${TAMBOLA_KEYSTORE_PASSWORD:-}" ]]; then
  if [[ -z "${TAMBOLA_KEYSTORE_PASSWORD_FILE:-}" || ! -r "$TAMBOLA_KEYSTORE_PASSWORD_FILE" ]]; then
    echo "Set TAMBOLA_KEYSTORE_PASSWORD or TAMBOLA_KEYSTORE_PASSWORD_FILE." >&2
    exit 2
  fi
  IFS= read -r TAMBOLA_KEYSTORE_PASSWORD < "$TAMBOLA_KEYSTORE_PASSWORD_FILE" || [[ -n "$TAMBOLA_KEYSTORE_PASSWORD" ]]
  export TAMBOLA_KEYSTORE_PASSWORD
fi

bash ./gradlew :app:bundleRelease

release_dir="releases/1.5.0"
mkdir -p "$release_dir"
bundle="$release_dir/Tambola-Keyboard-1.5.0.aab"
cp app/build/outputs/bundle/release/app-release.aab "$bundle"
(cd "$release_dir" && shasum -a 256 "$(basename "$bundle")" > SHA256SUMS.txt)
echo "Created $bundle"
echo "Created $release_dir/SHA256SUMS.txt"

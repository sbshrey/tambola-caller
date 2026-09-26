#!/usr/bin/env bash
# Linux CI/release check. The image must already exist in the local Docker daemon.
set -euo pipefail
image="${1:?Supply an immutable Docker image ID}"
output="${2:?Supply a new evidence directory}"
[[ "$image" =~ ^sha256:[a-f0-9]{64}$ ]] || { echo 'Invalid image ID' >&2; exit 2; }
[[ ! -e "$output" ]] || { echo 'Evidence directory already exists' >&2; exit 2; }
mkdir -p "$output"
output="$(cd "$output" && pwd)"
version=0.119.0
archive="$output/grype.tar.gz"
# This official release asset was verified with GitHub's signed release attestation.
checksum=3fa2dc4b924621ab65404cf08d0b8438d896d80ab949c9d5a4ca283c36004c9b
curl --fail --show-error --location --proto '=https' --tlsv1.2 \
  "https://github.com/anchore/grype/releases/download/v$version/grype_${version}_linux_amd64.tar.gz" --output "$archive"
printf '%s  %s\n' "$checksum" "$archive" | sha256sum --check --status
tar --extract --gzip --file "$archive" --directory "$output" grype
cat > "$output/config.yaml" <<'CONFIG'
check-for-app-update: false
db:
  auto-update: true
  validate-by-hash-on-start: true
  validate-age: true
  max-allowed-built-age: 120h
CONFIG
GRYPE_DB_CACHE_DIR="$output/db" "$output/grype" --config "$output/config.yaml" \
  "docker:$image" --output json --file "$output/grype.json" --fail-on high
result=0
python3 tools/check-container-advisories.py "$output/grype.json" --image "$image" > "$output/gate.json" || result=$?
cat "$output/gate.json"
exit "$result"

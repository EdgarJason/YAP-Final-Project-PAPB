#!/usr/bin/env bash
# Run inside the existing hook. Use --json to receive the wake identifier.
set -euo pipefail

: "${YAP_BASE_URL:?Set YAP_BASE_URL to the HTTPS gateway origin}"
base="${YAP_BASE_URL%/}"
[[ "$base" =~ ^https://[^/@?#[:space:]]+$ ]] || { echo 'YAP_BASE_URL must be an HTTPS origin' >&2; exit 2; }
signal="$(curl --fail --silent --show-error --max-time 10 "$base/api/build/signal")"
if [[ "${1:-}" == --json ]]; then
  jq -ce 'if .pending == false then {pending} elif .pending == true and (.wakeId | strings | test("^[a-f0-9]{64}$")) then {pending, wakeId} else error("Invalid YAP signal") end' <<< "$signal"
else
  jq -er 'if (.pending | type) == "boolean" then (.pending | tostring) else error("Invalid YAP signal") end' <<< "$signal"
fi

#!/usr/bin/env bash
# Run inside the existing hook. Outputs exactly true or false.
set -euo pipefail

: "${YAP_BASE_URL:?Set YAP_BASE_URL to the HTTPS gateway origin}"
base="${YAP_BASE_URL%/}"
[[ "$base" =~ ^https://[^/@?#[:space:]]+$ ]] || { echo 'YAP_BASE_URL must be an HTTPS origin' >&2; exit 2; }
curl --fail --silent --show-error --max-time 10 "$base/api/build/signal" |
  jq -er 'if (.pending | type) == "boolean" then (.pending | tostring) else error("Invalid YAP signal") end'

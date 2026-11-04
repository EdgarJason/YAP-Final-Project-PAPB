#!/usr/bin/env bash
set -euo pipefail

: "${YAP_BASE_URL:?Set YAP_BASE_URL to the HTTPS gateway origin}"
: "${YAP_WORKER_TOKEN:?Load YAP_WORKER_TOKEN from the worker secret store}"
base="${YAP_BASE_URL%/}"
[[ "$base" =~ ^https://[^/@?#[:space:]]+$ ]] || { echo 'YAP_BASE_URL must be an HTTPS origin' >&2; exit 2; }
[[ "$YAP_WORKER_TOKEN" =~ ^[a-zA-Z0-9_-]{32,}$ ]] || { echo 'Worker token must be URL-safe' >&2; exit 2; }

request() {
  printf 'header = "Authorization: Bearer %s"\n' "$YAP_WORKER_TOKEN" |
    curl --config - --fail-with-body --silent --show-error --max-time 120 "$@"
}

case "${1:-}" in
  claim)
    [[ "$#" -eq 1 ]] || exit 2
    request --request POST "$base/api/build/claim"
    ;;
  result|fail|question)
    action="$1"
    id="${2:-}"
    lease="${3:-}"
    for value in "$id" "$lease"; do
      [[ "$value" =~ ^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$ ]] ||
        { echo 'Invalid build ID or lease' >&2; exit 2; }
    done
    options=(--request PUT --header "X-YAP-Lease: $lease")
    if [[ "$action" == result ]]; then
      [[ "$#" -eq 4 && -f "$4" ]] || { echo 'Provide a signed APK file' >&2; exit 2; }
      "${APKSIGNER:-apksigner}" verify "$4"
      options+=(--header 'Content-Type: application/vnd.android.package-archive' --data-binary "@$4")
    elif [[ "$action" == question ]]; then
      [[ "$#" -eq 4 ]] || { echo 'Provide the clarification question' >&2; exit 2; }
      json="$(jq -cn --arg question "$4" '{question: $question}')"
      options+=(--header 'Content-Type: application/json' --data-binary "$json")
    else
      [[ "$#" -eq 3 ]] || exit 2
    fi
    request "${options[@]}" "$base/api/build/$id/$action"
    ;;
  *)
    echo 'Usage: worker.sh claim | result ID LEASE FILE.apk | fail ID LEASE | question ID LEASE TEXT' >&2
    exit 2
    ;;
esac

#!/usr/bin/env bash
# YAP worker helper v2 — Vercel + Supabase cloud gateway.
# Protocol: prepare (server-chosen object path) -> upload APK directly to
# Supabase Storage (standard PUT <= 6 MiB, TUS resumable > 6 MiB) -> finalize
# with JSON only. Legacy binary upload to /result is rejected with 426.
# Tokens are read from the environment (YAP_WORKER_TOKEN) and are NEVER
# written to files or logs. Short-lived upload URLs/tokens and the lease
# live in a mode-600 state file under $YAP_WORKER_STATE_DIR and are deleted
# after finalize/fail. See server/supabase/README.md.
set -euo pipefail

: "${YAP_BASE_URL:?Set YAP_BASE_URL to the HTTPS gateway origin}"
: "${YAP_WORKER_TOKEN:?Load YAP_WORKER_TOKEN from the worker secret store}"
base="${YAP_BASE_URL%/}"
# Production origins must be HTTPS. Plain HTTP is accepted only on
# loopback (127.0.0.1/localhost/[::1]) so the worker can be tested
# against a local gateway; it must never be used for a real origin.
if [[ "$base" =~ ^https://[^/@?#[:space:]]+$ ]]; then
  :
elif [[ "$base" =~ ^http://(127\.0\.0\.1|localhost|\[::1\])(:[0-9]+)?$ ]]; then
  :
else
  echo 'YAP_BASE_URL must be an HTTPS origin (loopback http://127.0.0.1 is test-only)' >&2; exit 2
fi
[[ "$YAP_WORKER_TOKEN" =~ ^[a-zA-Z0-9_-]{32,}$ ]] || { echo 'Worker token must be URL-safe' >&2; exit 2; }

STATE_DIR="${YAP_WORKER_STATE_DIR:-$HOME/.local/state/yap-worker}"
mkdir -p "$STATE_DIR"
chmod 700 "$STATE_DIR" 2>/dev/null || true
STATE_FILE="$STATE_DIR/last-claim.json"

new_uuid() { cat /proc/sys/kernel/random/uuid; }
now_iso() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# request <curl-args...>: authenticated JSON request; prints body to stdout.
request() {
  printf 'header = "Authorization: Bearer %s"\n' "$YAP_WORKER_TOKEN" |
    curl --config - --fail-with-body --silent --show-error --max-time 120 "$@"
}

json_request() { # json_request METHOD URL [JSON-BODY]
  if [[ $# -eq 3 ]]; then
    request --request "$1" --header 'Content-Type: application/json' --data-binary "$3" "$2"
  else
    request --request "$1" "$2"
  fi
}

valid_uuid() {
  [[ "$1" =~ ^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$ ]]
}
require_id_lease() {
  valid_uuid "$1" || { echo 'Invalid build ID' >&2; exit 2; }
  valid_uuid "$2" || { echo 'Invalid lease' >&2; exit 2; }
}

save_state() { # save_state JSON
  printf '%s' "$1" > "$STATE_FILE.tmp" && chmod 600 "$STATE_FILE.tmp" && mv "$STATE_FILE.tmp" "$STATE_FILE"
}
state_field() { # state_field KEY — empty if no state file
  [[ -f "$STATE_FILE" ]] || return 0
  jq -r --arg key "$1" '.[$key] // empty' "$STATE_FILE" 2>/dev/null || true
}

# --- claim ---------------------------------------------------------------
# claim [new|reuse] — new (default) generates a fresh claimRequestId; reuse
# re-sends the persisted claimRequestId (resume after a lost response).
cmd_claim() {
  local mode="${1:-new}" claim_request_id
  if [[ "$mode" == reuse ]]; then
    claim_request_id="$(state_field claimRequestId)"
    [[ -n "$claim_request_id" ]] || { echo 'No persisted claimRequestId to reuse; run claim new first.' >&2; exit 2; }
  elif [[ "$mode" == new ]]; then
    claim_request_id="${YAP_CLAIM_REQUEST_ID:-$(new_uuid)}"
  else
    echo 'Usage: worker-v2.sh claim [new|reuse]' >&2; exit 2
  fi
  local body response code
  body="$(jq -cn --arg id "$claim_request_id" '{claimRequestId: $id}')"
  response="$(request --request POST --header 'Content-Type: application/json' --data-binary "$body" \
    --write-out '\n%{http_code}' "$base/api/build/claim")" || exit $?
  code="${response##*$'\n'}"
  response="${response%$'\n'*}"
  if [[ "$code" == 204 || -z "$response" ]]; then
    exit 0 # empty queue: no output, like worker.sh v1
  fi
  [[ "$code" == 200 ]] || { echo "Claim failed: HTTP $code: $response" >&2; exit 1; }
  if [[ "$(jq -r '.replayed // false' <<< "$response")" == true && -z "$(jq -r '.id // empty' <<< "$response")" ]]; then
    echo 'Claim lama sudah tidak aktif (lease diganti, kedaluwarsa, atau tugas selesai); jalankan claim new.' >&2
    exit 1
  fi
  local job_id lease expires
  job_id="$(jq -r '.id' <<< "$response")"
  lease="$(jq -r '.lease' <<< "$response")"
  expires="$(jq -r '.leaseExpiresAt // empty' <<< "$response")"
  save_state "$(jq -cn --arg id "$job_id" --arg lease "$lease" --arg crid "$claim_request_id" \
    --arg exp "$expires" '{jobId: $id, lease: $lease, claimRequestId: $crid, leaseExpiresAt: $exp}')"
  printf '%s\n' "$response"
}

cmd_renew() { # renew ID LEASE
  require_id_lease "$1" "$2"
  local response
  response="$(request --request PUT --header "X-YAP-Lease: $2" "$base/api/build/$1/renew")"
  local expires
  expires="$(jq -r '.leaseExpiresAt // empty' <<< "$response")"
  if [[ -f "$STATE_FILE" ]]; then
    jq --arg exp "$expires" '.leaseExpiresAt = $exp' "$STATE_FILE" > "$STATE_FILE.tmp" && mv "$STATE_FILE.tmp" "$STATE_FILE"
  fi
  printf '%s\n' "$response"
}

# --- worker mutations (v1-compatible, plus operationId for safe retry) -----
cmd_mutation() { # mutation ACTION ID LEASE TEXT [FIELD]
  local action="$1" id="$2" lease="$3" text="$4" field="${5:-$action}"
  require_id_lease "$id" "$lease"
  local op_id json
  op_id="$(new_uuid)"
  json="$(jq -cn --arg field "$field" --arg text "$text" --arg op "$op_id" '{($field): $text, operationId: $op}')"
  request --request PUT --header "X-YAP-Lease: $lease" \
    --header 'Content-Type: application/json' --data-binary "$json" \
    "$base/api/build/$id/$action"
  # A released job (question/fail) ends this attempt: drop local state.
  if [[ "$action" == question || "$action" == fail || "$action" == message ]]; then
    rm -f "$STATE_FILE"
  fi
}

# --- prepare / upload / finalize ------------------------------------------
cmd_prepare() { # prepare ID LEASE FILE [apk|source]
  local id="$1" lease="$2" file="$3" kind="${4:-apk}"
  require_id_lease "$id" "$lease"
  [[ -f "$file" ]] || { echo "File not found: $file" >&2; exit 2; }
  local size sha op_id json response
  size="$(stat -c %s "$file")"
  sha="$(sha256sum "$file" | awk '{print $1}')"
  op_id="$(new_uuid)"
  json="$(jq -cn --arg op "$op_id" --arg kind "$kind" --argjson size "$size" --arg sha "$sha" \
    '{operationId: $op, kind: $kind, size: $size, sha256: $sha}')"
  response="$(request --request POST --header "X-YAP-Lease: $lease" \
    --header 'Content-Type: application/json' --data-binary "$json" \
    "$base/api/build/$id/artifacts/prepare")"
  # Persist the artifact reservation (mode 600) for upload/finalize.
  save_state "$(jq -cn --arg id "$id" --arg lease "$lease" --argjson prep "$response" \
    '{jobId: $id, lease: $lease, prepare: $prep}')"
  printf '%s\n' "$response"
}

tus_upload() { # tus_upload FILE UPLOAD_URL TOKEN BUCKET OBJECT_PATH CONTENT_TYPE
  # Minimal TUS client (bash+curl) following the Supabase resumable
  # upload contract: 6 MiB chunks, signed upload token in the
  # x-signature header (never Authorization Bearer), metadata
  # bucketName/objectName/contentType. Resumes from the server offset.
  local file="$1" upload_url="$2" token="$3" bucket="$4" object_path="$5" content_type="${6:-application/octet-stream}"
  local size chunk_size=6291456 offset=0 location
  size="$(stat -c %s "$file")"
  local b64_bucket b64_object b64_type metadata
  b64_bucket="$(printf '%s' "$bucket" | base64 -w0)"
  b64_object="$(printf '%s' "$object_path" | base64 -w0)"
  b64_type="$(printf '%s' "$content_type" | base64 -w0)"
  metadata="bucketName $b64_bucket,objectName $b64_object,contentType $b64_type"
  # Create the resumable upload; server returns the upload URL in Location.
  local create_headers
  create_headers="$(curl --fail-with-body --silent --show-error --max-time 120 -D - -o /dev/null \
    --request POST "$upload_url" \
    --header "x-signature: $token" \
    --header 'Tus-Resumable: 1.0.0' \
    --header "Upload-Length: $size" \
    --header "Upload-Metadata: $metadata")"
  location="$(grep -i '^location:' <<< "$create_headers" | tail -1 | tr -d '\r' | sed 's/^[Ll]ocation:[[:space:]]*//')"
  [[ -n "$location" ]] || { echo 'TUS create did not return a Location.' >&2; exit 1; }
  # Location may be relative: resolve it against the resumable endpoint.
  if [[ "$location" =~ ^/ ]]; then
    location="$(sed -E 's#^(https?://[^/]+).*#\1#' <<< "$upload_url")$location"
  elif [[ ! "$location" =~ ^https?:// ]]; then
    location="${upload_url%/*}/$location"
  fi
  # Resume from the server-reported offset if a previous attempt stalled.
  local head_offset
  head_offset="$(curl --fail-with-body --silent --show-error --max-time 60 -D - -o /dev/null \
    --request HEAD "$location" \
    --header "x-signature: $token" \
    --header 'Tus-Resumable: 1.0.0' | grep -i '^upload-offset:' | tail -1 | awk '{print $2}' | tr -d '\r')"
  [[ "$head_offset" =~ ^[0-9]+$ ]] && offset="$head_offset"
  while (( offset < size )); do
    local remaining=$(( size - offset ))
    local take=$(( remaining < chunk_size ? remaining : chunk_size ))
    local chunk_file
    chunk_file="$(mktemp)"
    # Byte-exact slice without a pipeline (tail|head + pipefail would
    # die with SIGPIPE once head closes the pipe early).
    dd if="$file" of="$chunk_file" bs=1M iflag=skip_bytes,count_bytes skip="$offset" count="$take" status=none
    local patch_headers new_offset
    patch_headers="$(curl --fail-with-body --silent --show-error --max-time 300 -D - -o /dev/null \
      --request PATCH "$location" \
      --header "x-signature: $token" \
      --header 'Tus-Resumable: 1.0.0' \
      --header 'Content-Type: application/offset+octet-stream' \
      --header "Upload-Offset: $offset" \
      --data-binary "@$chunk_file")"
    rm -f "$chunk_file"
    new_offset="$(grep -i '^upload-offset:' <<< "$patch_headers" | tail -1 | awk '{print $2}' | tr -d '\r')"
    [[ "$new_offset" =~ ^[0-9]+$ ]] || { echo 'TUS PATCH did not return Upload-Offset.' >&2; exit 1; }
    [[ "$new_offset" -gt "$offset" ]] || { echo 'TUS upload stalled.' >&2; exit 1; }
    offset="$new_offset"
  done
  echo "TUS upload complete: $size bytes."
}

cmd_upload() { # upload ID [FILE] — reads the artifact reservation from state
  local id="$1" file="${2:-}"
  valid_uuid "$id" || { echo 'Invalid build ID' >&2; exit 2; }
  [[ -f "$STATE_FILE" ]] || { echo 'No prepare state found; run prepare first.' >&2; exit 2; }
  local upload_url token method artifact_id bucket object_path content_type
  upload_url="$(jq -r '.prepare.uploadUrl // empty' "$STATE_FILE")"
  token="$(jq -r '.prepare.uploadToken // empty' "$STATE_FILE")"
  method="$(jq -r '.prepare.method // "standard"' "$STATE_FILE")"
  artifact_id="$(jq -r '.prepare.artifactId // empty' "$STATE_FILE")"
  bucket="$(jq -r '.prepare.bucket // "yap-artifacts"' "$STATE_FILE")"
  object_path="$(jq -r '.prepare.objectPath // empty' "$STATE_FILE")"
  case "$file" in
    *.apk) content_type='application/vnd.android.package-archive' ;;
    *.zip) content_type='application/zip' ;;
    *) content_type='application/octet-stream' ;;
  esac
  [[ -n "$upload_url" && -n "$artifact_id" ]] || { echo 'Prepare state is incomplete.' >&2; exit 2; }
  if [[ -z "$file" ]]; then
    echo 'Usage: worker-v2.sh upload ID FILE.apk' >&2; exit 2
  fi
  [[ -f "$file" ]] || { echo "File not found: $file" >&2; exit 2; }
  if [[ "$method" == tus ]]; then
    [[ -n "$token" ]] || { echo 'TUS upload needs an upload token from prepare.' >&2; exit 2; }
    [[ -n "$object_path" ]] || { echo 'TUS upload needs the object path from prepare.' >&2; exit 2; }
    tus_upload "$file" "$upload_url" "$token" "$bucket" "$object_path" "$content_type"
  else
    # Standard signed upload: token is in the URL; no Authorization header.
    curl --fail-with-body --silent --show-error --max-time 300 \
      --request PUT --header "Content-Type: $content_type" \
      --data-binary "@$file" "$upload_url"
    echo "Upload complete: $(stat -c %s "$file") bytes."
  fi
}

cmd_finalize() { # finalize ID LEASE FILE.apk [APP_NAME]
  local id="$1" lease="$2" file="$3" app_name="${4:-}"
  require_id_lease "$id" "$lease"
  [[ -f "$file" ]] || { echo "File not found: $file" >&2; exit 2; }
  [[ -f "$STATE_FILE" ]] || { echo 'No prepare state found; run prepare + upload first.' >&2; exit 2; }
  local artifact_id
  artifact_id="$(jq -r '.prepare.artifactId // empty' "$STATE_FILE")"
  [[ -n "$artifact_id" ]] || { echo 'Prepare state has no artifactId.' >&2; exit 2; }

  # 1. Verify the APK locally before asking the server to publish it.
  "${APKSIGNER:-apksigner}" verify "$file"
  local size sha
  size="$(stat -c %s "$file")"
  sha="$(sha256sum "$file" | awk '{print $1}')"
  if (( size > 33554432 )); then echo 'APK melebihi 32 MiB.' >&2; exit 2; fi

  # 2. App name first (metadata), so the download filename is correct.
  if [[ -n "$app_name" ]]; then
    cmd_mutation metadata "$id" "$lease" "$app_name" appName >/dev/null
  fi

  # 3. Preview: assets/index.html from the APK, bounded to 2 MiB.
  local preview_file="" preview_size=0
  preview_file="$(mktemp)"
  if unzip -l "$file" 'assets/index.html' >/dev/null 2>&1; then
    preview_size="$(unzip -l "$file" 'assets/index.html' | awk '/assets\/index.html/ {print $1}')"
    if [[ "$preview_size" =~ ^[0-9]+$ ]] && (( preview_size <= 2097152 )); then
      unzip -p "$file" 'assets/index.html' > "$preview_file"
    else
      : > "$preview_file"
    fi
  fi

  # 4. Worker verification report (apksigner is worker-side; the server
  #    verifies the bytes itself outbound — it is not labelled as a server
  #    signature check).
  local package_name label verification
  package_name=""; label=""
  if command -v "${AAPT2:-aapt2}" >/dev/null 2>&1; then
    local badging
    badging="$("${AAPT2:-aapt2}" dump badging "$file" 2>/dev/null || true)"
    package_name="$(grep -o "package: name='[^']*'" <<< "$badging" | head -1 | sed "s/.*name='//;s/'//")"
    label="$(grep -o "application-label:'[^']*'" <<< "$badging" | head -1 | sed "s/.*:'//;s/'//")"
  fi
  verification="$(jq -cn --arg apksigner 'verified' --arg package "$package_name" \
    --arg label "$label" --arg sha "$sha" --argjson size "$size" \
    '{apksigner: $apksigner, package: $package, label: $label, sha256: $sha, size: $size}')"

  # 5. Finalize (JSON only). Retry with the same operationId is idempotent.
  local op_id json response
  op_id="$(new_uuid)"
  json="$(jq -n --arg op "$op_id" --arg art "$artifact_id" \
    --rawfile preview "$preview_file" --argjson verification "$verification" \
    '{operationId: $op, artifactId: $art,
      previewHtml: (if $preview == "" then null else $preview end),
      verification: $verification}')"
  rm -f "$preview_file"
  response="$(request --request PUT --header "X-YAP-Lease: $lease" \
    --header 'Content-Type: application/json' --data-binary "$json" \
    "$base/api/build/$id/result")"
  rm -f "$STATE_FILE"
  printf '%s\n' "$response"
}

case "${1:-}" in
  claim)   [[ "$#" -le 2 ]] || exit 2; cmd_claim "${2:-new}" ;;
  renew)   [[ "$#" -eq 3 ]] || exit 2; cmd_renew "$2" "$3" ;;
  resume)  cmd_claim reuse ;;
  prepare) [[ "$#" -ge 4 && "$#" -le 5 ]] || exit 2; cmd_prepare "$2" "$3" "$4" "${5:-apk}" ;;
  upload)  [[ "$#" -eq 3 ]] || exit 2; cmd_upload "$2" "$3" ;;
  finalize) [[ "$#" -ge 4 && "$#" -le 5 ]] || exit 2; cmd_finalize "$2" "$3" "$4" "${5:-}" ;;
  question|message|metadata)
    [[ "$#" -eq 4 ]] || exit 2
    field="$1"; [[ "$1" == metadata ]] && field=appName
    cmd_mutation "$1" "$2" "$3" "$4" "$field" ;;
  fail)
    [[ "$#" -eq 3 ]] || exit 2
    cmd_mutation fail "$2" "$3" "build_failed" errorCode ;;
  *)
    echo 'Usage: worker-v2.sh claim [new|reuse] | resume | renew ID LEASE | prepare ID LEASE FILE [apk|source] | upload ID FILE | finalize ID LEASE FILE [APP_NAME] | question ID LEASE TEXT | message ID LEASE TEXT | metadata ID LEASE APP_NAME | fail ID LEASE' >&2
    exit 2 ;;
esac

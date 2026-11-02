#!/usr/bin/env bash

commerce_runtime_validation() {
  local commerce_base="${BASE_URL:-http://localhost:${APP_PORT:-8080}}"
  local commerce_dir
  commerce_dir="$(mktemp -d "${TMPDIR:-/tmp}/qwenbridge-commerce.XXXXXX")" || return 1
  local commerce_query='black Nike shoes size 42 under 100 EUR, not refurbished, in stock'
  local commerce_id="commerce-shell-$(date +%s)-${RANDOM}"
  local commerce_payload
  commerce_payload="$(jq -n --arg query "${commerce_query}" --arg id "${commerce_id}" '{query:$query,requestId:$id}')" || return 1
  local commerce_headers=(-H 'Content-Type: application/json')
  if [[ -n "${QWENBRIDGE_TEST_API_TOKEN:-}" ]]; then
    commerce_headers+=(-H "Authorization: Bearer ${QWENBRIDGE_TEST_API_TOKEN}")
  fi
  curl -fsS --max-time 120 "${commerce_headers[@]}" --data "${commerce_payload}" \
    "${commerce_base}/api/v2/search" >"${commerce_dir}/search.json" || return 1
  jq -e '.apiVersion==2 and .status=="OK" and .queryIR.tenant=="public"
    and (.queryIR.constraints|length)>=7 and (.results.hits|length)==1
    and .results.hits[0].id=="shoe-good" and .confidence.calibrated==false' \
    "${commerce_dir}/search.json" >/dev/null || return 1
  local commerce_evidence
  commerce_evidence="$(jq -r '.evidence[0].id' "${commerce_dir}/search.json")"
  curl -fsS --max-time 10 "${commerce_headers[@]}" \
    "${commerce_base}/api/v2/evidence/${commerce_evidence}/source" \
    | jq -e --arg query "${commerce_query}" '.text==$query' >/dev/null || return 1
  curl -fsS --max-time 10 "${commerce_headers[@]}" \
    "${commerce_base}/api/v2/search/${commerce_id}/suggestions" \
    | jq -e --arg id "${commerce_id}" '.apiVersion==2 and .baseRequestId==$id and (.queries|type)=="array"' >/dev/null || return 1
  curl -fsS --max-time 120 "${commerce_headers[@]}" --data '{"query":"same shoes but size 41"}' \
    "${commerce_base}/api/v2/search/${commerce_id}/refine" \
    | jq -e '.status=="OK" and (.results.hits|length)==1 and .results.hits[0].id=="shoe-size"
      and any(.queryIR.constraints[]; .field=="price" and .value=="100")' >/dev/null || return 1
  curl -fsS --max-time 10 "${commerce_headers[@]}" "${commerce_base}/api/v2/schemas/commerce/1" \
    | jq -e '.version==1 and .fields.price.type=="DECIMAL" and .fields.brand.normalization=="LOWERCASE"' >/dev/null || return 1
  curl -fsS -N --max-time 120 "${commerce_headers[@]}" --data "$(jq -n --arg query "${commerce_query}" '{query:$query}')" \
    "${commerce_base}/api/v2/search/stream" >"${commerce_dir}/stream.txt" || return 1
  python3 - "${commerce_dir}/stream.txt" <<'PY'
import json, sys
events = [json.loads(line[5:].strip()) for line in open(sys.argv[1]) if line.startswith('data:')]
assert events[0]['kind'] == 'search.started'
assert events[-1]['kind'] == 'search.completed'
assert events[-1]['response']['status'] == 'OK'
assert [h['id'] for h in events[-1]['response']['results']['hits']] == ['shoe-good']
PY
  local commerce_rc=$?
  rm -rf "${commerce_dir}"
  return "${commerce_rc}"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -uo pipefail
  commerce_runtime_validation
fi

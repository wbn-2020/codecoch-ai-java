#!/usr/bin/env bash
set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-http://127.0.0.1:8848}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"
NACOS_USERNAME="${NACOS_USERNAME:-}"
NACOS_PASSWORD="${NACOS_PASSWORD:-}"
NACOS_ACCESS_TOKEN="${NACOS_ACCESS_TOKEN:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG_DIR="${ROOT_DIR}/docs/nacos"

token="${NACOS_ACCESS_TOKEN}"
if [[ -z "${token}" && -n "${NACOS_USERNAME}" && -n "${NACOS_PASSWORD}" ]]; then
  token="$(curl -sS -X POST "${NACOS_ADDR}/nacos/v1/auth/users/login" \
    --data-urlencode "username=${NACOS_USERNAME}" \
    --data-urlencode "password=${NACOS_PASSWORD}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
fi

for file in "${CONFIG_DIR}"/*.yml; do
  data_id="$(basename "${file}")"
  args=(
    -sS
    -X POST "${NACOS_ADDR}/nacos/v1/cs/configs"
    --data-urlencode "dataId=${data_id}"
    --data-urlencode "group=${NACOS_GROUP}"
    --data-urlencode "content@${file}"
    --data-urlencode "type=yaml"
  )
  if [[ -n "${NACOS_NAMESPACE}" ]]; then
    args+=(--data-urlencode "tenant=${NACOS_NAMESPACE}")
  fi
  if [[ -n "${token}" ]]; then
    args+=(--data-urlencode "accessToken=${token}")
  fi
  result="$(curl "${args[@]}")"
  if [[ "${result}" == "true" ]]; then
    echo "SUCCESS ${data_id}"
  else
    echo "FAILED  ${data_id}: ${result}" >&2
    exit 1
  fi
done

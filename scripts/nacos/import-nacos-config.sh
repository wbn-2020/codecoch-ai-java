#!/usr/bin/env bash
set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-http://127.0.0.1:8848}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"
NACOS_USERNAME="${NACOS_USERNAME:-}"
NACOS_PASSWORD="${NACOS_PASSWORD:-}"
NACOS_ACCESS_TOKEN="${NACOS_ACCESS_TOKEN:-}"
NACOS_TARGET="${NACOS_TARGET:-auto}"
NACOS_AUDIT_DIR="${NACOS_AUDIT_DIR:-}"
NACOS_DATA_IDS="${NACOS_DATA_IDS:-}"
CONFIRM_WRITE="${CONFIRM_WRITE:-false}"
ALLOW_CREATE_CONFIG="${ALLOW_CREATE_CONFIG:-false}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG_DIR="${ROOT_DIR}/docs/nacos"
GUARD_SCRIPT="${ROOT_DIR}/scripts/nacos/nacos_config_guard.py"

if [[ -z "${NACOS_DATA_IDS}" ]]; then
  profile="${SPRING_PROFILES_ACTIVE:-dev}"
  if [[ ! "${profile}" =~ ^[A-Za-z0-9_-]+$ ]]; then
    echo "SPRING_PROFILES_ACTIVE must be one simple profile name." >&2
    exit 1
  fi
  NACOS_DATA_IDS="codecoachai-common-${profile}.yml,codecoachai-redis-${profile}.yml,codecoachai-gateway-${profile}.yml,codecoachai-core-${profile}.yml,codecoachai-ai-${profile}.yml,codecoachai-search-${profile}.yml"
fi

PYTHON_BIN="${PYTHON_BIN:-}"
if [[ -z "${PYTHON_BIN}" ]]; then
  for candidate in python3 python; do
    if command -v "${candidate}" >/dev/null 2>&1 &&
      "${candidate}" -c 'import sys; raise SystemExit(sys.version_info < (3, 9))' >/dev/null 2>&1; then
      PYTHON_BIN="${candidate}"
      break
    fi
  done
fi
if [[ -z "${PYTHON_BIN}" ]]; then
  echo "Python 3 is required to run ${GUARD_SCRIPT}" >&2
  exit 1
fi

mode="audit"
if [[ "${CONFIRM_WRITE}" != "true" ]]; then
  echo "DRY-RUN: exact namespace audit only; no Nacos config will be written."
else
  mode="publish"
  if [[ -z "${NACOS_AUDIT_DIR}" ]]; then
    NACOS_AUDIT_DIR="/tmp/codecoachai-nacos-audit-$(date +%Y%m%d-%H%M%S)"
  fi
  echo "WRITE ENABLED: CAS publish with exact namespace readback."
  echo "Audit directory: ${NACOS_AUDIT_DIR}"
fi

echo "Target: ${NACOS_ADDR}, group: ${NACOS_GROUP}, selector: ${NACOS_TARGET}, namespaceId: ${NACOS_NAMESPACE}"

args=(
  "${GUARD_SCRIPT}"
  "${mode}"
  --nacos-addr "${NACOS_ADDR}"
  --group "${NACOS_GROUP}"
  --config-dir "${CONFIG_DIR}"
  --target "${NACOS_TARGET}"
)
if [[ -n "${NACOS_NAMESPACE}" ]]; then
  args+=(--namespace-id "${NACOS_NAMESPACE}")
fi
if [[ -n "${NACOS_AUDIT_DIR}" ]]; then
  args+=(--audit-dir "${NACOS_AUDIT_DIR}")
fi
if [[ -n "${NACOS_DATA_IDS}" ]]; then
  IFS=',' read -r -a selected_data_ids <<< "${NACOS_DATA_IDS}"
  for data_id in "${selected_data_ids[@]}"; do
    data_id="${data_id#"${data_id%%[![:space:]]*}"}"
    data_id="${data_id%"${data_id##*[![:space:]]}"}"
    if [[ -n "${data_id}" ]]; then
      args+=(--data-id "${data_id}")
    fi
  done
fi
if [[ "${CONFIRM_WRITE}" == "true" ]]; then
  args+=(--confirm-write)
fi
if [[ "${ALLOW_CREATE_CONFIG}" == "true" ]]; then
  args+=(--allow-create-config)
fi

exec "${PYTHON_BIN}" "${args[@]}"

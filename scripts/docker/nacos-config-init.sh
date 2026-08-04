#!/bin/sh
set -eu

NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-nacos:8848}"
NACOS_CONFIG_SCHEME="${NACOS_CONFIG_SCHEME:-http}"
NACOS_CONFIG_CONTEXT_PATH="${NACOS_CONFIG_CONTEXT_PATH:-/nacos}"
NACOS_CONFIG_GROUP="${NACOS_CONFIG_GROUP:-DEFAULT_GROUP}"
NACOS_CONFIG_BOOTSTRAP_ENABLED="${NACOS_CONFIG_BOOTSTRAP_ENABLED:-true}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"
NACOS_USERNAME="${NACOS_USERNAME:-}"
NACOS_PASSWORD="${NACOS_PASSWORD:-}"
NACOS_ACCESS_TOKEN="${NACOS_ACCESS_TOKEN:-}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
CONFIG_DIRECTORY="${NACOS_CONFIG_DIRECTORY:-/config}"
AUDIT_DIRECTORY="${NACOS_CONFIG_AUDIT_DIRECTORY:-/tmp/nacos-config-audit}"
GUARD="${NACOS_CONFIG_GUARD:-/scripts/nacos_config_guard.py}"

case "$NACOS_CONFIG_BOOTSTRAP_ENABLED" in
  true|false) ;;
  *)
    printf '%s\n' "NACOS_CONFIG_BOOTSTRAP_ENABLED must be true or false" >&2
    exit 2
    ;;
esac

if [ -z "$NACOS_SERVER_ADDR" ]; then
  printf '%s\n' "NACOS_SERVER_ADDR must be non-empty" >&2
  exit 2
fi
case "$NACOS_SERVER_ADDR" in
  *://*|*/*)
    printf '%s\n' "NACOS_SERVER_ADDR must contain only host:port" >&2
    exit 2
    ;;
esac
case "$NACOS_CONFIG_SCHEME" in
  http|https) ;;
  *)
    printf '%s\n' "NACOS_CONFIG_SCHEME must be http or https" >&2
    exit 2
    ;;
esac
case "$NACOS_CONFIG_CONTEXT_PATH" in
  /*) ;;
  *)
    printf '%s\n' "NACOS_CONFIG_CONTEXT_PATH must start with /" >&2
    exit 2
    ;;
esac
NACOS_CONFIG_SERVER_URL="${NACOS_CONFIG_SCHEME}://${NACOS_SERVER_ADDR}${NACOS_CONFIG_CONTEXT_PATH}"

if [ -z "$NACOS_NAMESPACE" ]; then
  printf '%s\n' "NACOS_NAMESPACE must be a non-empty dedicated namespace ID" >&2
  exit 2
fi
if [ "$NACOS_NAMESPACE" = "public" ]; then
  printf '%s\n' "NACOS_NAMESPACE must not use the ambiguous literal public namespace" >&2
  exit 2
fi
case "$SPRING_PROFILES_ACTIVE" in
  ""|*[!A-Za-z0-9_-]*)
    printf '%s\n' "SPRING_PROFILES_ACTIVE must be one simple profile name" >&2
    exit 2
    ;;
esac
if { [ -n "$NACOS_USERNAME" ] && [ -z "$NACOS_PASSWORD" ]; } ||
   { [ -z "$NACOS_USERNAME" ] && [ -n "$NACOS_PASSWORD" ]; }; then
  printf '%s\n' "NACOS_USERNAME and NACOS_PASSWORD must be provided together" >&2
  exit 2
fi
if [ ! -r "$GUARD" ]; then
  printf 'Nacos config guard is missing: %s\n' "$GUARD" >&2
  exit 2
fi

set -- \
  --nacos-addr "$NACOS_CONFIG_SERVER_URL" \
  --group "$NACOS_CONFIG_GROUP" \
  --config-dir "$CONFIG_DIRECTORY" \
  --target namespace \
  --namespace-id "$NACOS_NAMESPACE" \
  --username "$NACOS_USERNAME" \
  --password "$NACOS_PASSWORD" \
  --access-token "$NACOS_ACCESS_TOKEN"

for component in common redis gateway core ai search; do
  config_file="${CONFIG_DIRECTORY}/codecoachai-${component}-${SPRING_PROFILES_ACTIVE}.yml"
  if [ ! -r "$config_file" ]; then
    printf 'Missing reviewed Nacos config for profile %s: %s\n' \
      "$SPRING_PROFILES_ACTIVE" "$config_file" >&2
    exit 2
  fi
  set -- "$@" --data-id "codecoachai-${component}-${SPRING_PROFILES_ACTIVE}.yml"
done

if [ "$NACOS_CONFIG_BOOTSTRAP_ENABLED" = "true" ]; then
  exec python "$GUARD" publish "$@" \
    --confirm-write \
    --allow-create-config \
    --create-missing-only \
    --audit-dir "$AUDIT_DIRECTORY"
fi

exec python "$GUARD" audit "$@"

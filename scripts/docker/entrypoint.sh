#!/bin/sh
set -eu
umask 027

APP_JAR="${APP_JAR:-/app/app.jar}"
APP_JAVA_TOOL_OPTIONS="${APP_JAVA_TOOL_OPTIONS:-}"
JAVA_BIN="${JAVA_BIN:-java}"
HEALTHCHECK_URL="${HEALTHCHECK_URL:-http://127.0.0.1:8080/actuator/health}"
HEALTHCHECK_TIMEOUT_MILLIS="${HEALTHCHECK_TIMEOUT_MILLIS:-3000}"
HEALTHCHECK_EXPECTED_STATUS="${HEALTHCHECK_EXPECTED_STATUS:-UP}"
HEALTH_STARTUP_TIMEOUT_SECONDS="${HEALTH_STARTUP_TIMEOUT_SECONDS:-180}"
HEALTH_MONITOR_INTERVAL_SECONDS="${HEALTH_MONITOR_INTERVAL_SECONDS:-10}"
HEALTH_MONITOR_FAILURE_THRESHOLD="${HEALTH_MONITOR_FAILURE_THRESHOLD:-6}"
HEALTH_MONITOR_ENABLED="${HEALTH_MONITOR_ENABLED:-true}"
HEALTH_PROBE_CLASSPATH="${HEALTH_PROBE_CLASSPATH:-/opt/codecoachai/health-probe}"

require_positive_integer() {
  name="$1"
  value="$2"
  case "$value" in
    ''|*[!0-9]*)
      printf '%s must be a positive integer\n' "$name" >&2
      exit 2
      ;;
  esac
  if [ "$value" -le 0 ]; then
    printf '%s must be greater than zero\n' "$name" >&2
    exit 2
  fi
}

require_positive_integer HEALTHCHECK_TIMEOUT_MILLIS "$HEALTHCHECK_TIMEOUT_MILLIS"
require_positive_integer HEALTH_STARTUP_TIMEOUT_SECONDS "$HEALTH_STARTUP_TIMEOUT_SECONDS"
require_positive_integer HEALTH_MONITOR_INTERVAL_SECONDS "$HEALTH_MONITOR_INTERVAL_SECONDS"
require_positive_integer HEALTH_MONITOR_FAILURE_THRESHOLD "$HEALTH_MONITOR_FAILURE_THRESHOLD"

case "$HEALTHCHECK_URL" in
  http://127.0.0.1:*|http://localhost:*|http://\[::1\]:*) ;;
  *)
    printf 'HEALTHCHECK_URL must target an HTTP loopback address\n' >&2
    exit 2
    ;;
esac

case "$HEALTH_MONITOR_ENABLED" in
  true|false) ;;
  *)
    printf 'HEALTH_MONITOR_ENABLED must be true or false\n' >&2
    exit 2
    ;;
esac

if [ ! -r "$APP_JAR" ]; then
  printf 'Application JAR is not readable: %s\n' "$APP_JAR" >&2
  exit 2
fi
if ! command -v "$JAVA_BIN" >/dev/null 2>&1 && [ ! -x "$JAVA_BIN" ]; then
  printf 'Java executable is unavailable: %s\n' "$JAVA_BIN" >&2
  exit 2
fi

stopping=0
app_pid=''
ready=0

terminate_application() {
  reason="$1"
  if [ -z "$app_pid" ] || ! kill -0 "$app_pid" 2>/dev/null; then
    return
  fi

  printf 'Stopping application: %s\n' "$reason" >&2
  kill -TERM "$app_pid" 2>/dev/null || true
  remaining=20
  while [ "$remaining" -gt 0 ] && kill -0 "$app_pid" 2>/dev/null; do
    sleep 1
    remaining=$((remaining - 1))
  done
  if kill -0 "$app_pid" 2>/dev/null; then
    kill -KILL "$app_pid" 2>/dev/null || true
  fi
}

handle_signal() {
  stopping=1
  terminate_application "container signal"
}

trap handle_signal TERM INT HUP

JAVA_TOOL_OPTIONS="$APP_JAVA_TOOL_OPTIONS" "$JAVA_BIN" -jar "$APP_JAR" "$@" &
app_pid=$!

if [ "$HEALTH_MONITOR_ENABLED" = "true" ]; then
  startup_deadline=$(( $(date +%s) + HEALTH_STARTUP_TIMEOUT_SECONDS ))
  consecutive_failures=0

  while kill -0 "$app_pid" 2>/dev/null; do
    if JAVA_TOOL_OPTIONS='' "$JAVA_BIN" -cp "$HEALTH_PROBE_CLASSPATH" HealthProbe \
      "$HEALTHCHECK_URL" \
      "$HEALTHCHECK_TIMEOUT_MILLIS" \
      "$HEALTHCHECK_EXPECTED_STATUS" >/dev/null 2>&1; then
      if [ "$ready" -eq 0 ]; then
        printf 'Application health endpoint is ready\n'
      fi
      ready=1
      consecutive_failures=0
    elif [ "$ready" -eq 0 ]; then
      if [ "$(date +%s)" -ge "$startup_deadline" ]; then
        terminate_application "startup health timeout"
        wait "$app_pid" 2>/dev/null || true
        exit 1
      fi
    else
      consecutive_failures=$((consecutive_failures + 1))
      if [ "$consecutive_failures" -ge "$HEALTH_MONITOR_FAILURE_THRESHOLD" ]; then
        terminate_application "liveness health failure threshold reached"
        wait "$app_pid" 2>/dev/null || true
        exit 1
      fi
    fi

    sleep "$HEALTH_MONITOR_INTERVAL_SECONDS" &
    wait "$!" || true
    if [ "$stopping" -eq 1 ]; then
      break
    fi
  done
fi

set +e
wait "$app_pid"
exit_code=$?
set -e

if [ "$stopping" -eq 1 ] && [ "$exit_code" -eq 0 ]; then
  exit 143
fi
if [ "$HEALTH_MONITOR_ENABLED" = "true" ] && [ "$ready" -eq 0 ]; then
  exit 1
fi
exit "$exit_code"

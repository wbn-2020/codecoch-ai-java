#!/usr/bin/env bash
set -euo pipefail

# Read-only runtime diagnosis. This script never publishes, deletes, restarts,
# or writes files in Nacos or application containers.
NACOS_ADDR="${NACOS_ADDR:-http://127.0.0.1:8848}"
NACOS_CONTAINER="${NACOS_CONTAINER:-codecoachai-nacos}"
CLIENT_CONTAINER="${CLIENT_CONTAINER:-codecoachai-gateway}"
DATA_ID="${DATA_ID:-codecoachai-gateway-dev.yml}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NACOS_USERNAME="${NACOS_USERNAME:-}"
NACOS_PASSWORD="${NACOS_PASSWORD:-}"
NACOS_ACCESS_TOKEN="${NACOS_ACCESS_TOKEN:-}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
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
  echo "python3 is required" >&2
  exit 1
fi

echo "===== containers ====="
docker inspect "${NACOS_CONTAINER}" \
  --format 'nacos image={{.Config.Image}} status={{.State.Status}} restart={{.RestartCount}} mounts={{range .Mounts}}{{.Source}}:{{.Destination}}:{{.Mode}} {{end}}'
docker inspect "${CLIENT_CONTAINER}" \
  --format 'client image={{.Config.Image}} status={{.State.Status}} restart={{.RestartCount}} mounts={{range .Mounts}}{{.Source}}:{{.Destination}}:{{.Mode}} {{end}}'

echo "===== safe environment ====="
docker inspect "${NACOS_CONTAINER}" --format '{{range .Config.Env}}{{println .}}{{end}}' |
  awk -F= '/^(MODE|SPRING_DATASOURCE_PLATFORM|NACOS_AUTH_ENABLE|PREFER_HOST_MODE|NACOS_APPLICATION_PORT|JVM_XMS|JVM_XMX)=/{print}'
docker inspect "${CLIENT_CONTAINER}" --format '{{range .Config.Env}}{{println .}}{{end}}' |
  awk -F= '/^(NACOS_SERVER_ADDR|SPRING_PROFILES_ACTIVE|HOME|JM_SNAPSHOT_PATH|JAVA_TOOL_OPTIONS)=/{print}'

echo "===== exact namespace API hashes ====="
"${PYTHON_BIN}" - "${NACOS_ADDR}" "${DATA_ID}" "${NACOS_GROUP}" <<'PY'
import hashlib
import json
import os
import sys
import urllib.parse
import urllib.request

address, data_id, group = sys.argv[1:]
root = address.rstrip("/")
if not root.endswith("/nacos"):
    root += "/nacos"
token = os.environ.get("NACOS_ACCESS_TOKEN", "")
username = os.environ.get("NACOS_USERNAME", "")
password = os.environ.get("NACOS_PASSWORD", "")

def request(path, query=None, form=None):
    global token
    query = dict(query or {})
    if token:
        query["accessToken"] = token
    url = root + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    body = None
    if form is not None:
        body = urllib.parse.urlencode(form).encode()
    with urllib.request.urlopen(
        urllib.request.Request(url, data=body), timeout=10
    ) as response:
        return response.read()

if not token and username and password:
    payload = json.loads(
        request(
            "/v1/auth/users/login",
            form={"username": username, "password": password},
        )
    )
    token = payload.get("accessToken") or ""

namespaces = json.loads(request("/v1/console/namespaces")).get("data", [])
print(
    "namespaces="
    + json.dumps(
        [
            {
                "id": item.get("namespace") or "",
                "name": item.get("namespaceShowName") or "",
                "type": item.get("type"),
            }
            for item in namespaces
        ],
        ensure_ascii=False,
        sort_keys=True,
    )
)

for label, tenant in (("builtin-public", ""), ("literal-public", "public")):
    payload = json.loads(
        request(
            "/v1/cs/configs",
            query={
                "search": "accurate",
                "dataId": data_id,
                "group": group,
                "tenant": tenant,
                "pageNo": "1",
                "pageSize": "10",
            },
        )
    )
    matches = [
        item
        for item in payload.get("pageItems", [])
        if (item.get("dataId") or "") == data_id
        and (item.get("group") or "") == group
        and (item.get("tenant") or "") == tenant
    ]
    if not matches:
        print(label + " missing")
        continue
    content = (matches[0].get("content") or "").encode()
    print(
        "{} tenant={!r} bytes={} md5={} sha256={}".format(
            label,
            tenant,
            len(content),
            hashlib.md5(content).hexdigest(),
            hashlib.sha256(content).hexdigest(),
        )
    )
PY

echo "===== Nacos persisted cache hashes ====="
docker exec "${NACOS_CONTAINER}" sh -c '
find /home/nacos/data -type f -name "$1" 2>/dev/null |
while IFS= read -r file; do
  echo "FILE=$file"
  printf "bytes="
  wc -c < "$file"
  md5sum "$file"
  sha256sum "$file"
done
' sh "${DATA_ID}"

echo "===== client snapshot hashes ====="
docker exec "${CLIENT_CONTAINER}" sh -c '
find /root /home /tmp /app -type f -name "$1" 2>/dev/null |
while IFS= read -r file; do
  echo "FILE=$file"
  printf "bytes="
  wc -c < "$file"
  md5sum "$file"
  sha256sum "$file"
done
' sh "${DATA_ID}"

echo "===== recent Nacos request evidence ====="
docker exec "${NACOS_CONTAINER}" sh -c '
grep -F "$1" /home/nacos/logs/config-client-request.log /home/nacos/logs/config-pull-check.log 2>/dev/null |
tail -n 120
' sh "${DATA_ID}"

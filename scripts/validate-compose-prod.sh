#!/usr/bin/env bash
# Prüft die resultierende produktive Compose-Konfiguration ohne Container-Start.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export POSTGRES_DB="${POSTGRES_DB:-wasessenwir}"
export POSTGRES_USER="${POSTGRES_USER:-wasessenwir}"
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-ci-validation-only}"
export APP_PORT="${APP_PORT:-8182}"

CONFIG="$(docker compose -f compose.yaml -f compose.prod.yaml config)"

fail() {
  echo "validate-compose-prod: $*" >&2
  exit 1
}

echo "$CONFIG" | grep -Eq 'APP_SESSION_COOKIE_SECURE: ("true"|true)' \
  || fail "APP_SESSION_COOKIE_SECURE muss in der Prod-Konfiguration true sein"

echo "$CONFIG" | grep -Eq 'APP_REQUIRE_SECURE_COOKIES: ("true"|true)' \
  || fail "APP_REQUIRE_SECURE_COOKIES muss true sein"

echo "$CONFIG" | grep -Eq 'APP_FORWARD_CLIENT_IP: ("true"|true)' \
  || fail "APP_FORWARD_CLIENT_IP muss true sein"

echo "$CONFIG" | grep -Eq 'APP_TRUSTED_PROXIES:[[:space:]]*172\.30\.90\.0/24' \
  || fail "APP_TRUSTED_PROXIES muss auf das Compose-Netz 172.30.90.0/24 zeigen"

# Canonical Compose-Config: host_ip / published / target unter dem Frontend-Service.
python3 - "$CONFIG" "$APP_PORT" <<'PY'
import sys
import yaml

config = yaml.safe_load(sys.argv[1])
app_port = str(sys.argv[2])
services = config.get("services") or {}

networks = config.get("networks") or {}
default_net = networks.get("default") or {}
ipam = ((default_net.get("ipam") or {}).get("config")) or []
subnets = [entry.get("subnet") for entry in ipam if isinstance(entry, dict)]
if "172.30.90.0/24" not in subnets:
    raise SystemExit(f"Prod-Netz muss Subnetz 172.30.90.0/24 nutzen, gefunden: {subnets}")

for name in ("postgres", "backend"):
    ports = (services.get(name) or {}).get("ports")
    if ports:
        raise SystemExit(f"{name} darf keine veröffentlichten Ports haben: {ports}")

frontend = services.get("frontend") or {}
ports = frontend.get("ports") or []
if len(ports) != 1:
    raise SystemExit(f"Frontend darf genau eine Portbindung haben, gefunden: {ports}")

port = ports[0]
if isinstance(port, str):
    if not port.startswith("127.0.0.1:"):
        raise SystemExit(f"Frontend-Port muss an 127.0.0.1 gebunden sein: {port}")
    if f"127.0.0.1:{app_port}:8080" not in port.replace('"', ""):
        raise SystemExit(f"Erwartete Bindung 127.0.0.1:{app_port}:8080, gefunden: {port}")
else:
    if str(port.get("host_ip")) != "127.0.0.1":
        raise SystemExit(f"Frontend-Port muss an 127.0.0.1 gebunden sein: {port}")
    if str(port.get("published")) != app_port:
        raise SystemExit(f"Erwarteter veröffentlichter Port {app_port}, gefunden: {port}")
    if int(port.get("target")) != 8080:
        raise SystemExit(f"Container-Zielport muss 8080 sein: {port}")
PY

LOCAL_CONFIG="$(docker compose -f compose.yaml config)"
python3 - "$LOCAL_CONFIG" <<'PY'
import sys
import yaml

config = yaml.safe_load(sys.argv[1])
ports = ((config.get("services") or {}).get("frontend") or {}).get("ports") or []
if not ports:
    raise SystemExit("Lokale compose.yaml muss weiterhin einen Frontend-Port veröffentlichen")
PY

echo "validate-compose-prod: OK"

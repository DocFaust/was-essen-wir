#!/usr/bin/env bash
# Laufzeittest für lokale und produktive Compose-Konfiguration (CI).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "ci-compose-runtime: $*" >&2
  exit 1
}

assert_loopback_only() {
  local project="$1"
  local port="$2"
  local mapping
  mapping="$(docker compose --project-name "$project" port frontend 8080)"
  echo "$mapping" | grep -Eq "^127\\.0\\.0\\.1:${port}$" \
    || fail "Erwartete Portbindung 127.0.0.1:${port}, gefunden: ${mapping}"

  if command -v ss >/dev/null 2>&1; then
    ss -lntp | grep -E "127\\.0\\.0\\.1:${port}\\b" >/dev/null \
      || fail "ss zeigt keinen Listener auf 127.0.0.1:${port}"
    if ss -lntp | grep -E "(0\\.0\\.0\\.0|\\*|\\[::\\]):${port}\\b" >/dev/null; then
      fail "Port ${port} darf nicht öffentlich gebunden sein"
    fi
  fi
}

echo "== Prod-Compose-Laufzeit =="
PROD_PROJECT="was-essen-wir-prod-ci"
PROD_PORT="18182"
export POSTGRES_DB=wasessenwir
export POSTGRES_USER=wasessenwir
export POSTGRES_PASSWORD
POSTGRES_PASSWORD="$(openssl rand -hex 18)"
export APP_PORT="$PROD_PORT"

prod_cleanup() {
  docker compose --project-name "$PROD_PROJECT" -f compose.yaml -f compose.prod.yaml \
    down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap prod_cleanup EXIT

./scripts/validate-compose-prod.sh
docker compose --project-name "$PROD_PROJECT" -f compose.yaml -f compose.prod.yaml \
  up --build --detach --wait --wait-timeout 600
assert_loopback_only "$PROD_PROJECT" "$PROD_PORT"
curl -fsS "http://127.0.0.1:${PROD_PORT}/api/status" | grep -q '"application":"was-essen-wir"' \
  || fail "Prod-API /api/status nicht erreichbar"
curl -fsSI "http://127.0.0.1:${PROD_PORT}/" | grep -qi '^HTTP/' \
  || fail "Prod-Frontend nicht erreichbar"
# Backend/Postgres ohne Host-Ports
if docker compose --project-name "$PROD_PROJECT" -f compose.yaml -f compose.prod.yaml port backend 8080 >/dev/null 2>&1; then
  fail "Backend darf keinen Host-Port veröffentlichen"
fi
if docker compose --project-name "$PROD_PROJECT" -f compose.yaml -f compose.prod.yaml port postgres 5432 >/dev/null 2>&1; then
  fail "Postgres darf keinen Host-Port veröffentlichen"
fi
prod_cleanup
trap - EXIT

echo "== Lokale HTTP-Compose-Laufzeit =="
LOCAL_PROJECT="was-essen-wir-local-ci"
LOCAL_PORT="18080"
export APP_PORT="$LOCAL_PORT"
POSTGRES_PASSWORD="$(openssl rand -hex 18)"
export POSTGRES_PASSWORD

local_cleanup() {
  docker compose --project-name "$LOCAL_PROJECT" -f compose.yaml \
    down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap local_cleanup EXIT

docker compose --project-name "$LOCAL_PROJECT" -f compose.yaml \
  up --build --detach --wait --wait-timeout 600
curl -fsS "http://127.0.0.1:${LOCAL_PORT}/api/status" | grep -q '"application":"was-essen-wir"' \
  || fail "Lokale API /api/status nicht erreichbar"
local_cleanup
trap - EXIT

echo "ci-compose-runtime: OK"

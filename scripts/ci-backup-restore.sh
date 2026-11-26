#!/usr/bin/env bash
# Isolierter Backup-/Restore-Laufzeittest für CI und lokale Prüfung.
# Keine produktiven Daten. Eigenes Compose-Projekt und Backup-Verzeichnis.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROJECT="was-essen-wir-backup-ci"
PORT="${BACKUP_CI_APP_PORT:-18280}"
BACKUP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/waew-backup-ci.XXXXXX")"
COOKIE_JAR="$(mktemp "${TMPDIR:-/tmp}/waew-cookies.XXXXXX")"
export POSTGRES_DB=wasessenwir
export POSTGRES_USER=wasessenwir
export POSTGRES_PASSWORD
POSTGRES_PASSWORD="$(openssl rand -hex 18)"
export APP_PORT="$PORT"
export COMPOSE_PROJECT_NAME="$PROJECT"
export COMPOSE_FILES="compose.yaml"
export WAEW_ENV_FILE=""
BASE="http://127.0.0.1:${PORT}"

ADMIN_USER="Ada"
ADMIN_PASS="ein-langes-ci-passwort"
MEMBER_USER="Bea"
MEMBER_PASS="mitglied-ci-passwort"

fail() {
  echo "ci-backup-restore: $*" >&2
  exit 1
}

cleanup() {
  docker compose --project-name "$PROJECT" -f compose.yaml \
    down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$BACKUP_DIR" "$COOKIE_JAR"
}
trap cleanup EXIT

chmod +x scripts/backup-postgres.sh scripts/restore-postgres.sh scripts/lib/compose-env.sh

echo "== Compose starten (${PROJECT}) =="
docker compose --project-name "$PROJECT" -f compose.yaml \
  up --build --detach --wait --wait-timeout 600

echo "== Setup-Token aus Backend-Logs =="
LOGS="$(docker compose --project-name "$PROJECT" -f compose.yaml logs backend)"
SETUP_TOKEN="$(printf '%s\n' "$LOGS" | grep -Eo 'SETUP_TOKEN[[:space:]]+[A-Za-z0-9_-]+' | tail -1 | awk '{print $2}')"
[[ -n "$SETUP_TOKEN" ]] || fail "SETUP_TOKEN fehlt in den Backend-Logs"

read_csrf() {
  python3 - "$COOKIE_JAR" <<'PY'
import sys, urllib.parse
path = sys.argv[1]
token = ""
with open(path, encoding="utf-8", errors="replace") as fh:
    for line in fh:
        if line.startswith("#") and not line.startswith("#HttpOnly_"):
            continue
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 7 and parts[5] == "XSRF-TOKEN":
            token = urllib.parse.unquote(parts[6])
print(token)
PY
}

csrf_headers() {
  local token
  token="$(read_csrf)"
  if [[ -z "$token" ]]; then
    curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" "$BASE/api/setup/status" >/dev/null
    token="$(read_csrf)"
  fi
  [[ -n "$token" ]] || fail "CSRF-Token fehlt"
  printf '%s' "$token"
}

api() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  local token
  token="$(csrf_headers)"
  if [[ -n "$data" ]]; then
    curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" \
      -X "$method" "$BASE$path" \
      -H "Content-Type: application/json" \
      -H "X-XSRF-TOKEN: $token" \
      --data "$data"
  else
    curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" \
      -X "$method" "$BASE$path" \
      -H "X-XSRF-TOKEN: $token"
  fi
}

echo "== Repräsentative Anwendungsdaten anlegen =="
# CSRF-Cookie holen
curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" "$BASE/api/setup/status" >/dev/null

api POST /api/setup "$(python3 - <<PY
import json
print(json.dumps({
  "username": "$ADMIN_USER",
  "displayName": "Ada Lovelace",
  "password": "$ADMIN_PASS",
  "passwordConfirmation": "$ADMIN_PASS",
  "setupToken": "$SETUP_TOKEN",
}))
PY
)" >/dev/null

api POST /api/auth/login "$(python3 - <<PY
import json
print(json.dumps({"username": "$ADMIN_USER", "password": "$ADMIN_PASS", "rememberMe": True}))
PY
)" >/dev/null

MEMBER_JSON="$(api POST /api/users "$(python3 - <<PY
import json
print(json.dumps({
  "username": "$MEMBER_USER",
  "displayName": "Bea Beispiel",
  "password": "$MEMBER_PASS",
  "role": "MEMBER",
}))
PY
)")"
MEMBER_ID="$(printf '%s' "$MEMBER_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')"

DISH_JSON="$(api POST /api/dishes "$(python3 - <<'PY'
import json
print(json.dumps({
  "name": "CI-Pizza",
  "description": "Backup-Testgericht",
  "mealTypes": ["ABENDESSEN"],
  "categories": ["Italienisch"],
  "effort": "EINFACH",
  "ingredients": ["Teig", "Tomate", "Mozzarella"],
  "recipeText": None,
  "recipeUrl": None,
  "recipeSource": None,
  "comments": "nur CI",
  "mealPrep": "UNKNOWN",
  "homeOffice": "UNKNOWN",
  "confirmDuplicate": True,
}))
PY
)")"
DISH_ID="$(printf '%s' "$DISH_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')"

VARIANT_JSON="$(api POST "/api/dishes/${DISH_ID}/variants" "$(python3 - <<'PY'
import json
print(json.dumps({
  "name": "Salami",
  "description": None,
  "effort": None,
  "ingredientsSpecified": True,
  "ingredients": ["Teig", "Tomate", "Mozzarella", "Salami"],
  "recipeText": None,
  "recipeUrl": None,
  "recipeSource": None,
  "mealPrep": None,
  "homeOffice": None,
}))
PY
)")"
VARIANT_ID="$(printf '%s' "$VARIANT_JSON" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["variants"][-1]["id"])')"

api PUT "/api/dishes/${DISH_ID}/rating" '{"stars":5}' >/dev/null

ADMIN_ID="$(api GET /api/auth/me | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')"
TODAY="$(python3 -c 'from datetime import date; print(date.today().isoformat())')"

api POST /api/meal-plans "$(python3 - <<PY
import json
print(json.dumps({
  "mealDate": "$TODAY",
  "mealType": "ABENDESSEN",
  "dishId": "$DISH_ID",
  "variantId": "$VARIANT_ID",
  "dinerIds": ["$ADMIN_ID", "$MEMBER_ID"],
  "comment": "CI-Plan",
}))
PY
)" >/dev/null

api POST /api/meal-history "$(python3 - <<PY
import json
print(json.dumps({
  "mealDate": "$TODAY",
  "mealType": "MITTAGESSEN",
  "dishId": "$DISH_ID",
  "variantId": None,
  "dinerIds": ["$ADMIN_ID"],
  "comment": "CI-Historie",
}))
PY
)" >/dev/null

# Remember-me-Zeile und Flyway-Stand für Vorher-Vergleich
REMEMBER_BEFORE="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from remember_me_token"')"
FLYWAY_BEFORE="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select max(version) from flyway_schema_history where success"')"
USERS_BEFORE="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from app_user"')"
[[ "$REMEMBER_BEFORE" -ge 1 ]] || fail "erwartet mind. ein Remember-me-Token vor Backup"
[[ "$USERS_BEFORE" == "2" ]] || fail "erwartet 2 Benutzer vor Backup, gefunden: $USERS_BEFORE"

echo "== Backup erstellen =="
BACKUP_DIR="$BACKUP_DIR" RETENTION_DAYS=30 \
  COMPOSE_PROJECT_NAME="$PROJECT" COMPOSE_FILES="compose.yaml" \
  ./scripts/backup-postgres.sh
BACKUP_FILE="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'wasessenwir-pg-*.dump' | sort | tail -1)"
[[ -n "$BACKUP_FILE" && -s "$BACKUP_FILE" ]] || fail "Backup-Datei fehlt"
PERM="$(stat -c '%a' "$BACKUP_FILE" 2>/dev/null || stat -f '%OLp' "$BACKUP_FILE")"
[[ "$PERM" == "600" ]] || fail "Backup-Berechtigung sollte 600 sein, ist ${PERM}"

echo "== Fehlerfälle prüfen =="
set +e
BACKUP_DIR="$BACKUP_DIR" COMPOSE_PROJECT_NAME="$PROJECT" COMPOSE_FILES="compose.yaml" \
  CONFIRM=YES BACKUP_FILE="${BACKUP_DIR}/fehlt.dump" \
  ./scripts/restore-postgres.sh >/tmp/waew-restore-missing.out 2>&1
RC=$?
set -e
[[ "$RC" -ne 0 ]] || fail "Restore ohne Datei muss fehlschlagen"
grep -qi 'fehlt\|nicht\|missing\|Backup-Datei' /tmp/waew-restore-missing.out \
  || fail "Fehlermeldung bei fehlender Backup-Datei unverständlich"

echo "ungueltig" >"${BACKUP_DIR}/ungueltig.dump"
chmod 600 "${BACKUP_DIR}/ungueltig.dump"
set +e
BACKUP_DIR="$BACKUP_DIR" COMPOSE_PROJECT_NAME="$PROJECT" COMPOSE_FILES="compose.yaml" \
  CONFIRM=YES BACKUP_FILE="${BACKUP_DIR}/ungueltig.dump" SKIP_SAFETY_BACKUP=1 \
  ./scripts/restore-postgres.sh >/tmp/waew-restore-invalid.out 2>&1
RC=$?
set -e
[[ "$RC" -ne 0 ]] || fail "Restore mit ungültigem Archiv muss fehlschlagen"

set +e
BACKUP_DIR="$BACKUP_DIR" COMPOSE_PROJECT_NAME="$PROJECT" COMPOSE_FILES="compose.yaml" \
  BACKUP_FILE="$BACKUP_FILE" \
  ./scripts/restore-postgres.sh >/tmp/waew-restore-noconfirm.out 2>&1
RC=$?
set -e
[[ "$RC" -eq 4 ]] || fail "Restore ohne CONFIRM=YES muss Exit 4 liefern (war ${RC})"

# Fehlgeschlagenes Backup: Postgres kurz stoppen
docker compose --project-name "$PROJECT" -f compose.yaml stop postgres >/dev/null
set +e
BACKUP_DIR="$BACKUP_DIR" COMPOSE_PROJECT_NAME="$PROJECT" COMPOSE_FILES="compose.yaml" \
  ./scripts/backup-postgres.sh >/tmp/waew-backup-fail.out 2>&1
RC=$?
set -e
[[ "$RC" -ne 0 ]] || fail "Backup ohne laufendes Postgres muss fehlschlagen"
docker compose --project-name "$PROJECT" -f compose.yaml start postgres >/dev/null
# warten bis ready
for _ in $(seq 1 30); do
  docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
    sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null 2>&1 && break
  sleep 1
done

echo "== Daten zerstören =="
docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -v ON_ERROR_STOP=1 --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
    delete from meal_event;
    delete from rating;
    delete from dish;
    delete from remember_me_token;
    delete from app_user where username_normalized = '"'"'bea'"'"';
  "' >/dev/null

REMAINING_USERS="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from app_user"')"
[[ "$REMAINING_USERS" == "1" ]] || fail "Zerstörung unvollständig (Benutzer=${REMAINING_USERS})"

echo "== Restore =="
BACKUP_DIR="$BACKUP_DIR" COMPOSE_PROJECT_NAME="$PROJECT" COMPOSE_FILES="compose.yaml" \
  CONFIRM=YES BACKUP_FILE="$BACKUP_FILE" SKIP_SAFETY_BACKUP=1 \
  ./scripts/restore-postgres.sh

echo "== Wiederhergestellte Daten prüfen =="
USERS_AFTER="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from app_user"')"
DISHES_AFTER="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from dish where name = '"'"'CI-Pizza'"'"'"')"
VARIANTS_AFTER="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from dish_variant"')"
INGREDIENTS_AFTER="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from ingredient"')"
RATINGS_AFTER="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select stars from rating"')"
MEALS_AFTER="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from meal_event"')"
FLYWAY_AFTER="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select max(version) from flyway_schema_history where success"')"
REMEMBER_AFTER="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from remember_me_token"')"
ROLES="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select role from app_user order by role"')"
HASH_PRESENT="$(docker compose --project-name "$PROJECT" -f compose.yaml exec -T postgres \
  sh -c 'psql -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from app_user where password_hash like '"'"'\$2%'"'"'"')"

[[ "$USERS_AFTER" == "2" ]] || fail "Benutzer nach Restore: $USERS_AFTER"
[[ "$DISHES_AFTER" == "1" ]] || fail "Gericht nach Restore fehlt"
[[ "$VARIANTS_AFTER" -ge 1 ]] || fail "Variante nach Restore fehlt"
[[ "$INGREDIENTS_AFTER" -ge 3 ]] || fail "Zutaten nach Restore unvollständig ($INGREDIENTS_AFTER)"
[[ "$RATINGS_AFTER" == "5" ]] || fail "Bewertung nach Restore: $RATINGS_AFTER"
[[ "$MEALS_AFTER" == "2" ]] || fail "Mahlzeiten nach Restore: $MEALS_AFTER"
[[ "$FLYWAY_AFTER" == "$FLYWAY_BEFORE" ]] || fail "Flyway-Version $FLYWAY_AFTER != $FLYWAY_BEFORE"
[[ "$REMEMBER_AFTER" == "0" ]] || fail "Remember-me hätte nach Restore invalidiert sein müssen (war $REMEMBER_AFTER)"
printf '%s\n' "$ROLES" | grep -q ADMIN || fail "ADMIN-Rolle fehlt"
printf '%s\n' "$ROLES" | grep -q MEMBER || fail "MEMBER-Rolle fehlt"
[[ "$HASH_PRESENT" == "2" ]] || fail "Passwort-Hashes nach Restore unvollständig"

# Anwendung fachlich erreichbar und Login möglich
curl -fsS "$BASE/api/status" | grep -q '"application":"was-essen-wir"' \
  || fail "API nach Restore nicht erreichbar"
rm -f "$COOKIE_JAR"
COOKIE_JAR="$(mktemp "${TMPDIR:-/tmp}/waew-cookies.XXXXXX")"
curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" "$BASE/api/setup/status" >/dev/null
LOGIN_BODY="$(api POST /api/auth/login "$(python3 - <<PY
import json
print(json.dumps({"username": "$ADMIN_USER", "password": "$ADMIN_PASS"}))
PY
)")"
printf '%s' "$LOGIN_BODY" | grep -q "$ADMIN_USER" || fail "Login nach Restore fehlgeschlagen"
DISH_LIST="$(api GET /api/dishes)"
printf '%s' "$DISH_LIST" | grep -q 'CI-Pizza' || fail "Gericht über API nach Restore nicht sichtbar"

echo "ci-backup-restore: OK"

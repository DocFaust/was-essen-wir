#!/usr/bin/env bash
# PostgreSQL-Backup (pg_dump Custom-Format) für lokale und produktive Compose-Stacks.
#
# Umgebungsvariablen:
#   BACKUP_DIR              Zielverzeichnis außerhalb der Container (Standard: <repo>/backups)
#   RETENTION_DAYS          Aufbewahrung in Tagen (Standard: 30); 0 = keine Bereinigung
#   COMPOSE_FILES           Compose-Dateien, z. B. "compose.yaml" oder "compose.yaml compose.prod.yaml"
#   COMPOSE_PROJECT_NAME    optionaler Compose-Projektname
#   WAEW_ENV_FILE           optionaler Pfad zur .env (Standard: <repo>/.env)
#   BACKUP_FILE_PREFIX      Dateipräfix (Standard: wasessenwir-pg)
#
# Exit-Codes: 0 Erfolg, 1 Fehler, 2 Nutzung/Voraussetzung, 3 Lock belegt
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/compose-env.sh
source "$ROOT/scripts/lib/compose-env.sh"

waew_require_cmd docker flock

PREFIX="${BACKUP_FILE_PREFIX:-wasessenwir-pg}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET="${BACKUP_DIR}/${PREFIX}-${TIMESTAMP}.dump"
TMP="${TARGET}.tmp.$$"
LOCK="${BACKUP_DIR}/.wasessenwir-pg.lock"
REMOTE_VERIFY="/tmp/waew-backup-verify.dump"

fail() {
  echo "backup-postgres: $*" >&2
  exit 1
}

usage_fail() {
  echo "backup-postgres: $*" >&2
  exit 2
}

cleanup_tmp() {
  rm -f "$TMP"
  docker compose "${COMPOSE_ARGS[@]}" exec -T postgres rm -f "$REMOTE_VERIFY" >/dev/null 2>&1 || true
}

# Nur eigene, eindeutig benannte Dump-Dateien löschen (exakter Präfix + Zeitstempelmuster).
prune_old_backups() {
  local days="$1"
  if [[ "$days" -le 0 ]]; then
    return 0
  fi
  find "$BACKUP_DIR" -maxdepth 1 -type f \
    -name "${PREFIX}-????????T??????Z.dump" \
    -mtime "+${days}" \
    -print -delete 2>/dev/null || true
}

verify_archive() {
  local file="$1"
  local magic
  magic="$(head -c 5 "$file" || true)"
  [[ "$magic" == "PGDMP" ]] || fail "Backup hat kein pg_dump-Custom-Format-Magic"

  docker compose "${COMPOSE_ARGS[@]}" exec -T postgres rm -f "$REMOTE_VERIFY" >/dev/null 2>&1 || true
  docker compose "${COMPOSE_ARGS[@]}" cp "$file" "postgres:${REMOTE_VERIFY}" >/dev/null \
    || fail "Kopieren zur Verifikation fehlgeschlagen"
  docker compose "${COMPOSE_ARGS[@]}" exec -T postgres pg_restore --list "$REMOTE_VERIFY" >/dev/null \
    || fail "Backup-Archiv ist nicht lesbar (pg_restore --list fehlgeschlagen)"
  docker compose "${COMPOSE_ARGS[@]}" exec -T postgres rm -f "$REMOTE_VERIFY" >/dev/null 2>&1 || true
}

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR" 2>/dev/null || true

if ! [[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]]; then
  usage_fail "RETENTION_DAYS muss eine nicht-negative Ganzzahl sein"
fi

COMPOSE_ARGS=()
waew_compose_args COMPOSE_ARGS "$ROOT"

# Postgres muss laufen; Zugangsdaten kommen aus dem Container-Environment
# (keine Passwörter in Kommandozeilenargumenten oder Logausgaben).
if ! docker compose "${COMPOSE_ARGS[@]}" ps --status running --services 2>/dev/null | grep -qx postgres; then
  fail "Postgres-Container läuft nicht (COMPOSE_FILES=${COMPOSE_FILES:-compose.yaml})"
fi

exec 9>"$LOCK"
if ! flock -n 9; then
  echo "backup-postgres: ein anderer Backup-/Restore-Vorgang läuft bereits (Lock: $LOCK)" >&2
  exit 3
fi

trap cleanup_tmp EXIT

echo "backup-postgres: erstelle ${TARGET}"

# Custom-Format (-Fc): konsistenter Snapshot während des laufenden Betriebs.
if ! docker compose "${COMPOSE_ARGS[@]}" exec -T postgres \
  sh -c 'pg_dump -Fc --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >"$TMP"
then
  cleanup_tmp
  fail "pg_dump fehlgeschlagen"
fi

if [[ ! -s "$TMP" ]]; then
  cleanup_tmp
  fail "Backup-Datei ist leer"
fi

chmod 600 "$TMP"
verify_archive "$TMP"

mv -f "$TMP" "$TARGET"
chmod 600 "$TARGET"
trap - EXIT
cleanup_tmp

echo "backup-postgres: OK ($(wc -c <"$TARGET" | tr -d ' ') Bytes)"

# Bereinigung erst nach erfolgreichem Backup.
prune_old_backups "$RETENTION_DAYS"
exit 0

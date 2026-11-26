#!/usr/bin/env bash
# PostgreSQL-Wiederherstellung (pg_restore) für lokale und produktive Compose-Stacks.
#
# Pflicht:
#   BACKUP_FILE=/pfad/zur/datei.dump
#   CONFIRM=YES                 explizite Bestätigung (destruktiv)
#
# Optional:
#   COMPOSE_FILES, COMPOSE_PROJECT_NAME, WAEW_ENV_FILE  (wie backup-postgres.sh)
#   BACKUP_DIR, RETENTION_DAYS, BACKUP_FILE_PREFIX      für das Sicherheitsbackup
#   SKIP_SAFETY_BACKUP=1        nur für isolierte CI-Tests
#   INVALIDATE_AUTH_TOKENS=1    Standard: Remember-me leeren, Setup-Token defensiv prüfen
#
# Exit-Codes: 0 Erfolg, 1 Fehler, 2 Nutzung, 3 Lock, 4 Bestätigung verweigert
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/compose-env.sh
source "$ROOT/scripts/lib/compose-env.sh"

waew_require_cmd docker flock

PREFIX="${BACKUP_FILE_PREFIX:-wasessenwir-pg}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/backups}"
INVALIDATE_AUTH_TOKENS="${INVALIDATE_AUTH_TOKENS:-1}"
SKIP_SAFETY_BACKUP="${SKIP_SAFETY_BACKUP:-0}"
LOCK="${BACKUP_DIR}/.wasessenwir-pg.lock"
REMOTE_DUMP="/tmp/waew-restore.dump"
SERVICES_STOPPED=0
RESTORE_FAILED=0

fail() {
  echo "restore-postgres: $*" >&2
  exit 1
}

usage_fail() {
  echo "restore-postgres: $*" >&2
  exit 2
}

log() {
  echo "restore-postgres: $*"
}

COMPOSE_ARGS=()
waew_compose_args COMPOSE_ARGS "$ROOT"

compose() {
  docker compose "${COMPOSE_ARGS[@]}" "$@"
}

postgres_running() {
  compose ps --status running --services 2>/dev/null | grep -qx postgres
}

remote_rm() {
  compose exec -T postgres rm -f "$REMOTE_DUMP" >/dev/null 2>&1 || true
}

verify_host_archive() {
  local file="$1"
  [[ -f "$file" ]] || fail "Backup-Datei fehlt: $file"
  [[ -s "$file" ]] || fail "Backup-Datei ist leer: $file"
  local magic
  magic="$(head -c 5 "$file" || true)"
  [[ "$magic" == "PGDMP" ]] || fail "Ungültiges Backup-Archiv (kein pg_dump Custom-Format): $file"

  remote_rm
  compose cp "$file" "postgres:${REMOTE_DUMP}" >/dev/null \
    || fail "Kopieren der Backup-Datei in den Postgres-Container fehlgeschlagen"
  compose exec -T postgres pg_restore --list "$REMOTE_DUMP" >/dev/null \
    || fail "Backup-Archiv ist nicht lesbar (pg_restore --list)"
}

stop_app_writers() {
  log "stoppe Backend und Frontend (Schreibschutz während Restore)"
  compose stop backend frontend >/dev/null
  SERVICES_STOPPED=1
}

start_app() {
  log "starte Backend und Frontend"
  compose start backend frontend >/dev/null 2>&1 || compose up -d backend frontend >/dev/null
  SERVICES_STOPPED=0
}

wait_for_backend() {
  local i
  for ((i = 1; i <= 90; i++)); do
    if compose exec -T backend /healthcheck.sh >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  compose logs --tail 80 backend >&2 || true
  fail "Backend nach Restore nicht healthy (möglicher Schema-/Flyway-Konflikt)"
}

invalidate_auth_tokens() {
  [[ "$INVALIDATE_AUTH_TOKENS" == "1" ]] || {
    log "INVALIDATE_AUTH_TOKENS=0 – Remember-me-/Setup-Token unverändert"
    return 0
  }
  log "invalidiere Remember-me-Token und bereinige Setup-Token defensiv"
  # Sessions sind In-Memory und sterben mit dem Backend-Neustart.
  # Remember-me aus dem Backup würde widerrufene Geräte sonst wieder aktivieren.
  compose exec -T postgres sh -c \
    'psql -v ON_ERROR_STOP=1 --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DELETE FROM remember_me_token;"' \
    >/dev/null \
    || fail "Invalidierung der Remember-me-Token fehlgeschlagen"
  compose exec -T postgres sh -c \
    'psql -v ON_ERROR_STOP=1 --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "UPDATE app_setup SET token_hash = NULL, expires_at = NULL, updated_at = NOW() WHERE completed_at IS NOT NULL AND token_hash IS NOT NULL;"' \
    >/dev/null \
    || fail "Bereinigung der Setup-Token fehlgeschlagen"
}

on_exit() {
  remote_rm
  if [[ "$RESTORE_FAILED" -eq 1 ]]; then
    echo "restore-postgres: FEHLER – Backend/Frontend bleiben gestoppt, damit kein Teilzustand live geht." >&2
    echo "restore-postgres: Prüfen Sie das Sicherheitsbackup unter ${BACKUP_DIR} und die Container-Logs." >&2
    return
  fi
  if [[ "$SERVICES_STOPPED" -eq 1 ]]; then
    start_app || true
  fi
}

BACKUP_FILE="${BACKUP_FILE:-${1:-}}"
[[ -n "$BACKUP_FILE" ]] || usage_fail "BACKUP_FILE setzen oder als erstes Argument übergeben"

if [[ "${CONFIRM:-}" != "YES" ]]; then
  echo "restore-postgres: Destruktive Aktion. Setzen Sie CONFIRM=YES zur Bestätigung." >&2
  echo "restore-postgres: Beispiel: CONFIRM=YES BACKUP_FILE=$BACKUP_FILE $0" >&2
  exit 4
fi

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR" 2>/dev/null || true

postgres_running || fail "Postgres-Container läuft nicht"

exec 9>"$LOCK"
if ! flock -n 9; then
  echo "restore-postgres: ein anderer Backup-/Restore-Vorgang läuft (Lock: $LOCK)" >&2
  exit 3
fi

trap on_exit EXIT

log "prüfe Archiv ${BACKUP_FILE}"
verify_host_archive "$BACKUP_FILE"

if [[ "$SKIP_SAFETY_BACKUP" != "1" ]]; then
  log "erstelle Sicherheitsbackup der aktuellen Datenbank"
  # Gemeinsames Lock: flock ist rekursiv pro Prozess (gleiche FD-Nummer nicht nötig),
  # daher Unterprozess mit eigenem Lock – kurz freigeben indem wir im Kind denselben
  # Lock-Pfad nutzen würde blockieren. Deshalb Sicherheitsbackup ohne paralleles
  # zweites Lock: wir rufen pg_dump direkt analog auf.
  SAFETY_TARGET="${BACKUP_DIR}/${PREFIX}-pre-restore-$(date -u +%Y%m%dT%H%M%SZ).dump"
  SAFETY_TMP="${SAFETY_TARGET}.tmp.$$"
  if ! compose exec -T postgres \
    sh -c 'pg_dump -Fc --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >"$SAFETY_TMP"
  then
    rm -f "$SAFETY_TMP"
    fail "Sicherheitsbackup fehlgeschlagen – Restore abgebrochen"
  fi
  [[ -s "$SAFETY_TMP" ]] || {
    rm -f "$SAFETY_TMP"
    fail "Sicherheitsbackup ist leer – Restore abgebrochen"
  }
  chmod 600 "$SAFETY_TMP"
  magic="$(head -c 5 "$SAFETY_TMP" || true)"
  [[ "$magic" == "PGDMP" ]] || {
    rm -f "$SAFETY_TMP"
    fail "Sicherheitsbackup ungültig – Restore abgebrochen"
  }
  mv -f "$SAFETY_TMP" "$SAFETY_TARGET"
  chmod 600 "$SAFETY_TARGET"
  log "Sicherheitsbackup: ${SAFETY_TARGET}"
else
  log "SKIP_SAFETY_BACKUP=1 – kein Sicherheitsbackup"
fi

stop_app_writers
sleep 2

log "stelle Datenbank wieder her (pg_restore --clean --if-exists)"
RESTORE_FAILED=1
# --exit-on-error: bei Fehlern abbrechen, kein stiller Teilzustand.
if ! compose exec -T postgres sh -c \
  "pg_restore --clean --if-exists --exit-on-error --no-owner --no-acl --no-password -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" ${REMOTE_DUMP}"
then
  fail "pg_restore fehlgeschlagen"
fi

invalidate_auth_tokens
RESTORE_FAILED=0
remote_rm

start_app
log "warte auf Anwendungsstart (Flyway prüft Schemaversion gegen den wiederhergestellten Stand)"
# Flyway: Schema kommt vollständig aus dem Dump inklusive flyway_schema_history.
# Bei Schema-Mismatch startet das Backend nicht – das ist beabsichtigt.
wait_for_backend

log "OK – Wiederherstellung abgeschlossen"
log "Hinweis: Alle Benutzer müssen sich erneut anmelden (Remember-me invalidiert, Sessions neu)."
exit 0

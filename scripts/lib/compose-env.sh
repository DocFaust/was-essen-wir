#!/usr/bin/env bash
# Gemeinsame Hilfen für Backup-/Restore-Skripte.
# shellcheck shell=bash

waew_require_cmd() {
  local cmd
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || {
      echo "Fehlendes Kommando: $cmd" >&2
      exit 2
    }
  done
}

# Setzt Compose-Argumente in das Array-Namen $1 (nameref).
# COMPOSE_FILES: komma- oder leerzeichengetrennte Dateiliste relativ zum Repo-Root.
# COMPOSE_PROJECT_NAME / WAEW_ENV_FILE optional.
waew_compose_args() {
  local -n _out="$1"
  local root="$2"
  _out=()
  if [[ -n "${COMPOSE_PROJECT_NAME:-}" ]]; then
    _out+=(--project-name "$COMPOSE_PROJECT_NAME")
  fi
  local files="${COMPOSE_FILES:-compose.yaml}"
  local f
  for f in ${files//,/ }; do
    [[ -n "$f" ]] || continue
    if [[ "$f" = /* ]]; then
      _out+=(-f "$f")
    else
      _out+=(-f "$root/$f")
    fi
  done
  local env_file="${WAEW_ENV_FILE:-$root/.env}"
  if [[ -f "$env_file" ]]; then
    _out+=(--env-file "$env_file")
  fi
}

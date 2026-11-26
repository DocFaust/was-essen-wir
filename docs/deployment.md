# Deployment und Betrieb

**Stand:** Iteration 10.5.

Zielumgebung: Netcup-Rootserver, bestehender Host-Nginx mit TLS, Docker Compose, eine Backend-Instanz. Geplante öffentliche URL: `https://essen.faustens.de`.

Der private HTTP-Betrieb im Heimnetz (Raspberry Pi) bleibt unverändert über `compose.yaml` unterstützt.

**Wichtig:** Technische Deployment-Bereitschaft (Code, Doku, CI) ist nicht gleich Betriebsfreigabe. Die Betriebsfreigabe erfolgt erst nach erfolgreicher manueller Prüfung auf dem Zielserver anhand der [Go-Live-Checkliste](go-live-checklist.md).

## Voraussetzungen

- Docker Compose v2
- Bestehender Nginx-Systemdienst auf dem Host (nicht in Compose)
- DNS-A/AAAA-Eintrag für `essen.faustens.de` auf den Rootserver
- Offene Firewall-Ports 80/443 am Host; Anwendungsports nicht öffentlich
- Git-Checkout dieses Repositories auf dem Server
- Für automatische Backups: systemd auf dem Host

## Vertrauensgrenzen (Proxy-Header)

Kette: Browser → Host-Nginx → Frontend-Nginx → Spring Boot.

| Stufe | Vertrauensstellung |
| --- | --- |
| Host-Nginx | Einzige öffentliche Eingangsstufe. Überschreibt `X-Forwarded-For`, `X-Real-IP` und `X-Forwarded-Proto` mit Werten aus der TCP-Verbindung (`$remote_addr`, `$scheme`). Client-gesetzte Forwarded-Header werden nicht übernommen. |
| Frontend-Nginx (Prod) | Vertraut nur Loopback und das Compose-Netz `172.30.90.0/24` (`set_real_ip_from`). Setzt gegenüber dem Backend erneut `X-Forwarded-For` / `X-Real-IP` und reicht `X-Forwarded-Proto` durch. |
| Backend | Wertet Forwarded-Header nur aus, wenn `APP_FORWARD_CLIENT_IP=true` und die Peer-Adresse in `APP_TRUSTED_PROXIES` liegt (Standard `172.30.90.0/24`). Sonst ausschließlich `request.getRemoteAddr()`. |
| Lokaler HTTP | `APP_FORWARD_CLIENT_IP=false` (Standard). Frontend überschreibt Forwarded-Header mit der TCP-Peer-Adresse. Spoofing über `X-Forwarded-For` greift nicht. |

Das produktive Compose-Netz ist fest auf `172.30.90.0/24` gesetzt. Der Frontend-Port ist ausschließlich an `127.0.0.1` gebunden.

## DNS

1. A-Record (und bei Bedarf AAAA) `essen.faustens.de` → öffentliche Server-IP.
2. Propagation prüfen: `dig +short essen.faustens.de`.

## Produktive `.env`

```sh
cp .env.example .env
chmod 600 .env
```

Mindestens setzen:

| Variable | Prod-Wert |
| --- | --- |
| `POSTGRES_PASSWORD` | starkes, einzigartiges Passwort |
| `APP_PORT` | z. B. `8182` (nur Loopback, siehe Compose-Prod) |
| `APP_SESSION_COOKIE_SECURE` | wird durch `compose.prod.yaml` auf `true` gezwungen |
| `OPENAI_API_KEY` | nur falls `AI_PROVIDER=openai` |

Keine echten Produktivgeheimnisse in Git. `.env` bleibt lokal auf dem Server (`chmod 600`, Besitz nur Betriebskonto).

Zusätzliche optionale Limits: `BACKEND_MEMORY_LIMIT`, `POSTGRES_MEMORY_LIMIT`, `FRONTEND_MEMORY_LIMIT`, `APP_MAX_REQUEST_SIZE`, `APP_TRUSTED_PROXIES`.

## Start der Anwendung

```sh
docker compose --env-file .env -f compose.yaml -f compose.prod.yaml up -d --build
```

Persistentes Volume `postgres-data` bleibt erhalten (gleicher Projektname `was-essen-wir`).

Konfiguration prüfen:

```sh
./scripts/validate-compose-prod.sh
docker compose --env-file .env -f compose.yaml -f compose.prod.yaml ps
ss -lntp | grep 8182   # oder APP_PORT
```

Erwartung: nur `127.0.0.1:8182` (Frontend). Keine veröffentlichten Backend- oder PostgreSQL-Ports.

## Host-Nginx

Beispiel-VHost: [`deploy/nginx/essen.faustens.de.conf.example`](../deploy/nginx/essen.faustens.de.conf.example).

1. Datei nach `/etc/nginx/sites-available/essen.faustens.de.conf` kopieren und Upstream-Port an `APP_PORT` anpassen.
2. Symlink nach `sites-enabled` setzen (andere VHosts unberührt lassen).
3. Zuerst HTTP-Serverblock für ACME, dann Certbot:

```sh
sudo certbot --nginx -d essen.faustens.de
sudo nginx -t
sudo systemctl reload nginx
```

Timeouts für `/api/ai/` sind im Beispiel auf 45s gesetzt. `client_max_body_size` ist überall 256k. HSTS nur auf HTTPS, ohne `includeSubDomains` und ohne `preload`.

## Ersteinrichtung

```sh
docker compose --env-file .env -f compose.yaml -f compose.prod.yaml logs backend | grep SETUP_TOKEN
```

Token einmalig im Browser unter `https://essen.faustens.de` verwenden. Danach ist die Einrichtung geschlossen.

## HTTPS- und Cookie-Prüfung

Nach dem Login prüfen:

- `WAEW_SESSION`: `HttpOnly`, `Secure`, `SameSite=Lax`
- `WAEW_REMEMBER` (optional): `HttpOnly`, `Secure`, `SameSite=Lax`
- `XSRF-TOKEN`: lesbar für das Frontend (`HttpOnly` aus), `Secure`, `SameSite=Lax`
- Antwortheader: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`
- Auf HTTPS: `Strict-Transport-Security` (vom Host-Nginx)

## Backup und Wiederherstellung

Skripte nutzen `pg_dump`/`pg_restore` im Custom-Format über den laufenden Postgres-Container. Zugangsdaten kommen aus dem Container-Environment – nicht aus Kommandozeilenargumenten. Backup-Verzeichnis liegt außerhalb der Container und DB-Volumes (Standard `<repo>/backups`, konfigurierbar über `BACKUP_DIR`).

### Was enthält ein Backup?

Alle PostgreSQL-Daten der Anwendung, insbesondere:

- Benutzer, Rollen, Passwort-Hashes (BCrypt)
- Gerichte, Varianten, Zutaten
- Persönliche Bewertungen
- Essensplanung und Historie
- Remember-me-Token (nur Hashes)
- Setup-Zustand (`app_setup`)
- Flyway-Schemahistorie

### Was gehört nicht ins DB-Backup und muss separat gesichert werden?

| Artefakt | Zweck |
| --- | --- |
| `.env` (`chmod 600`) | DB-Passwort, API-Schlüssel, Betriebsflags |
| Host-Nginx-VHost und TLS-Zertifikate/Certbot | Öffentlicher HTTPS-Zugang |
| Compose-Dateien / Git-Stand | Abbildbare Anwendungskonfiguration |
| systemd-Timer/Service (falls angepasst) | Backup-Automatisierung |

Ein lokales Backup auf demselben Server schützt **nicht** vor vollständigem Serververlust. Zusätzlich regelmäßig verschlüsselte Kopien der Dump-Dateien und der `.env` an einen externen Aufbewahrungsort übertragen (beliebiger Anbieter oder Offline-Medium; keine konkrete Infrastruktur im Projekt vorgeschrieben).

### Manuelles Backup

Lokal (Heimnetz):

```sh
./scripts/backup-postgres.sh
```

Produktiv:

```sh
BACKUP_DIR=/var/backups/was-essen-wir \
RETENTION_DAYS=30 \
COMPOSE_FILES="compose.yaml compose.prod.yaml" \
WAEW_ENV_FILE=/opt/was-essen-wir/.env \
./scripts/backup-postgres.sh
```

Eigenschaften:

- Dateiname `wasessenwir-pg-YYYYMMDDTHHMMSSZ.dump`
- Verzeichnis `chmod 700`, Dateien `chmod 600`
- Lesbarkeit per `pg_restore --list`
- Atomare Fertigstellung (keine `.tmp` als Erfolg)
- `flock` gegen parallele Backup-/Restore-Läufe
- Bereinigung eigener Dump-Dateien erst nach erfolgreichem Backup (Standard 30 Tage)

### Automatisches Backup (systemd)

Beispieldateien: [`deploy/systemd/`](../deploy/systemd/).

```sh
sudo mkdir -p /var/backups/was-essen-wir
sudo chown wasessenwir:wasessenwir /var/backups/was-essen-wir
sudo chmod 700 /var/backups/was-essen-wir

# Pfade in Service-Unit an die Installation anpassen, dann:
sudo cp deploy/systemd/was-essen-wir-backup.service /etc/systemd/system/
sudo cp deploy/systemd/was-essen-wir-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now was-essen-wir-backup.timer
systemctl list-timers was-essen-wir-backup.timer
```

Kein zusätzlicher Backup-Container. Ausführung täglich (Standard 03:15 Hostzeit).

### Wiederherstellung

```sh
CONFIRM=YES \
BACKUP_FILE=/var/backups/was-essen-wir/wasessenwir-pg-….dump \
COMPOSE_FILES="compose.yaml compose.prod.yaml" \
WAEW_ENV_FILE=/opt/was-essen-wir/.env \
BACKUP_DIR=/var/backups/was-essen-wir \
./scripts/restore-postgres.sh
```

Ablauf:

1. Archiv prüfen (Magic + `pg_restore --list`)
2. Explizite Bestätigung `CONFIRM=YES` (sonst Exit 4)
3. Sicherheitsbackup der aktuellen DB
4. Backend und Frontend stoppen (keine parallelen Schreibzugriffe)
5. `pg_restore --clean --if-exists --exit-on-error`
6. Remember-me-Token löschen; Setup-Token defensiv leeren, falls Einrichtung bereits abgeschlossen war
7. Dienste starten; Healthcheck abwarten

Bei Fehlern bleiben Backend/Frontend gestoppt, damit kein Teilzustand live geht. Sessions sind In-Memory und enden mit dem Neustart. Nach Restore müssen sich alle erneut anmelden.

**Flyway:** Das Dump enthält Schema und `flyway_schema_history`. Die Anwendungswelt muss zum Backup-Stand passen (gleiche Migrationsversion). Ein neueres Backend gegen ein älteres Schema migriert ggf. nach; ein älteres Backend gegen neueres Schema startet typischerweise nicht – das ist beabsichtigt.

### Wiederherstellung auf einem neuen Server

1. Docker, Compose, Git-Checkout, Host-Nginx vorbereiten
2. `.env` sicher übertragen (`chmod 600`)
3. Stack starten (leere DB), dann Restore wie oben
4. DNS/TLS und Checkliste durchgehen
5. Externes Backup der neuen Umgebung einrichten

### Schutz der Backup-Dateien

- Verzeichnis nur für das Betriebskonto (`700` / `600`)
- Nicht in Git, nicht in öffentlich erreichbare Webroots
- Externe Kopien verschlüsselt übertragen/aufbewahren (z. B. `gpg -c` vor dem Kopieren)
- Regelmäßig Wiederherstellung in einer isolierten Umgebung testen (`./scripts/ci-backup-restore.sh` als Vorlage)

## Aktualisierung der Anwendung

Vor jedem Update ein Backup:

```sh
BACKUP_DIR=/var/backups/was-essen-wir \
COMPOSE_FILES="compose.yaml compose.prod.yaml" \
WAEW_ENV_FILE=/opt/was-essen-wir/.env \
./scripts/backup-postgres.sh
```

Dann:

```sh
git pull
docker compose --env-file .env -f compose.yaml -f compose.prod.yaml up -d --build
docker compose --env-file .env -f compose.yaml -f compose.prod.yaml ps
```

Volumes nicht mit `--volumes` löschen. Offene Sitzungen enden bei Backend-Neustart; Remember-me stellt sie wieder her (sofern nicht nach Restore invalidiert).

### Fehlgeschlagene Updates / Migrationen

1. Backend-Logs: `docker compose … logs backend`
2. Bei Flyway-Fehler: nicht manuell am Schema „reparieren“, sondern Ursache klären
3. Bei Bedarf auf vorherigen Git-Stand zurück und Restore aus dem Sicherheitsbackup
4. Erst nach erfolgreichem Healthcheck wieder freigeben

## Betriebsprüfungen

### Container-Healthchecks

```sh
docker compose --env-file .env -f compose.yaml -f compose.prod.yaml ps
```

Alle Services `healthy` bzw. Postgres ready.

### Container-Logs

```sh
docker compose --env-file .env -f compose.yaml -f compose.prod.yaml logs --since 1h backend
docker compose --env-file .env -f compose.yaml -f compose.prod.yaml logs --since 1h frontend
```

Keine Passwörter, Setup-Token (außer dem einmaligen Bootstrap) oder API-Schlüssel in Tickets/Chats kopieren.

### TLS-Zertifikat

```sh
echo | openssl s_client -connect essen.faustens.de:443 -servername essen.faustens.de 2>/dev/null | openssl x509 -noout -dates -subject
sudo certbot certificates
```

Certbot-Renewal ist Host-Sache (`certbot renew` / Timer).

### Zugangsdaten und API-Schlüssel rotieren

1. Neues `POSTGRES_PASSWORD` / `OPENAI_API_KEY` in `.env`
2. Stack neu starten (`up -d`)
3. Alte Schlüssel beim Provider widerrufen
4. Bei Verdacht auf Kompromittierung: Passwörter der Benutzer zurücksetzen, Remember-me durch Logout/Passwortänderung widerrufen

### Sicherheitsvorfall (Kurzablauf)

1. Erreichbarkeit einschränken (Nginx-VHost deaktivieren oder Firewall)
2. Container-Logs und Zeitpunkt sichern
3. Backup des aktuellen Stands (forensisch), bekannte gute Backups nicht überschreiben
4. Geheimnisse rotieren (`.env`, DB-Passwort, API-Schlüssel, Benutzerpasswörter)
5. Ursache beheben, Scans prüfen, erst dann wieder freigeben

### KI deaktivieren

In `.env`:

```sh
AI_ENABLED=false
```

Stack neu starten. Kernfunktionen bleiben nutzbar. Bei Provider-Problemen oder Kostenalarm sofort umsetzen.

### Sicherheitsscans auswerten

- Workflow [`.github/workflows/security.yml`](../.github/workflows/security.yml): Trivy (FS, Backend-/Frontend-/PostgreSQL-Images), `npm audit --omit=dev`
- Läuft auf PRs, `main` und wöchentlich
- CRITICAL/HIGH mit verfügbarer Fehlerbehebung brechen die Pipeline
- Ausnahmen nur in [`security-exceptions.md`](security-exceptions.md) + `.trivyignore`
- Artifacts der Scan-Reports in GitHub Actions prüfen

## Fehlerdiagnose

| Symptom | Prüfung |
| --- | --- |
| Backend startet nicht | `APP_REQUIRE_SECURE_COOKIES` vs. Secure-Cookies; Logs |
| 502 vom Host-Nginx | Frontend auf `127.0.0.1:APP_PORT`? |
| Cookies fehlen | Secure-Cookies brauchen HTTPS; `X-Forwarded-Proto https` |
| 413 | Request größer als 256 KiB |
| KI-Timeouts | Provider-Timeout &lt; 45s Proxy-Timeout |
| Backup schlägt fehl | Postgres running? Lock? Rechte auf `BACKUP_DIR`? |
| Restore partial | Backend gestoppt lassen; Sicherheitsbackup einspielen |

## Abgrenzung

Nicht in dieser Anleitung: tatsächliche Serverinstallation, Änderungen an anderen Host-VHosts, Keycloak, Multi-Tenancy, Backup-Weboberfläche, Monitoring-Infrastruktur, externe Backup-Dienste als Produktpflicht.

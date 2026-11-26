# Checkliste produktive Inbetriebnahme

**Ziel:** `https://essen.faustens.de` auf dem Netcup-Rootserver.

Unterscheidung:

| Stufe | Bedeutung |
| --- | --- |
| **Technische Deployment-Bereitschaft** | Code, Tests, Doku und CI für Iteration 10.5 sind erfüllt (Repository). |
| **Betriebsfreigabe** | Alle Punkte unten auf dem Zielserver erfolgreich geprüft und dokumentiert. |

Ohne abgeschlossene Betriebsfreigabe keinen produktiven Internetzugang schalten.

## A. Vorbereitung Host

- [ ] Vorgesehener Host-Port (`APP_PORT`, z. B. 8182) ist frei (`ss -lntp`)
- [ ] Docker-Netz `172.30.90.0/24` überschneidet sich nicht mit bestehenden Netzen (`docker network ls` / `ip route`)
- [ ] DNS-A/AAAA für `essen.faustens.de` zeigt auf den Server (`dig +short`)
- [ ] Firewall erlaubt 80/443; Anwendungsports nicht öffentlich
- [ ] Repository ausgecheckt, Betriebskonto ohne unnötige Rechte

## B. Konfiguration

- [ ] `.env` aus `.env.example`, starke `POSTGRES_PASSWORD`, ggf. `OPENAI_API_KEY`
- [ ] Dateiberechtigungen: `.env` → `600`, Besitz Betriebskonto
- [ ] Keine Produktivgeheimnisse in Git oder Tickets
- [ ] `APP_PORT` und optionale Limits gesetzt

## C. Compose-Start

- [ ] `docker compose --env-file .env -f compose.yaml -f compose.prod.yaml up -d --build`
- [ ] `./scripts/validate-compose-prod.sh` erfolgreich
- [ ] `docker compose … ps` – alle Services healthy
- [ ] Frontend nur `127.0.0.1:APP_PORT`
- [ ] Backend und PostgreSQL ohne Host-Ports (`docker inspect` / Validate-Skript)

## D. Nginx und TLS

- [ ] VHost aus `deploy/nginx/essen.faustens.de.conf.example` installiert
- [ ] Upstream-Port = `APP_PORT`
- [ ] Certbot / gültiges Zertifikat für `essen.faustens.de`
- [ ] HTTP → HTTPS-Weiterleitung aktiv
- [ ] `nginx -t` und Reload ohne Fehler
- [ ] Andere VHosts unverändert

## E. Sicherheit am Live-System

- [ ] HTTP-Sicherheitsheader tatsächlich gesetzt (CSP, XCTO, Frame, Referrer, Permissions, HSTS auf HTTPS)
- [ ] Session-/Remember-me-/CSRF-Cookies: `Secure`, erwartetes `HttpOnly`/`SameSite`
- [ ] Proxy-Kette: Host-Nginx überschreibt Forwarded-Header; Spoofing-Test von außen ohne Wirkung auf AttemptLimiter
- [ ] Keine direkt öffentlich erreichbaren Ports von Backend/Postgres/Frontend (nur 80/443)

## F. Anwendung

- [ ] Setup-Token aus Backend-Log, Administrator eingerichtet
- [ ] Login und Logout
- [ ] Remember-me (optional) funktioniert über Neustart der Sitzung
- [ ] Kernfunktionen ohne KI prüfbar
- [ ] Bei konfiguriertem Provider: KI-Aufruf erfolgreich; bei Problemen `AI_ENABLED=false` getestet

## G. Backup

- [ ] systemd-Timer installiert und aktiv (`was-essen-wir-backup.timer`)
- [ ] Erstes manuelles Backup erfolgreich, Datei `600`, `pg_restore --list` ok
- [ ] Externe verschlüsselte Kopie von Backup + `.env`-Sicherung eingerichtet
- [ ] Wiederherstellbarkeit zumindest einmal in isolierter Umgebung geübt

## H. Abschluss

- [ ] Abschließender Funktionstest (Heute/Plan/Gerichte/Historie/Familie)
- [ ] [Sicherheitsreview](security-review-10.5.md): keine ungeklärten BLOCKER/HIGH für Deployment-Bereitschaft
- [ ] Betriebsfreigabe schriftlich festgehalten (Datum, Person)

**Betriebsfreigabe erteilt:** ☐ ja / ☐ nein – Datum: ________ – durch: ________

# Deployment – öffentlicher HTTPS-Betrieb

**Stand:** Iteration 10.4. Der konkrete öffentliche Produktivstart erfolgt erst nach Abschluss von Iteration 10.5.

Zielumgebung: Netcup-Rootserver, bestehender Host-Nginx mit TLS, Docker Compose für die Anwendung, eine Backend-Instanz. Geplante öffentliche URL: `https://essen.faustens.de`.

Der private HTTP-Betrieb im Heimnetz (Raspberry Pi) bleibt unverändert über `compose.yaml` unterstützt.

## Voraussetzungen

- Docker Compose v2
- Bestehender Nginx-Systemdienst auf dem Host (nicht in Compose)
- DNS-A/AAAA-Eintrag für `essen.faustens.de` auf den Rootserver
- Offene Firewall-Ports 80/443 am Host; Anwendungsports nicht öffentlich
- Git-Checkout dieses Repositories auf dem Server

## Vertrauensgrenzen (Proxy-Header)

Kette: Browser → Host-Nginx → Frontend-Nginx → Spring Boot.

| Stufe | Vertrauensstellung |
| --- | --- |
| Host-Nginx | Einzige öffentliche Eingangsstufe. Überschreibt `X-Forwarded-For`, `X-Real-IP` und `X-Forwarded-Proto` mit Werten aus der TCP-Verbindung (`$remote_addr`, `$scheme`). Client-gesetzte Forwarded-Header werden nicht übernommen. |
| Frontend-Nginx (Prod) | Vertraut nur Loopback und das Compose-Netz `172.30.90.0/24` (`set_real_ip_from`). Setzt gegenüber dem Backend erneut `X-Forwarded-For` / `X-Real-IP` und reicht `X-Forwarded-Proto` durch. |
| Backend | Wertet Forwarded-Header nur aus, wenn `APP_FORWARD_CLIENT_IP=true` und die Peer-Adresse in `APP_TRUSTED_PROXIES` liegt (Standard `172.30.90.0/24`). Sonst ausschließlich `request.getRemoteAddr()`. |
| Lokaler HTTP | `APP_FORWARD_CLIENT_IP=false` (Standard). Frontend überschreibt Forwarded-Header mit der TCP-Peer-Adresse. Spoofing über `X-Forwarded-For` greift nicht. |

Das produktive Compose-Netz ist fest auf `172.30.90.0/24` gesetzt; es werden keine pauschalen RFC1918-Bereiche vertraut. Der Frontend-Port ist ausschließlich an `127.0.0.1` gebunden. Direkte Loopback-Zugriffe unter Umgehung des Host-Nginx bleiben ein lokales Restrisiko.

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

Keine echten Produktivgeheimnisse in Git. `.env` bleibt lokal auf dem Server.

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

Timeouts für `/api/ai/` sind im Beispiel auf 45s gesetzt (abgestimmt mit internem Nginx und Provider-Timeout 30s). `client_max_body_size` ist überall 256k, abgestimmt mit dem Backend-Limit.

HSTS wird nur auf HTTPS gesetzt, ohne `includeSubDomains` und ohne `preload`.

## Ersteinrichtung

```sh
docker compose --env-file .env -f compose.yaml -f compose.prod.yaml logs backend | grep SETUP_TOKEN
```

Token einmalig im Browser unter `https://essen.faustens.de` verwenden. Danach ist die Einrichtung geschlossen.

## HTTPS- und Cookie-Prüfung

Nach dem Login in den Entwicklertools bzw. per `curl -I` prüfen:

- `WAEW_SESSION`: `HttpOnly`, `Secure`, `SameSite=Lax`
- `WAEW_REMEMBER` (optional): `HttpOnly`, `Secure`, `SameSite=Lax`
- `XSRF-TOKEN`: lesbar für das Frontend (`HttpOnly` aus), `Secure`, `SameSite=Lax`
- Antwortheader: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`
- Auf HTTPS: `Strict-Transport-Security` (vom Host-Nginx)

## Aktualisierung

```sh
git pull
docker compose --env-file .env -f compose.yaml -f compose.prod.yaml up -d --build
```

Volumes nicht mit `--volumes` löschen. Offene Sitzungen enden bei Backend-Neustart; Remember-me stellt sie wieder her.

## Fehlerdiagnose

| Symptom | Prüfung |
| --- | --- |
| Backend startet nicht | `APP_REQUIRE_SECURE_COOKIES` vs. `APP_SESSION_COOKIE_SECURE`; Logs `docker compose ... logs backend` |
| 502 vom Host-Nginx | Lauscht Frontend auf `127.0.0.1:APP_PORT`? `docker compose ... ps` |
| Cookies fehlen | Secure-Cookies brauchen HTTPS; Host-Nginx setzt `X-Forwarded-Proto https` |
| 413 | Request größer als 256 KiB (Nginx und Backend) |
| KI-Timeouts | Provider-Timeout &lt; 45s Proxy-Timeout; siehe Iteration 10.3 |

## Abgrenzung

Nicht in dieser Anleitung: Backup/Restore-Automatisierung, CI-Sicherheitsscans, Betriebsfreigabe (Iteration 10.5), Änderungen an anderen Host-VHosts, Keycloak.

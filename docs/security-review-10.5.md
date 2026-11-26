# Abschließendes Sicherheitsreview – Iteration 10.5

**Stand:** Iteration 10.5 (Code- und Dokumentationsreview vor öffentlichem Betrieb).  
**Ziel:** Technische Deployment-Bereitschaft für `https://essen.faustens.de`.  
**Nicht Bestandteil:** Verifikation auf dem Netcup-Rootserver (siehe [go-live-checklist.md](go-live-checklist.md)).

Klassifikation: **BLOCKER** | **HIGH** | **MEDIUM** | **LOW** | **OK** | **NOT VERIFIED**

Bereits in 10.2–10.4 behobene Punkte werden nur bei neuen Erkenntnissen erneut als offen geführt.

---

## 1. Authentifizierung und Autorisierung

| Thema | Status | Nachweis / Anmerkung |
| --- | --- | --- |
| Setup-Prozess | **OK** | Einmaliges Token (SHA-256 in DB), Log-Ausgabe einmalig, Transaktion mit Sperre (`SetupService`, `V3__users_and_setup.sql`). Tests: `AuthIntegrationTest`, `SetupRotationIntegrationTest`. |
| Login / Logout | **OK** | Session-Fixation-Schutz über Session-Erneuerung, einheitliches `LOGIN_FAILED`. `AuthService`, `AuthIntegrationTest`. |
| Session-Sicherheit | **OK** | Serverseitige Sessions, `HttpOnly` + `SameSite=Lax`, Prod `Secure` erzwungen (`SecureCookiesValidator`, `compose.prod.yaml`). Öffentliche Statusendpunkte erzeugen keine Session (10.2). |
| Remember-me | **OK** | Selektor + Hash, Rotation, Widerruf bei Passwort/Deaktivierung, Max. 8 Tokens/Nutzer. Nach Restore: Invalidierung (`scripts/restore-postgres.sh`). Tests: `RememberMeIntegrationTest`. |
| Passwortänderungen | **OK** | Widerruf aller Remember-me + fremde Sessions (`UserService`). |
| Benutzerdeaktivierung | **OK** | `ActiveUserFilter` + Widerruf. |
| Rollen | **OK** | `ADMIN` / `MEMBER`; Benutzerverwaltung nur Admin. |
| CSRF | **OK** | Cookie-Repository + Header; Frontend sendet `X-XSRF-TOKEN`. |
| Persönliche Bewertungen | **OK** | `user_id` nur aus Sitzung; keine fremden Sterne in API (`RatingController`/Service, `RatingIntegrationTest`). |
| Brute-Force | **OK** | `AttemptLimiter` für Login/Setup/Passwort; Kapazitätsgrenze und keine Verdrängung aktiver Sperren (10.2, `AttemptLimiterTest`). |
| AttemptLimiter-Speicher | **OK** | `APP_ATTEMPT_LIMITER_MAX_KEYS` (Standard 10000); In-Memory pro Instanz (bewusst, eine Backend-Instanz). |
| Login-Timing | **OK** | Spring Security Dummy-Hash (10.2, `LoginTimingProtectionTest`). |

Verbleibend:

| Befund | Status | Hinweis |
| --- | --- | --- |
| AttemptLimiter / Sessions nur pro Instanz | **LOW** | Architektur: eine Backend-Instanz. Kein Cluster. |
| Direkter Loopback-Zugriff umgeht Host-Nginx | **LOW** | Dokumentiert; Port nur `127.0.0.1`. Lokales Restrisiko auf dem Server. |

---

## 2. KI-Sicherheit

| Thema | Status | Nachweis |
| --- | --- | --- |
| Auth der KI-Endpunkte | **OK** | Sitzung + CSRF; `AiController`. |
| Nutzungslimits | **OK** | Pro User/Minute, User/Tag, global/Tag (`AiUsageLimiter`, 10.3). |
| Parallelität | **OK** | Pro User und global. |
| Provider-Timeouts | **OK** | Standard 30s; Nginx `/api/ai/` 45s. |
| Ausgabetoken | **OK** | `AI_MAX_OUTPUT_TOKENS`. |
| API-Schlüssel | **OK** | Nur Backend-Env; nicht im Frontend. |
| Fixture in Prod | **OK** | `AI_ALLOW_FIXTURE=false` in Prod-Compose; Fixture nur Testprofil/`AI_ALLOW_FIXTURE`. |
| Provider-Ausfall | **OK** | Fehlerantworten ohne DB-Schreiben; Kern ohne KI nutzbar. |
| Limits ≠ Euro-Kosten | **MEDIUM** | Dokumentiert; Provider-seitige Kostenkontrollen bleiben empfohlen (`architektur.md`, `ki-assistent.md`). |

---

## 3. Deployment-Sicherheit

| Thema | Status | Nachweis |
| --- | --- | --- |
| Loopback-Portbindung | **OK** | `compose.prod.yaml` `!override` → `127.0.0.1`; `validate-compose-prod.sh`, `ci-compose-runtime.sh`. |
| Keine öffentlichen Backend-/DB-Ports | **OK** | Compose + CI-Laufzeittest. |
| Proxy-Vertrauensgrenzen | **OK** | Host-Nginx-Beispiel, `nginx.prod.conf`, `ClientIpResolver`, CIDR `172.30.90.0/24`. |
| Forwarded-Header-Spoofing | **OK** | Tests `ClientIpIntegrationTest`, `ClientIpSpoofingDisabledIntegrationTest`. |
| Secure Cookies | **OK** | Prod erzwungen; `SecureCookiesIntegrationTest`. |
| HTTP-Sicherheitsheader | **OK** | Spring `SecurityHeaders` + Frontend-Nginx; HSTS nur Host-Nginx. `SecurityHeadersIntegrationTest`. |
| Request-Größe 256 KiB | **OK** | Nginx + Backend inkl. Chunked (10.4). |
| Container-Härtung | **OK** | `read_only`, `cap_drop`, `no-new-privileges`, Non-Root (Backend/Frontend). Postgres ohne pauschales `cap_drop ALL` (begründet). |
| Ressourcenlimits | **OK** | Compose-Prod Memory/CPU/PIDs. |

Verbleibend:

| Befund | Status | Hinweis |
| --- | --- | --- |
| Tatsächliche Header/Cookies auf Zielserver | **NOT VERIFIED** | Muss in Go-Live-Checkliste geprüft werden. |
| Netz-Kollision `172.30.90.0/24` | **NOT VERIFIED** | Host-Prüfung vor Start. |

---

## 4. Betriebssicherheit (10.5)

| Thema | Status | Nachweis |
| --- | --- | --- |
| Backup (`pg_dump -Fc`) | **OK** | `scripts/backup-postgres.sh` – Lock, Rechte, Verify, Retention. |
| Restore | **OK** | `scripts/restore-postgres.sh` – Confirm, Safety-Backup, Stop Writers, `--exit-on-error`, Token-Invalidierung. |
| Backup-Dateischutz | **OK** | `700`/`600`, Doku in `deployment.md`; `.gitignore` `backups/`. |
| Automatisierter Backup-/Restore-Test | **OK** | `scripts/ci-backup-restore.sh` + CI-Job. |
| Sicherheitsscans | **OK** | `.github/workflows/security.yml` (Trivy FS/Images, npm audit prod); Strategie + `security-exceptions.md`. |
| Externe Backup-Kopie | **LOW** | Dokumentiert als Betriebsaufgabe; kein Anbieter vorgeschrieben. |
| Monitoring | **OK** (Abgrenzung) | Bewusst nicht eingeführt; Healthchecks + Logs reichen laut Vorgabe. |

---

## 5. Abhängigkeiten und Scans

| Thema | Status | Hinweis |
| --- | --- | --- |
| Dependabot | **OK** | Gradle, npm, Actions, Docker – wöchentlich, keine Major-Autos. |
| Trivy CRITICAL/HIGH (fix verfügbar) | **OK** (CI-Gate) | `ignore-unfixed: true`; Ausnahmen nur dokumentiert. |
| npm Produktionsabhängigkeiten | **OK** | `npm audit --omit=dev` lokal ohne Befunde (Stand Iteration 10.5). Dev-only-Hinweise zählen nicht zum Prod-Gate. |
| Erste Trivy-Image-Ergebnisse auf `main`/PR | **NOT VERIFIED** | Werden durch GitHub-CI (`security.yml`) dieser Iteration geliefert. |

---

## 6. Zusammenfassung offener Punkte

### BLOCKER

Keine im Repository-Stand.

### HIGH

Keine ungeklärten HIGH für die technische Deployment-Bereitschaft.

### MEDIUM

1. KI-Nutzungslimits begrenzen Anfragen, nicht Euro-Kosten – Betriebsmaßnahme beim Provider.

### LOW

1. In-Memory-Limiter nur pro Instanz (architekturell akzeptiert).
2. Loopback-Bypass des Host-Nginx (dokumentiertes lokales Restrisiko).
3. Externe Backup-Aufbewahrung ist Betriebsaufgabe, nicht automatisiert im Repo.

### NOT VERIFIED (Zielserver)

Alles in [go-live-checklist.md](go-live-checklist.md): DNS, Netz, TLS, echte Header/Cookies, erstes Prod-Backup, externe Kopie, Funktionstest.

---

## 7. Freigabeempfehlung

**Technische Deployment-Bereitschaft:** ja, sofern CI (inkl. Security-Workflow und Backup-/Restore-Job) auf dem Merge-Stand grün ist.  
**Betriebsfreigabe:** nein, bis die Go-Live-Checkliste auf dem Netcup-Rootserver vollständig und erfolgreich ist.

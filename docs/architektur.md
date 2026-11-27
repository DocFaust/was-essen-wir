# Architektur

**Stand:** 26.11.2026

## Entscheidungen

- Eine Installation bedient genau eine Familie. Benutzer und Familienmitglieder werden zur Laufzeit verwaltet und nicht im Quellcode hinterlegt. Die Anwendung bleibt eine private Familienanwendung, auch wenn sie über das Internet erreichbar ist. Keine Multi-Tenant-Funktionalität und keine öffentliche Selbstregistrierung.
- Frontend: React, TypeScript, Vite, Material UI. Mobile first. Die PWA ist das Architekturziel. Manifest und Icons werden vorbereitet; Offline-Synchronisation und Caching-Strategien folgen erst nach gesonderter Entscheidung.
- Backend: Spring Boot, Java 25, Gradle mit Kotlin DSL, REST.
- Persistenz: PostgreSQL. Schema-Migrationen mit Flyway.
- Betrieb: Docker Compose. Unterstützte Plattformen: `linux/amd64` und `linux/arm64`. Der Raspberry Pi bleibt eine unterstützte Plattform. Entwicklung auf Windows und Linux.
- Betriebsmodi:
  - **Heimnetz (umgesetzt):** privater HTTP-Betrieb im lokalen Netz. `APP_SESSION_COOKIE_SECURE` bleibt dafür `false`.
  - **Öffentlich (technisch bereit, Betriebsfreigabe ausstehend):** Betrieb hinter dem bestehenden Host-Nginx mit TLS-Terminierung, geplant unter `https://essen.faustens.de` (`compose.yaml` + `compose.prod.yaml`). Das Frontend lauscht nur auf `127.0.0.1`. Backend und PostgreSQL bleiben ohne veröffentlichte Ports. Secure-Cookies sind verpflichtend (`APP_REQUIRE_SECURE_COOKIES`). Backup/Restore und CI-Sicherheitsscans sind umgesetzt (Iteration 10.5). Keine Keycloak-/OAuth-Infrastruktur. Anleitung in [`deployment.md`](deployment.md). Die Betriebsfreigabe erfolgt erst nach erfolgreicher Prüfung auf dem Zielserver ([`go-live-checklist.md`](go-live-checklist.md)).
- Konfiguration über Umgebungsvariablen. `.env` bleibt lokal. `.env.example` enthält keine echten Geheimnisse.
- Authentifizierung: Spring Security mit lokaler Benutzerverwaltung, serverseitigen HTTP-Sessions und optionalem Remember-me. Keine Keycloak-, OAuth2-, JWT- oder separate Authentifizierungsinfrastruktur. Keine öffentliche Selbstregistrierung und keine E-Mail-Verifizierung.
- KI optional über eine austauschbare Provider-Abstraktion. Ollama und die OpenAI-Chat-Completions-API laufen nur serverseitig. Die KI erhält keinen Datenbankzugriff, wählt keine Gerichte selbst und speichert keine Gerichte. Kernfunktionen bleiben ohne LLM nutzbar. Missbrauch und unkontrollierte API-Kosten bei externen Providern werden durch serverseitige Nutzungslimits, Parallelitätsgrenzen und Ausgabe-Token-Begrenzung eingedämmt (Iteration 10.3). Die Limits begrenzen das Anfragevolumen und sind kein garantiertes Ausgabenlimit; zusätzliche Provider-Kontrollen bleiben empfohlen. Der Ablauf steht in [`ki-assistent.md`](ki-assistent.md).

## Iteration 1

Lauffähiges Grundgerüst mit `GET /api/status`, Actuator-Health, einer Flyway-Baseline ohne fachliche Tabellen und einer Startseite, die den Backend-Status anzeigt.

## Iteration 2

Gerichte, Varianten und Zutaten über JDBC und Flyway. Die Vererbung optionaler Varianteneigenschaften berechnet ausschließlich das Backend und liefert sie in der API mit. Die Oberfläche zeigt diese aufgelösten Werte an und sendet die gespeicherten Angaben zurück.

## Iteration 3

Lokale Benutzer mit den Rollen `ADMIN` und `MEMBER`. Passwörter liegen als BCrypt-Hashes vor. Die Anmeldung erzeugt eine serverseitige Session, erneuert die Session-ID und setzt ein `HttpOnly`-Cookie `WAEW_SESSION` mit `SameSite=Lax`. CSRF bleibt aktiv: das Frontend liest das Cookie `XSRF-TOKEN` und sendet es als Kopfzeile `X-XSRF-TOKEN`. Authentifizierungsdaten werden nicht in `localStorage` gespeichert.

Solange kein Administrator existiert, ist die Einrichtung offen. Das Backend erzeugt dafür ein zufälliges Setup-Token, speichert nur seinen SHA-256-Hash und schreibt das Token einmalig ins Log. Die Ersteinrichtung prüft dieses Token in einer Datenbanktransaktion mit Sperre und kann genau einmal gelingen. Danach ist das Token ungültig, auch nach einem Neustart.

`APP_SESSION_COOKIE_SECURE` steht standardmäßig auf `false`, weil das Heimnetz per HTTP erreichbar ist. Browser verwerfen `Secure`-Cookies auf reinem HTTP. `compose.prod.yaml` setzt Secure-Cookies verpflichtend (`APP_SESSION_COOKIE_SECURE=true`, `APP_REQUIRE_SECURE_COOKIES=true`) für Sitzung, Remember-me und CSRF. Unsichere Kombinationen lehnt das Backend beim Start ab.

Optional kann sich eine Person auf einem Gerät bis zu 30 Tage angemeldet halten (`APP_REMEMBER_ME_ENABLED`, `APP_REMEMBER_ME_DURATION`). Die Checkbox ist standardmäßig aus; ohne sie bleibt es bei der 12-Stunden-Sitzung. Das Cookie `WAEW_REMEMBER` ist `HttpOnly`, `SameSite=Lax` und trägt Selektor plus Geheimnis. PostgreSQL speichert nur den SHA-256 des Geheimnisses. Jede Wiederanmeldung rotiert das Geheimnis und legt eine normale Sitzung an. Eine überlappende Anfrage darf das gerade ersetzte Geheimnis noch 30 Sekunden verwenden. Danach widerruft eine Wiederverwendung alle dauerhaften Anmeldungen dieser Person. Abmelden gilt nur für das aktuelle Gerät. Passwortänderung, Passwortzurücksetzung und Deaktivierung widerrufen alle Geräte. Höchstens acht Tokens bleiben pro Person erhalten; abgelaufene Zeilen werden beim Anmelden und bei der Wiederanmeldung gelöscht. CSRF bleibt für zustandsändernde Aufrufe Pflicht. Der Klartext steht nicht in `localStorage`, nicht in der API und nicht in der Datenbank.

## Iteration 4

Persönliche Sternebewertungen liegen in einer gemeinsamen Tabelle für Grundgericht und Variante. Die Benutzer-ID kommt nur aus der Sitzung. Antworten enthalten keine fremden Bewertungen. API-Antworten senden `Cache-Control: no-store`, das Frontend speichert Bewertungen nicht in `localStorage` und lädt sie nach einem Benutzerwechsel neu.

## Laufzeitgrenzen

Im Standard-Compose ist PostgreSQL nicht auf dem Host veröffentlicht. Das Frontend ist der einzige veröffentlichte Dienst und leitet `/api` an das Backend weiter. Actuator bleibt im internen Backend-Netz und liefert keine Konfigurationsdetails. `GET /api/status` und `GET /actuator/health` bleiben ohne Anmeldung erreichbar, damit die bestehende Gesundheitsprüfung funktioniert. Sie enthalten keine Benutzer- oder Token-Daten. Alle Gerichte- und Benutzerendpunkte verlangen eine gültige Sitzung.

Für den öffentlichen Betrieb gilt zusätzlich: Der Compose-Frontend-Port ist an `127.0.0.1` gebunden; der Zugang erfolgt ausschließlich über HTTPS am Host-Nginx. Backend und PostgreSQL bleiben nur intern erreichbar.

## Iteration 5

Essensvorschläge berechnet ein eigener Dienst im Backend: Kandidaten zur Mahlzeit, Score aus den Bewertungen der ausgewählten aktiven Mitglieder, gewichtete Ziehung ohne doppelte Gerichtsfamilie, kurze Begründung. `POST /api/recommendations` verlangt eine Sitzung und CSRF. Die Antwort enthält keine Einzelbewertungen und keinen Score. `GET /api/members` liefert angemeldeten Personen nur Kennung und Anzeigenamen aktiver Mitglieder, damit Mitessende ausgewählt werden können. Bewertungen für die Berechnung lädt eine gebündelte Abfrage. Es gibt keine Tabelle für Vorschläge: sie werden nicht gespeichert und gelten nicht als gegessen. Die Formel steht in [`empfehlungslogik.md`](empfehlungslogik.md).

## Iteration 6

Planung und Historie sind Zustände derselben Mahlzeit `meal_event`. Bestätigen setzt den Status von `PLANNED` auf `EATEN` und legt keine zweite Zeile an. Ein eigener Wiederholungsrechner liest gegessene und geplante Mahlzeiten in einer Abfrage und zieht sie vom bestehenden Score ab. Gelöschte Gerichte bleiben über einen Namenssnapshot lesbar; die Wiederholung zählt nur noch vorhandene Gerichte. Das Referenzdatum der Empfehlung ist standardmäßig der lokale Kalendertag der Installation (`app.time-zone`, Standard `Europe/Berlin`), nicht der UTC-Tag. Die Endpunkte `/api/meal-plans` und `/api/meal-history` verlangen eine Sitzung und CSRF. Jede angemeldete Person darf die gemeinsame Planung und Historie bearbeiten. Die Benutzerverwaltung bleibt bei `ADMIN`.

## Iteration 7

Der Essensassistent ist eine optionale Schicht vor dem bestehenden Empfehlungsdienst. `POST /api/ai/recommendations` verlangt eine Sitzung und CSRF. Das Modell liefert nur ein versioniertes JSON mit Mahlzeit, Datum, genannten Namen, Präferenzen und Katalogbegriffen. Namen und Katalog löst das Backend lokal auf. Bewertungen, Historie und der Katalog gehen nicht an den Provider. `AI_ENABLED=false` lässt die Anwendung ohne externe Verbindung starten. Ollama kann über eine LAN-Adresse auf einem anderen Rechner liegen. Die OpenAI-Chat-Completions-Schnittstelle wird nur vom Backend aufgerufen. Der Ablauf steht in [`ki-assistent.md`](ki-assistent.md).

## Iteration 8

Die Oberfläche priorisiert die täglichen Aufgaben, ohne neue Fachendpunkte. Die Startseite lädt die Planung des lokalen Tages über das bestehende `GET /api/meal-plans`. Einplanen, Bestätigen und Nachtragen verwenden weiter `/api/meal-plans` und `/api/meal-history`. Die Navigation hat fünf Ziele: Heute, Plan, Gerichte, Historie und Mehr. Profil, Familie und Abmelden liegen unter Mehr. Auf schmalen Viewports sitzt die Leiste unten im Layoutfluss, ab der MUI-Breakpoint-Grenze `md` darüber, damit sie den Inhalt nicht überdeckt. Die Gerichtssuche bleibt eine explizite Anfrage und lädt nicht bei jedem Tastendruck. Die KI bleibt optional und steht unter den regelbasierten Vorschlägen. Die Bewertung steht in [`ux-review.md`](ux-review.md).

## Iteration 9

`POST /api/ai/dish-drafts` übersetzt eine Beschreibung in einen versionierten Entwurf für ein Grundgericht oder eine Variante. Der Endpunkt liest nur und schreibt nicht. Ollama erhält dasselbe JSON-Schema als `format`, OpenAI als Structured Output `dish_draft`. Seit Iteration 10.6 darf das Modell die erlaubten Felder eines bekannten Gerichts ergänzen. Ausdrückliche Angaben der Person haben Vorrang. `null` bleibt bei einer Variante geerbt, wenn die Eigenschaft nicht geändert wird. Ein ähnlicher Name wird lokal angezeigt und sperrt das Speichern nicht. Die vorhandenen CRUD-Endpunkte legen das Gericht erst nach der Bestätigung im Formular an.

## Iteration 10

Public Deployment Hardening. Die Architekturentscheidungen für Heimnetz und öffentlichen HTTPS-Betrieb stehen oben. Iteration 10.5 schließt Betriebssicherheit (Backup/Restore, CI-Scans, Review, Checkliste) ab. Der produktive öffentliche Betrieb bleibt bis zur erfolgreichen Go-Live-Prüfung auf dem Zielserver gesperrt.

### Iteration 10.2 – Anwendungssicherheit

- **Öffentliche Statusendpunkte und Sessions:** `GET /api/status` und `GET /api/setup/status` erzeugen keine serverseitige HTTP-Session. CSRF bleibt über das Cookie `XSRF-TOKEN` (`CookieCsrfTokenRepository`) aktiv; das CSRF-Cookie allein legt keine `WAEW_SESSION` an. Sessions entstehen bei erfolgreicher Anmeldung, Remember-me-Wiederherstellung und den dafür vorgesehenen Authentifizierungsabläufen. Actuator-Health bleibt anonym ohne Session.
- **AttemptLimiter:** In-Memory-Begrenzung fehlgeschlagener Login-, Passwort- und Setup-Versuche für eine Backend-Instanz. Schlüssel werden normalisiert und auf 128 Zeichen gekürzt. Die maximale Anzahl gleichzeitig gehaltener Schlüssel ist konfigurierbar (`APP_ATTEMPT_LIMITER_MAX_KEYS`, Standard 10000). Abgelaufene Einträge werden bei Zugriff und bei Kapazitätsdruck entfernt. Nicht abgelaufene Einträge werden nie zugunsten neuer Schlüssel verdrängt; ist die Karte voll, werden neue Schlüssel abgelehnt (Rate-Limit), damit bestehende Sperren nicht umgangen werden können. Die fachlichen Schwellen (`APP_LOGIN_MAX_ATTEMPTS`, `APP_LOGIN_WINDOW`, Setup-Pendants) bleiben unverändert. Vor dem Schlüssel aus Benutzername und Client-IP prüft der Login eine Grenze nur pro Client-IP (`APP_LOGIN_IP_MAX_ATTEMPTS`, Standard 30 im `APP_LOGIN_WINDOW`). Sie zählt jeden Versuch, auch mit wechselnden Benutzernamen, und wird nach einer erfolgreichen Anmeldung zurückgesetzt. Leere Benutzernamen oder Passwörter werden danach ohne weiteren Schlüssel abgewiesen. So kann eine einzelne IP die Schlüsselkapazität nicht füllen und keine unbegrenzten BCrypt-Prüfungen auslösen. Client-gesetzte `Forwarded`-/`X-Forwarded-For`-Kopfzeilen fließen nicht in den Schlüssel ein; maßgeblich ist die vom Container gesehene Peer-Adresse.
- **Login-Timing:** Spring Security 7.1.2 führt in `DaoAuthenticationProvider` bei unbekannten Benutzernamen einen BCrypt-Vergleich gegen einen Dummy-Hash aus (`mitigateAgainstTimingAttack`). Bei deaktivierten Konten bleibt `alwaysPerformAdditionalChecksOnUser` aktiv, sodass die Passwortprüfung ebenfalls stattfindet. Die API liefert einheitlich `LOGIN_FAILED`. Zusätzliche künstliche Wartezeiten oder ein eigener Provider sind nicht erforderlich.

### Iteration 10.3 – KI-Absicherung

- **Nutzungs- und Parallelitätslimits:** Alle KI-Provider-Aufrufe laufen über `AiProviderGateway` und `AiUsageLimiter` (In-Memory, eine Backend-Instanz). Gemeinsame Grenzen für `/api/ai/recommendations` und `/api/ai/dish-drafts`: pro Benutzer und Minute, pro Benutzer und Tag, global pro Tag, parallele Anfragen pro Benutzer und insgesamt. Die Zuordnung erfolgt über die authentifizierte Benutzer-ID. Prüfung vor dem Provider-Aufruf; abgelehnte Anfragen erzeugen keinen Provider-Verkehr. Gestartete Aufrufe zählen auch bei Fehler oder Timeout. Parallelitätskapazität wird im `finally` freigegeben. Tagesgrenzen nutzen `app.time-zone`. Zähler gehen bei Neustart verloren (bewusst).
- **Fehler:** HTTP 429 mit bestehendem Problem-JSON, optional `Retry-After`. Codes `AI_RATE_LIMITED`, `AI_BUSY`, `AI_DAILY_LIMIT`. Die Oberfläche unterscheidet vorübergehende Auslastung und Tageslimit; keine automatischen Wiederholungen.
- **Timeouts:** Provider-Standard 30s (`OLLAMA_TIMEOUT` / `OPENAI_TIMEOUT`). Internes Nginx für `/api/ai/` 45s Lese-/Sende-Timeout, übrige `/api/` 10s. Provider-Timeouts müssen unter dem Proxy-Timeout bleiben. Ausgabe zusätzlich über `AI_MAX_OUTPUT_TOKENS` (Standard 1024) begrenzt; Eingabe- und Antwortgrößengrenzen bleiben.
- **Fixture:** Nur mit Spring-Profil `test` oder `AI_ALLOW_FIXTURE=true`. Nicht für den Produktivbetrieb.
- **Kosten:** Die Limits begrenzen das Anfragevolumen, nicht die Euro-Kosten. Zusätzliche Nutzungs- und Kostenkontrollen des externen Providers bleiben empfohlen. Bei `AI_ENABLED=false` gibt es keine externen Aufrufe.
- AttemptLimiter und AI-Limits sind pro Instanz (eine Backend-Instanz).

### Iteration 10.4 – Produktionsdeployment

- **Compose-Prod:** `compose.prod.yaml` überschreibt die Frontend-Ports mit `!override` auf `127.0.0.1:${APP_PORT:-8182}:8080`. Backend und PostgreSQL ohne Host-Ports. Secure-Cookies und Client-IP-Weiterleitung sind aktiv. Healthchecks, Restart-Policies und Volume `postgres-data` bleiben.
- **Proxy-Kette:** Host-Nginx setzt Forwarded-Header kontrolliert; Frontend-Nginx (`nginx.prod.conf`) nutzt `real_ip` nur für Loopback und das Compose-Netz `172.30.90.0/24` und überschreibt `X-Forwarded-For` gegenüber dem Backend. `ClientIpResolver` wertet Header nur bei `APP_FORWARD_CLIENT_IP` und Trusted-CIDRs (`APP_TRUSTED_PROXIES`, Standard `172.30.90.0/24`) aus. Lokal bleiben Forwarded-Header unvertrauenswürdig.
- **Header:** Spring Security liefert für API-Antworten CSP (restriktiv), `X-Content-Type-Options`, `X-Frame-Options`, Referrer- und Permissions-Policy. HSTS setzt nur der HTTPS-terminierende Host-Nginx (ohne `includeSubDomains`/`preload`). Statische Dokumente erhalten passende Header im Frontend-Nginx; keine doppelten HSTS-Header.
- **Request-Größe:** Host-Nginx, Frontend-Nginx und Backend begrenzen auf 256 KiB (inkl. Chunked-Bodies im Backend).
- **Härtung:** Backend und Frontend als Non-Root, `read_only` Root-FS mit tmpfs, `cap_drop: ALL`, `no-new-privileges`, konfigurierbare Speicher-/CPU-/PID-Limits. PostgreSQL ohne pauschales `cap_drop ALL` (Init braucht Privilegien), mit `no-new-privileges` und Limits.
- **Dokumentation:** [`deployment.md`](deployment.md), VHost-Beispiel unter `deploy/nginx/`.

### Iteration 10.5 – Betriebssicherheit

- **Backup:** `scripts/backup-postgres.sh` – `pg_dump -Fc` im laufenden Postgres-Container, Zeitstempel-Dateiname, konfigurierbares Host-Verzeichnis, `chmod 600`/`700`, Lock gegen Parallelität, Verify per `pg_restore --list`, Retention (Standard 30 Tage) erst nach Erfolg. systemd-Timer-Vorlage unter `deploy/systemd/`.
- **Restore:** `scripts/restore-postgres.sh` – explizite Datei + `CONFIRM=YES`, Archivprüfung, Sicherheitsbackup, Stop von Backend/Frontend, `pg_restore --clean --if-exists --exit-on-error`, Invalidierung von Remember-me und defensives Leeren von Setup-Tokens nach abgeschlossener Einrichtung, kontrollierter Wiederanlauf. Flyway-Historie kommt mit dem Dump.
- **CI:** `scripts/ci-backup-restore.sh`; Workflow `security.yml` (Trivy FS/Images, npm audit production). Ausnahmen nur in [`security-exceptions.md`](security-exceptions.md).
- **Review / Freigabe:** [`security-review-10.5.md`](security-review-10.5.md), [`go-live-checklist.md`](go-live-checklist.md). Technische Deployment-Bereitschaft ≠ Betriebsfreigabe.

### Iteration 10.6 – Praxistauglichkeit des KI-Assistenten

- **Entwurf:** `DishDraftPrompt` fordert einen möglichst vollständigen Entwurf. Erlaubt sind Name, Beschreibung, Mahlzeiten, Kategorien, Aufwand, typische Zutaten ohne Mengen, kurze Zubereitung, Meal Prep und Homeoffice. Ein bekannter Name genügt. Bei Unsicherheit bleibt der Entwurf leer (`warnings` enthält `unbekannt`), ohne erfundenes Rezept.
- **Vorrang:** `DishDraftValidator` übernimmt ausdrückliche Angaben aus dem Text vor den Modellwerten. Ausgeschlossene und ersetzte Zutaten werden nicht wieder ergänzt. Eine Variante behält die Zutaten des Grundgerichts, wenn nur Zutaten hinzugefügt werden. Nur eine ausdrücklich vollständige Liste ersetzt sie. Nicht genannte Eigenschaften bleiben leer, damit die Vererbung gilt. Mahlzeiten und Kategorien bleiben am Grundgericht. Ein Zubereitungstext, der eine ausgeschlossene oder ersetzte Zutat nennt, erzeugt einen Prüfhinweis.
- **Grenzen:** JSON-Schema, Längen, Enums, keine Modell-IDs, keine erfundenen URLs, keine persönlichen Kommentare und keine externen Rezeptquellen. Eine übernommene Zubereitung ohne angegebene Quelle erhält serverseitig `KI-generiert`. Kein zweiter Modellaufruf, keine Speicherung durch die KI, Rate-Limits unverändert.
- **Fehler:** `ProviderHttp` unterscheidet Quota (`insufficient_quota`, `credit_balance_exhausted`), ungültigen Zugang, Anbieter-Rate-Limit, Timeout, Netzwerk, ungültige Modellantwort und sonstige HTTP-Fehler. Die Oberfläche nennt bei Quota und Zugang eine administrierende Person. Schlüssel, Antwortkörper und interne Providerdetails erscheinen weder in der Meldung noch im Protokoll. Details in [`ki-assistent.md`](ki-assistent.md).

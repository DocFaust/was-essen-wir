# Was essen wir?

Selbst gehosteter Familien-Essensplaner für genau eine Familie pro Installation. Die Anwendung läuft im privaten Heimnetz, mobile-first, und kommt ohne öffentliche Registrierung aus.

**Stand Iteration 9:** Gerichteverwaltung, lokale Anmeldung, persönliche Sternebewertungen, regelbasierte Essensvorschläge, Wochenplan und Essenshistorie. Die Startseite beantwortet „Was essen wir heute?“ und hält Vorschläge, Einplanen und das Erfassen von Gegessenem mit wenigen Taps erreichbar. Die Navigation ist Heute, Plan, Gerichte, Historie und Mehr. Kürzlich gegessene und bereits geplante Gerichte beeinflussen neue Vorschläge. Sichtbar bleiben nur die eigenen Bewertungen. Ein optionaler Essensassistent übersetzt natürlichsprachliche Wünsche; die Auswahl bleibt regelbasiert und funktioniert auch ohne ihn. Dieselbe optionale KI kann eine Beschreibung in einen prüfbaren Entwurf für ein Gericht oder eine Variante übersetzen. Gespeichert wird erst im vorhandenen Formular. Ohne `AI_ENABLED=true` gibt es keine KI-Verbindung und die manuelle Erfassung bleibt vollständig. Der Ablauf steht in [`docs/ki-assistent.md`](docs/ki-assistent.md), die UX-Bewertung in [`docs/ux-review.md`](docs/ux-review.md).

## Voraussetzungen

- Java 25 (Temurin)
- Node.js 22.12 oder neuer, npm
- Docker mit Compose v2, für den Gesamtstart, Integrationstests und E2E-Tests
- Für die Zielplattform: Raspberry Pi 5, `linux/arm64`

Windows und Linux sind beide Entwicklungsumgebungen. Pfade im Repository sind relativ.

## Konfiguration

```sh
cp .env.example .env
```

In `.env` ein eigenes `POSTGRES_PASSWORD` setzen. Die Datei bleibt lokal und ist von Git ignoriert. `.env.example` enthält keine echten Zugangsdaten.

| Variable | Bedeutung |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Datenbank im Compose-Stack |
| `APP_PORT` | Host-Port des Frontends, Standard `8080` |
| `POSTGRES_PORT` | Host-Port nur im Entwicklungs-Override, Standard `5432` |
| `TZ` | Zeitzone der Container, Standard `Europe/Berlin` |
| `APP_TIME_ZONE` | Kalendertag des Backends für „heute“, Standard `Europe/Berlin` |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Backend auf dem Host, außerhalb von Compose |
| `AI_ENABLED` | Essensassistent, Standard `false` |
| `AI_PROVIDER` | `ollama` oder `openai`, Standard `ollama` |
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `OLLAMA_TIMEOUT` | Ollama im LAN, Standard `http://127.0.0.1:11434`, `qwen3.5:9b`, `30s` |
| `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_TIMEOUT` | nur im Backend, Standardmodell `gpt-4.1-mini` |

## Lokaler Entwicklungsstart

PostgreSQL für das auf dem Host laufende Backend veröffentlichen:

```sh
docker compose --env-file .env -f compose.yaml -f compose.dev.yaml up -d postgres
```

Backend:

```sh
cd backend
export SPRING_DATASOURCE_PASSWORD=dein-lokales-passwort
./gradlew bootRun
```

Unter Windows `.\gradlew.bat bootRun` und die Variable mit `$env:SPRING_DATASOURCE_PASSWORD` setzen. Das Backend lauscht auf `http://127.0.0.1:8080`.

Frontend, in einem zweiten Terminal:

```sh
cd frontend
npm ci
npm run dev
```

Vite stellt die Oberfläche auf `http://127.0.0.1:5173` bereit und leitet `/api` an `http://127.0.0.1:8080` weiter.

## Start über Docker Compose

```sh
docker compose --env-file .env -f compose.yaml up --build
```

Die Oberfläche ist unter `http://127.0.0.1:8080` erreichbar. PostgreSQL und das Backend bleiben im Compose-Netz. Nur das Frontend veröffentlicht einen Port und leitet `/api` an das Backend weiter.

Stoppen, Datenbankvolume behalten:

```sh
docker compose --env-file .env -f compose.yaml down
```

Stoppen und Datenbankvolume löschen:

```sh
docker compose --env-file .env -f compose.yaml down --volumes
```

## Ersteinrichtung

Beim ersten Start mit leerer Datenbank gibt das Backend einmalig ein Setup-Token ins Log:

```sh
docker compose --env-file .env -f compose.yaml logs backend
```

Die Zeile enthält `SETUP_TOKEN` und den Klartext. Die Oberfläche erklärt denselben Weg. Das Token liegt nicht in der API, nicht im Frontend und nicht in Git. Gespeichert wird nur ein SHA-256-Hash. Es gilt 24 Stunden (`APP_SETUP_TOKEN_TTL`) und kann genau einmal den ersten Administrator anlegen. Danach ist die Einrichtung zu, auch nach einem Neustart.

Ein Neustart vor Ablauf wiederholt das noch gültige Token nicht. Wenn das Log verloren ist oder das Token abgelaufen ist, ohne die Datenbank zu löschen:

```sh
# In .env nur solange die Einrichtung noch nicht abgeschlossen ist:
APP_SETUP_ROTATE_TOKEN=true
```

Backend neu starten, das neue Token aus dem Log kopieren, die Variable wieder auf `false` setzen oder entfernen und erneut starten. Bleibt sie auf `true`, erzeugt jeder weitere Neustart vor Abschluss ein neues Token und entwertet das vorherige. Nach abgeschlossener Einrichtung wird die Variable ignoriert.

Ein abgelaufenes Token wird auch ohne diese Variable beim nächsten Start ersetzt und neu geloggt.

## Benutzer und Rollen

`ADMIN` verwaltet Konten, Rollen, Aktivierung und temporäre Passwörter. `MEMBER` ändert den eigenen Anzeigenamen und das eigene Passwort. Beide dürfen Gerichte, Varianten und die eigenen Sternebewertungen pflegen und Essensvorschläge starten. Der letzte aktive Administrator kann nicht deaktiviert oder herabgestuft werden. Ein temporäres Passwort muss bei der nächsten Anmeldung geändert werden. Es gibt keine E-Mail-Zurücksetzung.

Bewertungen liegen in PostgreSQL und gelten nur für die angemeldete Person. *Nicht bewertet* ist kein Mittelwert und keine drei Sterne. Die Oberfläche speichert sie nicht im `localStorage`. Vorschläge verwenden die Bewertungen der ausgewählten Mitessenden nur serverseitig. Die Formel steht in [`docs/empfehlungslogik.md`](docs/empfehlungslogik.md).

## Sitzungen, CSRF und HTTP

Die Anmeldung speichert nichts im `localStorage`. Das Session-Cookie `WAEW_SESSION` ist `HttpOnly` und `SameSite=Lax`. Die Sitzung läuft nach 12 Stunden ab (`APP_SESSION_TIMEOUT`). Zustandsändernde Aufrufe senden das Cookie `XSRF-TOKEN` als Kopfzeile `X-XSRF-TOKEN`.

Im Heimnetz läuft die Anwendung über HTTP. `APP_SESSION_COOKIE_SECURE` bleibt dafür `false`, sonst speichert der Browser das Cookie nicht. Steht später HTTPS vor dem Frontend, die Variable auf `true` setzen. Zusätzliche Authentifizierungscontainer sind nicht nötig.

## Sicherung und Wiederherstellung

Benutzer und der Einrichtungszustand liegen in PostgreSQL, Sitzungen nur im Speicher des Backends. Ein Neustart oder ein Restore verlangt deshalb eine neue Anmeldung, öffnet aber keine zweite Ersteinrichtung, wenn die Sicherung nach der Einrichtung entstanden ist.

Eine Sicherung von vor der Einrichtung enthält den Token-Hash, nicht den Klartext. Fehlt das alte Log, das Token wie oben rotieren. `down --volumes` löscht die Datenbank und startet die Einrichtung von vorn; Gerichte gehen dabei mit verloren.

## Backend- und Frontend-Build

```sh
cd backend
./gradlew bootJar
```

Das Jar liegt unter `backend/build/libs/app.jar`.

```sh
cd frontend
npm ci
npm run typecheck
npm run lint
npm run build
```

Das Produktions-Frontend liegt unter `frontend/dist`.

## Tests

Backend, Unit- und Integrationstests. Die Integrationstests starten PostgreSQL per Testcontainers und brauchen ein laufendes Docker:

```sh
cd backend
./gradlew test jacocoTestReport
./gradlew check
```

Frontend:

```sh
cd frontend
npm test
npm run test:coverage
npm run typecheck
npm run lint
```

`npm run test:coverage` und `./gradlew check` prüfen zusätzlich verbindliche Mindestgrenzen. Berichtspfade, Ausschlüsse und die gemessenen Werte stehen in [`docs/test-coverage.md`](docs/test-coverage.md).

## E2E-Tests

Playwright prüft Chromium in einem mobilen Viewport gegen eine isolierte Compose-Umgebung. Projektname, Volume und Port (`18080`) sind vom Entwicklungstack getrennt. Das Datenbankpasswort erzeugt das Startskript zur Laufzeit.

```sh
cd frontend
npm ci
npx playwright install chromium
npm run test:e2e
```

## CI

`.github/workflows/ci.yml` läuft bei Push auf `main` und bei Pull Requests:

- Backend: `./gradlew check` (Unit- und Integrationstests mit Testcontainers, JaCoCo-Report und Coverage-Grenze). Der HTML-Report wird als Artifact hochgeladen, auch wenn die Grenze fehlschlägt.
- Frontend: `npm ci`, TypeScript, Lint, Vitest mit Coverage-Grenze, Production-Build. Der Coverage-Report wird ebenfalls als Artifact hochgeladen.
- E2E: Compose-Stack, Playwright, Screenshots und Traces bei Fehlern
- Compose-Konfiguration
- Nativer Lauf auf `ubuntu-24.04-arm`: Image-Build und Abruf von `/api/status`

Die Pipeline braucht weder das Heimnetz noch eine produktive Datenbank oder die OpenAI-API.

Dependabot prüft wöchentlich Gradle, npm, GitHub Actions, Dockerfiles und Compose-Images.

## ARM64

Die Dockerfiles nutzen offizielle Images mit `linux/arm64` und `linux/amd64`. Java-Bytecode ist plattformunabhängig; die Laufzeitimages bringen die passende JRE mit. Auf dem Raspberry Pi 5 genügt derselbe Compose-Start wie in der Entwicklung. Der CI-Job `ARM64` startet den Stack nativ auf einem ARM64-Runner.

Image-Builds laden Gradle und npm innerhalb der Container. Ein TLS-Interceptor, dessen Zertifikat nur im Host-Truststore liegt, lässt diesen Download im Container fehlschlagen. Auf GitHub-hosted Runnern und im normalen Heimnetz entfällt dieser Zwischenschritt.

## Fachliche Dokumentation

- [`docs/fachkonzept.md`](docs/fachkonzept.md)
- [`docs/architektur.md`](docs/architektur.md)
- [`docs/roadmap.md`](docs/roadmap.md)
- [`docs/test-coverage.md`](docs/test-coverage.md)
- [`docs/datenmodell.md`](docs/datenmodell.md)
- [`AGENTS.md`](AGENTS.md)

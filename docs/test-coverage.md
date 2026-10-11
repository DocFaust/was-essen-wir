# Testabdeckung

Stand der Messung: 11.10.2026, auf `main` nach Iteration 9.

Die Grenzen prüfen das gesamte produktive Quellset. Eine Datei, die kein Test importiert, zählt mit. Die Prüfung läuft lokal und in GitHub Actions und hängt nicht von SonarQube ab.

## Werkzeuge

| Bereich | Werkzeug | Version |
| --- | --- | --- |
| Backend | JaCoCo über das Gradle-Plugin, an die JUnit-Aufgabe `test` gebunden | 0.8.15, passend zu Java 25 |
| Frontend | Vitest mit dem V8-Provider `@vitest/coverage-v8` | dieselbe Version wie Vitest, 5.0.3 |

JaCoCo instrumentiert nur die Test-JVM. `bootRun` und das Jar bleiben unverändert. Der Agent hängt nicht an der produktiven Laufzeit.

## Lokale Befehle

Backend, Bericht und Prüfung:

```sh
cd backend
./gradlew test jacocoTestReport
./gradlew check
```

Unter Windows `.\gradlew.bat`. `check` führt die Tests aus, schreibt den Bericht und bricht ab, wenn eine Grenze unterschritten wird.

Frontend:

```sh
cd frontend
npm run test:coverage
```

`npm test` bleibt die Ausführung ohne Coverage-Grenze. Die Grenze gilt für den vollständigen Lauf `test:coverage`. Eine einzelne Testdatei unterschreitet sie, weil alle Quelldateien im Nenner bleiben. Die Vitest-Zeitgrenze liegt bei 15 Sekunden, weil die Instrumentierung die bestehenden Oberflächen-Tests auf dem CI-Runner über die Standardgrenze von 5 Sekunden hebt.

## Reportpfade

| Bericht | Pfad |
| --- | --- |
| JaCoCo HTML | `backend/build/reports/jacoco/test/html/index.html` |
| JaCoCo XML | `backend/build/reports/jacoco/test/jacocoTestReport.xml` |
| Vitest Text | Konsolenausgabe von `npm run test:coverage` |
| Vitest HTML | `frontend/coverage/index.html` |
| Vitest LCOV | `frontend/coverage/lcov.info` |

`backend/build/` und jedes Verzeichnis `coverage/` stehen in `.gitignore`. Die Docker-Builds übernehmen sie nicht: `backend/.dockerignore` schließt `build` aus, `frontend/.dockerignore` schließt `coverage` aus. Das Backend-Image enthält nur `app.jar`, das Frontend-Image nur `dist`.

## Gemessene Ausgangswerte

Gemessen vor dem Aktivieren der Grenzen, mit allen produktiven Quelldateien und den unten begründeten Ausschlüssen.

| Bereich | Zeilen | Zweige |
| --- | ---: | ---: |
| Backend | 90,04 % (3271/3633) | 69,54 % (1249/1796) |
| Frontend | 74,56 % (1105/1482) | 66,73 % (702/1052) |

Die Frontend-Werte können sich um wenige Zehntelpunkte bewegen, weil einzelne Oberflächenpfade zeitabhängig sind. Der Abstand zu den Grenzen bleibt groß.

## Aktive Mindestschwellen

| Bereich | Zeilen | Zweige | Durchsetzung |
| --- | ---: | ---: | --- |
| Backend | 70 % | 60 % | `jacocoTestCoverageVerification`, abhängig von `check` |
| Frontend | 65 % | 55 % | `coverage.thresholds` in `frontend/vite.config.ts` |

Beide Zielwerte aus dem Auftrag waren mit der vorhandenen Suite erreichbar. Die Grenzen stehen deshalb auf diesen Werten und nicht auf 0 %. Unterschreitung beendet `./gradlew check` beziehungsweise `npm run test:coverage` mit einem Fehler.

Die Grenzen gelten für das gesamte Bündel, nicht für jede Datei einzeln.

## Langfristige Zielwerte

| Bereich | Zeilen | Zweige |
| --- | ---: | ---: |
| Backend | 90 % | 75 % |
| Frontend | 80 % | 70 % |

Das sind die nächsten Stufen oberhalb der heutigen Messung. Sie sind noch nicht die aktive Prüfung, weil die Zweigabdeckung im Backend und beide Frontend-Werte darunter liegen.

## Ausschlüsse

### Backend

Keine. Es gibt keinen generierten Java-Quelltext. Controller, Services, Security, KI, Empfehlung und Persistenz bleiben in der Messung. JaCoCo wertet nur das Main-Source-Set aus; Testquellen erscheinen deshalb nicht als produktive Zeilen.

### Frontend

Ausgeschlossen, weil sie keine produktive Laufzeitlogik sind:

- `src/**/*.test.ts` und `src/**/*.test.tsx`
- `src/test/**`, aktuell nur das Test-Setup
- `src/**/*.d.ts`, die Vite-Umgebungsreferenz
- `src/**/types.ts` in `auth`, `dishes`, `meals` und `recommendations`, ausschließlich TypeScript-Typen ohne Laufzeitcode

Nicht ausgeschlossen sind Einstieg (`main.tsx`), Theme, Seiten, Formulare, API-Module und die übrige Anwendungslogik. E2E-Specs liegen außerhalb von `src` und zählen nicht zur Unit-Coverage. `coverage.include` ist `src/**/*.{ts,tsx}`, damit ungetestete Dateien im Nenner bleiben.

## Bekannte Testlücken

Die genannten Verhaltensweisen sind bereits durch Unit- oder Integrationstests abgedeckt: Ablehnung und Gewichtung der Empfehlung, Wiederholungsvermeidung, CSRF und anonyme Aufrufe, KI-Validierung inklusive verworfener unbelegter Angaben, Variantenvererbung, Speichern und Abbrechen in der Erfassung sowie Fehlerantworten. Dafür sind keine zusätzlichen Tests entstanden.

Offen bleibt:

- `WasEssenWirApplication.main` startet nur Spring und ist ungetestet. Das Paket `de.wasessenwir` liegt dadurch bei 3 von 5 Zeilen.
- Zweige in `DishDraftValidator`, `DraftText` und den Provider-Fehlerpfaden. Die fachlichen Fälle sind getestet, nicht jede Kombination aus ungültigem JSON und HTTP-Status.
- Das Paket `security` liegt bei der Zweigabdeckung knapp unter 60 %. CSRF, Rollen und Sitzungen sind über Integrationstests abgedeckt; einzelne Filterzweige nicht.
- `PasswordChangePage` hat keine Unit-Tests. Der Ablauf „temporäres Passwort ersetzen“ läuft im E2E-Test `frontend/e2e/family.spec.ts`.
- `ProfilePage`, `UsersPage` und Teile von `DishDetailPage` sind nur teilweise per Vitest erfasst.
- `main.tsx` ist der Bootstrap und wird von Vitest nicht ausgeführt.

## Anheben der Schwellen

1. Bericht erzeugen und die größten Lücken in produktiver Logik lesen.
2. Tests für fehlendes Verhalten ergänzen, nicht für einzelne ungedeckte Zeilen.
3. Die Konstante in `backend/build.gradle.kts` oder `frontend/vite.config.ts` in kleinen Schritten anheben.
4. Dieselbe Grenze lokal und in `.github/workflows/ci.yml` gelten lassen. Die CI ruft dieselben Befehle auf.
5. Eine Grenze nicht absenken, um eine Änderung grün zu bekommen.

## SonarQube und SonarCloud

Im Repository gibt es keine SonarQube- oder SonarCloud-Anbindung, kein `sonar-project.properties` und keinen Token. Es wird hier kein Projekt angelegt.

Für eine spätere Anbindung die vorhandenen Berichte einlesen, ohne eine zweite Messung zu konfigurieren:

```properties
sonar.coverage.jacoco.xmlReportPaths=backend/build/reports/jacoco/test/jacocoTestReport.xml
sonar.javascript.lcov.reportPaths=frontend/coverage/lcov.info
```

Projektkennung und Token kommen aus dem Sonar-Projekt und bleiben außerhalb des Repositorys. Gradle und Vitest bleiben die verbindlichen Gates.

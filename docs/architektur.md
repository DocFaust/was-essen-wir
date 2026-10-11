# Architektur

**Stand:** 11.10.2026

## Entscheidungen

- Eine Installation bedient genau eine Familie. Benutzer und Familienmitglieder werden zur Laufzeit verwaltet und nicht im Quellcode hinterlegt.
- Frontend: React, TypeScript, Vite, Material UI. Mobile first. Die PWA ist das Architekturziel. Manifest und Icons werden vorbereitet; Offline-Synchronisation und Caching-Strategien folgen erst nach gesonderter Entscheidung.
- Backend: Spring Boot, Java 25, Gradle mit Kotlin DSL, REST.
- Persistenz: PostgreSQL. Schema-Migrationen mit Flyway.
- Betrieb: Docker Compose. Zielplattform ist ein Raspberry Pi 5 (`linux/arm64`). Entwicklung auf Windows und Linux. Ausschließlich privates Heimnetz, keine öffentliche Internetfreigabe.
- Konfiguration über Umgebungsvariablen. `.env` bleibt lokal. `.env.example` enthält keine echten Geheimnisse.
- Authentifizierung: Spring Security mit lokaler Benutzerverwaltung und serverseitigen HTTP-Sessions. Keine Keycloak-, OAuth2-, JWT- oder separate Authentifizierungsinfrastruktur. Keine öffentliche Selbstregistrierung und keine E-Mail-Verifizierung.
- KI optional über eine austauschbare Provider-Abstraktion. Ollama und die OpenAI-Chat-Completions-API laufen nur serverseitig. Die KI erhält keinen Datenbankzugriff und wählt keine Gerichte selbst. Kernfunktionen bleiben ohne LLM nutzbar. Der Ablauf steht in [`ki-assistent.md`](ki-assistent.md).

## Iteration 1

Lauffähiges Grundgerüst mit `GET /api/status`, Actuator-Health, einer Flyway-Baseline ohne fachliche Tabellen und einer Startseite, die den Backend-Status anzeigt.

## Iteration 2

Gerichte, Varianten und Zutaten über JDBC und Flyway. Die Vererbung optionaler Varianteneigenschaften berechnet ausschließlich das Backend und liefert sie in der API mit. Die Oberfläche zeigt diese aufgelösten Werte an und sendet die gespeicherten Angaben zurück.

## Iteration 3

Lokale Benutzer mit den Rollen `ADMIN` und `MEMBER`. Passwörter liegen als BCrypt-Hashes vor. Die Anmeldung erzeugt eine serverseitige Session, erneuert die Session-ID und setzt ein `HttpOnly`-Cookie `WAEW_SESSION` mit `SameSite=Lax`. CSRF bleibt aktiv: das Frontend liest das Cookie `XSRF-TOKEN` und sendet es als Kopfzeile `X-XSRF-TOKEN`. Authentifizierungsdaten werden nicht in `localStorage` gespeichert.

Solange kein Administrator existiert, ist die Einrichtung offen. Das Backend erzeugt dafür ein zufälliges Setup-Token, speichert nur seinen SHA-256-Hash und schreibt das Token einmalig ins Log. Die Ersteinrichtung prüft dieses Token in einer Datenbanktransaktion mit Sperre und kann genau einmal gelingen. Danach ist das Token ungültig, auch nach einem Neustart.

`APP_SESSION_COOKIE_SECURE` steht standardmäßig auf `false`, weil das Heimnetz per HTTP erreichbar ist. Browser verwerfen `Secure`-Cookies auf reinem HTTP. Ein späterer HTTPS-Zugang setzt die Variable auf `true`.

## Iteration 4

Persönliche Sternebewertungen liegen in einer gemeinsamen Tabelle für Grundgericht und Variante. Die Benutzer-ID kommt nur aus der Sitzung. Antworten enthalten keine fremden Bewertungen. API-Antworten senden `Cache-Control: no-store`, das Frontend speichert Bewertungen nicht in `localStorage` und lädt sie nach einem Benutzerwechsel neu.

## Laufzeitgrenzen

Im Standard-Compose ist PostgreSQL nicht auf dem Host veröffentlicht. Das Frontend ist der einzige veröffentlichte Dienst und leitet `/api` an das Backend weiter. Actuator bleibt im internen Backend-Netz und liefert keine Konfigurationsdetails. `GET /api/status` und `GET /actuator/health` bleiben ohne Anmeldung erreichbar, damit die bestehende Gesundheitsprüfung funktioniert. Sie enthalten keine Benutzer- oder Token-Daten. Alle Gerichte- und Benutzerendpunkte verlangen eine gültige Sitzung.

## Iteration 5

Essensvorschläge berechnet ein eigener Dienst im Backend: Kandidaten zur Mahlzeit, Score aus den Bewertungen der ausgewählten aktiven Mitglieder, gewichtete Ziehung ohne doppelte Gerichtsfamilie, kurze Begründung. `POST /api/recommendations` verlangt eine Sitzung und CSRF. Die Antwort enthält keine Einzelbewertungen und keinen Score. `GET /api/members` liefert angemeldeten Personen nur Kennung und Anzeigenamen aktiver Mitglieder, damit Mitessende ausgewählt werden können. Bewertungen für die Berechnung lädt eine gebündelte Abfrage. Es gibt keine Tabelle für Vorschläge: sie werden nicht gespeichert und gelten nicht als gegessen. Die Formel steht in [`empfehlungslogik.md`](empfehlungslogik.md).

## Iteration 6

Planung und Historie sind Zustände derselben Mahlzeit `meal_event`. Bestätigen setzt den Status von `PLANNED` auf `EATEN` und legt keine zweite Zeile an. Ein eigener Wiederholungsrechner liest gegessene und geplante Mahlzeiten in einer Abfrage und zieht sie vom bestehenden Score ab. Gelöschte Gerichte bleiben über einen Namenssnapshot lesbar; die Wiederholung zählt nur noch vorhandene Gerichte. Das Referenzdatum der Empfehlung ist standardmäßig der lokale Kalendertag der Installation (`app.time-zone`, Standard `Europe/Berlin`), nicht der UTC-Tag. Die Endpunkte `/api/meal-plans` und `/api/meal-history` verlangen eine Sitzung und CSRF. Jede angemeldete Person darf die gemeinsame Planung und Historie bearbeiten. Die Benutzerverwaltung bleibt bei `ADMIN`.

## Iteration 7

Der Essensassistent ist eine optionale Schicht vor dem bestehenden Empfehlungsdienst. `POST /api/ai/recommendations` verlangt eine Sitzung und CSRF. Das Modell liefert nur ein versioniertes JSON mit Mahlzeit, Datum, genannten Namen, Präferenzen und Katalogbegriffen. Namen und Katalog löst das Backend lokal auf. Bewertungen, Historie und der Katalog gehen nicht an den Provider. `AI_ENABLED=false` lässt die Anwendung ohne externe Verbindung starten. Ollama kann über eine LAN-Adresse auf einem anderen Rechner liegen. Die OpenAI-Chat-Completions-Schnittstelle wird nur vom Backend aufgerufen. Der Ablauf steht in [`ki-assistent.md`](ki-assistent.md).

## Iteration 8

Die Oberfläche priorisiert die täglichen Aufgaben, ohne neue Fachendpunkte. Die Startseite lädt die Planung des lokalen Tages über das bestehende `GET /api/meal-plans`. Einplanen, Bestätigen und Nachtragen verwenden weiter `/api/meal-plans` und `/api/meal-history`. Die Navigation hat fünf Ziele: Heute, Plan, Gerichte, Historie und Mehr. Profil, Familie und Abmelden liegen unter Mehr. Auf schmalen Viewports sitzt die Leiste unten im Layoutfluss, ab der MUI-Breakpoint-Grenze `md` darüber, damit sie den Inhalt nicht überdeckt. Die Gerichtssuche bleibt eine explizite Anfrage und lädt nicht bei jedem Tastendruck. Die KI bleibt optional und steht unter den regelbasierten Vorschlägen. Die Bewertung steht in [`ux-review.md`](ux-review.md).

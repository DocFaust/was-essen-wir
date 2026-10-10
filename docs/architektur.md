# Architektur

**Stand:** 10.10.2026

## Entscheidungen

- Eine Installation bedient genau eine Familie. Benutzer und Familienmitglieder werden zur Laufzeit verwaltet und nicht im Quellcode hinterlegt.
- Frontend: React, TypeScript, Vite, Material UI. Mobile first. Die PWA ist das Architekturziel. Manifest und Icons werden vorbereitet; Offline-Synchronisation und Caching-Strategien folgen erst nach gesonderter Entscheidung.
- Backend: Spring Boot, Java 25, Gradle mit Kotlin DSL, REST.
- Persistenz: PostgreSQL. Schema-Migrationen mit Flyway.
- Betrieb: Docker Compose. Zielplattform ist ein Raspberry Pi 5 (`linux/arm64`). Entwicklung auf Windows und Linux. Ausschließlich privates Heimnetz, keine öffentliche Internetfreigabe.
- Konfiguration über Umgebungsvariablen. `.env` bleibt lokal. `.env.example` enthält keine echten Geheimnisse.
- Authentifizierung: Spring Security mit lokaler Benutzerverwaltung und serverseitigen HTTP-Sessions. Keine Keycloak-, OAuth2-, JWT- oder separate Authentifizierungsinfrastruktur. Keine öffentliche Selbstregistrierung und keine E-Mail-Verifizierung.
- KI in einer späteren Iteration: OpenAI-API nur serverseitig, über eine austauschbare Provider-Abstraktion. Die KI erhält keinen Datenbankzugriff. Kernfunktionen bleiben ohne LLM nutzbar.

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

Essensvorschläge berechnet ein eigener Dienst im Backend: Kandidaten zur Mahlzeit, Score aus den Bewertungen der ausgewählten aktiven Mitglieder, gewichtete Ziehung ohne doppelte Gerichtsfamilie, kurze Begründung. `POST /api/recommendations` verlangt eine Sitzung und CSRF. Die Antwort enthält keine Einzelbewertungen und keinen Score. `GET /api/members` liefert angemeldeten Personen nur Kennung und Anzeigenamen aktiver Mitglieder, damit Mitessende ausgewählt werden können. Bewertungen für die Berechnung lädt eine gebündelte Abfrage. Es gibt keine neue Tabelle: Vorschläge werden nicht gespeichert. Die Formel steht in [`empfehlungslogik.md`](empfehlungslogik.md).

# Architektur

**Stand:** 10.10.2026

## Entscheidungen

- Eine Installation bedient genau eine Familie. Benutzer und Familienmitglieder werden zur Laufzeit verwaltet und nicht im Quellcode hinterlegt.
- Frontend: React, TypeScript, Vite, Material UI. Mobile first. Die PWA ist das Architekturziel. Manifest und Icons werden vorbereitet; Offline-Synchronisation und Caching-Strategien folgen erst nach gesonderter Entscheidung.
- Backend: Spring Boot, Java 25, Gradle mit Kotlin DSL, REST.
- Persistenz: PostgreSQL. Schema-Migrationen mit Flyway.
- Betrieb: Docker Compose. Zielplattform ist ein Raspberry Pi 5 (`linux/arm64`). Entwicklung auf Windows und Linux. Ausschließlich privates Heimnetz, keine öffentliche Internetfreigabe.
- Konfiguration über Umgebungsvariablen. `.env` bleibt lokal. `.env.example` enthält keine echten Geheimnisse.
- Authentifizierung in einer späteren Iteration: Spring Security mit lokaler Benutzerverwaltung und serverseitigen Sessions. Keine Keycloak-, OAuth2- oder separate Authentifizierungsinfrastruktur.
- KI in einer späteren Iteration: OpenAI-API nur serverseitig, über eine austauschbare Provider-Abstraktion. Die KI erhält keinen Datenbankzugriff. Kernfunktionen bleiben ohne LLM nutzbar.

## Iteration 1

Lauffähiges Grundgerüst mit `GET /api/status`, Actuator-Health, einer Flyway-Baseline ohne fachliche Tabellen und einer Startseite, die den Backend-Status anzeigt. Keine Gerichte, Bewertungen, Authentifizierung oder KI.

## Laufzeitgrenzen

Im Standard-Compose ist PostgreSQL nicht auf dem Host veröffentlicht. Das Frontend ist der einzige veröffentlichte Dienst und leitet `/api` an das Backend weiter. Actuator bleibt im internen Backend-Netz und liefert keine Konfigurationsdetails.

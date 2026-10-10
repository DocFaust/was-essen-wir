# Roadmap

Jede Iteration ist für sich testbar. Fachliche Anforderungen stehen in [`fachkonzept.md`](fachkonzept.md), technische Festlegungen in [`architektur.md`](architektur.md). Spätere Iterationen werden nicht vorgezogen.

1. **Projektfundament – abgeschlossen.** Monorepo, Spring Boot und React, PostgreSQL mit Flyway, Docker Compose für `linux/arm64`, Tests und CI. Keine Fachfunktionen, keine Authentifizierung, keine KI.
2. **Gerichteverwaltung – abgeschlossen.** Grundgerichte und Varianten anlegen, anzeigen, suchen, filtern, bearbeiten und löschen. Dazu Mahlzeitentypen, Kategorien, Aufwand, Zutaten ohne Mengen, Rezepttext, Rezeptlink, Rezeptquelle, Kommentare und Eignungsmerkmale.
3. **Familie und Login – abgeschlossen.** Spring Security, lokale Benutzer, serverseitige Sessions, einmaliger Einrichtungsassistent und Administrationsrolle. Keine öffentliche Registrierung.
4. **Persönliche Bewertungen.** Sterne pro Benutzer und Gericht oder Variante. Nur der jeweilige Benutzer ändert die eigene Bewertung.
5. **Regelbasierte Essensvorschläge.** Mahlzeit, Mitessende und nachvollziehbarer Algorithmus ohne KI.
6. **Historie und Planung einschließlich Meal Prep.** Wochenkalender, geplant gegenüber tatsächlich gegessen, gezieltes Vorbereiten und zusätzliche Portionen, Homeoffice-Kontext.
7. **KI-Integration.** Natürlichsprachliche Wünsche, kontrollierte Schreibaktionen und gelegentliche neue Gerichte über eine austauschbare Provider-Abstraktion. Kernfunktionen bleiben ohne LLM nutzbar.
8. **Familienabstimmung und Lernen.** Optionale Abstimmungen und schwache lernende Signale.

Bewertungsvererbung, Empfehlungsgewichte, Allergien, Abstimmungsfristen und Aufbewahrung bleiben offene Punkte im Fachkonzept, bis die jeweilige Iteration sie festlegt.

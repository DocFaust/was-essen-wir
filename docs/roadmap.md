# Roadmap

Jede Iteration ist für sich testbar. Fachliche Anforderungen stehen in [`fachkonzept.md`](fachkonzept.md), technische Festlegungen in [`architektur.md`](architektur.md). Spätere Iterationen werden nicht vorgezogen.

1. **Projektfundament – abgeschlossen.** Monorepo, Spring Boot und React, PostgreSQL mit Flyway, Docker Compose für `linux/arm64`, Tests und CI. Keine Fachfunktionen, keine Authentifizierung, keine KI.
2. **Gerichteverwaltung – abgeschlossen.** Grundgerichte und Varianten anlegen, anzeigen, suchen, filtern, bearbeiten und löschen. Dazu Mahlzeitentypen, Kategorien, Aufwand, Zutaten ohne Mengen, Rezepttext, Rezeptlink, Rezeptquelle, Kommentare und Eignungsmerkmale.
3. **Familie und Login – abgeschlossen.** Spring Security, lokale Benutzer, serverseitige Sessions, einmaliger Einrichtungsassistent und Administrationsrolle. Keine öffentliche Registrierung.
4. **Persönliche Bewertungen – abgeschlossen.** Jede angemeldete Person bewertet Grundgerichte und Varianten unabhängig mit 1–5 Sternen oder lässt sie unbewertet. Gespeichert und angezeigt wird nur die eigene Bewertung. Durchschnitt, fremde Bewertungen und Empfehlungen folgen später.
5. **Regelbasierte Essensvorschläge – abgeschlossen.** Mahlzeit, Mitessende und bis zu drei regelbasierte Vorschläge ohne KI. Persönliche Bewertungen beeinflussen die Ziehung serverseitig und bleiben in der Antwort verborgen.
6. **Historie und Planung – abgeschlossen.** Gemeinsame Planung und Essenshistorie für die Familie, bestätigt gegenüber tatsächlich gegessen, Namenssnapshot beim Löschen von Gerichten. Kürzlich gegessene und bereits geplante Gerichte beeinflussen die Vorschläge. Zusätzliche Meal-Prep-Portionen bleiben ein späterer Schritt.
7. **KI-Assistent – abgeschlossen.** Optionale Provider Ollama und OpenAI. Natürlichsprachliche Wünsche werden in strukturierte Kriterien übersetzt; die vorhandene Empfehlung wählt die Gerichte. Details in [`ki-assistent.md`](ki-assistent.md). Kontrollierte Schreibaktionen und gelegentliche neue Gerichte bleiben ein späterer Schritt. Kernfunktionen bleiben ohne LLM nutzbar.
8. **Alltagstauglichkeit und Mobile UX – abgeschlossen.** Die Startseite beantwortet „Was essen wir heute?“. Vorschläge, Einplanen und das Erfassen von Gegessenem brauchen weniger Bedienhandlungen. Die Navigation priorisiert die täglichen Aufgaben. Bewertung und Begründung stehen in [`ux-review.md`](ux-review.md). Keine neue Fachfunktion.
9. **Familienabstimmung und Lernen.** Optionale Abstimmungen und schwache lernende Signale.

Allergien, Abstimmungsfristen und Aufbewahrung bleiben offene Punkte im Fachkonzept, bis die jeweilige Iteration sie festlegt. Die Gewichte der regelbasierten Vorschläge stehen in [`empfehlungslogik.md`](empfehlungslogik.md).

# Roadmap

Jede Iteration ist für sich testbar. Fachliche Anforderungen stehen in [`fachkonzept.md`](fachkonzept.md), technische Festlegungen in [`architektur.md`](architektur.md). Spätere Iterationen werden nicht vorgezogen.

1. **Projektfundament – abgeschlossen.** Monorepo, Spring Boot und React, PostgreSQL mit Flyway, Docker Compose für `linux/arm64`, Tests und CI. Keine Fachfunktionen, keine Authentifizierung, keine KI.
2. **Gerichteverwaltung – abgeschlossen.** Grundgerichte und Varianten anlegen, anzeigen, suchen, filtern, bearbeiten und löschen. Dazu Mahlzeitentypen, Kategorien, Aufwand, Zutaten ohne Mengen, Rezepttext, Rezeptlink, Rezeptquelle, Kommentare und Eignungsmerkmale.
3. **Familie und Login – abgeschlossen.** Spring Security, lokale Benutzer, serverseitige Sessions, einmaliger Einrichtungsassistent und Administrationsrolle. Keine öffentliche Registrierung. Optionales „Angemeldet bleiben“ speichert ein rotierendes Geräte-Token in PostgreSQL und stellt die Sitzung nach Ablauf oder Neustart wieder her.
4. **Persönliche Bewertungen – abgeschlossen.** Jede angemeldete Person bewertet Grundgerichte und Varianten unabhängig mit 1–5 Sternen oder lässt sie unbewertet. Gespeichert und angezeigt wird nur die eigene Bewertung. Durchschnitt, fremde Bewertungen und Empfehlungen folgen später.
5. **Regelbasierte Essensvorschläge – abgeschlossen.** Mahlzeit, Mitessende und bis zu drei regelbasierte Vorschläge ohne KI. Persönliche Bewertungen beeinflussen die Ziehung serverseitig und bleiben in der Antwort verborgen.
6. **Historie und Planung – abgeschlossen.** Gemeinsame Planung und Essenshistorie für die Familie, bestätigt gegenüber tatsächlich gegessen, Namenssnapshot beim Löschen von Gerichten. Kürzlich gegessene und bereits geplante Gerichte beeinflussen die Vorschläge. Zusätzliche Meal-Prep-Portionen bleiben ein späterer Schritt.
7. **KI-Assistent – abgeschlossen.** Optionale Provider Ollama und OpenAI. Natürlichsprachliche Wünsche werden in strukturierte Kriterien übersetzt; die vorhandene Empfehlung wählt die Gerichte. Details in [`ki-assistent.md`](ki-assistent.md). Die KI speichert nicht selbst. Kernfunktionen bleiben ohne LLM nutzbar.
8. **Alltagstauglichkeit und Mobile UX – abgeschlossen.** Die Startseite beantwortet „Was essen wir heute?“. Vorschläge, Einplanen und das Erfassen von Gegessenem brauchen weniger Bedienhandlungen. Die Navigation priorisiert die täglichen Aufgaben. Bewertung und Begründung stehen in [`ux-review.md`](ux-review.md). Keine neue Fachfunktion.
9. **KI-gestützte Gerichteerfassung – abgeschlossen.** Natürlichsprachliche Beschreibungen werden zu einem prüfbaren Entwurf für ein Grundgericht oder eine Variante. Gespeichert wird erst, wenn die Person das vorhandene Formular bestätigt. Unbelegte Eigenschaften bleiben leer. Mögliche Duplikate sind ein Hinweis. Details in [`ki-assistent.md`](ki-assistent.md).
10. **Public Deployment Hardening.** Sicherheitshärtung für den optionalen öffentlichen Betrieb hinter HTTPS. Der Heimnetzbetrieb bleibt unterstützt. Der öffentliche Betrieb ist erst nach Abschluss dieser Iteration freigegeben. Keine fachlichen Funktionen.

    10.1 – Architektur und Arbeitsanweisungen
    - Allgemeine Agentenregeln ergänzen.
    - Betriebsmodell und Architekturentscheidungen aktualisieren.
    - Projektdokumentation konsistent halten.

    10.2 – Anwendungssicherheit – abgeschlossen.
    - Unnötige anonyme Session-Erzeugung an öffentlichen Statusendpunkten entfernt.
    - AttemptLimiter mit Kapazitätsgrenze, Bereinigung abgelaufener Einträge und Schutz bestehender Sperren abgesichert.
    - Login-Timing geprüft: Spring Security 7.1.2 (`DaoAuthenticationProvider`) deckt unbekannte und deaktivierte Benutzer ab; keine zusätzliche App-Logik nötig.
    - Sicherheitsrelevante Regressionstests ergänzt.

    10.3 – KI-Absicherung – abgeschlossen.
    - Serverseitiges Rate-Limiting und Parallelitätsgrenzen für alle KI-Endpunkte.
    - HTTP 429 mit Retry-After und verständlicher UI-Meldung.
    - Provider- und interner Nginx-Timeout abgestimmt; Ausgabe-Token begrenzt.
    - Fixture-Provider auf Testprofil bzw. `AI_ALLOW_FIXTURE` beschränkt.
    - Nutzungslimits begrenzen das Anfragevolumen, ersetzen aber keine Provider-Kostenkontrollen.

    10.4 – Produktionsdeployment – abgeschlossen.
    - `compose.prod.yaml` mit Loopback-Frontend-Port, Secure-Cookies und Container-Härtung.
    - Host-Nginx-VHost-Beispiel für `essen.faustens.de` mit TLS und kontrollierten Proxy-Headern.
    - Vertrauenswürdige Client-IP für AttemptLimiter; Schutz gegen Header-Spoofing.
    - HTTP-Sicherheitsheader (CSP, Frame-Schutz, Referrer-/Permissions-Policy); HSTS am Host-Nginx.
    - Request-Größenbegrenzung einheitlich 256 KiB (Nginx und Backend, inkl. Chunked).
    - Deployment-Anleitung in `docs/deployment.md`; automatisierte Compose- und Sicherheitstests.

    10.5 – Betriebssicherheit – abgeschlossen.
    - PostgreSQL-Backup/`pg_restore` inkl. systemd-Timer-Vorlage und Token-Invalidierung nach Restore.
    - Isolierter Backup-/Restore-CI-Test; Trivy- und npm-audit-Scans in GitHub Actions.
    - Erweiterte Betriebsdokumentation, Sicherheitsreview und Go-Live-Checkliste.
    - Technische Deployment-Bereitschaft im Repository; Betriebsfreigabe erst nach Prüfung auf dem Zielserver.

    10.6 – Praxistauglichkeit des KI-Assistenten – abgeschlossen.
    - Ein bekannter Gerichtsname reicht für einen prüfbaren Entwurf. Die KI darf Beschreibung, Mahlzeiten, Kategorien, Aufwand, typische Zutaten ohne Mengen, eine kurze Zubereitung, Meal Prep und Homeoffice ergänzen.
    - Ausdrückliche Angaben haben Vorrang. Ausgeschlossene oder ersetzte Zutaten kommen nicht zurück. Rezeptlinks, externe Quellen und persönliche Kommentare werden nicht erfunden. Unbekannte Gerichte bleiben ohne erfundenes Rezept.
    - Providerfehler unterscheiden Guthaben, Zugang, Anbieter-Rate-Limit, Netzwerk, Timeout, ungültige Antwort und sonstige Fehler. Interne Nutzungslimits bleiben getrennt.
    - Gespeichert wird weiterhin erst im Formular. Keine neue Migration, kein zweiter KI-Aufruf, keine Deployment-Änderung.

11. **Familienabstimmung und Lernen.** Optionale Abstimmungen und schwache lernende Signale.

Allergien, Abstimmungsfristen und Aufbewahrung bleiben offene Punkte im Fachkonzept, bis die jeweilige Iteration sie festlegt. Die Gewichte der regelbasierten Vorschläge stehen in [`empfehlungslogik.md`](empfehlungslogik.md).

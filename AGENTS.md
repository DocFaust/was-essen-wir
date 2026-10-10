# Agentenregeln – Was essen wir?

## Auftrag
Implementiere eine mobile-first PWA für die Essensentscheidung einer einzelnen Familie pro Installation. Arbeite in kleinen, testbaren Schritten. **Kein Anwendungscode ohne konkret vereinbarten Umsetzungsschritt.**

## Verbindliche Quellen
- `docs/fachkonzept.md`: fachliche Anforderungen und Abgrenzung (maßgeblich)
- `docs/architektur.md`: technische Leitplanken
- `docs/datenmodell.md`: konzeptionelle Entitäten
- `docs/empfehlungslogik.md`: Regeln für Vorschläge
- `docs/roadmap.md`: Reihenfolge der Umsetzung

## Arbeitsregeln
- Lies nur die für die Aufgabe relevanten Dokumente; widersprüchliche Anforderungen vor Umsetzung klären.
- Mobile first; einfache, touchfreundliche UI. Keine unnötige Komplexität.
- Genau eine Familie pro Installation. Benutzer und Familienmitglieder nicht im Code hinterlegen.
- Keine echten Familiendaten, API-Schlüssel, Passwörter oder produktiven Daten in Git, Tests oder Logs.
- Betrieb nur im privaten Heimnetz. Keine öffentliche Internetfreigabe.
- Authentifizierung später nur mit Spring Security, lokaler Benutzerverwaltung und serverseitigen Sessions. Kein Keycloak, kein OAuth2, keine separate Authentifizierungsinfrastruktur.
- Alle KI-Aktionen serverseitig validieren und autorisieren; die KI erhält keinen direkten Datenbankzugriff. Provider austauschbar halten.
- Persönliche Sternebewertungen dürfen nur durch den jeweiligen Benutzer geändert werden.
- KI ist optional; Kernfunktionen müssen ohne LLM nutzbar bleiben.
- Bei Änderungen passende Tests und Dokumentation ergänzen; keine stillen Architekturwechsel.
- Keine Multi-Tenant-Funktionalität oder öffentliche Registrierung einführen.

## Definition of Done
Eine Änderung ist abgeschlossen, wenn:

1. Die Implementierung die vereinbarte Iteration erfüllt.
2. Passende automatisierte Tests vorhanden sind.
3. Vorhandene Tests erfolgreich laufen.
4. Linting, TypeScript-Prüfung und Builds erfolgreich sind.
5. Die relevante Dokumentation aktualisiert ist.
6. Keine Secrets oder privaten Familiendaten enthalten sind.
7. Die Änderung mobile-first umgesetzt ist.

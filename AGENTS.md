# Agentenregeln – Was essen wir?

## Auftrag
Implementiere eine mobile-first PWA für die Essensentscheidung einer einzelnen Familie pro Installation. Arbeite in kleinen, testbaren Schritten. **Kein Anwendungscode ohne konkret vereinbarten Umsetzungsschritt.**

## Verbindliche Quellen
- `docs/fachkonzept.md`: fachliche Anforderungen und Abgrenzung (maßgeblich)
- `docs/architektur.md`: technische Leitplanken
- `docs/datenmodell.md`: konzeptionelle Entitäten
- `docs/empfehlungslogik.md`: Regeln für Vorschläge
- `docs/roadmap.md`: Reihenfolge der Umsetzung

## Projektspezifische Vorgaben
- Mobile first; einfache, touchfreundliche UI. Keine unnötige Komplexität.
- Genau eine Familie pro Installation. Benutzer und Familienmitglieder nicht im Code hinterlegen.
- Die Anwendung bleibt eine private Familienanwendung, auch wenn sie über das Internet erreichbar ist.
- Keine Multi-Tenant-Funktionalität und keine öffentliche Selbstregistrierung.
- Authentifizierung nur mit Spring Security, lokaler Benutzerverwaltung, serverseitigen Sessions und Remember-me. Kein Keycloak, kein OAuth2, keine JWT-Umstellung und keine separate Authentifizierungsinfrastruktur.
- Betrieb per Docker Compose. Unterstützte Plattformen: `linux/amd64` und `linux/arm64` (einschließlich Raspberry Pi).
- Heimnetzbetrieb über HTTP bleibt unterstützt. Öffentlicher Betrieb ausschließlich über HTTPS hinter einem Reverse-Proxy; Backend und PostgreSQL bleiben intern und werden nicht öffentlich exponiert. Secure-Cookies sind beim öffentlichen HTTPS-Betrieb verpflichtend.
- Der öffentliche Betrieb ist erst nach Abschluss der Sicherheitshärtung (Iteration 10) freigegeben. Bis dahin keinen produktiven Internetzugang schalten.
- Alle KI-Aktionen serverseitig validieren und autorisieren; die KI erhält keinen direkten Datenbankzugriff. Provider austauschbar halten.
- Bei Verwendung externer KI-Provider Missbrauch und unkontrollierte API-Kosten durch geeignete Schutzmaßnahmen begrenzen.
- Persönliche Sternebewertungen dürfen nur durch den jeweiligen Benutzer geändert werden.
- KI ist optional; Kernfunktionen müssen ohne LLM nutzbar bleiben.
- Keine echten Familiendaten, API-Schlüssel, Passwörter oder produktiven Daten in Git, Tests, Logs oder Dokumentationsbeispielen.

## Architektur- und Entwicklungsgrundsätze
- **KISS:** Bevorzuge die einfachste Lösung, welche die vereinbarten Anforderungen vollständig erfüllt.
- **YAGNI:** Keine Funktionen, Abstraktionen oder Erweiterungspunkte auf Vorrat implementieren.
- **SOLID** dort anwenden, wo es die Verständlichkeit und Wartbarkeit tatsächlich verbessert.
- Bestehende Architektur, Technologien, Projektstrukturen und Konventionen respektieren.
- Vor einer Implementierung den relevanten bestehenden Code analysieren.
- Vorhandene Komponenten und Funktionen wiederverwenden, statt parallele Implementierungen zu schaffen.
- Keine neuen Abhängigkeiten ohne konkreten, nachvollziehbaren Nutzen.
- Keine wesentlichen Architekturänderungen ohne vorherige Abstimmung.
- Lesbaren, verständlichen und wartbaren Code gegenüber unnötig komplexen Lösungen bevorzugen.
- Technische Schulden nicht durch Workarounds oder zusätzliche Komplexität verschleiern.

## Arbeitsweise
- Ausschließlich die vereinbarte Aufgabe umsetzen.
- Keine eigenmächtigen funktionalen Erweiterungen.
- Keine aufgabenfremden Refactorings oder Formatierungsänderungen.
- Unklarheiten und widersprüchliche Anforderungen vor der Umsetzung klären.
- Fachliche und wesentliche technische Entscheidungen nicht eigenmächtig treffen.
- Fehlerursachen beheben, statt Symptome zu umgehen.
- Bestehende Funktionalität und Datenintegrität erhalten.
- Abwärtskompatibilität und notwendige Datenmigrationen berücksichtigen.
- Änderungen möglichst klein, nachvollziehbar und überprüfbar halten.
- Keine destruktiven Aktionen ohne ausdrückliche Zustimmung.
- Lies nur die für die Aufgabe relevanten Dokumente.
- Bei Änderungen passende Tests und Dokumentation ergänzen; keine stillen Architekturwechsel.

## Qualitätssicherung
- Neue und geänderte Funktionen durch angemessene automatisierte Tests absichern.
- Neben dem Erfolgsfall auch relevante Fehlerfälle und Randbedingungen testen.
- Bestehende Tests nicht abschwächen oder entfernen, um eine Änderung erfolgreich erscheinen zu lassen.
- Keine Linter-Regeln, Typprüfungen, Coverage-Grenzen oder Sicherheitsprüfungen umgehen.
- Vor Abschluss alle für die Änderung relevanten Tests, Linter, Typprüfungen und Builds ausführen.
- Warnungen und Fehler analysieren und beheben, sofern sie im Zusammenhang mit der Aufgabe stehen.
- Nicht durchführbare Prüfungen ausdrücklich benennen.
- Dokumentation und Implementierung konsistent halten.
- Keine Prüfungen als erfolgreich darstellen, die nicht tatsächlich durchgeführt wurden.

## Sicherheit und Datenschutz
- Security by Design und Least Privilege berücksichtigen.
- Eingaben serverseitig validieren.
- Autorisierung niemals ausschließlich im Frontend durchsetzen.
- Keine echten Zugangsdaten, Tokens oder sonstigen Secrets im Quellcode, in Tests, Logs oder Dokumentationsbeispielen hinterlegen.
- Sensible Konfiguration über Umgebungsvariablen bereitstellen.
- Lokale Konfigurationsdateien mit Secrets von der Versionsverwaltung ausschließen.
- Bestehende Sicherheitsmechanismen nicht ohne nachvollziehbaren Grund ersetzen oder abschwächen.
- Bewusst vorgesehene einmalige Bootstrap-Secrets ausschließlich über den dafür vorgesehenen, geschützten Ausgabekanal bereitstellen.

## Definition of Done
Eine Änderung ist abgeschlossen, wenn:

1. Die Implementierung die vereinbarte Iteration erfüllt.
2. Passende automatisierte Tests vorhanden sind (bei reinen Dokumentationsänderungen entfällt dieser Punkt, sofern keine Tests betroffen sind).
3. Vorhandene Tests erfolgreich laufen (sofern Anwendungscode oder Tests geändert wurden).
4. Linting, TypeScript-Prüfung und Builds erfolgreich sind (sofern Frontend- oder Backend-Code geändert wurde).
5. Die relevante Dokumentation aktualisiert ist.
6. Keine Secrets oder privaten Familiendaten enthalten sind.
7. Die Änderung mobile-first umgesetzt ist (sofern die Oberfläche betroffen ist).
8. Ein Abschlussbericht die wesentlichen Punkte aus dem folgenden Abschnitt enthält.

## Abschlussbericht
Nach Abschluss einer Aufgabe berichten:

- Welche Änderungen vorgenommen wurden.
- Welche wesentlichen Entscheidungen getroffen wurden.
- Welche Tests und Prüfungen mit welchem Ergebnis ausgeführt wurden.
- Welche Einschränkungen, offenen Punkte oder Risiken verbleiben.
- Welche Entscheidungen ohne ausdrückliche Vorgabe getroffen werden mussten.

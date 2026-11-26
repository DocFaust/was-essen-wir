# Sicherheitsausnahmen

**Stand:** Iteration 10.5 (Ausnahmen für postgres:17-alpine am 2026-11-26)

Ausnahmen von den automatisierten Sicherheitsscans (Trivy `.trivyignore`, bewusst akzeptierte `npm audit`-Befunde) sind hier zu dokumentieren. Pauschale Ausnahmen sind nicht erlaubt.

## Regeln

1. Nur konkrete CVE-/Advisory-IDs.
2. Begründung, betroffene Komponente (Produktiv vs. Dev/Test), verfügbare Fehlerbehebung ja/nein.
3. Wiedervorlagedatum (höchstens 90 Tage).
4. Nach Ablauf erneut prüfen oder beheben; abgelaufene Einträge entfernen oder erneuern.
5. Keine Abschwächung der Quality Gates außerhalb dieses Dokuments.

## Aktuelle Ausnahmen

| ID | Komponente | Schwere | Fix verfügbar | Begründung | Wiedervorlage |
| --- | --- | --- | --- | --- | --- |
| CVE-2026-86144 | `libxml2` im Image `postgres:17-alpine` (Produktiv) | HIGH | Ja, Alpine `2.13.9-r3`; das offizielle Postgres-Image ist noch nicht neu gebaut | Die Anwendung nutzt keine XML-Funktionen von PostgreSQL. Die Ausnahme entfällt, sobald das neu gebaute Image den Fix enthält. | 2026-12-10 |
| 26 CVEs der Go-Standardbibliothek, Liste in `.trivyignore-postgres` | `usr/local/bin/gosu` im Image `postgres:17-alpine` (Produktiv) | 1 CRITICAL, 25 HIGH | Nur durch ein neues gosu-Build im Upstream-Image | gosu ist mit Go 1.24.6 gebaut und wechselt beim Containerstart nur den Benutzer, bevor es PostgreSQL startet. Es öffnet keine Verbindungen und führt kein TLS, kein x509 und kein URL-Parsing aus. Die betroffenen Pakete sind darüber nicht erreichbar. Ein Wechsel auf PostgreSQL 18 behebt das nicht. | 2027-02-24 |

Die Postgres-Ausnahmen stehen in einer eigenen Datei `.trivyignore-postgres`, die nur der Scan von `postgres:17-alpine` verwendet. Für die eigenen Images und die Dateisystem-Scans gelten sie nicht.

# Sicherheitsausnahmen

**Stand:** Iteration 10.5

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
| – | – | – | – | Keine Ausnahmen zum Stand Iteration 10.5. | – |

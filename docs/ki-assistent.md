# KI-Assistent

Iteration 7 ergänzt eine optionale Interpretationsschicht. Natürlichsprachliche Essenswünsche werden in strukturierte Kriterien übersetzt. Die Gerichte wählt weiterhin der bestehende Empfehlungsdienst. Die KI berechnet keine eigene Rangfolge, speichert nichts und führt keine Werkzeuge aus.

## Architektur

```text
POST /api/ai/recommendations
        |
        v
AI Assistant Service
        |
        +-- Intent Prompt und Provider
        |       +-- Ollama
        |       +-- OpenAI
        |       +-- Fixture (nur Tests)
        |
        +-- Validierung
        +-- lokale Namensauflösung
        +-- Katalogabgleich
        |
        v
Recommendation Service
        |
        v
Antwort mit Kriterien, Begründung und bis zu drei Vorschlägen
```

`GET /api/ai/status` sagt der Oberfläche, ob der Assistent aktiv ist und welcher Provider konfiguriert ist. Die Antwort enthält keine Adresse und keinen Schlüssel.

Bei `AI_ENABLED=false` wird kein Provider-Client aufgerufen. Ein nicht erreichbarer Provider verhindert den Start nicht, weil Verbindungen erst bei einer Anfrage entstehen.

## Konfiguration

```yaml
app:
  ai:
    enabled: ${AI_ENABLED:false}
    provider: ${AI_PROVIDER:ollama}
    max-input-length: ${AI_MAX_INPUT_LENGTH:500}
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://127.0.0.1:11434}
      model: ${OLLAMA_MODEL:qwen3.5:9b}
      timeout: ${OLLAMA_TIMEOUT:30s}
    openai:
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_MODEL:gpt-4.1-mini}
      timeout: ${OPENAI_TIMEOUT:30s}
```

Der Provider wird nur über `AI_PROVIDER` gewählt: `ollama` oder `openai`. Ein Wechsel braucht keine Codeänderung. `fixture` ist ein Testdouble ohne Netz und nicht für den Alltag gedacht.

`127.0.0.1` im Backend-Container ist der Container selbst, nicht ein anderer Rechner. `host.docker.internal` ist unter Linux keine verlässliche Adresse für einen anderen Rechner im Heimnetz. Ollama auf einem anderen Gerät erreicht man über seine LAN-Adresse oder einen DNS-Namen:

```env
AI_ENABLED=true
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://192.168.1.20:11434
OLLAMA_MODEL=qwen3.5:9b
```

```env
OLLAMA_BASE_URL=http://ollama.home:11434
```

OpenAI wird nur vom Backend aufgerufen:

```env
AI_ENABLED=true
AI_PROVIDER=openai
OPENAI_API_KEY=dein-schluessel
OPENAI_MODEL=gpt-4.1-mini
```

Der Schlüssel bleibt in der Umgebung des Backends. Er steht nicht im Frontend, nicht in Antworten und nicht in Logs.

## Ollama

`POST {base-url}/api/chat` mit `stream: false`, `format: json` und `think: false`. Das Modell ist konfigurierbar und nicht im Code fest verdrahtet. Die fachliche Prüfung der JSON-Antwort macht das Backend, nicht das Modell.

## OpenAI

Verwendet wird die Chat-Completions-Schnittstelle:

`POST {base-url}/v1/chat/completions`

Der Körper setzt `response_format` auf Structured Outputs:

```json
{
  "type": "json_schema",
  "json_schema": { "name": "meal_intent", "strict": true, "schema": {} }
}
```

Das Modell muss diese Form unterstützen. `gpt-4.1-mini` ist der voreingestellte Name und kann durch `OPENAI_MODEL` ersetzt werden. Es ist kein weiterer OpenAI-Dienst nötig. Der Aufruf sendet `Authorization: Bearer` nur an die konfigurierte Basisadresse.

## Unterstützte Anfragen

Beispiele, die die Schicht abbilden soll:

- „Was könnten wir heute Abend essen?“
- „Heute bitte etwas Schnelles.“
- „Katja isst mit und wir haben wenig Zeit.“
- „Schlag etwas vor, das wir länger nicht gegessen haben.“
- „Ich hätte Lust auf Nudeln.“
- „Heute lieber nichts Aufwendiges.“
- „Was könnten wir morgen Mittag essen?“
- „Keine Pizza.“
- „Etwas Ähnliches wie Lasagne.“

Fehlende Mahlzeit wird Abendessen, fehlendes Datum heute, fehlende Personen die angemeldete Person. Das entspricht der bisherigen Vorschlagsansicht. Eine Rückfrage gibt es, wenn ein genannter Name zu mehreren aktiven Mitgliedern passt, zu niemandem passt oder das Modell die Anfrage als widersprüchlich markiert. Es gibt keinen Gesprächsverlauf: die nächste Eingabe ist eine neue Anfrage.

„Länger nicht gegessen“ braucht keinen eigenen KI-Score. Das Referenzdatum geht in die bestehende Wiederholungs- und Planungsstrafe.

## Strukturierte Interpretation

Version 1, vom Modell erwartet und vom Backend geprüft:

```json
{
  "version": 1,
  "mealType": "ABENDESSEN",
  "date": "2026-10-17",
  "participantNames": ["Katja"],
  "onlyNamedParticipants": false,
  "preferSimple": true,
  "preferHomeOffice": false,
  "preferMealPrep": false,
  "dishSearchTerms": ["Nudeln"],
  "avoidDishTerms": ["Pizza"],
  "needsClarification": false,
  "clarificationQuestion": null
}
```

`mealType` ist `FRUEHSTUECK`, `MITTAGESSEN`, `ABENDESSEN`, `SNACK` oder `null`. `preferSimple` fasst schnell und einfach zusammen, weil der Empfehlungsdienst dafür ein Feld hat. `onlyNamedParticipants` lässt die angemeldete Person weg, wenn ausdrücklich nur die genannten Personen essen sollen.

Die KI liefert keine Benutzer-IDs. Namen löst das Backend gegen Anzeigename und Benutzername der aktiven Mitglieder auf, zuerst exakt, sonst über Wortbestandteile ab zwei Zeichen. Mehrdeutigkeit und unbekannte Namen werden zur Rückfrage. Deaktivierte Konten zählen nicht.

## Katalog

Suchbegriffe und Ausschlüsse werden lokal mit Gerichtsnamen, Variantennamen, Kategorien und gespeicherten Zutaten abgeglichen. Ein Treffer ist ein ganzes Wort, Großschreibung und Umlaute spielen keine Rolle. „Reis“ trifft „Preis“ nicht.

- Gericht, Kategorie oder Zutat des Grundgerichts: die ganze Familie ist Kandidat.
- Nur eine Variante oder ihre Zutat: nur diese Variante.
- Ein Ausschluss entfernt die ganze Familie über das bestehende `excludedDishIds`.
- Trifft kein Suchbegriff, gibt es keine Vorschläge und einen Hinweis.
- Aus einem Namen wird nicht auf Allergene, vegetarische Eignung oder ungenannte Zutaten geschlossen. Es gibt keine Embeddings und keine Vektordatenbank.

Die Vorauswahl ist ein Filter vor der unveränderten Bewertung und Ziehung. Die Formel in [`empfehlungslogik.md`](empfehlungslogik.md) bleibt dieselbe.

## Validierung

Modellausgaben gelten als nicht vertrauenswürdig.

- Nur JSON der Version 1, unbekannte Felder werden ignoriert und nicht ausgeführt.
- `participantIds` dürfen nicht gesetzt sein.
- Mahlzeitentypen müssen dem Enum entsprechen.
- Datum muss `YYYY-MM-DD` zwischen 2000-01-01 und 2100-12-31 sein.
- Präferenzen sind Wahrheitswerte.
- Höchstens 12 Namen à 80 Zeichen und 8 Begriffe à 80 Zeichen.
- Rückfragen höchstens 300 Zeichen.
- Texte mit `://` oder Steuerzeichen werden abgewiesen.
- Die Benutzeranfrage ist auf 500 Zeichen begrenzt, höchstens 2000, wenn die Konfiguration dazwischen liegt.
- Provider-Antworten werden nach 32 KiB abgeschnitten und dann abgewiesen.
- Es werden keine URLs geöffnet und keine Befehle ausgeführt.

## API

Angemeldet, `POST` mit CSRF, `Cache-Control: no-store`.

`POST /api/ai/recommendations`

```json
{ "message": "Heute Abend etwas Schnelles mit Katja", "seed": null }
```

Die Antwort enthält `reply`, `clarificationQuestion`, `criteria`, `suggestions` und `explanation`. `suggestions` sind die bekannten Vorschläge ohne Sterne, Score oder fremde Bewertungen. `criteria` nennt Mahlzeit, Datum, aufgelöste Mitessende, Präferenzen, Suchbegriffe, Ausschlüsse, getroffene Katalognamen und Hinweise.

| Situation | Status |
| --- | --- |
| Assistent aus | `404`, `AI_DISABLED` |
| nicht angemeldet | `401` |
| ohne CSRF | `403` |
| leere oder zu lange Eingabe | `400` |
| Provider nicht erreichbar oder nicht konfiguriert | `503`, `AI_UNAVAILABLE` |
| Zeitüberschreitung | `504`, `AI_TIMEOUT` |
| ungültige Modellausgabe | `502`, `AI_INVALID` |
| Rückfrage oder keine passenden Gerichte | `200` mit leeren Vorschlägen |

Es gibt keine automatischen Wiederholungen. Ein Fehler ändert keine Gerichte, Planungen, Historieneinträge oder Bewertungen.

## Datenschutz

Der Prompt enthält nur:

- die aktuelle Benutzeranfrage
- das heutige Datum
- die erlaubten Mahlzeitentypen
- die unterstützten Präferenzen
- die Anweisung, Namen wörtlich zu nennen und nichts zu erfinden

Nicht im Prompt: Familienliste, Benutzer-IDs, Passwörter, Sternebewertungen, Essenshistorie, Planung, Katalog. Die Namensauflösung und der Katalogabgleich laufen danach lokal. Die Provider-Auswahl steht nur in der Serverkonfiguration.

Bei Ollama verlässt die Anfrage das Backend in Richtung des konfigurierten Servers, auch wenn der im LAN steht. Bei OpenAI wird sie an einen externen Dienst übertragen. Im Fixture-Testmodus bleibt sie im Backend.

## Fehler und Oberfläche

Die Startseite zeigt „Frag den Essensassistenten“ nur, wenn der Status `enabled` ist, und zwar unter der regelbasierten Auswahl. Die klassische Auswahl bleibt darüber vollständig nutzbar. Die Oberfläche zeigt die kurze Antwort, die tatsächlich verwendeten Kriterien und die vorhandenen Gerichtskarten. Einplanen und als gegessen erfassen verwenden Datum, Mahlzeit und aufgelöste Mitessende aus den Kriterien. Die Mitessenden für diese Übernahme lädt die Oberfläche erst, wenn jemand einen Vorschlag übernimmt.

Technische Fehler nennen keine Adressen, Schlüssel oder Stacktraces. „Erneut versuchen“ sendet dieselbe Anfrage einmal erneut, „Neue Anfrage“ leert das Formular.

## Grenzen der ersten Version

- Kein Chatverlauf und kein Lernen aus Gesprächen.
- Keine Schreibaktionen, keine automatische Planung, keine neuen Gerichte, keine Rezepte, keine Bilder, keine Spracheingabe.
- Keine eigenen KI-Bewertungen und keine Sicht auf fremde Sterne.
- Katalogwünsche funktionieren nur, wenn Name, Variante, Kategorie oder Zutat den Begriff enthalten.
- Ein Ausschluss trifft die ganze Gerichtsfamilie, auch wenn nur eine Variante gemeint war.
- „Ähnlich wie“ ist ein Suchbegriff, keine semantische Nachbarschaft.

# KI-Assistent

Iteration 7 ergänzt eine optionale Interpretationsschicht. Natürlichsprachliche Essenswünsche werden in strukturierte Kriterien übersetzt. Die Gerichte wählt weiterhin der bestehende Empfehlungsdienst. Die KI berechnet keine eigene Rangfolge, speichert nichts und führt keine Werkzeuge aus.

## Architektur

```text
POST /api/ai/recommendations  (und /api/ai/dish-drafts)
        |
        v
AI Service
        |
        +-- AiProviderGateway (Nutzungs- und Parallelitätslimits)
        |       +-- Ollama
        |       +-- OpenAI
        |       +-- Fixture (nur Tests / AI_ALLOW_FIXTURE)
        |
        +-- Validierung
        +-- lokale Namensauflösung / Entwurfslogik
        |
        v
Antwort (Vorschläge bzw. Entwurf)
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
    max-output-tokens: ${AI_MAX_OUTPUT_TOKENS:1024}
    allow-fixture: ${AI_ALLOW_FIXTURE:false}
    rate-limit:
      per-user-per-minute: ${AI_RATE_LIMIT_PER_USER_PER_MINUTE:5}
      per-user-per-day: ${AI_RATE_LIMIT_PER_USER_PER_DAY:50}
      global-per-day: ${AI_RATE_LIMIT_GLOBAL_PER_DAY:150}
      max-concurrent-per-user: ${AI_MAX_CONCURRENT_PER_USER:1}
      max-concurrent-global: ${AI_MAX_CONCURRENT_GLOBAL:3}
      max-tracked-users: ${AI_RATE_LIMIT_MAX_TRACKED_USERS:1000}
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

Der Provider wird nur über `AI_PROVIDER` gewählt: `ollama` oder `openai`. Ein Wechsel braucht keine Codeänderung. `fixture` ist ein Testdouble ohne Netz und nur mit Spring-Profil `test` oder `AI_ALLOW_FIXTURE=true` zulässig. Ungültige Grenzwerte und Timeouts werden beim Start abgewiesen.

## Nutzungslimits

Alle Provider-Aufrufe (Essensvorschläge und Gerichteentwurf) teilen dieselben In-Memory-Grenzen einer Backend-Instanz. Die Zuordnung erfolgt über die angemeldete Benutzer-ID, unabhängig vom Provider. Die Prüfung liegt vor dem externen Aufruf; abgelehnte Anfragen erzeugen keine Provider-Kosten. Gestartete Aufrufe zählen auch bei Fehler oder Timeout. Parallele Anfragen werden hart begrenzt und bei ausgeschöpfter Kapazität sofort mit 429 abgelehnt – ohne Warteschlange. Freigabe der Parallelitätsplätze erfolgt zuverlässig nach Ende des Provider-Aufrufs.

| Grenze | Standard |
| --- | --- |
| pro Benutzer und Minute | 5 |
| pro Benutzer und Tag | 50 |
| insgesamt pro Tag | 150 |
| parallel pro Benutzer | 1 |
| parallel insgesamt | 3 |

Tagesgrenzen folgen `app.time-zone` (Standard `Europe/Berlin`). Abgelaufene Minutenfenster und inaktive Tageszähler werden bereinigt; die Zahl gehaltener Benutzerkennungen ist durch `AI_RATE_LIMIT_MAX_TRACKED_USERS` begrenzt. Bei einem Backend-Neustart gehen die Zähler verloren (bewusst akzeptiert). Es gibt keine Datenbank- oder Redis-Persistenz.

Die Limits begrenzen das Anfragevolumen und sind **kein** garantiertes finanzielles Ausgabenlimit. Zusätzliche Nutzungs- und Kostenkontrollen des jeweiligen externen Providers bleiben empfohlen. Eigene Abrechnungslogik gibt es nicht.

## Timeouts

- Provider-Timeout: Standard 30 Sekunden (`OLLAMA_TIMEOUT` / `OPENAI_TIMEOUT`), höchstens 2 Minuten.
- Internes Nginx für `/api/ai/`: 45 Sekunden Lese- und Sende-Timeout; übrige `/api/`-Pfade bleiben bei 10 Sekunden.
- Provider-Timeouts müssen unter dem Proxy-Timeout bleiben, sonst bricht Nginx vor dem Backend ab.
- Es gibt keine automatischen Provider-Retries.
- Generierte Tokens sind über `AI_MAX_OUTPUT_TOKENS` (Standard 1024) begrenzt; Antwortkörper bleiben auf 32 KiB begrenzt.

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
| Minutenlimit oder vorübergehende Auslastung | `429`, `AI_RATE_LIMITED` oder `AI_BUSY`, optional `Retry-After` |
| Tageslimit (Benutzer oder global) | `429`, `AI_DAILY_LIMIT`, optional `Retry-After` |
| Netzwerk, Verbindung oder leere Adresse | `503`, `AI_UNAVAILABLE` |
| Zeitüberschreitung | `504`, `AI_TIMEOUT` |
| ungültige Modellausgabe | `502`, `AI_INVALID` |
| kein API-Guthaben (`insufficient_quota`, `credit_balance_exhausted`) | `503`, `AI_QUOTA` |
| ungültiger Schlüssel oder fehlende Berechtigung | `503`, `AI_PROVIDER_AUTH` |
| Rate-Limit des Anbieters | `429`, `AI_PROVIDER_RATE_LIMIT` |
| sonstiger Fehler des Anbieters | `502`, `AI_PROVIDER_ERROR` |
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

Technische Fehler nennen keine Adressen, Schlüssel oder Stacktraces. Bei vorübergehender Auslastung zeigt „Erneut versuchen“ dieselbe Anfrage manuell erneut; beim Tageslimit entfällt dieser Knopf. „Neue Anfrage“ leert das Formular. Während einer laufenden Anfrage bleiben die Schaltflächen gesperrt, damit keine versehentlichen Mehrfachaufrufe entstehen.

## Grenzen der ersten Version

- Kein Chatverlauf und kein Lernen aus Gesprächen.
- Keine automatische Planung, keine Bilder, keine Spracheingabe. Die Vorschläge enthalten keine Rezepte. Ein Gericht oder eine Variante entsteht nur, wenn die Person den Entwurf im Formular speichert.
- Keine eigenen KI-Bewertungen und keine Sicht auf fremde Sterne.
- Katalogwünsche funktionieren nur, wenn Name, Variante, Kategorie oder Zutat den Begriff enthalten.
- Ein Ausschluss trifft die ganze Gerichtsfamilie, auch wenn nur eine Variante gemeint war.
- „Ähnlich wie“ ist ein Suchbegriff, keine semantische Nachbarschaft.

## Gerichteentwurf

`POST /api/ai/dish-drafts` verlangt eine Sitzung und CSRF. Der Endpunkt erzeugt nur einen Entwurf. Er schreibt nicht in die Datenbank. Angelegt wird ein Gericht oder eine Variante erst über die vorhandenen Endpunkte, wenn die Person im Formular auf Speichern tippt. Abbrechen legt nichts an.

Die Schaltfläche heißt „Mit KI erstellen“. Ein Name oder eine Beschreibung genügt. Nach dem Entwurf füllt die Oberfläche das vorhandene Formular und bittet, die Angaben vor dem Speichern zu prüfen. Duplikate und eine unklare Grundgericht-Auswahl bleiben eigene Hinweise. Alle Felder sind vor dem Speichern bearbeitbar.

Die Anfrage enthält die Beschreibung, die Art `DISH` oder `VARIANT` und bei einer Variante die ID des Grundgerichts. Die Beschreibung ist auf die bestehende Eingabelänge begrenzt, höchstens 2000 Zeichen. Eine unbekannte oder fremde Grundgericht-ID wird abgelehnt. Mehrere namensähnliche Grundgerichte werden als Auswahl zurückgegeben, ohne den Provider aufzurufen. Die Oberfläche startet die Variante bevorzugt aus den Details eines Gerichts.

### Schema

Ollama erhält das Schema als `format`-Objekt, OpenAI als Structured Output mit dem Namen `dish_draft`. Beide müssen Schemaversion 1 liefern. Alle Schlüssel sind Pflicht, unbekannte Werte sind `null` oder leere Listen. `additionalProperties` ist falsch. Unbekannte Enums, eine andere Schemaversion und gesetzte IDs (`id`, `dishId`, `baseDishId`, `variantId`, `participantIds`) werden verworfen.

| Feld | Bedeutung |
| --- | --- |
| `name`, `description`, `recipeText`, `recipeUrl`, `recipeSource`, `comments` | Text oder `null` |
| `mealTypes` | `FRUEHSTUECK`, `MITTAGESSEN`, `ABENDESSEN`, `SNACK` |
| `categories`, `ingredients`, `removedIngredients`, `warnings` | kurze Listen |
| `effort` | `EINFACH`, `MITTEL`, `AUFWENDIG` oder `null` |
| `mealPrep`, `homeOffice` | `true`, `false` oder `null` |

Die Antwort an den Browser mappt das auf die vorhandenen DTOs. `removedIngredients` bleibt intern und wird für eine Variante mit den Zutaten des Grundgerichts verrechnet. `ingredientsSpecified` ist bei einem Grundgericht `null` und bei einer Variante nur dann `true`, wenn der Text Zutaten nennt. `null` bei Aufwand, Meal Prep und Homeoffice einer Variante bedeutet weiter Vererbung, nicht „nicht geprüft“.

### Ergänzt, ausdrücklich, unbekannt

Das Modell erstellt einen Entwurf. Ein bekannter Gerichtsname genügt. Es darf Beschreibung, Mahlzeiten, Kategorien, Aufwand, typische Zutaten ohne Mengen, eine kurze Zubereitung, Meal Prep und Homeoffice ergänzen. Ausdrückliche Angaben im Text haben Vorrang, auch wenn das Modell etwas anderes liefert. `false` gilt bei ausdrücklicher Verneinung. Kennt das Modell das Gericht nicht sicher, lässt es die ergänzten Felder leer und setzt `warnings` auf `unbekannt`. Das Backend übernimmt dann kein Rezept und keine erfundenen Zutaten.

Nicht ergänzt werden persönliche Kommentare, Familiengewohnheiten, Rezeptlinks und externe Rezeptquellen. Übernimmt der Entwurf eine Zubereitung und nennt die Person keine Quelle, setzt das Backend `recipeSource` auf `KI-generiert`, auch wenn das Modell die Kennzeichnung weglässt. Eine Quelle aus dem Text bleibt erhalten und gewinnt. Nennt der Zubereitungstext eine ausdrücklich ausgeschlossene oder ersetzte Zutat, erscheint ein Prüfhinweis.

| Text | Ergebnis |
| --- | --- |
| bis 30 Minuten, einfach, schnell, unkompliziert, wenig Aufwand | `EINFACH`, auch gegen einen anderen Modellwert |
| über 30 bis 75 Minuten, mittel, mittelschwer | `MITTEL` |
| über 75 Minuten, aufwendig | `AUFWENDIG` |
| „eine Stunde“, „halbe Stunde“, „anderthalb/eineinhalb Stunde“ | 60, 30, 90 Minuten, dann die Aufwandsklasse |
| Aufwandswort und Dauer widersprechen sich | das Wort gewinnt, mit Hinweis |
| Vorkochen, Vorbereiten, Meal Prep | Meal Prep `true`; Verneinung `false`; sonst der Modellvorschlag oder `null` |
| Homeoffice | analog |
| „lässt sich aufwärmen“ | Kommentar, kein eigenes Feld und kein Meal Prep |
| ausdrücklich vegetarisch | Kategorie nur am Grundgericht; sonst dürfen Modellkategorien ergänzt werden |
| „ohne Sellerie“, „mit X statt Y“ | die genannte Zutat fehlt im Entwurf, auch wenn das Modell sie ergänzt |
| Mahlzeit an einer Variante | Hinweis, Mahlzeiten bleiben am Grundgericht |

Es gibt keine Minutenangabe in der Datenbank. Eine genannte Dauer wird auf die Aufwandsklasse abgebildet. Mengenangaben in Zutaten werden entfernt.

Zutaten aus dem Text kommen aus „mit X statt Y“, „auch mit“, „mit zusätzlichen“, „Zutaten:“, „aus A und B“ und „ohne/kein“. „mit Reis“ im Namen eines Gerichts ist keine Zutat. Bei einem Grundgericht ohne solche Liste gelten die typischen Zutaten des Modells. Eine ausdrückliche Liste ersetzt sie. Bei einer Variante bleiben nicht genannte Eigenschaften leer und werden vererbt. Hinzugefügte Zutaten ergänzen die Liste des Grundgerichts. Nur „Zutaten:“ und „aus A und B“ ersetzen sie. Eine Ersetzung übernimmt die übrige Liste des Grundgerichts nur, wenn die ersetzte Zutat darin vorkommt. Sonst stehen die genannten und die zulässigen Ergänzungen im Formular, mit einem Hinweis zur Prüfung.

### Duplikate

Vor der Anzeige sucht das Backend lokal im Katalog, ohne Embeddings. Groß- und Kleinschreibung und naheliegende Schreibweisen zählen, Varianten eingeschlossen. Ab einem Schwellenwert erscheint ein Hinweis, zum Beispiel „Ein ähnliches Gericht existiert bereits: Spaghetti Bolognese.“ Die Person kann das vorhandene Gericht öffnen, eine Variante anlegen oder trotzdem speichern. Das ist keine Sperre. Die vorhandene Namensprüfung mit `confirmDuplicate` bleibt zusätzlich bestehen.

### Datenschutz des Entwurfs

Bei einem neuen Gericht gehen an den Provider nur die Beschreibung, die erlaubten Feldnamen, die Enums und das Schema. Bei einer Variante zusätzlich Name und Zutaten des ausgewählten Grundgerichts. Nicht übertragen werden Familienmitglieder, Bewertungen, Essenshistorie, Wochenplanung, Zugangsdaten und der vollständige Katalog. Der Schlüssel bleibt serverseitig. Antworten tragen `Cache-Control: no-store`. Protokolle enthalten keine Beschreibung und keine Modellantwort.

### Fehler des Entwurfs

| Situation | Verhalten |
| --- | --- |
| KI deaktiviert | `404`, `AI_DISABLED`; die manuelle Erfassung bleibt, die KI-Option erscheint nicht |
| Nutzungslimit | `429` wie bei den Vorschlägen; gemeinsames Kontingent |
| Netzwerk oder Verbindung | `503`, `AI_UNAVAILABLE` |
| Timeout | `504`, `AI_TIMEOUT` |
| ungültiges JSON, Enum oder Schema | `502`, `AI_INVALID` |
| kein API-Guthaben | `503`, `AI_QUOTA`; Hinweis an eine administrierende Person |
| ungültiger Zugang | `503`, `AI_PROVIDER_AUTH`; Hinweis an eine administrierende Person |
| Rate-Limit des Anbieters | `429`, `AI_PROVIDER_RATE_LIMIT` |
| sonstiger Anbieterfehler | `502`, `AI_PROVIDER_ERROR` |
| kein erkennbarer Name | `422`, `AI_UNINTERPRETABLE` |
| Grundgericht fehlt | `404`, `BASE_DISH_NOT_FOUND` |
| mehrere Grundgerichte | `200`, `outcome` `NEEDS_BASE_DISH` |
| Speichern schlägt fehl | die Formulareingaben bleiben; der Entwurf hat nichts verändert |

Bei einem KI-Fehler bleibt der Text stehen. Die Person kann es erneut versuchen oder zur manuellen Erfassung wechseln. Ein fehlgeschlagener Entwurf verändert keine bestehenden Gerichte.

### Grenzen der Erfassung

- Keine Internetsuche, keine Bilder, kein OCR, keine Spracheingabe, keine Nährwerte, keine Allergenerkennung. Eine kurze Zubereitung darf das Modell vorschlagen, wenn es das Gericht kennt. Der Entwurf wird nicht von selbst gespeichert.
- Keine neue Provider, keine Embeddings, keine frei ausführbaren Werkzeuge.
- Die Art kommt von der Oberfläche. Ein Variantenwunsch auf der Seite „Neues Gericht“ wird nicht still zu einer Variante umgedeutet.
- Typische Zutaten darf das Modell ergänzen. Ausdrückliche Listen und Ausschlüsse aus dem Text haben Vorrang. „mit Reis“ im Namen bleibt keine eigene Zutatenangabe.
- „nicht nur zum Vorkochen“ kann als Verneinung gelesen werden.
- Die Duplikatsuche ist eine Zeichen- und Wortähnlichkeit, keine Bedeutungssuche.

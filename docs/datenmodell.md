# Datenmodell

Iteration 2 persistiert Gerichte. Benutzer, Bewertungen, Planung, Historie, Vorschläge, Abstimmungen und Audit bleiben Kandidaten für spätere Iterationen: `User`, `Rating`, `PreferenceRule`, `MealPlan`, `MealHistory`, `MealParticipant`, `PreparedPortion`, `SuggestionSession`, `DecisionEvent`, `Vote`, `AuditEvent`.

`PreparedPortion` erhält später Empfänger, Verzehrdatum, Status und eine optionale Referenz auf den ursprünglichen Kochvorgang. Keine Lagerbestandsverwaltung.

## Grundgericht `dish`

| Spalte | Bedeutung |
| --- | --- |
| `id` | UUID, von der Anwendung vergeben |
| `name` | Pflicht, gekürzt, höchstens 120 Zeichen |
| `description` | optionaler Text |
| `effort` | `EINFACH`, `MITTEL`, `AUFWENDIG` oder leer |
| `recipe_text` | optionaler Rezepttext |
| `recipe_url` | optionaler http- oder https-Link |
| `recipe_source` | optionaler Freitext, keine Stammdaten |
| `comments` | optionaler Kommentartext, bis es Benutzerkommentare gibt |
| `meal_prep`, `homeoffice` | `true` geeignet, `false` ausdrücklich nicht geeignet, `null` nicht geprüft |
| `created_at`, `updated_at` | Zeitpunkte |

Mahlzeitentypen liegen in `dish_meal_type`: `FRUEHSTUECK`, `MITTAGESSEN`, `ABENDESSEN`, `SNACK`. Kategorien liegen als Namen in `dish_category`.

## Variante `dish_variant`

Eine Variante gehört zu genau einem Grundgericht. Der Name ist je Grundgericht eindeutig, unabhängig von Groß- und Kleinschreibung. Optionale Texte und der Aufwand sind `null`, wenn sie nicht gesetzt sind. `ingredients_specified` unterscheidet eine eigene Zutatenliste, die leer sein darf, von der übernommenen Liste. `meal_prep` und `homeoffice` sind wieder dreiwertig; `null` bedeutet hier übernehmen, nicht „nicht geprüft“.

## Zutaten `ingredient`

Ein Eintrag gehört entweder zum Grundgericht oder zur Variante. Gespeichert wird nur der Name, keine Menge. Nicht genannte Zutaten bedeuten unbekannt, nicht frei davon. Löschen eines Gerichts oder einer Variante entfernt die zugehörigen Zeilen per Fremdschlüssel.

Gleichnamige Grundgerichte sind erlaubt, werden aber ohne ausdrückliche Bestätigung nicht ein zweites Mal angelegt. Dafür gibt es keine Unique-Constraint.

## API

Die Collection liegt unter `/api/dishes`.

| Methode | Pfad | Erfolg |
| --- | --- | --- |
| `GET` | `/api/dishes` | Liste mit Name, Mahlzeitentypen, Aufwand, Eignung des Grundgerichts und Variantennamen |
| `POST` | `/api/dishes` | `201` und das vollständige Gericht |
| `GET` | `/api/dishes/{id}` | Gericht mit Varianten und aufgelösten Werten |
| `PUT` | `/api/dishes/{id}` | aktualisiertes Gericht |
| `DELETE` | `/api/dishes/{id}` | `204` |
| `POST` | `/api/dishes/{id}/variants` | Gericht inklusive neuer Variante |
| `PUT` | `/api/dishes/{id}/variants/{variantId}` | Gericht inklusive geänderter Variante |
| `DELETE` | `/api/dishes/{id}/variants/{variantId}` | Gericht ohne die Variante |

Die Suche `q` prüft Gerichts- und Variantennamen. Filter `mealType`, `effort`, `mealPrep` und `homeOffice` gelten gemeinsam und beziehen sich auf das Grundgericht. Eignungsfilter sind `SUITABLE`, `UNSUITABLE` oder `UNKNOWN`.

Schreibende Gerichts-Payloads enthalten `confirmDuplicate`. Ohne `true` antwortet ein gleichnamiges weiteres Gericht mit `409` und `code=DUPLICATE_DISH_NAME`. Validierung antwortet mit `400`, unbekannte IDs mit `404`.

Eignungswerte eines Grundgerichts sind `UNKNOWN`, `SUITABLE` und `UNSUITABLE`. Eine Variante sendet `INHERIT`, `SUITABLE` oder `UNSUITABLE`. In `resolved` steht der wirksame Wert und ob er übernommen wurde. Die Oberfläche zeigt `resolved` an und schickt die gespeicherten Felder beim Speichern zurück.

# Datenmodell

Iteration 4 persistiert persönliche Bewertungen. Planung, Historie, Vorschläge, Abstimmungen und Audit bleiben Kandidaten für spätere Iterationen: `PreferenceRule`, `MealPlan`, `MealHistory`, `MealParticipant`, `PreparedPortion`, `SuggestionSession`, `DecisionEvent`, `Vote`, `AuditEvent`.

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

## Benutzer `app_user`

| Spalte | Bedeutung |
| --- | --- |
| `id` | UUID, von der Anwendung vergeben |
| `username` | Anzeigenform, 2–40 Zeichen |
| `username_normalized` | NFKC, getrimmt, kleingeschrieben; eindeutig |
| `display_name` | 1–80 Zeichen |
| `password_hash` | BCrypt, nie in API-Antworten |
| `role` | `ADMIN` oder `MEMBER` |
| `active` | deaktivierte Konten können sich nicht anmelden |
| `password_change_required` | temporäres Passwort muss bei der nächsten Anmeldung ersetzt werden |
| `created_at`, `updated_at` | Zeitpunkte |

## Einrichtung `app_setup`

Genau eine Zeile. `token_hash` ist SHA-256 des Setup-Tokens. `expires_at` begrenzt die Gültigkeit, Standard 24 Stunden. `completed_at` beendet die Einrichtung dauerhaft; danach ist `token_hash` leer. Der Klartext des Tokens wird nicht gespeichert.

## Bewertung `rating`

Eine Tabelle für beide Ziele, analog zu `ingredient`. Grundgericht und Variante teilen sich Benutzer, Sterne und Zeitstempel. Getrennte Tabellen würden dieselben Regeln doppelt führen und die gemeinsame Abfrage für die Übersicht erschweren. Ein Bewertungsziel ist immer genau eines: `dish_id` oder `variant_id`.

| Spalte | Bedeutung |
| --- | --- |
| `id` | UUID, von der Anwendung vergeben |
| `user_id` | bewertende Person, aus der Sitzung, nicht aus der Anfrage |
| `dish_id` | Grundgericht, sonst leer |
| `variant_id` | Variante, sonst leer |
| `stars` | 1 bis 5 |
| `created_at`, `updated_at` | Zeitpunkte; Ändern lässt `created_at` stehen |

`rating_one_target` verlangt genau eine der beiden Referenzen. `rating_stars_range` lässt nur 1 bis 5 zu. Teilindizes erzwingen höchstens eine Zeile pro Benutzer und Grundgericht und pro Benutzer und Variante. Fremdschlüssel mit `ON DELETE CASCADE` entfernen Bewertungen, wenn das Gericht, die Variante oder das Benutzerkonto gelöscht wird. Eine Variante zu löschen lässt die Bewertung des Grundgerichts stehen. Fehlende Zeile bedeutet *nicht bewertet*, nicht drei Sterne.

## API

Öffentlich, jeweils mit CSRF bei zustandsändernden Aufrufen:

| Methode | Pfad | Erfolg |
| --- | --- | --- |
| `GET` | `/api/setup/status` | `{ "setupRequired": true/false }`, ohne Token oder Benutzernamen |
| `POST` | `/api/setup` | `201` und der erste Administrator, genau einmal |
| `POST` | `/api/auth/login` | angemeldete Person |
| `POST` | `/api/auth/logout` | `204`, Session serverseitig ungültig |

Angemeldet:

| Methode | Pfad | Wer |
| --- | --- | --- |
| `GET` | `/api/auth/me` | jede angemeldete Person |
| `PUT` | `/api/auth/profile` | eigener Anzeigename |
| `PUT` | `/api/auth/password` | eigenes Passwort |
| `GET`, `POST` | `/api/users` | nur `ADMIN` |
| `PUT` | `/api/users/{id}` | nur `ADMIN`: Anzeigename, Rolle, Aktivierung |
| `POST` | `/api/users/{id}/password` | nur `ADMIN`: temporäres Passwort |

`401` bedeutet nicht angemeldet oder eine ungültige Sitzung, `403` fehlende Rechte oder ein noch zu änderndes temporäres Passwort, `400` ungültige Eingaben, `409` fachliche Konflikte wie den letzten aktiven Administrator oder einen bereits vergebenen Benutzernamen. Passwort-Hashes erscheinen in keiner Antwort.

Die Gerichte-Collection liegt unter `/api/dishes` und verlangt eine gültige Anmeldung.

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

Gerichteantworten enthalten zusätzlich `myRating`: die Sterne der angemeldeten Person oder `null`. Dasselbe Feld steht an jeder Variante in der Detailantwort. Die Übersicht lädt diese Werte in einer Abfrage, nicht pro Gericht.

Eigene Bewertungen, jeweils nur für die Sitzung:

| Methode | Pfad | Erfolg |
| --- | --- | --- |
| `GET` | `/api/ratings` | alle eigenen Bewertungen, ohne fremde Zeilen |
| `GET` | `/api/dishes/{id}/rating` | `{ "stars": 1–5 }` oder `{ "stars": null }` |
| `PUT` | `/api/dishes/{id}/rating` | setzt oder ändert `{ "stars": 1–5 }`, idempotent |
| `DELETE` | `/api/dishes/{id}/rating` | `204`, auch wenn noch keine Bewertung existiert |
| `GET` | `/api/dishes/{id}/variants/{variantId}/rating` | wie beim Grundgericht |
| `PUT` | `/api/dishes/{id}/variants/{variantId}/rating` | setzt oder ändert die Variante |
| `DELETE` | `/api/dishes/{id}/variants/{variantId}/rating` | `204` |

Der Körper enthält nur `stars`. Eine mitgesendete Benutzer-ID wird nicht verwendet. `0` und `6` antworten mit `400`. Unbekannte Gerichte oder Varianten antworten mit `404`.

Eignungswerte eines Grundgerichts sind `UNKNOWN`, `SUITABLE` und `UNSUITABLE`. Eine Variante sendet `INHERIT`, `SUITABLE` oder `UNSUITABLE`. In `resolved` steht der wirksame Wert und ob er übernommen wurde. Die Oberfläche zeigt `resolved` an und schickt die gespeicherten Felder beim Speichern zurück.

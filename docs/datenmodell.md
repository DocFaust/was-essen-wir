# Datenmodell

Iteration 4 persistiert persönliche Bewertungen. Iteration 5 berechnet Vorschläge daraus und speichert sie nicht. Iteration 6 speichert Planung und Historie in einer gemeinsamen Mahlzeit. Abstimmungen und Audit bleiben Kandidaten für spätere Iterationen: `PreferenceRule`, `PreparedPortion`, `SuggestionSession`, `DecisionEvent`, `Vote`, `AuditEvent`.

`PreparedPortion` erhält später Empfänger, Verzehrdatum, Status und eine optionale Referenz auf den ursprünglichen Kochvorgang. Keine Lagerbestandsverwaltung. Eine geplante oder gegessene Mahlzeit ist noch keine vorbereitete Portion.

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

## Mahlzeit `meal_event`

Eine Zeile ist entweder geplant oder gegessen. Bestätigen ändert `status` und legt keinen zweiten Eintrag an. Dieselbe Mahlzeit kann an mehreren Tagen vorkommen, und ein Tag kann mehrere Zeilen derselben Mahlzeit haben.

| Spalte | Bedeutung |
| --- | --- |
| `id` | UUID, von der Anwendung vergeben |
| `meal_date` | Kalendertag, ohne Uhrzeit |
| `meal_type` | `FRUEHSTUECK`, `MITTAGESSEN`, `ABENDESSEN`, `SNACK` |
| `status` | `PLANNED` oder `EATEN` |
| `dish_id` | Grundgericht, leer nachdem das Gericht gelöscht wurde |
| `variant_id` | Variante oder leer |
| `dish_name`, `variant_name` | Namenssnapshot zum Speicherzeitpunkt |
| `comment` | optional, höchstens 500 Zeichen |
| `created_by` | anlegende Person aus der Sitzung, leer wenn das Konto gelöscht wurde |
| `created_at`, `updated_at` | Zeitpunkte |

Mitessende liegen in `meal_participant`: technische ID, Mahlzeit, Benutzer und Anzeigename. Dieselbe Person steht je Mahlzeit nur einmal. `user_id` wird leer, wenn das Konto gelöscht wird; `display_name` bleibt. Eine Mahlzeit ohne Mitessende lässt die Datenbank am Transaktionsende nicht zu.

Gericht und Variante verwenden `ON DELETE SET NULL`. Die Zeile und der Snapshot bleiben, damit die Historie lesbar bleibt. Eine Variante muss zum referenzierten Gericht gehören, solange beide Verweise gesetzt sind. Wiederholungsberechnungen ignorieren Zeilen ohne `dish_id`. Eine gelöschte Variante, deren Name noch im Snapshot steht, zählt nur noch als Familie und nicht als das Grundgericht.

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

Aktive Mitglieder für die Mitessendenauswahl, für jede angemeldete Person:

| Methode | Pfad | Erfolg |
| --- | --- | --- |
| `GET` | `/api/members` | `{ "id", "displayName" }` je aktivem Mitglied, ohne Benutzername, Rolle oder Passwort |

Essensvorschläge, `POST /api/recommendations`, für jede angemeldete Person. Der Körper enthält `mealType`, `dinerIds`, optional `preferSimple`, `preferHomeOffice`, `preferMealPrep`, `excludedDishIds` und `seed`. `excludedDishIds` sind Grundgerichte und schließen die ganze Gerichtsfamilie aus. `seed` macht die Ziehung wiederholbar; ohne Seed würfelt das Backend.

Die Antwort enthält `suggestions` mit höchstens drei Einträgen und optional `explanation`. Jeder Vorschlag hat `dishId`, `variantId`, `name`, `dishName`, `variantName`, `reason`, `effort`, `mealPrep` und `homeOffice`. Keine Sterne, keine Benutzerkennungen und kein Score. Unbekannte oder inaktive Mitessende antworten mit `400`. Eine fehlende Mahlzeit ebenfalls. Weniger als drei passende Familien liefern weniger Vorschläge und einen Erklärungssatz. Optional kann `referenceDate` als `YYYY-MM-DD` mitgeschickt werden. Ohne dieses Feld gilt der lokale Kalendertag der Installation.

Planung und Historie, für jede angemeldete Person. Der Schreibkörper enthält `mealDate`, `mealType`, `dishId`, optionales `variantId`, `dinerIds` und optionalen `comment`. Die Erstellerin kommt aus der Sitzung.

| Methode | Pfad | Erfolg |
| --- | --- | --- |
| `GET` | `/api/meal-plans?from&to` | geplante Mahlzeiten im Zeitraum |
| `POST` | `/api/meal-plans` | `201` und die Planung |
| `PUT` | `/api/meal-plans/{id}` | geänderte oder verschobene Planung |
| `DELETE` | `/api/meal-plans/{id}` | `204` |
| `POST` | `/api/meal-plans/{id}/eaten` | dieselbe Zeile, jetzt `EATEN` |
| `GET` | `/api/meal-history?from&to&dinerId` | gegessene Mahlzeiten, neueste zuerst; `dinerId` ist optional |
| `POST` | `/api/meal-history` | `201`, auch ohne vorherige Planung |
| `PUT` | `/api/meal-history/{id}` | korrigierter Historieneintrag |
| `DELETE` | `/api/meal-history/{id}` | `204` |

Ein falscher Status, zum Beispiel eine schon gegessene Mahlzeit noch einmal bestätigen, antwortet mit `409`. Unbekannte Gerichte mit `404`, eine Variante eines anderen Gerichts und unbekannte oder inaktive neue Mitessende mit `400`. Deaktivierte Personen, die schon Mitessende sind, dürfen beim Korrigieren bleiben. Antworten nennen den Snapshot-Namen auch dann, wenn `dishId` leer ist.

Eignungswerte eines Grundgerichts sind `UNKNOWN`, `SUITABLE` und `UNSUITABLE`. Eine Variante sendet `INHERIT`, `SUITABLE` oder `UNSUITABLE`. In `resolved` steht der wirksame Wert und ob er übernommen wurde. Die Oberfläche zeigt `resolved` an und schickt die gespeicherten Felder beim Speichern zurück.

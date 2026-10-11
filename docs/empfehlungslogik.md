# Empfehlungslogik

Iteration 5 erzeugt bis zu drei Vorschläge regelbasiert im Backend. Iteration 6 ergänzt die Kochhistorie und bereits geplante Mahlzeiten. Iteration 7 kann die Kandidaten vorher auf Katalogbegriffe einschränken. Die KI verändert die Gewichte nicht. Zutatenbestände bleiben ein späterer Score-Faktor. Konkrete Gewichte sind Startparameter und können nach Alltagserfahrung angepasst werden.

## Ablauf

1. **Kandidaten.** Alle Grundgerichte mit der gewählten Mahlzeit und ihre Varianten. Die Mahlzeit ist ein harter Filter. Ein Gericht ohne diese Mahlzeit fällt weg. Grundgericht und Variante sind getrennte Kandidaten. Fehlende optionale Eigenschaften einer Variante werden wie in der Gerichteverwaltung vom Grundgericht übernommen. Katalogbegriffe des Assistenten sind ein weiterer harter Filter vor der Gewichtung: ein Suchtreffer auf Gericht, Kategorie oder Grundzutat behält die Familie, ein Treffer nur auf eine Variante behält diese Variante. Ein Ausschluss entfernt die Familie. Ohne Treffer gibt es keine Vorschläge. Die Abgleichregeln stehen in [`ki-assistent.md`](ki-assistent.md).
2. **Gewichtung.** Persönliche Sterne der ausgewählten Mitessenden, ein zusätzlicher Malus für Ein-Stern-Bewertungen, ein Bonus für übereinstimmende positive Bewertungen, ein kleiner Bonus für völlig unbewertete Kandidaten, weiche Kontextpräferenzen, eine Wiederholungsstrafe und eine Planungsstrafe.
3. **Auswahl.** Gewichtete Zufallsauswahl ohne doppelte Gerichtsfamilie. Eine Familie ist das Grundgericht einschließlich seiner Varianten. Innerhalb der Familie bestimmt das höchste Auswahlgewicht, wie oft die Familie erscheint. Welches Mitglied gezeigt wird, entscheidet eine zweite gewichtete Ziehung.
4. **Begründung.** Ein kurzer Satz aus den aggregierten Signalen. Keine Namen, keine Sterne, keine Einzelbewertungen.

Ausgeschlossene Gerichtsfamilien werden vor der Ziehung entfernt. Ein optionaler Seed macht dieselbe Ziehung wiederholbar. Ohne Seed würfelt das Backend neu.

## Sterne

| Bewertung | Beitrag |
| --- | ---: |
| 1 Stern | −8 |
| 2 Sterne | −3 |
| 3 Sterne | 0 |
| 4 Sterne | +3 |
| 5 Sterne | +5 |
| Nicht bewertet | 0, ohne Signal |

Die Skala ist absichtlich unsymmetrisch: zwei Personen mit 5 Sternen und eine Person mit 1 Stern ergeben `(5 + 5 − 8) / 3 = 2/3`. Der arithmetische Sternedurchschnitt wäre 3,67 und würde das Gericht zu weit oben einsortieren.

## Score

`n` ist die Anzahl der ausgewählten Mitessenden. Jede Person liefert höchstens ein Signal.

- Eigene Bewertung des Kandidaten: der Beitrag aus der Tabelle.
- Variante ohne eigene Bewertung, Grundgericht bewertet: halber Beitrag der Grundgerichtsbewertung. Das ist ein schwächeres Signal, keine übernommene Sternezahl.
- Eine gesetzte Variantenbewertung ersetzt das Grundgericht vollständig, auch wenn sie neutral oder schlechter ist.
- Sonst: 0. Eine fehlende Bewertung ist damit nicht dasselbe wie eine explizite Drei.

```
Präferenz   = Summe der Beiträge / n
Ablehnung   = 6 × Anzahl expliziter Ein-Stern-Bewertungen / n
Einigung    = 1,5, wenn mindestens zwei Personen 4 oder 5 Sterne gegeben haben und niemand 1 Stern
Erkundung   = 1, wenn niemand ein Signal hat, sonst 0
Kontext     = Summe der weichen Präferenzen
Wiederholung = zeitabhängige Essensstrafe, höchstens 12
Planung     = Strafe für schon geplante Mahlzeiten, höchstens 6
Score       = Präferenz − Ablehnung + Einigung + Erkundung + Kontext + Wiederholung + Planung
Gewicht     = max(0,25, Score + 12)
```

Die Division durch `n` hält die Präferenz über unterschiedlich große Runden vergleichbar. Ein einzelner 5-Sterne-Esser liegt bei 5. Drei Personen mit je 5 Sternen liegen bei 6,5, nicht bei 15. Der Einigungsbonus belohnt nur übereinstimmende Zustimmung und entfällt, sobald jemand 1 Stern gegeben hat.

Beispiel mit drei Personen, 5 / 5 / 1:

```
Präferenz = 2/3
Ablehnung = 2
Score     = −4/3
```

Ein völlig unbewertetes Gericht liegt bei 1 und damit darüber. Ein Gericht mit drei 4-Sterne-Bewertungen liegt bei 4,5. Die Ein-Stern-Bewertung schließt nicht aus: das Auswahlgewicht bleibt mindestens 0,25, erscheint aber selten.

## Weiche Präferenzen

Nur gesetzte Wünsche verändern den Score. „Nicht geprüft“ ist weder geeignet noch ungeeignet und gibt 0.

| Wunsch | Passend | Neutral | Entgegen |
| --- | ---: | ---: | ---: |
| Einfach oder schnell | einfach +1,2 | mittel +0,2 | aufwendig −0,8 |
| Homeoffice | geeignet +1,2 | nicht geprüft 0 | nicht geeignet −1 |
| Meal Prep | geeignet +1,2 | nicht geprüft 0 | nicht geeignet −1 |

Diese Werte schieben die Ziehung, sie filtern nicht. Ein unpassendes Gericht bleibt Kandidat, solange es die Mahlzeit trifft. Bei wenigen Gerichten entstehen dadurch keine leeren Ergebnisse.

## Wiederholung und Planung

Das Referenzdatum ist `referenceDate` aus der Anfrage oder sonst der lokale Kalendertag der Installation (`Europe/Berlin`, über `APP_TIME_ZONE` änderbar). Ein UTC-Tageswechsel verschiebt den Tag damit nicht von allein. Gegessene und geplante Mahlzeiten der nächsten Fenster lädt eine Abfrage. Gerichte ohne gültige `dish_id` zählen nicht mit.

Nur `EATEN` mit Mahlzeitdatum zwischen Referenz minus 14 Tage und dem Referenzdatum erzeugt die Wiederholungsstrafe. Pro Ereignis:

```
Anteil      = Anzahl der ausgewählten Mitessenden, die dabei waren / Anzahl der ausgewählten Mitessenden
Variante    = 1,0 für dasselbe Ziel, sonst 0,5
Beitrag     = Basis × Variante × Anteil
Wiederholung = −min(12, Summe der Beiträge)
```

Dasselbe Ziel ist das Grundgericht ohne Variante oder genau dieselbe Variante. Eine andere Variante derselben Familie erhält den Faktor 0,5. Jedes Essensereignis zählt einmal, nicht zusätzlich als Familie. Eine gelöschte Variante, von der nur der Name übrig ist, ist kein exaktes Grundgericht und bleibt beim Faktor 0,5.

| Letztes Essen | Basis |
| --- | ---: |
| Heute | 8 |
| Gestern | 6 |
| Vor 2–3 Tagen | 4 |
| Vor 4–7 Tagen | 2 |
| Vor 8–14 Tagen | 1 |
| Älter oder in der Zukunft | 0 |

Mehrere Essen im Fenster addieren sich. Die Summe ist bei 12 gedeckelt, damit häufiges Essen stärker wiegt als ein einzelnes, aber nicht unbegrenzt. Wer gestern allein Pizza gegessen hat und heute mit einer weiteren Person isst, löst nur den halben Anteil aus. Wer nicht dabei war, löst für dieses Ereignis nichts aus.

Nur `PLANNED` zwischen dem Referenzdatum und sieben Tage danach erzeugt die Planungsstrafe. Dieselbe Anteils- und Variantenregel, andere Basis, Deckel 6:

| Geplant | Basis |
| --- | ---: |
| Am Referenzdatum | 4 |
| Morgen | 3 |
| In 2–3 Tagen | 2 |
| In 4–7 Tagen | 1 |
| Danach oder davor | 0 |

Eine gelöschte Planung fehlt in der Abfrage und straft nicht mehr. Eine bestätigte Mahlzeit ist nur noch `EATEN` und wird nicht gleichzeitig als Planung gezählt. Das Mindestgewicht 0,25 bleibt: eine Wiederholung kann weiterhin gezogen werden, wenn wenig anderes passt.

Liegt eine Wiederholungsstrafe vor, lautet die Begründung „Kürzlich gegessen und deshalb seltener vorgeschlagen.“ Eine Planungsstrafe ohne Wiederholungsstrafe nennt „Schon für die nächsten Tage eingeplant.“ Sonst gelten die bisherigen Sätze.

## Begründungen

Die erste passende Formulierung wird angezeigt:

- Niemand hat ein Signal: „Noch kaum bewertet und deshalb eine Chance wert.“
- Präferenz mindestens 2,5 und kein Ein-Stern: „Passt gut zu den Vorlieben der ausgewählten Personen.“
- Mindestens zwei positive Bewertungen und kein Ein-Stern: „Wird von mehreren Mitessenden gerne gegessen.“
- Mindestens eine Ein-Stern-Bewertung in einer Gruppe: „Eine mögliche Alternative, auch wenn die Vorlieben auseinandergehen.“
- Gewünschtes einfaches Essen und Aufwand einfach: „Eine unkomplizierte Alternative mit geringem Aufwand.“
- Gewünschtes Homeoffice und geeignet: „Gut geeignet fürs Homeoffice.“
- Gewünschtes Meal Prep und geeignet: „Lässt sich gut vorbereiten.“
- Aufwand einfach: „Eine abwechslungsreiche Alternative mit geringem Aufwand.“
- Sonst: „Eine abwechslungsreiche Alternative aus der Sammlung.“

## Grenzen

- Keine Allergien oder anderen harten Ausschlüsse außer Mahlzeit und vom Benutzer ausgeschlossenen Familien.
- Keine Allergieerkennung aus Gerichtsnamen. Zutaten zählen nur, wenn sie gespeichert sind.
- Die KI setzt Kriterien, sie berechnet keine Rangfolge.
- Vorschläge werden nicht gespeichert und gelten nicht als gegessen.
- Die Begründung nennt keine Person und keine Sternezahl. Wer die Formel kennt, kann aus der Formulierung trotzdem grobe Gruppen ableiten, zum Beispiel dass mindestens zwei Personen ein Gericht mögen.
- Bei weniger als drei Gerichtsfamilien gibt es weniger Vorschläge und einen erklärenden Satz. Es werden keine Gerichte erfunden und keine Familie doppelt gezeigt.

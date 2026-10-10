# Empfehlungslogik

Iteration 5 erzeugt bis zu drei Vorschläge regelbasiert im Backend. Historie, Planung, Zutatenbestände, natürlichsprachliche Wünsche und KI sind vorbereitet als spätere Score-Faktoren, aber noch nicht umgesetzt. Konkrete Gewichte sind Startparameter und können nach Alltagserfahrung angepasst werden.

## Ablauf

1. **Kandidaten.** Alle Grundgerichte mit der gewählten Mahlzeit und ihre Varianten. Die Mahlzeit ist ein harter Filter. Ein Gericht ohne diese Mahlzeit fällt weg. Grundgericht und Variante sind getrennte Kandidaten. Fehlende optionale Eigenschaften einer Variante werden wie in der Gerichteverwaltung vom Grundgericht übernommen.
2. **Gewichtung.** Persönliche Sterne der ausgewählten Mitessenden, ein zusätzlicher Malus für Ein-Stern-Bewertungen, ein Bonus für übereinstimmende positive Bewertungen, ein kleiner Bonus für völlig unbewertete Kandidaten und weiche Kontextpräferenzen.
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
Score       = Präferenz − Ablehnung + Einigung + Erkundung + Kontext
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

- Keine Kochhistorie, keine Wiederholungsvermeidung und keine geplanten Mahlzeiten.
- Keine Allergien oder anderen harten Ausschlüsse außer Mahlzeit und vom Benutzer ausgeschlossenen Familien.
- Keine Zutaten, keine natürlichsprachlichen Wünsche, keine KI.
- Vorschläge werden nicht gespeichert und gelten nicht als gegessen.
- Die Begründung nennt keine Person und keine Sternezahl. Wer die Formel kennt, kann aus der Formulierung trotzdem grobe Gruppen ableiten, zum Beispiel dass mindestens zwei Personen ein Gericht mögen.
- Bei weniger als drei Gerichtsfamilien gibt es weniger Vorschläge und einen erklärenden Satz. Es werden keine Gerichte erfunden und keine Familie doppelt gezeigt.

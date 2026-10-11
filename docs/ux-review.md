# UX-Bewertung – Iteration 8

Stand: 11.10.2026. Bewertet wurde die Oberfläche nach Iteration 7, also mit Gerichten, Anmeldung, Bewertungen, regelbasierten Vorschlägen, Wochenplan, Historie und optionalem Assistenten. Compose-Dateien liegen im Projektroot. Diese Iteration ändert keine Fachendpunkte.

## 1. Identifizierte UX-Probleme

Die Startseite hat die Frage „Was essen wir?“ gestellt, aber nicht beantwortet, was heute schon geplant ist. Oben stand der Assistent, darunter das vollständige Vorschlagsformular, darunter eine Statuszeile „Backend erreichbar“. Für den Alltag war das die falsche Reihenfolge: Infrastruktur und die optionale KI vor der eigentlichen Entscheidung.

Eine Vorschlagsrunde brauchte das Formular immer vollständig. Mahlzeit, jede mitessende Person und drei Präferenzen lagen als hohe Button-Stapel offen, obwohl Abendessen und die angemeldete Person die üblichen Werte sind. Die Vorschlagskarte hatte vier ähnlich große Aktionen: Gericht öffnen, Einplanen, als gegessen eintragen, ausschließen.

Der Wochenplan zeigte sieben Tage mit leeren Slots „Noch offen“ und je einem Einplanen-Button, plus drei volle Buttons für den Wochenwechsel. Eine geplante Mahlzeit ließ sich nur über Bearbeiten im Datum ändern. „Verschieben“ war keine eigene, kurze Aktion.

Gegessenes nachtragen begann in der Historie, nicht auf der Startseite. Im Dialog wurden Datum, Mahlzeit, die ganze Gerichtsliste, Mitessende und der Kommentar erneut gezeigt, auch wenn Gericht und Mahlzeit von der Vorschlagskarte schon feststanden.

Die Kopfzeile zeigte Anzeigename, für Administrierende „Familie“ und „Abmelden“. Zusammen mit der Leiste Start, Plan, Historie, Gerichte, Profil waren Verwaltung und Abmelden so präsent wie die tägliche Entscheidung. Die Leiste hatte keine Icons, der aktive Bereich war nur über die MUI-Auswahlklasse erkennbar, und Profil verdrängte keinen seltenen Bereich zugunsten eines „Mehr“.

Die Gerichtsliste war funktional, der Name aber in einem Rahmenbutton zusammen mit den Metadaten. Die Suche löste richtigerweise erst mit „Suchen“ eine Anfrage aus. Leere Zustände bei Gerichten und Historie nannten das Problem, die nächste Aktion lag aber oberhalb der Filter und damit weit vom Leertext entfernt.

Dialoge waren auf kleinen Breiten schon fullscreen. Der Kommentar war trotzdem immer offen und gleichrangig mit dem Gericht. Erfolg nach dem Speichern war nur am verschwundenen Dialog zu erkennen. Die Scrollposition des inneren `main`-Bereichs wurde beim Seitenwechsel nicht gemerkt.

Bei 320 CSS-Pixeln waren gestapelte Vollbreiten-Buttons bedienbar, aber der erste Bildschirm reichte nicht für Heute, Vorschläge und eine Karte. Horizontales Scrollen der Seite war bereits unterbunden. Die Daumenreichweite litt an der Menge, nicht an zu kleinen Zielen: Buttons sind mindestens 48 CSS-Pixel hoch.

## 2. Gewählte Verbesserungen

Die Startseite heißt „Was essen wir heute?“ und zeigt das Datum, die heutigen Planungen mit „Als gegessen bestätigen“ und bei leerem Tag den kurzen Text „Heute ist noch nichts geplant.“ Darunter liegen drei Schnellaktionen: Gegessenes eintragen, Gericht hinzufügen, Wochenplan ansehen. Sie stehen vor dem Formular, damit sie ohne Scrollen erreichbar sind.

Vorschläge starten mit Mahlzeit, Mitessenden und dem Button „Was essen wir?“. Standard sind Abendessen, das heutige Datum beim Übernehmen und die angemeldete Person. Einfach, Homeoffice und Meal Prep liegen zugeklappt unter „Weitere Einstellungen“. Während einer neuen Berechnung verschwinden die alten Karten. Ein laufender Request blockiert einen zweiten Tipp. Ein Fehler bietet „Erneut versuchen“ und behält die Eingaben.

Auf der Karte ist Einplanen die gefüllte Hauptaktion, „Als gegessen eintragen“ die zweite. Gericht öffnen und Ausschluss sind Textaktionen. Nach dem Speichern bleibt die Ansicht stehen und zeigt eine kurze Erfolgsmeldung. Bekanntes Gericht, Variante, Datum, Mahlzeit und Mitessende werden in den Dialog übernommen und nicht noch einmal abgefragt. Der Kommentar ist zugeklappt, bis jemand ihn öffnet.

Der Wochenplan ist eine Tagesliste der aktuellen Woche. Heute hat einen Rahmen, eine Fläche und das Wort „Heute“. Der Wochenwechsel ist eine Zeile. Eine Mahlzeit kann bestätigt, bearbeitet, verschoben oder gelöscht werden. Löschen und das Bestätigen bleiben bestätigt. Verschieben öffnet denselben Dialog mit dem Datum zuerst und speichert über die vorhandene Aktualisierung.

Die Gerichte zeigen den Namen groß und die Varianten als eigenen Text. Die Suche bleibt ein expliziter Request. Bewertungen sitzen direkt an der Karte. Leere Gerichte- und Historienzustände bieten eine Anlage- beziehungsweise Nachtragen-Aktion. Reste vom Vortag sind ein weiterer Historieneintrag. Die Historie sagt das in einem Satz. Es gibt keine Resteverwaltung.

Ladezustände sind Zeilen mit Text, keine Vollbild-Spinner. Fehler bleiben verständlich und ohne Stacktrace. Speicher-Buttons von Gericht und Variante kleben am unteren Rand des scrollenden Bereichs. Der Mahlzeit-Dialog ist unterhalb des MUI-Breakpoints `sm` weiterhin fullscreen, die Aktionen bleiben am Dialogrand. Felder rücken beim Fokussieren in den sichtbaren Bereich. `interactive-widget=resizes-content` und `viewport-fit=cover` berücksichtigen Tastatur und Safe Area.

## 3. Begründung der Navigation

Die fünf Ziele sind **Heute, Plan, Gerichte, Historie, Mehr**.

Ein eigener Punkt „Vorschläge“ würde die Startseite doppeln. Die Vorschläge sind die Antwort auf „Was essen wir heute?“ und bleiben dort. Historie bleibt ein primäres Ziel, weil Nachtragen und Nachsehen zum Alltag gehören und nicht in „Mehr“ verschwinden sollen. Profil, Familie und Abmelden sind selten und liegen unter Mehr. Die Kopfzeile zeigt nur noch den Namen und führt ebenfalls zu Mehr. Während eines erzwungenen Passwortwechsels, wenn die Leiste fehlt, liegt Abmelden auf dieser Seite.

Die Leiste hat höchstens fünf Punkte, Icons plus Beschriftung, und der aktive Punkt ist über `aria`-Zustand, Schriftstärke und die MUI-Auswahl markiert, nicht nur über Farbe. Sie liegt im Flex-Layout und überdeckt den Inhalt nicht. Unter `md` steht sie unten, darüber zwischen Kopfzeile und Inhalt, damit eine breite Desktop-Ansicht nicht eine Leiste am Fensterrand erzwingt. Safe Area ist als unterer Innenabstand auf schmalen Breiten berücksichtigt.

## 4. Vorher-Nachher

| Ablauf | Vorher | Nachher |
| --- | --- | --- |
| Heute entscheiden | Startseite ohne Tagesplan, zuerst Assistent oder volles Formular | Datum und heutige Planung, sonst ein kurzer Leerzustand, dann Vorschläge |
| Vorschläge | Mahlzeit, alle Personen und drei Schalter immer offen, Button „Vorschläge anzeigen“ | Abendessen und eigene Person vorausgewählt, Schalter zugeklappt, ein Tipp auf „Was essen wir?“ |
| Vorschlag einplanen | Dialog mit Suche, Liste, Kommentar, obwohl Gericht schon feststand | Gericht, Variante, Datum, Mahlzeit und Mitessende sind gesetzt. Speichern bleibt auf der Seite und meldet „Eingeplant.“ |
| Geplantes als gegessen | Nur im Wochenplan, vier gleich große Aktionen | Auf der Startseite „Als gegessen bestätigen“, im Plan dieselbe Hauptaktion plus Verschieben |
| Direkt nachtragen | Historie öffnen, „Nachtragen“, alles neu wählen | „Gegessenes eintragen“ auf der Startseite, Datum heute, Abendessen, eigene Person |
| Nächste Tage | Volle Wochenbuttons und leere Slots für jede Mahlzeit | Eine Wochenzeile, Tage als Karten, heute beschriftet, nur vorhandene Mahlzeiten |
| Essenswunsch | Assistent über dem Formular, immer gleich laut | Nur wenn KI aktiv ist, unter den Vorschlägen. Ohne KI bleibt die Auswahl vollständig |
| Gericht suchen und bewerten | Suche per Button, Name und Metadaten in einem Rahmen | Name groß, Varianten lesbar, Bewertung an der Karte, weiter ein Request pro Suche |
| Abmelden und Familie | Immer in der Kopfzeile | Unter Mehr |

## 5. Verbleibende Schwächen

Die Historienfilter bleiben ein langes Formular. Sie werden bewusst nicht bei jedem Tippen abgeschickt. Wer nur die letzten Einträge sehen will, scrollt an Datum und Personen vorbei. Der obere Button „Nachtragen“ und die Aktion im Leerzustand sagen dasselbe.

Verschieben öffnet den bestehenden Dialog und kein eigenes Datumsblatt. Das ist ein zusätzlicher Schritt, vermeidet aber eine zweite Speicherlogik.

Die Bottom Navigation auf sehr schmalen Breiten teilt sich fünf Beschriftungen. „Gerichte“ und „Historie“ bleiben einzeilig, haben aber wenig Luft.

Ein Vollbild-Dialog verdeckt die Navigation, solange er offen ist. Abbrechen oder Speichern schließt ihn. Die Bildschirmtastatur kann auf einzelnen Browsern trotz `interactive-widget` ein Feld schieben. Die Felder rücken dann in die Mitte, der Speichern-Button sitzt am Dialogrand.

Die Scrollposition wird pro Pfad im inneren `main` gemerkt, aber nicht über einen Reload hinaus. Es gibt keine Virtualisierung der Gerichtsliste. Bei einer Familiengröße ist das nicht nötig. Eine State-Bibliothek wurde nicht eingeführt.

Reste erzeugen keinen Hinweis und keine Portionsplanung. Das ist Absicht.

## 6. Testergebnisse der mobilen Ansichten

Frontend-Tests prüfen Startseite, Navigation, Standardwerte, Schnellaktionen, Vorschläge inklusive Ausblenden alter Karten, Einplanung mit übernommenem Gericht, Verschieben, Kommentar erst auf Wunsch, KI an und aus sowie die Suche ohne Request pro Tastendruck.

Playwright `frontend/e2e/z-mobile-ux.spec.ts` läuft nach den bestehenden End-to-End-Tests und durchläuft Anmeldung, Startseite, Vorschläge, Einplanen, Plan, Bestätigen, direktes Nachtragen, Gerichtssuche, Bewertung und eine KI-Anfrage gegen den Fixture-Provider. Dieselben Seiten werden bei 320, 375, 430 und 1280 CSS-Pixeln auf horizontales Überlaufen, erreichbare Navigation und einen sichtbaren Speichern-Button im Dialog geprüft. Die älteren End-to-End-Tests bleiben bei 320 CSS-Pixeln und nutzen dieselben Abläufe mit den neuen Beschriftungen.

Am 11.10.2026 lief die Playwright-Suite gegen den Compose-Stack (`mobile-chromium`, Pixel 5) mit 6 bestandenen Tests: Familie, Mahlzeiten, Bewertungen, regelbasierte Vorschläge, Assistent und `z-mobile-ux`. Der Alltagstest prüft die genannten Abläufe und danach dieselben Seiten bei 320, 375, 430 und 1280 CSS-Pixeln: kein horizontales Überlaufen, Navigation sichtbar, Speichern im Dialog im Viewport. Die Assistenten-Anfrage nennt das angelegte Gericht über „wie …“, weil die Ziehung höchstens drei Gerichte gewichtet auswählt und ein voller Katalog das neueste Gericht sonst nicht sicher enthält.

Die Vitest-Suite des Frontends (53 Tests), `tsc`, Lint und der Produktionsbuild waren grün. Backend-Tests liefen aus dem Gradle-Cache grün; diese Iteration ändert keinen Java-Quelltext. Der Build warnt vor einem JavaScript-Chunk über 500 kB (etwa 623 kB, gzip etwa 188 kB). Das kommt von MUI im einen Bundle. Es wurde keine weitere Bibliothek und kein Code-Splitting ergänzt.

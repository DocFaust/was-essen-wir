# Fachkonzept – Was essen wir?

**Version:** 1.4 · **Stand:** 10.10.2026 · **Status:** Konsolidierte fachliche Basis für die Umsetzung

Technische Festlegungen zu Authentifizierung, Betrieb und Stack stehen in [`architektur.md`](architektur.md). Der Umsetzungsstand steht in [`roadmap.md`](roadmap.md). Iteration 2 ergänzt die Gerichteverwaltung. Anmeldung, Bewertungen, Vorschläge, Planung und KI folgen später.

## 1. Ziel und Abgrenzung

Eine für eine einzelne Familie pro Installation bestimmte, mobil nutzbare Webanwendung beantwortet die Frage „Was essen wir?“ auf Basis gemeinsam gepflegter Gerichte, persönlicher Vorlieben, aktueller Mitessender und bisheriger Entscheidungen. Ziel ist eine schnelle, alltagstaugliche Entscheidung, keine vollumfängliche Rezept- oder Warenwirtschaft.

**Leitlinien:** Bekanntes zuerst; gelegentlich neue Ideen; geringer Erfassungsaufwand; individuelle Mitwirkung; transparente, nachvollziehbare Empfehlungen; KI optional für die Grundfunktion; kein täglicher Planungszwang.

**Nicht im Kernumfang:** Multi-Tenancy, öffentliche Registrierung, Vorratshaltung, Zutatenmengen, Kalorien/Nährwerte, automatische Einkaufslisten, verpflichtende Rezepte, Kochzeit-Erfassung.

## 2. Benutzer und Rechte

- Eine Familie (Single-Family), mehrere persönliche Benutzerprofile.
- Alle angemeldeten Mitglieder dürfen Grundgerichte und Varianten anlegen, Stammdaten bearbeiten, Vorschläge starten, Mahlzeiten planen und Abstimmungen initiieren.
- Jedes Mitglied bewertet Gerichte und Varianten selbst mit 1–5 Sternen; andere Mitglieder dürfen diese Bewertungen ansehen, aber nicht ändern.
- Eine Administrationsrolle verwaltet Benutzer und grundlegende Einstellungen.
- Keine öffentliche Selbstregistrierung; Zugriff nur nach Anmeldung.
- Authentifizierung mit Spring Security, lokaler Benutzerverwaltung und serverseitigen Sessions. Keine Keycloak-, OAuth2- oder separate Authentifizierungsinfrastruktur.
- Benutzer und Familienmitglieder werden zur Laufzeit angelegt und nicht im Quellcode hinterlegt.

## 3. Gerichte, Varianten und Zutaten

- **Grundgericht:** Pflichtfeld Name; optional Beschreibung, Mahlzeitentypen, Kategorien, Aufwand, Zutaten, Rezepttext, Rezeptlink, Rezeptquelle und Kommentare.
- **Rezeptquelle:** optionaler Freitext, keine Auswahlliste und keine eigene Entität. Beispiele: Eigenkreation, Thermomix / Cookidoo, Rezeptbuch „Bayerische Küche“, Seite 42, Chefkoch, Von Oma.
- **Variante:** Optionales Kind eines Grundgerichts, z. B. Pizza → Salami/Margherita. Varianten können abweichende Eigenschaften und eigene Bewertungen haben. Die Vererbung optionaler Eigenschaften steht in Abschnitt 3.3.
- Bewertungen von Grundgericht und Variante sind getrennt; keine automatische Überschreibung oder implizite Gleichsetzung. Für noch nicht bewertete Varianten kann das System die Bewertung des Grundgerichts als schwächeren Hinweis nutzen (zu validierende Standardregel).
- **Aufwand:** optional `EINFACH`, `MITTEL`, `AUFWENDIG`; keine Pflicht zur Zeitangabe.
- **Zutaten:** optionale Liste von Zutatennamen pro Gericht/Variante; keine Mengen oder Lagerbestände. Nicht dokumentierte Zutaten bedeuten *unbekannt*, nicht *frei davon*.
- Für neue Gerichte ist ausschließlich der Name verpflichtend; Duplikatwarnung statt stiller Mehrfachanlage.

### 3.1 Verwendungskontexte: Meal Prep und Homeoffice

- Gerichte und optional Varianten können mit mehreren **Eignungsmerkmalen** versehen werden: `MEAL_PREP`, `HOMEOFFICE`. Diese sind unabhängig von Mahlzeitentypen und Aufwand.
- **Meal Prep für eine Person:** Vorschläge berücksichtigen deren persönliche Bewertungen, die Mahlzeit und die Meal-Prep-Eignung. Bei Bedarf kann eine Mahlzeit für einen zukünftigen Tag geplant werden. Eine Portion für eine Person ist möglich, ohne dass die gesamte Familie mitisst.
- **Homeoffice-Essen für eine Person:** Vorschläge berücksichtigen deren Bewertungen und bevorzugen auf Wunsch einfache, homeofficegeeignete Gerichte.
- Fehlende Eignungsmerkmale bedeuten **nicht geprüft**, nicht automatisch **ungeeignet**. Das UI soll dies unterscheiden können.
- Optionaler späterer Ausbau: `KALT_ESSBAR` und `AUFWAERMBAR` als zusätzliche Merkmale. Noch keine verbindliche Anforderung.
- Ein Gericht kann gleichzeitig für beide Kontexte geeignet sein; Varianten dürfen eine abweichende Eignung haben.
- **Nicht festgelegt** und **ausdrücklich nicht geeignet** sind verschiedene Angaben. Nur eine gesetzte Angabe geht in Filter und spätere Vorschläge ein.

### 3.2 Meal-Prep-Planung (verbindlich)

Zwei gleichberechtigte Abläufe werden unterstützt:

1. **Gezielt vorbereiten:** Eine eigenständige Mahlzeit für eine bestimmte Person und einen zukünftigen Verzehrtag planen.
2. **Zusätzliche Portion:** Beim Planen oder Erfassen eines gemeinsamen Kochvorgangs eine oder mehrere Portionen für spätere Mahlzeiten vorsehen.

Eine vorbereitete Portion referenziert optional den ursprünglichen Kochvorgang, hat eine Zielperson, ein geplantes Verzehrdatum und einen Status (geplant, vorbereitet, verzehrt oder verworfen). Einfache Portionsanzahl ist zulässig, jedoch keine Zutatenmengen, Lager- oder Vorratsverwaltung. Optional kann markiert werden, ob ein Gericht kalt essbar oder aufzuwärmen ist; eine nicht gepflegte Angabe bleibt unbekannt. Dass etwas vorbereitet wurde, bedeutet nicht automatisch, dass es gegessen wurde.

### 3.3 Vererbung von Varianten

Eine Variante überschreibt nur Eigenschaften, die bei ihr gesetzt sind. Nicht gesetzte optionale Eigenschaften werden vom Grundgericht übernommen. Mahlzeitentypen, Kategorien und Kommentare gehören nur zum Grundgericht.

| Eigenschaft | Nicht gesetzt bei der Variante | Gesetzt bei der Variante |
| --- | --- | --- |
| Beschreibung, Aufwand, Rezepttext, Rezeptlink, Rezeptquelle | Wert des Grundgerichts | Eigener, nicht leerer Wert |
| Zutaten | Liste des Grundgerichts | Eigene Liste, die auch leer sein darf |
| Meal Prep, Homeoffice | Eignung des Grundgerichts: nicht geprüft, geeignet oder nicht geeignet | Eigene Eignung, einschließlich ausdrücklich nicht geeignet |

Ein leeres Textfeld ist nicht gesetzt und wird nicht gespeichert. Deshalb zeigt eine Variante ohne eigene Rezeptquelle die Quelle des Grundgerichts. Bei Zutaten und Eignungsmerkmalen bleibt der Unterschied zwischen „nicht festgelegt“ und einem gesetzten Wert erhalten, auch wenn die eigene Zutatenliste leer oder die Eignung ausdrücklich verneint ist. API und Oberfläche verwenden dieselbe, serverseitig aufgelöste Darstellung.

## 4. Persönliche Bewertungen

- Genau eine aktuelle Bewertung pro Benutzer und bewertbarem Objekt (Grundgericht oder Variante); Werte 1–5 oder *nicht bewertet*.
- Bedeutung: 1 = mag ich überhaupt nicht; 2 = ungern; 3 = okay; 4 = gerne; 5 = Lieblingsessen.
- Niedrige Bewertungen reduzieren die Empfehlungswahrscheinlichkeit deutlich, schließen aber nicht automatisch aus.
- Bewertungen werden niemals automatisch aus dem Verhalten verändert.
- Eigene Bewertungen dürfen direkt per UI oder eindeutiger KI-Anweisung geändert werden.

## 5. Mahlzeiten, Anwesenheit und Planung

- Mahlzeitentypen: Frühstück, Mittagessen, Abendessen, Snack.
- Bei einer Anfrage werden die tatsächlich Mitessenden ausgewählt; ihre Bewertungen und persönlichen Regeln sind relevant. Die zuletzt verwendete Auswahl kann als Komfortvorgabe dienen.
- Kein täglicher Planungszwang. Ein optionaler Wochenkalender erlaubt einzelne Einträge für beliebige Tage/Mahlzeiten.
- Status unterscheiden: vorgeschlagen, geplant, tatsächlich gegessen. Planänderungen bleiben möglich.
- Kochhistorie erfasst Datum, Mahlzeitentyp, Grundgericht, gegebenenfalls Varianten und Teilnehmende. Geplantes Essen gilt nicht automatisch als gegessen.

## 6. Empfehlungen

### 6.1 Ablauf
1. Mahlzeit, Mitessende und optionale aktuelle Wünsche erfassen.
2. Kandidaten aus der Sammlung ermitteln und harte Ausschlüsse prüfen (z. B. bestätigte Allergien/Unverträglichkeiten).
3. Kandidaten anhand von Bewertungen, Verteilung zwischen Mitessenden, jüngster Kochhistorie, Aufwand, Familien-/Personenregeln und behutsamem Auswahlverhalten bewerten.
4. Drei unterschiedliche Vorschläge präsentieren; Neuwürfeln möglich. Gelegentlich zusätzlich einen klar markierten Entdeckervorschlag zeigen.
5. Direkt wählen oder optional eine Abstimmung starten.
6. Gewählte Mahlzeit optional planen und später explizit als gegessen markieren.

### 6.2 Fairness und Lernen
- Nicht nur Mittelwert: Eine extrem schlechte Bewertung eines Mitessenden soll stärker zählen als ein einfacher Durchschnitt suggeriert.
- Wiederholungen derselben Gerichte und Kategorien reduzieren; nicht starr die Top-3 anzeigen.
- Ein nicht ausgewählter Vorschlag ist **keine** ausdrückliche Ablehnung.
- Wiederholt gewählte Gerichte können einen kleinen Bonus erhalten; ausdrückliche wiederholte Ablehnungen einen kleinen, zeitlich abklingenden Malus.
- Das System verändert niemals Sternebewertungen automatisch. Lernsignale müssen nachvollziehbar und bei Bedarf zurücksetzbar sein.
- Gewichtungen sind Startparameter und werden erst nach Test mit realistischen Beispieldaten festgelegt.

### 6.3 Neue Gerichte
- Gelegentlich darf die KI neue, als *neu* gekennzeichnete Gerichte empfehlen, passend zu bekannten Vorlieben und Aufwand.
- Neue Vorschläge werden nicht automatisch Teil der Sammlung; Übernahme nur nach aktiver Entscheidung.
- Bei fehlenden oder unklaren Informationen keine falschen Aussagen zu Inhaltsstoffen/Allergiesicherheit.

## 7. Abstimmungen

- Eine Abstimmung ist optional und enthält typischerweise 2–5 Kandidaten.
- Mitessende geben ihre aktuelle Präferenz ab; diese ist unabhängig von der langfristigen Sternebewertung.
- Ergebnisse unterstützen die Entscheidung; keine erzwungene automatische Auswahl.
- Abstimmungsmodalität (eine Stimme oder Rangfolge), Gleichstand und Frist sind noch im UX-Entwurf festzulegen; MVP-Standard: eine Stimme pro Person, Gleichstand manuell entscheiden.

## 8. Regeln

- **Familienregeln:** für gemeinsame Empfehlungen, z. B. werktags eher einfach.
- **Persönliche Regeln:** gelten bei Anwesenheit der Person, z. B. Pilze unerwünscht.
- **Situative Wünsche:** gelten standardmäßig nur für die aktuelle Anfrage, z. B. heute keine Nudeln.
- Strukturierte Speicherung mit Typ, Geltungsbereich, Priorität, optionalem Zeitbezug und Originaltext.
- Sicherheitsrelevante Ausschlüsse werden strikt behandelt; unbekannte Zutaten werden nicht als sicher interpretiert.
- Dauerhafte Familienregeländerungen benötigen eine Bestätigung.

## 9. KI-Integration und Schreibrechte

- KI direkt in bestehende Oberflächen integriert; kein Chat-Zwang.
- OpenAI API als Cloud-LLM; API-Schlüssel ausschließlich serverseitig. Modell und Provider über Konfiguration austauschbar halten.
- KI interpretiert natürliche Sprache und ruft nur explizit freigegebene Backend-Aktionen auf; keine direkte Datenbankverbindung.
- **Direkt ausführbar:** eindeutiges neues Gericht, eigene Bewertung, einfache Zutaten-/Kommentarergänzung, eindeutige persönliche Regel.
- **Bestätigung erforderlich:** Löschen, Zusammenführen, größere Massenänderungen, Änderungen gemeinsamer Regeln, mehrdeutige oder folgenschwere Aktionen.
- **Unzulässig:** Bewertungen anderer Personen ohne deren eigene authentifizierte Aktion ändern.
- Änderungen protokollieren; für einfache Aktionen möglichst Undo bereitstellen.
- KI-Ausfälle dürfen klassische Gerichteverwaltung und algorithmische Vorschläge nicht blockieren.
- Sparsame Datenübertragung: nur erforderliche Gerichtsdaten, Bewertungen und Regeln an die API senden; keine unnötigen personenbezogenen Informationen.

## 10. Hauptansichten

1. **Startseite:** Mahlzeit, Mitessende, optionaler Freitextwunsch, Vorschlagsaktion.
2. **Vorschläge:** drei Kandidaten, Begründungen, Varianten, neu würfeln, wählen, abstimmen.
3. **Gerichtesammlung:** Suche/Filter, Anlage/Bearbeitung, Varianten, Zutaten, persönliche Bewertungen.
4. **Wochenkalender:** optional befüllbare Mahlzeitenslots, Status und Änderungen.
5. **Historie:** tatsächlich gegessene Mahlzeiten, Filter nach Datum/Person/Mahlzeit.
6. **Familie & Regeln:** Profile, eigene Bewertungen, persönliche und gemeinsame Regeln.
7. **KI-Eingabe:** kontextabhängig in relevanten Ansichten.
8. **Kontext-Schnellauswahl:** „Meal Prep für eine Person“ und „Homeoffice für ein Familienmitglied“ als mobil gut erreichbare Vorschlagsfilter.

## 11. Fachliche Datenobjekte (konzeptionell)

`User`, `Dish`, `DishVariant`, `DishUsageTag`, `Ingredient`, `DishIngredient`, `Rating`, `PreferenceRule`, `MealPlan`, `MealHistory`, `MealParticipant`, `SuggestionSession`, `DecisionEvent`, `Vote`, `AuditEvent`.

Anforderungen an die technische Modellierung: Variantenvererbung mit klarer Überschreibungslogik; Bewertungsziel eindeutig (Gericht oder Variante); Nachvollziehbarkeit von Vorschlags-/Abstimmungsereignissen; konsistente Rechteprüfung; Zeitstempel und Soft-Delete für sensible Löschvorgänge prüfen.

## 12. Nichtfunktionale Anforderungen

- **Mobile first ist eine verbindliche Designvorgabe**, nicht nur responsive Nachrüstung: Smartphone-Ansichten werden zuerst entworfen, implementiert und getestet; Desktop folgt als Erweiterung.
- PWA mit auf Smartphones gut erreichbarer Navigation, großen Touch-Zielen, kurzen Formularen, responsiven Dialogen und ohne Hover-Abhängigkeiten.
- Die Kernaktion „Essen vorschlagen“ sowie Bewertungen, Anwesenheit und Kontextfilter müssen einhändig und mit wenigen Interaktionen bedienbar sein.
- Gute Nutzbarkeit bei typischen Smartphone-Breiten (ca. 320–430 CSS-Pixel), Hochformat; Tests auf Android-Mobilbrowsern und Desktop.
- Kein Offline-Zwang für die erste Version; Offline-Fähigkeit ist gesondert zu entscheiden.
- Betrieb auf einem Raspberry Pi 5 (`linux/arm64`) per Docker Compose, im privaten Heimnetz. Keine öffentliche Internetfreigabe.
- Backend Spring Boot / Java 25, Frontend React / MUI, PostgreSQL, Schema-Migrationen mit Flyway.
- Zugriff nach Anmeldung ausschließlich im privaten Heimnetz.
- Konfigurierbare API-Ausgabenlimits/Budgetüberwachung; keine API-Schlüssel im Frontend.
- Regelmäßige Datenbanksicherung, dokumentierte Wiederherstellung.
- Nachvollziehbare Empfehlungsergebnisse, Tests für Berechtigungen, Varianten und Ausschlussregeln.

## 13. Umsetzungsetappen

**MVP:** Login/Profile, Gerichte und Varianten, Bewertungen, Mahlzeitentypen, Aufwand, Zutaten, Anwesenheit, Verwendungskontexte (Meal Prep/Homeoffice), regelbasierte drei Vorschläge, Auswahl, Historie; mobile-first umgesetzt.

**Ausbaustufe 2:** OpenAI-Integration, natürlichsprachliche Eingaben, kontrollierte Schreibaktionen, Familien-/Personenregeln, gelegentliche neue Ideen.

**Ausbaustufe 3:** Optionale Abstimmungen, Wochenkalender, behutsames Lernen und verfeinerte Erklärbarkeit. Die Reihenfolge kann nach MVP-Tests angepasst werden.

## 14. Akzeptanzkriterien (Auswahl)

- Ein Familienmitglied kann in weniger als einer Minute ein neues Gericht allein mit Namen anlegen und bewerten.
- Ein Gericht kann mehrere Varianten mit unabhängig gepflegten Bewertungen besitzen.
- Die Auswahl der Mitessenden beeinflusst die Vorschläge; Bewertungen Abwesender fließen nicht ein.
- Ein-Stern-Bewertungen senken die Priorität, schließen nicht pauschal aus.
- Die Anwendung zeigt auch ohne KI-Verbindung Vorschläge aus vorhandenen Gerichten.
- Eine KI kann eine eigene Bewertung auf eindeutige Anweisung ändern, aber keine fremde Bewertung.
- Ein ungeplanter Tag im Kalender ist gültig und löst keine Fehlermeldung aus.
- Die Kochhistorie wird nur durch Bestätigung einer tatsächlich gegessenen Mahlzeit ergänzt.
- Neue KI-Ideen sind klar gekennzeichnet und werden nicht ohne Übernahme gespeichert.
- „Meal Prep für eine Person“ liefert auf ihre Vorlieben zugeschnittene Vorschläge ohne notwendige Anwesenheit anderer Familienmitglieder.
- „Homeoffice für ein Familienmitglied“ berücksichtigt dessen Kontext und kann einfache Gerichte priorisieren.
- Grundgerichte und Varianten können mehrere Eignungsmerkmale haben; fehlende Merkmale werden nicht als negative Eignungsbewertung interpretiert.
- Alle Kernfunktionen sind auf schmalen Smartphone-Displays ohne horizontales Scrollen und ohne Hover-Bedienung nutzbar.

## 15. Noch zu präzisierende Details vor Implementierung

- Exakte Bewertung/Vererbung bei unbewerteten Varianten.
- Konkrete Gewichtungen, Wiederholungsfenster und Fairnessregeln des Empfehlungsalgorithmus.
- Definition und sichere Behandlung von Allergien und harten Ausschlüssen.
- Abstimmungsfristen, Gleichstand und Benachrichtigungen.
- Sichtbarkeit/Änderungsrechte für persönliche Regeln und Kommentare.
- Datenschutz- und Aufbewahrungsdauer für KI-Anfragen und Audit-Logs.
- Optionaler Ausbau der Meal-Prep-Eignung um kalt essbar/aufwärmbar und gegebenenfalls Portionsplanung.

**Fazit:** Dieses Dokument ist die konsolidierte fachliche Basis, kein bereits verabschiedetes technisches Detaildesign. Offene Details sind ausdrücklich markiert und dürfen nicht stillschweigend als verbindliche Produktentscheidungen interpretiert werden.

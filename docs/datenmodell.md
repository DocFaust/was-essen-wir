# Datenmodell – fachlicher Entwurf

Kandidaten: `User`, `Dish`, `DishVariant`, `Ingredient`, `DishIngredient`, `Rating`, `PreferenceRule`, `MealPlan`, `MealHistory`, `MealParticipant`, `SuggestionSession`, `DecisionEvent`, `Vote`, `AuditEvent`.

Zusätzlich `PreparedPortion` (oder äquivalentes Planungsobjekt) mit Empfänger, Verzehrdatum, Status und optionaler Referenz auf den ursprünglichen Kochvorgang. Unterstützt sowohl gezieltes Vorbereiten als auch zusätzliche Portionen beim Familienessen. Keine Lagerbestandsverwaltung.

Eignung für Meal Prep und Homeoffice ist unabhängig vom Mahlzeitentyp; fehlende Kennzeichnung bedeutet unbekannt. Aufwärm-/Kaltverzehr-Eignung kann optional gespeichert werden.

Bewertungen von Gerichten und Varianten sind personengebunden; nicht bewertet ist nicht gleich drei Sterne. Das relationale Schema wird vor Implementierung konkretisiert.

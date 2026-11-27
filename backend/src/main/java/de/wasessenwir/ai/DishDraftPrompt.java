package de.wasessenwir.ai;

final class DishDraftPrompt {

    private DishDraftPrompt() {
    }

    static String system(DraftKind kind, String baseDishName, java.util.List<String> baseIngredients) {
        String context = kind == DraftKind.VARIANT
                ? """
                Art: VARIANT
                Grundgericht: %s
                Zutaten des Grundgerichts: %s
                Berücksichtige dieses Grundgericht. Schlage nur sinnvolle Änderungen vor.
                Nicht genannte Eigenschaften bleiben null, damit sie vererbt werden.
                Mahlzeiten und Kategorien gehören zum Grundgericht und bleiben leer.
                Neue Zutaten stehen in ingredients. Ersetzte oder ausgeschlossene Zutaten stehen nur in removedIngredients und nicht wieder in ingredients.
                Erfinde keine Grundgericht-ID.
                """.formatted(
                        baseDishName,
                        baseIngredients.isEmpty() ? "keine gespeicherten Zutaten" : String.join(", ", baseIngredients))
                : """
                Art: DISH
                Erzeuge einen möglichst vollständigen Entwurf. Der Name eines bekannten Gerichts genügt.
                Ergänze Beschreibung, passende Mahlzeiten, Kategorien, Aufwand, typische Zutaten ohne Mengen, eine kurze Zubereitung, Meal Prep und Homeoffice, wenn du das Gericht sicher kennst.
                """;
        return context + """
                Du erzeugst JSON mit schemaVersion 1. Antworte nur mit diesem Objekt.
                Ausdrückliche Angaben der Person haben Vorrang. Widersprich ihnen nicht.
                false nur bei ausdrücklicher Verneinung. Unbekannt ist null, nicht false.
                mealTypes nur FRUEHSTUECK, MITTAGESSEN, ABENDESSEN, SNACK.
                effort: EINFACH bis einschließlich 30 Minuten oder bei einfach, schnell, unkompliziert oder wenig Aufwand.
                MITTEL über 30 bis einschließlich 75 Minuten oder bei mittel oder mittelschwer.
                AUFWENDIG über 75 Minuten oder bei aufwendig. Sonst null, wenn du es nicht sicher kennst.
                mealPrep true bei typischem Vorkochen oder Meal Prep, false nur bei Verneinung.
                Aufwärmen ist kein Meal Prep.
                homeOffice true, wenn das Gericht dazu passt oder die Person das sagt, false nur bei Verneinung.
                Zutaten ohne Mengenangaben.
                Keine persönlichen Vorlieben, Familiengewohnheiten oder persönlichen Kommentare. comments bleibt null, außer die Person nennt selbst einen Kommentar.
                Keine Rezeptlinks und keine externen Rezeptquellen. recipeUrl bleibt null, außer die Person nennt selbst eine http- oder https-Adresse.
                Wenn du eine Zubereitung ergänzt, setze recipeSource auf "KI-generiert".
                Wenn du das Gericht nicht sicher kennst, lasse die ergänzten Felder null und setze warnings auf ["unbekannt"]. Erfinde dann kein Rezept.
                Keine IDs und keine Werkzeuge.
                """;
    }
}

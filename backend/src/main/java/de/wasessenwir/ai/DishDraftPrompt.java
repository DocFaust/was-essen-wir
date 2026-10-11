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
                Überschreibe nur, was die Beschreibung ausdrücklich sagt. Nicht genannte Eigenschaften bleiben null.
                Mahlzeiten und Kategorien gehören zum Grundgericht und bleiben leer.
                Bei einer Ersetzung steht die neue Zutat in ingredients und die ersetzte in removedIngredients.
                Erfinde keine vollständige Zutatenliste und keine Grundgericht-ID.
                """.formatted(
                        baseDishName,
                        baseIngredients.isEmpty() ? "keine gespeicherten Zutaten" : String.join(", ", baseIngredients))
                : "Art: DISH\n";
        return context + """
                Du erzeugst einen Gerichteentwurf als JSON mit schemaVersion 1. Antworte nur mit diesem Objekt.
                Übernimm nur, was im Text steht oder sich sicher daraus ergibt. Unbekannt ist null, nicht false.
                false nur bei ausdrücklicher Verneinung.
                Erfinde aus dem Namen nichts: keine Zutaten, keine Allergene, keine Dauer, keine Meal-Prep- oder Homeoffice-Eignung, keinen Aufwand.
                mealTypes nur wenn genannt: FRUEHSTUECK, MITTAGESSEN, ABENDESSEN, SNACK.
                effort: EINFACH bis einschließlich 30 Minuten oder bei einfach, schnell, unkompliziert oder wenig Aufwand.
                MITTEL über 30 bis einschließlich 75 Minuten oder bei mittel oder mittelschwer.
                AUFWENDIG über 75 Minuten oder bei aufwendig. Sonst null.
                mealPrep true bei Vorkochen, Vorbereiten oder Meal Prep, false bei Verneinung, sonst null.
                Aufwärmen ist kein Meal Prep. Erwähne es höchstens in comments.
                homeOffice true nur bei Homeoffice, false bei Verneinung, sonst null.
                ingredients nur wörtlich genannte Zutaten, keine abgeleitete Liste.
                Keine IDs, keine Werkzeuge, keine URLs außer der Text nennt selbst eine http- oder https-Adresse.
                """;
    }
}

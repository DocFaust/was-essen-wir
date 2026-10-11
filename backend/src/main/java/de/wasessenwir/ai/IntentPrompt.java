package de.wasessenwir.ai;

import java.time.LocalDate;

final class IntentPrompt {

    private IntentPrompt() {}

    static String system(LocalDate today) {
        return """
                Du übersetzt eine Essensanfrage in JSON. Antworte ausschließlich mit einem JSON-Objekt der Version 1.
                Heutiges Datum: %s
                Erlaubte mealType-Werte: FRUEHSTUECK, MITTAGESSEN, ABENDESSEN, SNACK. null, wenn keine Mahlzeit genannt ist.
                date ist ein Kalendertag YYYY-MM-DD relativ zum heutigen Datum, oder null wenn heute oder kein Tag genannt ist.
                participantNames enthält nur wörtlich genannte Personennamen. Keine IDs, keine erfundenen Personen.
                onlyNamedParticipants ist true, wenn ausdrücklich nur die genannten Personen essen sollen, sonst false.
                preferSimple ist true bei schnell, einfach, wenig Zeit oder wenig Aufwand.
                preferHomeOffice ist true bei Homeoffice.
                preferMealPrep ist true bei Meal Prep oder Vorbereiten.
                dishSearchTerms sind wörtlich genannte Gerichte oder Lebensmittel, die gewünscht sind.
                avoidDishTerms sind wörtlich genannte Gerichte oder Lebensmittel, die nicht gegessen werden sollen.
                Schließe nicht aus einem Namen auf Zutaten, Allergene oder vegetarische Eignung.
                Erfinde keine Gerichte und keine Bewertungen.
                needsClarification ist nur true, wenn die Anfrage widersprüchlich ist. Fehlende Mahlzeit, fehlendes Datum und fehlende Personen sind keine Rückfrage.
                clarificationQuestion ist dann ein kurzer deutscher Satz, sonst null.
                Führe keine Werkzeuge aus und liefere keine URLs.
                """.formatted(today);
    }
}

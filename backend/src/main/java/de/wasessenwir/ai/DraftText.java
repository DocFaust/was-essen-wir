package de.wasessenwir.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DraftText {

    static final Set<String> STOP = Set.of(
            "mit", "und", "oder", "auf", "vom", "von", "der", "die", "das", "den", "dem",
            "ein", "eine", "einer", "einem", "einen", "fuer", "für", "zum", "zur", "bei",
            "aus", "im", "in", "am", "uns", "wir", "ist", "sind", "auch", "noch", "gut",
            "gerne", "oft", "manchmal", "bitte", "nicht", "kein", "keine");

    private DraftText() {
    }

    static String fold(String value) {
        return TextMatch.fold(value);
    }

    static List<String> significant(String value) {
        List<String> tokens = new ArrayList<>();
        for (String token : TextMatch.tokens(fold(value))) {
            if (token.length() >= 3 && !STOP.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    static boolean mentioned(String foldedHaystack, String needle) {
        String folded = fold(needle);
        if (folded.length() < 3) {
            return false;
        }
        return TextMatch.containsTerm(foldedHaystack, folded)
                || TextMatch.containsTerm(foldedHaystack, folded + "s");
    }

    static int wordIndex(String folded, String word) {
        String needle = fold(word);
        int from = 0;
        while (from <= folded.length() - needle.length()) {
            int index = folded.indexOf(needle, from);
            if (index < 0) {
                return -1;
            }
            boolean left = index == 0 || !Character.isLetter(folded.charAt(index - 1));
            int end = index + needle.length();
            boolean right = end == folded.length() || !Character.isLetter(folded.charAt(end));
            if (left && right) {
                return index;
            }
            from = index + 1;
        }
        return -1;
    }

    static boolean negated(String folded, int index) {
        if (index < 0) {
            return false;
        }
        String before = folded.substring(Math.max(0, index - 28), index);
        return before.contains("nicht") || before.contains("kein") || before.contains("schlecht");
    }

    static String cleanPhrase(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").replaceAll("[,:;]+$", "").trim();
        cleaned = cleaned.replaceFirst("(?iu)^(ein|eine|einen|der|die|das)\\s+", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned;
    }

    static String firstSentence(String text) {
        String trimmed = text.trim();
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (current == '!' || current == '?') {
                return trimmed.substring(0, index).trim();
            }
            if (current == '.' && !abbreviation(trimmed, index)) {
                return trimmed.substring(0, index).trim();
            }
        }
        return trimmed;
    }

    static String label(String key) {
        return switch (key) {
            case "name" -> "Name";
            case "description" -> "Beschreibung";
            case "mealTypes" -> "Mahlzeiten";
            case "categories" -> "Kategorien";
            case "effort" -> "Aufwand";
            case "ingredients" -> "Zutaten";
            case "recipeText" -> "Rezept";
            case "recipeUrl" -> "Rezeptlink";
            case "recipeSource" -> "Rezeptquelle";
            case "comments" -> "Kommentar";
            case "mealPrep" -> "Meal Prep";
            case "homeOffice" -> "Homeoffice";
            default -> key;
        };
    }

    private static boolean abbreviation(String text, int period) {
        int start = Math.max(0, period - 2);
        String before = text.substring(start, period).toLowerCase(Locale.GERMAN);
        return before.equals("ca") || before.equals("z.");
    }
}

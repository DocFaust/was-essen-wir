package de.wasessenwir.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TextMatch {

    private TextMatch() {}

    static String fold(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.GERMAN).trim();
    }

    static boolean containsTerm(String foldedHaystack, String foldedNeedle) {
        if (foldedNeedle.length() < 2 || foldedHaystack.isEmpty()) {
            return false;
        }
        int from = 0;
        while (from <= foldedHaystack.length() - foldedNeedle.length()) {
            int index = foldedHaystack.indexOf(foldedNeedle, from);
            if (index < 0) {
                return false;
            }
            boolean left = index == 0 || !Character.isLetter(foldedHaystack.charAt(index - 1));
            int end = index + foldedNeedle.length();
            boolean right = end == foldedHaystack.length() || !Character.isLetter(foldedHaystack.charAt(end));
            if (left && right) {
                return true;
            }
            from = index + 1;
        }
        return false;
    }

    static List<String> tokens(String folded) {
        List<String> tokens = new ArrayList<>();
        for (String part : folded.split("[^\\p{L}\\p{N}]+")) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        return tokens;
    }
}

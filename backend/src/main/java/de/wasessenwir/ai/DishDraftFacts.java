package de.wasessenwir.ai;

import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.MealType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DishDraftFacts {

    private static final Pattern LEG = Pattern.compile("(?iu)^(?:bitte\\s+)?leg(?:e)?\\s+(.+?)\\s+an\\s*$");
    private static final Pattern EAT = Pattern.compile(
            "(?iu)^wir\\s+essen\\s+(?:(?:gerne|oft|manchmal|heute)\\s+)*(.+)$");
    private static final Pattern COOK = Pattern.compile(
            "(?iu)^wir\\s+(?:machen|kochen)\\s+(?:(?:gerne|oft|manchmal|uns|heute)\\s+)*(.+)$");
    private static final Pattern INSTEAD = Pattern.compile("(?iu)mit\\s+(.+?)\\s+statt\\s+([^.,;]+)");
    private static final Pattern ALSO_WITH = Pattern.compile("(?iu)auch\\s+mit\\s+([^.,;]+)");
    private static final Pattern INGREDIENT_LIST = Pattern.compile("(?iu)zutaten\\s*:\\s*([^.]*)");
    private static final Pattern FROM_LIST = Pattern.compile("(?iu)\\baus\\s+([^.]{0,160})");
    private static final Pattern SOURCE = Pattern.compile("(?iu)(?:rezeptquelle|quelle)\\s*:\\s*([^.]{1,200})");
    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+");
    private static final int MAX_NAME = 120;

    private final String name;
    private final String description;
    private final List<MealType> mealTypes;
    private final List<String> categories;
    private final Effort effort;
    private final List<String> ingredients;
    private final List<String> removedIngredients;
    private final boolean ingredientsMentioned;
    private final String recipeText;
    private final String recipeUrl;
    private final String recipeSource;
    private final String comments;
    private final Boolean mealPrep;
    private final Boolean homeOffice;
    private final List<String> warnings;

    private DishDraftFacts(
            String name,
            String description,
            List<MealType> mealTypes,
            List<String> categories,
            Effort effort,
            List<String> ingredients,
            List<String> removedIngredients,
            boolean ingredientsMentioned,
            String recipeText,
            String recipeUrl,
            String recipeSource,
            String comments,
            Boolean mealPrep,
            Boolean homeOffice,
            List<String> warnings) {
        this.name = name;
        this.description = description;
        this.mealTypes = mealTypes;
        this.categories = categories;
        this.effort = effort;
        this.ingredients = ingredients;
        this.removedIngredients = removedIngredients;
        this.ingredientsMentioned = ingredientsMentioned;
        this.recipeText = recipeText;
        this.recipeUrl = recipeUrl;
        this.recipeSource = recipeSource;
        this.comments = comments;
        this.mealPrep = mealPrep;
        this.homeOffice = homeOffice;
        this.warnings = warnings;
    }

    static DishDraftFacts read(String description, DraftKind kind) {
        String folded = DraftText.fold(description);
        List<String> warnings = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        boolean mentioned = collectIngredients(description, added, removed);
        String name = kind == DraftKind.VARIANT ? variantName(description, added) : dishName(description);
        List<MealType> meals = mealTypes(folded);
        if (kind == DraftKind.VARIANT && !meals.isEmpty()) {
            warnings.add("Mahlzeiten bleiben beim Grundgericht und werden nicht überschrieben.");
            meals = List.of();
        }
        List<String> categories = kind == DraftKind.DISH ? categories(folded) : List.of();
        Effort effort = effort(folded, warnings);
        Boolean mealPrep = flag(folded, List.of("meal prep", "mealprep", "meal-prep", "vorkochen", "vorbereiten"));
        Boolean homeOffice = flag(folded, List.of("home office", "home-office", "homeoffice"));
        String comments = null;
        if (folded.contains("aufwärm") || folded.contains("aufwaerm") || folded.contains("aufwarm")) {
            comments = "Lässt sich aufwärmen.";
            warnings.add("Aufwärmen hat kein eigenes Feld und steht nur als Kommentar im Entwurf.");
        }
        String source = source(description);
        String url = url(description);
        if (name != null && name.length() > MAX_NAME) {
            name = null;
        }
        return new DishDraftFacts(
                name,
                null,
                meals,
                categories,
                effort,
                List.copyOf(added),
                List.copyOf(removed),
                mentioned,
                null,
                url,
                source,
                comments,
                mealPrep,
                homeOffice,
                List.copyOf(warnings));
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    List<MealType> mealTypes() {
        return mealTypes;
    }

    List<String> categories() {
        return categories;
    }

    Effort effort() {
        return effort;
    }

    List<String> ingredients() {
        return ingredients;
    }

    List<String> removedIngredients() {
        return removedIngredients;
    }

    boolean ingredientsMentioned() {
        return ingredientsMentioned;
    }

    String recipeText() {
        return recipeText;
    }

    String recipeUrl() {
        return recipeUrl;
    }

    String recipeSource() {
        return recipeSource;
    }

    String comments() {
        return comments;
    }

    Boolean mealPrep() {
        return mealPrep;
    }

    Boolean homeOffice() {
        return homeOffice;
    }

    List<String> warnings() {
        return warnings;
    }

    DishDraftFacts withName(String name) {
        return new DishDraftFacts(
                name,
                description,
                mealTypes,
                categories,
                effort,
                ingredients,
                removedIngredients,
                ingredientsMentioned,
                recipeText,
                recipeUrl,
                recipeSource,
                comments,
                mealPrep,
                homeOffice,
                warnings);
    }

    DishDraftFacts enrich(String description, String recipeText, String recipeUrl, String recipeSource, String comments, List<String> extraWarnings) {
        List<String> merged = new ArrayList<>(warnings);
        for (String warning : extraWarnings) {
            if (!merged.contains(warning)) {
                merged.add(warning);
            }
        }
        return new DishDraftFacts(
                name,
                description == null ? this.description : description,
                mealTypes,
                categories,
                effort,
                ingredients,
                removedIngredients,
                ingredientsMentioned,
                recipeText == null ? this.recipeText : recipeText,
                recipeUrl == null ? this.recipeUrl : recipeUrl,
                recipeSource == null ? this.recipeSource : recipeSource,
                comments == null ? this.comments : comments,
                mealPrep,
                homeOffice,
                List.copyOf(merged));
    }

    private static String dishName(String description) {
        for (String raw : description.split("[.!?\\n]")) {
            String sentence = raw.trim();
            if (sentence.isEmpty() || meta(sentence)) {
                continue;
            }
            String name = dishNameFromSentence(sentence);
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    private static boolean meta(String sentence) {
        String folded = DraftText.fold(sentence);
        return folded.startsWith("dauert")
                || folded.startsWith("ist ")
                || folded.startsWith("eignet")
                || folded.startsWith("fürs")
                || folded.startsWith("fuers")
                || folded.startsWith("nicht für")
                || folded.startsWith("nicht fuer")
                || folded.startsWith("das ist");
    }

    private static String dishNameFromSentence(String sentence) {
        Matcher leg = LEG.matcher(sentence);
        if (leg.matches()) {
            return limit(DraftText.cleanPhrase(leg.group(1)));
        }
        Matcher eat = EAT.matcher(sentence);
        if (eat.matches()) {
            return limit(DraftText.cleanPhrase(eat.group(1)));
        }
        Matcher cook = COOK.matcher(sentence);
        if (cook.matches()) {
            return limit(DraftText.cleanPhrase(cook.group(1)));
        }
        if (sentence.split("\\s+").length <= 4 && DraftText.significant(sentence).size() >= 1
                && !sentence.matches("(?iu)^(leg|lege|erstelle|erzeuge|mach|mache|bitte)\\b.*")) {
            return limit(DraftText.cleanPhrase(sentence));
        }
        return null;
    }

    private static String variantName(String description, List<String> added) {
        if (!added.isEmpty()) {
            return limit("Mit " + String.join(" und ", added));
        }
        String folded = DraftText.fold(description);
        if (folded.contains("vegetarisch") && !DraftText.negated(folded, folded.indexOf("vegetarisch"))) {
            return "Vegetarisch";
        }
        if (folded.contains("schärfer") || folded.contains("schaerfer") || DraftText.wordIndex(folded, "scharf") >= 0) {
            return "Scharf";
        }
        return null;
    }

    private static boolean collectIngredients(String description, List<String> added, List<String> removed) {
        boolean mentioned = false;
        Matcher instead = INSTEAD.matcher(description);
        if (instead.find()) {
            added.addAll(splitList(instead.group(1)));
            removed.addAll(splitList(instead.group(2)));
            mentioned = true;
        }
        Matcher also = ALSO_WITH.matcher(description);
        if (also.find()) {
            added.addAll(splitList(also.group(1)));
            mentioned = true;
        }
        Matcher listed = INGREDIENT_LIST.matcher(description);
        if (listed.find()) {
            added.addAll(splitList(listed.group(1)));
            mentioned = true;
        }
        Matcher from = FROM_LIST.matcher(description);
        if (from.find()) {
            String phrase = from.group(1);
            if (phrase.contains(" und ") || phrase.contains(",")) {
                added.addAll(splitList(phrase));
                mentioned = true;
            }
        }
        LinkedHashSet<String> uniqueAdded = new LinkedHashSet<>();
        for (String item : added) {
            if (item != null && !item.isBlank()) {
                uniqueAdded.add(item);
            }
        }
        added.clear();
        added.addAll(uniqueAdded);
        LinkedHashSet<String> uniqueRemoved = new LinkedHashSet<>();
        for (String item : removed) {
            if (item != null && !item.isBlank()) {
                uniqueRemoved.add(item);
            }
        }
        removed.clear();
        removed.addAll(uniqueRemoved);
        return mentioned;
    }

    private static List<String> splitList(String phrase) {
        String[] parts = phrase.split("(?iu)\\s*(?:,| und )\\s*");
        List<String> items = new ArrayList<>();
        for (String part : parts) {
            String cleaned = DraftText.cleanPhrase(part);
            if (cleaned != null && cleaned.length() <= 120 && DraftText.significant(cleaned).size() >= 1) {
                items.add(cleaned);
            }
        }
        return items;
    }

    private static List<MealType> mealTypes(String folded) {
        List<MealType> types = new ArrayList<>();
        if (mentionedMeal(folded, "frühstück") || mentionedMeal(folded, "fruehstueck")) {
            types.add(MealType.FRUEHSTUECK);
        }
        if (mentionedMeal(folded, "mittagessen") || mentionedMeal(folded, "mittags") || mentionedMeal(folded, "mittag")) {
            types.add(MealType.MITTAGESSEN);
        }
        if (mentionedMeal(folded, "snack")) {
            types.add(MealType.SNACK);
        }
        if (mentionedMeal(folded, "abendessen") || mentionedMeal(folded, "abends") || mentionedMeal(folded, "abend")) {
            types.add(MealType.ABENDESSEN);
        }
        return List.copyOf(types);
    }

    private static boolean mentionedMeal(String folded, String word) {
        int index = DraftText.wordIndex(folded, word);
        return index >= 0 && !DraftText.negated(folded, index);
    }

    private static List<String> categories(String folded) {
        int index = folded.indexOf("vegetarisch");
        if (index >= 0 && !DraftText.negated(folded, index)) {
            return List.of("vegetarisch");
        }
        return List.of();
    }

    private static Effort effort(String folded, List<String> warnings) {
        Effort word = effortWord(folded);
        Integer minutes = minutes(folded);
        Effort fromTime = minutes == null ? null : fromMinutes(minutes);
        if (word != null) {
            if (fromTime != null && fromTime != word) {
                warnings.add("Zeitangabe und Aufwandswort weichen ab. Übernommen wird der genannte Aufwand.");
            }
            return word;
        }
        if (fromTime != null) {
            warnings.add("Die genannte Dauer entspricht dem Aufwand " + effortLabel(fromTime) + ".");
            return fromTime;
        }
        return null;
    }

    private static Effort effortWord(String folded) {
        record Candidate(String word, Effort effort) {
        }
        List<Candidate> candidates = List.of(
                new Candidate("aufwendig", Effort.AUFWENDIG),
                new Candidate("aufwändig", Effort.AUFWENDIG),
                new Candidate("aufwaendig", Effort.AUFWENDIG),
                new Candidate("mittelschwer", Effort.MITTEL),
                new Candidate("mittel", Effort.MITTEL),
                new Candidate("unkompliziert", Effort.EINFACH),
                new Candidate("einfach", Effort.EINFACH),
                new Candidate("schnell", Effort.EINFACH));
        Effort best = null;
        int bestAt = -1;
        for (Candidate candidate : candidates) {
            int index = DraftText.wordIndex(folded, candidate.word());
            if (candidate.word().equals("mittel") && DraftText.wordIndex(folded, "mittelschwer") >= 0) {
                continue;
            }
            if (index >= 0 && !DraftText.negated(folded, index) && index >= bestAt) {
                best = candidate.effort();
                bestAt = index;
            }
        }
        if (DraftText.wordIndex(folded, "wenig") >= 0 && folded.contains("aufwand")
                && !DraftText.negated(folded, DraftText.wordIndex(folded, "wenig"))) {
            return Effort.EINFACH;
        }
        return best;
    }

    static Integer minutes(String folded) {
        if (folded.contains("anderthalb stunde") || folded.contains("eineinhalb stunde")) {
            return 90;
        }
        if (folded.contains("halbe stunde")) {
            return 30;
        }
        if (folded.contains("eine stunde") || folded.contains("einer stunde")) {
            return 60;
        }
        Matcher hours = Pattern.compile("(\\d{1,2})\\s*stunden?").matcher(folded);
        if (hours.find()) {
            return Integer.parseInt(hours.group(1)) * 60;
        }
        Matcher mins = Pattern.compile("(\\d{1,3})\\s*(?:minuten|minute|min)\\b").matcher(folded);
        if (mins.find()) {
            return Integer.parseInt(mins.group(1));
        }
        return null;
    }

    static Effort fromMinutes(int minutes) {
        if (minutes <= 30) {
            return Effort.EINFACH;
        }
        if (minutes <= 75) {
            return Effort.MITTEL;
        }
        return Effort.AUFWENDIG;
    }

    private static String effortLabel(Effort effort) {
        return switch (effort) {
            case EINFACH -> "Einfach";
            case MITTEL -> "Mittel";
            case AUFWENDIG -> "Aufwendig";
        };
    }

    private static Boolean flag(String folded, List<String> words) {
        int found = -1;
        for (String word : words) {
            int index = word.contains(" ") ? folded.indexOf(word) : DraftText.wordIndex(folded, word);
            if (index >= 0 && (found < 0 || index < found)) {
                found = index;
            }
        }
        if (found < 0) {
            return null;
        }
        return !DraftText.negated(folded, found);
    }

    private static String source(String description) {
        Matcher matcher = SOURCE.matcher(description);
        if (!matcher.find()) {
            return null;
        }
        String value = DraftText.cleanPhrase(matcher.group(1));
        if (value == null || value.length() > 500) {
            return null;
        }
        return value;
    }

    private static String url(String description) {
        Matcher matcher = URL.matcher(description);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group().replaceAll("[,.)]+$", "");
        if (value.length() > 2_000) {
            return null;
        }
        return value;
    }

    private static String limit(String value) {
        if (value == null || value.length() > MAX_NAME) {
            return null;
        }
        return value.substring(0, 1).toUpperCase(Locale.GERMAN) + value.substring(1);
    }
}

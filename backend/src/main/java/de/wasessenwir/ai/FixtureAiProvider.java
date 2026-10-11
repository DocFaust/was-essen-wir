package de.wasessenwir.ai;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FixtureAiProvider implements AiProvider {

    private static final Pattern WITH_NAME = Pattern.compile("\\bmit\\s+(\\p{L}[\\p{L}\\-]{1,39})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern AVOID = Pattern.compile("\\b(?:ohne|keine|keinen|kein)\\s+(\\p{L}[\\p{L}\\-]{2,39})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SEARCH = Pattern.compile("\\b(?:auf|wie)\\s+(\\p{L}[\\p{L}\\-]{2,39})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> STOP = Set.of(
            "etwas", "dir", "uns", "mir", "dem", "der", "den", "die", "das", "einem", "einer",
            "ohne", "bitte", "heute", "morgen", "abend", "mittag", "und", "oder", "für");

    private final Clock clock;
    private final ObjectMapper mapper;

    FixtureAiProvider(Clock clock, ObjectMapper mapper) {
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    public String interpret(String systemPrompt, String userMessage) {
        String folded = TextMatch.fold(userMessage);
        ObjectNode body = mapper.createObjectNode();
        body.put("version", 1);
        String mealType = mealType(folded);
        if (mealType == null) {
            body.putNull("mealType");
        } else {
            body.put("mealType", mealType);
        }
        LocalDate date = date(folded);
        if (date == null) {
            body.putNull("date");
        } else {
            body.put("date", date.toString());
        }
        var names = body.putArray("participantNames");
        for (String name : names(userMessage)) {
            names.add(name);
        }
        body.put("onlyNamedParticipants", false);
        body.put("preferSimple", prefersSimple(folded));
        body.put("preferHomeOffice", folded.contains("homeoffice"));
        body.put("preferMealPrep", folded.contains("meal prep") || folded.contains("mealprep") || folded.contains("vorbereiten"));
        var search = body.putArray("dishSearchTerms");
        for (String term : terms(SEARCH, userMessage)) {
            search.add(term);
        }
        var avoid = body.putArray("avoidDishTerms");
        for (String term : terms(AVOID, userMessage)) {
            avoid.add(term);
        }
        body.put("needsClarification", false);
        body.putNull("clarificationQuestion");
        return mapper.writeValueAsString(body);
    }

    @Override
    public String draft(String systemPrompt, String userMessage) {
        DraftKind kind = systemPrompt != null && systemPrompt.contains("Art: VARIANT") ? DraftKind.VARIANT : DraftKind.DISH;
        return DishDraftJson.write(mapper, DishDraftFacts.read(userMessage, kind));
    }

    private LocalDate date(String folded) {
        LocalDate today = LocalDate.now(clock);
        if (folded.contains("übermorgen")) {
            return today.plusDays(2);
        }
        if (folded.contains("morgen")) {
            return today.plusDays(1);
        }
        return null;
    }

    private static String mealType(String folded) {
        if (folded.contains("frühstück") || folded.contains("fruehstueck")) {
            return "FRUEHSTUECK";
        }
        if (folded.contains("mittag")) {
            return "MITTAGESSEN";
        }
        if (folded.contains("snack")) {
            return "SNACK";
        }
        if (folded.contains("abend")) {
            return "ABENDESSEN";
        }
        return null;
    }

    private static boolean prefersSimple(String folded) {
        return folded.contains("schnell")
                || folded.contains("einfach")
                || folded.contains("wenig zeit")
                || folded.contains("nichts aufwendig")
                || folded.contains("nicht aufwendig")
                || folded.contains("unkompliziert");
    }

    private static List<String> names(String message) {
        List<String> names = new ArrayList<>();
        Matcher matcher = WITH_NAME.matcher(message);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!STOP.contains(name.toLowerCase(Locale.GERMAN))) {
                names.add(name);
            }
        }
        return names;
    }

    private static List<String> terms(Pattern pattern, String message) {
        List<String> terms = new ArrayList<>();
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String term = matcher.group(1);
            if (!STOP.contains(term.toLowerCase(Locale.GERMAN))) {
                terms.add(term);
            }
        }
        return terms;
    }
}

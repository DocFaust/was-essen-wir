package de.wasessenwir.dish;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.net.URI;

final class DishValidator {

    private DishValidator() {
    }

    static NormalizedDish dish(DishWriteRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Die Eingabe ist ungültig.");
        }
        return new NormalizedDish(
                requiredName(request.name()),
                optionalText(request.description(), 4_000, "Die Beschreibung darf höchstens 4000 Zeichen haben."),
                mealTypes(request.mealTypes()),
                names(request.categories(), 20, 80, "Es sind höchstens 20 Kategorien erlaubt.", "Ein Kategoriename darf höchstens 80 Zeichen haben."),
                request.effort(),
                names(request.ingredients(), 50, 120, "Es sind höchstens 50 Zutaten erlaubt.", "Ein Zutatenname darf höchstens 120 Zeichen haben."),
                optionalText(request.recipeText(), 20_000, "Der Rezepttext darf höchstens 20000 Zeichen haben."),
                url(request.recipeUrl()),
                optionalText(request.recipeSource(), 500, "Die Rezeptquelle darf höchstens 500 Zeichen haben."),
                optionalText(request.comments(), 4_000, "Der Kommentar darf höchstens 4000 Zeichen haben."),
                request.mealPrep() == null ? Suitability.UNKNOWN : request.mealPrep(),
                request.homeOffice() == null ? Suitability.UNKNOWN : request.homeOffice(),
                Boolean.TRUE.equals(request.confirmDuplicate()));
    }

    static NormalizedVariant variant(VariantWriteRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Die Eingabe ist ungültig.");
        }
        boolean ingredientsSpecified = Boolean.TRUE.equals(request.ingredientsSpecified());
        return new NormalizedVariant(
                requiredName(request.name()),
                optionalText(request.description(), 4_000, "Die Beschreibung darf höchstens 4000 Zeichen haben."),
                request.effort(),
                ingredientsSpecified,
                ingredientsSpecified
                        ? names(request.ingredients(), 50, 120, "Es sind höchstens 50 Zutaten erlaubt.", "Ein Zutatenname darf höchstens 120 Zeichen haben.")
                        : List.of(),
                optionalText(request.recipeText(), 20_000, "Der Rezepttext darf höchstens 20000 Zeichen haben."),
                url(request.recipeUrl()),
                optionalText(request.recipeSource(), 500, "Die Rezeptquelle darf höchstens 500 Zeichen haben."),
                request.mealPrep() == null ? SuitabilitySetting.INHERIT : request.mealPrep(),
                request.homeOffice() == null ? SuitabilitySetting.INHERIT : request.homeOffice());
    }

    private static String requiredName(String value) {
        String name = blankToNull(value);
        if (name == null) {
            throw new InvalidRequestException("Der Name ist erforderlich.");
        }
        if (name.length() > 120) {
            throw new InvalidRequestException("Der Name darf höchstens 120 Zeichen haben.");
        }
        return name;
    }

    private static String optionalText(String value, int maxLength, String tooLongMessage) {
        String text = blankToNull(value);
        if (text != null && text.length() > maxLength) {
            throw new InvalidRequestException(tooLongMessage);
        }
        return text;
    }

    private static String url(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        if (text.length() > 2_000) {
            throw new InvalidRequestException("Der Rezeptlink darf höchstens 2000 Zeichen haben.");
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Der Rezeptlink muss mit http:// oder https:// beginnen.");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || uri.getHost() == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new InvalidRequestException("Der Rezeptlink muss mit http:// oder https:// beginnen.");
        }
        return text;
    }

    private static List<MealType> mealTypes(List<MealType> values) {
        if (values == null) {
            return List.of();
        }
        List<MealType> unique = new ArrayList<>();
        for (MealType value : values) {
            if (value == null) {
                throw new InvalidRequestException("Die Eingabe ist ungültig.");
            }
            if (!unique.contains(value)) {
                unique.add(value);
            }
        }
        return List.copyOf(unique);
    }

    private static List<String> names(
            List<String> values,
            int maxCount,
            int maxLength,
            String tooManyMessage,
            String tooLongMessage) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > maxCount) {
            throw new InvalidRequestException(tooManyMessage);
        }
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null) {
                throw new InvalidRequestException("Die Eingabe ist ungültig.");
            }
            String name = blankToNull(value);
            if (name == null) {
                continue;
            }
            if (name.length() > maxLength) {
                throw new InvalidRequestException(tooLongMessage);
            }
            unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
        return List.copyOf(unique.values());
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

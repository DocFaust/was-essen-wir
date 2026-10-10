package de.wasessenwir.dish;

import java.util.List;

public final class DishInheritance {

    private DishInheritance() {
    }

    public static String text(String variantValue, String dishValue) {
        return variantValue == null ? dishValue : variantValue;
    }

    public static boolean inherited(String variantValue) {
        return variantValue == null;
    }

    public static Effort effort(Effort variantValue, Effort dishValue) {
        return variantValue == null ? dishValue : variantValue;
    }

    public static boolean inherited(Effort variantValue) {
        return variantValue == null;
    }

    public static List<String> ingredients(boolean specified, List<String> variantIngredients, List<String> dishIngredients) {
        return specified ? variantIngredients : dishIngredients;
    }

    public static Suitability suitability(SuitabilitySetting setting, Suitability dishValue) {
        return switch (setting) {
            case SUITABLE -> Suitability.SUITABLE;
            case UNSUITABLE -> Suitability.UNSUITABLE;
            case INHERIT -> dishValue;
        };
    }

    public static boolean inherited(SuitabilitySetting setting) {
        return setting == SuitabilitySetting.INHERIT;
    }
}

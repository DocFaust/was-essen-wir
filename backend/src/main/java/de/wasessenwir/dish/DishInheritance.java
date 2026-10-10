package de.wasessenwir.dish;

import java.util.List;

final class DishInheritance {

    private DishInheritance() {
    }

    static String text(String variantValue, String dishValue) {
        return variantValue == null ? dishValue : variantValue;
    }

    static boolean inherited(String variantValue) {
        return variantValue == null;
    }

    static Effort effort(Effort variantValue, Effort dishValue) {
        return variantValue == null ? dishValue : variantValue;
    }

    static boolean inherited(Effort variantValue) {
        return variantValue == null;
    }

    static List<String> ingredients(boolean specified, List<String> variantIngredients, List<String> dishIngredients) {
        return specified ? variantIngredients : dishIngredients;
    }

    static Suitability suitability(SuitabilitySetting setting, Suitability dishValue) {
        return switch (setting) {
            case SUITABLE -> Suitability.SUITABLE;
            case UNSUITABLE -> Suitability.UNSUITABLE;
            case INHERIT -> dishValue;
        };
    }

    static boolean inherited(SuitabilitySetting setting) {
        return setting == SuitabilitySetting.INHERIT;
    }
}

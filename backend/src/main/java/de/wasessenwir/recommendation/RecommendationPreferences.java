package de.wasessenwir.recommendation;

public record RecommendationPreferences(boolean preferSimple, boolean preferHomeOffice, boolean preferMealPrep) {

    public static RecommendationPreferences none() {
        return new RecommendationPreferences(false, false, false);
    }
}

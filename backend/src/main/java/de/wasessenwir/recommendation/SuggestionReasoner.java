package de.wasessenwir.recommendation;

import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.Suitability;

public final class SuggestionReasoner {

    static final String UNRATED = "Noch kaum bewertet und deshalb eine Chance wert.";
    static final String FITS = "Passt gut zu den Vorlieben der ausgewählten Personen.";
    static final String SEVERAL_LIKE = "Wird von mehreren Mitessenden gerne gegessen.";
    static final String MIXED = "Eine mögliche Alternative, auch wenn die Vorlieben auseinandergehen.";
    static final String SIMPLE = "Eine unkomplizierte Alternative mit geringem Aufwand.";
    static final String HOMEOFFICE = "Gut geeignet fürs Homeoffice.";
    static final String MEAL_PREP = "Lässt sich gut vorbereiten.";
    static final String EASY_VARIETY = "Eine abwechslungsreiche Alternative mit geringem Aufwand.";
    static final String VARIETY = "Eine abwechslungsreiche Alternative aus der Sammlung.";
    public static final String RECENT = "Kürzlich gegessen und deshalb seltener vorgeschlagen.";
    public static final String PLANNED_SOON = "Schon für die nächsten Tage eingeplant.";

    private SuggestionReasoner() {}

    public static String reason(ScoredCandidate scored, RecommendationPreferences preferences) {
        double recency = RecommendationScorer.factor(scored, "recency");
        if (recency < 0) {
            return RECENT;
        }
        double planned = RecommendationScorer.factor(scored, "planned");
        if (planned < 0) {
            return PLANNED_SOON;
        }
        RatingSummary ratings = scored.ratings();
        Candidate candidate = scored.candidate();
        if (!ratings.anySignal()) {
            return UNRATED;
        }
        if (ratings.preference() >= RecommendationWeights.STRONG_LIKING && ratings.oneStarCount() == 0) {
            return FITS;
        }
        if (ratings.positiveCount() >= 2 && ratings.oneStarCount() == 0) {
            return SEVERAL_LIKE;
        }
        if (ratings.oneStarCount() > 0 && ratings.dinerCount() >= 2) {
            return MIXED;
        }
        if (preferences.preferSimple() && candidate.effort() == Effort.EINFACH) {
            return SIMPLE;
        }
        if (preferences.preferHomeOffice() && candidate.homeOffice() == Suitability.SUITABLE) {
            return HOMEOFFICE;
        }
        if (preferences.preferMealPrep() && candidate.mealPrep() == Suitability.SUITABLE) {
            return MEAL_PREP;
        }
        if (candidate.effort() == Effort.EINFACH) {
            return EASY_VARIETY;
        }
        return VARIETY;
    }
}

package de.wasessenwir.recommendation;

public final class RecommendationWeights {

    public static final double STAR_1 = -8;
    public static final double STAR_2 = -3;
    public static final double STAR_3 = 0;
    public static final double STAR_4 = 3;
    public static final double STAR_5 = 5;
    public static final double INHERITED_FACTOR = 0.5;
    public static final double REJECTION_PER_ONE_STAR = 6;
    public static final double AGREEMENT = 1.5;
    public static final double EXPLORATION = 1;
    public static final double SIMPLE_EFFORT = 1.2;
    public static final double MEDIUM_EFFORT = 0.2;
    public static final double HIGH_EFFORT = -0.8;
    public static final double SUITABLE = 1.2;
    public static final double UNSUITABLE = -1;
    public static final double WEIGHT_OFFSET = 12;
    public static final double MIN_WEIGHT = 0.25;
    public static final double STRONG_LIKING = 2.5;
    public static final int SUGGESTION_LIMIT = 3;

    private RecommendationWeights() {}

    public static double starWeight(int stars) {
        return switch (stars) {
            case 1 -> STAR_1;
            case 2 -> STAR_2;
            case 3 -> STAR_3;
            case 4 -> STAR_4;
            case 5 -> STAR_5;
            default -> throw new IllegalArgumentException("Sterne außerhalb von 1 bis 5");
        };
    }
}

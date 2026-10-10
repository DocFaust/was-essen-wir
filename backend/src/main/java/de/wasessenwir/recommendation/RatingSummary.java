package de.wasessenwir.recommendation;

public record RatingSummary(
        int dinerCount,
        double preference,
        int oneStarCount,
        int positiveCount,
        boolean anySignal) {}

package de.wasessenwir.recommendation;

public record RepetitionPenalty(double eaten, double planned) {

    public static final RepetitionPenalty NONE = new RepetitionPenalty(0, 0);
}

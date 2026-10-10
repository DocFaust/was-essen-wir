package de.wasessenwir.recommendation;

import java.util.List;

public record ScoredCandidate(
        Candidate candidate,
        double score,
        double selectionWeight,
        List<ScoreFactor> factors,
        RatingSummary ratings) {}

package de.wasessenwir.recommendation;

public record RankedSuggestion(ScoredCandidate scored, String reason) {}

package de.wasessenwir.recommendation;

import java.util.List;

public record EngineResult(List<RankedSuggestion> suggestions, String explanation) {}

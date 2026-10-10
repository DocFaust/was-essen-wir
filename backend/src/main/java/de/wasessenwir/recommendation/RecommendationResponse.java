package de.wasessenwir.recommendation;

import java.util.List;

public record RecommendationResponse(List<SuggestionResponse> suggestions, String explanation) {}

package de.wasessenwir.ai;

import de.wasessenwir.recommendation.SuggestionResponse;
import java.util.List;

public record AiRecommendationResponse(
        String reply,
        String clarificationQuestion,
        InterpretedCriteria criteria,
        List<SuggestionResponse> suggestions,
        String explanation) {}

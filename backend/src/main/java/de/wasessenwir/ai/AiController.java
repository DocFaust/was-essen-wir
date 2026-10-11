package de.wasessenwir.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAssistantService assistant;
    private final DishDraftService drafts;

    public AiController(AiAssistantService assistant, DishDraftService drafts) {
        this.assistant = assistant;
        this.drafts = drafts;
    }

    @GetMapping("/status")
    AiStatusResponse status() {
        return assistant.status();
    }

    @PostMapping("/recommendations")
    AiRecommendationResponse recommend(@RequestBody AiRecommendationRequest request) {
        return assistant.recommend(request);
    }

    @PostMapping("/dish-drafts")
    DishDraftResponse draft(@RequestBody DishDraftRequest request) {
        return drafts.create(request);
    }
}

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

    public AiController(AiAssistantService assistant) {
        this.assistant = assistant;
    }

    @GetMapping("/status")
    AiStatusResponse status() {
        return assistant.status();
    }

    @PostMapping("/recommendations")
    AiRecommendationResponse recommend(@RequestBody AiRecommendationRequest request) {
        return assistant.recommend(request);
    }
}

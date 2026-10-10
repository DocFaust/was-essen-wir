package de.wasessenwir.rating;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RatingController {

    private final RatingService ratings;

    public RatingController(RatingService ratings) {
        this.ratings = ratings;
    }

    @GetMapping("/api/ratings")
    public List<OwnRatingResponse> list() {
        return ratings.listOwn();
    }

    @GetMapping("/api/dishes/{id}/rating")
    public RatingResponse getDish(@PathVariable UUID id) {
        return ratings.dishRating(id);
    }

    @PutMapping("/api/dishes/{id}/rating")
    public RatingResponse putDish(@PathVariable UUID id, @RequestBody RatingWriteRequest request) {
        return ratings.setDishRating(id, request);
    }

    @DeleteMapping("/api/dishes/{id}/rating")
    public ResponseEntity<Void> deleteDish(@PathVariable UUID id) {
        ratings.deleteDishRating(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/dishes/{id}/variants/{variantId}/rating")
    public RatingResponse getVariant(@PathVariable UUID id, @PathVariable UUID variantId) {
        return ratings.variantRating(id, variantId);
    }

    @PutMapping("/api/dishes/{id}/variants/{variantId}/rating")
    public RatingResponse putVariant(
            @PathVariable UUID id, @PathVariable UUID variantId, @RequestBody RatingWriteRequest request) {
        return ratings.setVariantRating(id, variantId, request);
    }

    @DeleteMapping("/api/dishes/{id}/variants/{variantId}/rating")
    public ResponseEntity<Void> deleteVariant(@PathVariable UUID id, @PathVariable UUID variantId) {
        ratings.deleteVariantRating(id, variantId);
        return ResponseEntity.noContent().build();
    }
}

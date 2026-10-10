package de.wasessenwir.rating;

import de.wasessenwir.dish.DishRepository;
import de.wasessenwir.dish.InvalidRequestException;
import de.wasessenwir.dish.NotFoundException;
import de.wasessenwir.security.CurrentUser;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RatingService {

    private final RatingRepository ratings;
    private final DishRepository dishes;

    public RatingService(RatingRepository ratings, DishRepository dishes) {
        this.ratings = ratings;
        this.dishes = dishes;
    }

    @Transactional(readOnly = true)
    public List<OwnRatingResponse> listOwn() {
        return ratings.findByUser(CurrentUser.requireId());
    }

    @Transactional(readOnly = true)
    public RatingResponse dishRating(UUID dishId) {
        requireDish(dishId);
        return new RatingResponse(ratings.findDishStar(CurrentUser.requireId(), dishId).orElse(null));
    }

    @Transactional(readOnly = true)
    public RatingResponse variantRating(UUID dishId, UUID variantId) {
        requireVariant(dishId, variantId);
        return new RatingResponse(ratings.findVariantStar(CurrentUser.requireId(), variantId).orElse(null));
    }

    @Transactional
    public RatingResponse setDishRating(UUID dishId, RatingWriteRequest request) {
        requireDish(dishId);
        int stars = stars(request);
        upsert(CurrentUser.requireId(), dishId, null, stars);
        return new RatingResponse(stars);
    }

    @Transactional
    public RatingResponse setVariantRating(UUID dishId, UUID variantId, RatingWriteRequest request) {
        requireVariant(dishId, variantId);
        int stars = stars(request);
        upsert(CurrentUser.requireId(), null, variantId, stars);
        return new RatingResponse(stars);
    }

    @Transactional
    public void deleteDishRating(UUID dishId) {
        requireDish(dishId);
        ratings.deleteDish(CurrentUser.requireId(), dishId);
    }

    @Transactional
    public void deleteVariantRating(UUID dishId, UUID variantId) {
        requireVariant(dishId, variantId);
        ratings.deleteVariant(CurrentUser.requireId(), variantId);
    }

    private void upsert(UUID userId, UUID dishId, UUID variantId, int stars) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (update(userId, dishId, variantId, stars, now) > 0) {
            return;
        }
        try {
            ratings.insert(UUID.randomUUID(), userId, dishId, variantId, stars, now);
        } catch (DataIntegrityViolationException exception) {
            if (update(userId, dishId, variantId, stars, now) == 0) {
                throw exception;
            }
        }
    }

    private int update(UUID userId, UUID dishId, UUID variantId, int stars, OffsetDateTime now) {
        if (dishId != null) {
            return ratings.updateDish(userId, dishId, stars, now);
        }
        return ratings.updateVariant(userId, variantId, stars, now);
    }

    private void requireDish(UUID dishId) {
        if (dishes.findDish(dishId).isEmpty()) {
            throw new NotFoundException("Gericht wurde nicht gefunden.");
        }
    }

    private void requireVariant(UUID dishId, UUID variantId) {
        if (dishes.findVariant(dishId, variantId).isEmpty()) {
            throw new NotFoundException("Variante wurde nicht gefunden.");
        }
    }

    private static int stars(RatingWriteRequest request) {
        if (request == null || request.stars() == null || request.stars() < 1 || request.stars() > 5) {
            throw new InvalidRequestException("Die Bewertung muss zwischen 1 und 5 Sternen liegen.");
        }
        return request.stars();
    }
}

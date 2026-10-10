package de.wasessenwir.rating;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RatingRepository {

    private final JdbcClient jdbc;

    public RatingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Integer> findDishStar(UUID userId, UUID dishId) {
        return jdbc.sql("""
                select stars
                from rating
                where user_id = :userId and dish_id = :dishId
                """)
                .param("userId", userId)
                .param("dishId", dishId)
                .query((rs, row) -> rs.getInt("stars"))
                .optional();
    }

    public Optional<Integer> findVariantStar(UUID userId, UUID variantId) {
        return jdbc.sql("""
                select stars
                from rating
                where user_id = :userId and variant_id = :variantId
                """)
                .param("userId", userId)
                .param("variantId", variantId)
                .query((rs, row) -> rs.getInt("stars"))
                .optional();
    }

    public Map<UUID, Integer> findDishStars(UUID userId, List<UUID> dishIds) {
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        List<TargetStar> rows = jdbc.sql("""
                select dish_id, stars
                from rating
                where user_id = :userId
                  and dish_id in (:dishIds)
                """)
                .param("userId", userId)
                .param("dishIds", dishIds)
                .query((rs, row) -> new TargetStar(rs.getObject("dish_id", UUID.class), rs.getInt("stars")))
                .list();
        Map<UUID, Integer> stars = new HashMap<>();
        for (TargetStar row : rows) {
            stars.put(row.targetId(), row.stars());
        }
        return Map.copyOf(stars);
    }

    public DishRatingView findForDish(UUID userId, UUID dishId) {
        List<RatingRow> rows = jdbc.sql("""
                select dish_id, variant_id, stars
                from rating
                where user_id = :userId
                  and (
                    dish_id = :dishId
                    or variant_id in (select id from dish_variant where dish_id = :dishId)
                  )
                """)
                .param("userId", userId)
                .param("dishId", dishId)
                .query((rs, row) -> new RatingRow(
                        rs.getObject("dish_id", UUID.class),
                        rs.getObject("variant_id", UUID.class),
                        rs.getInt("stars")))
                .list();
        Integer dishStars = null;
        Map<UUID, Integer> variantStars = new HashMap<>();
        for (RatingRow row : rows) {
            if (row.dishId() != null) {
                dishStars = row.stars();
            }
            if (row.variantId() != null) {
                variantStars.put(row.variantId(), row.stars());
            }
        }
        return new DishRatingView(dishStars, Map.copyOf(variantStars));
    }

    public List<OwnRatingResponse> findByUser(UUID userId) {
        return jdbc.sql("""
                select dish_id, variant_id, stars
                from rating
                where user_id = :userId
                order by created_at, id
                """)
                .param("userId", userId)
                .query((rs, row) -> new OwnRatingResponse(
                        rs.getObject("dish_id", UUID.class),
                        rs.getObject("variant_id", UUID.class),
                        rs.getInt("stars")))
                .list();
    }

    public int updateDish(UUID userId, UUID dishId, int stars, OffsetDateTime updatedAt) {
        return jdbc.sql("""
                update rating
                set stars = :stars, updated_at = :updatedAt
                where user_id = :userId and dish_id = :dishId
                """)
                .param("stars", stars)
                .param("updatedAt", updatedAt)
                .param("userId", userId)
                .param("dishId", dishId)
                .update();
    }

    public int updateVariant(UUID userId, UUID variantId, int stars, OffsetDateTime updatedAt) {
        return jdbc.sql("""
                update rating
                set stars = :stars, updated_at = :updatedAt
                where user_id = :userId and variant_id = :variantId
                """)
                .param("stars", stars)
                .param("updatedAt", updatedAt)
                .param("userId", userId)
                .param("variantId", variantId)
                .update();
    }

    public void insert(UUID id, UUID userId, UUID dishId, UUID variantId, int stars, OffsetDateTime now) {
        jdbc.sql("""
                insert into rating (id, user_id, dish_id, variant_id, stars, created_at, updated_at)
                values (:id, :userId, :dishId, :variantId, :stars, :now, :now)
                """)
                .param("id", id)
                .param("userId", userId)
                .param("dishId", dishId)
                .param("variantId", variantId)
                .param("stars", stars)
                .param("now", now)
                .update();
    }

    public void deleteDish(UUID userId, UUID dishId) {
        jdbc.sql("delete from rating where user_id = :userId and dish_id = :dishId")
                .param("userId", userId)
                .param("dishId", dishId)
                .update();
    }

    public void deleteVariant(UUID userId, UUID variantId) {
        jdbc.sql("delete from rating where user_id = :userId and variant_id = :variantId")
                .param("userId", userId)
                .param("variantId", variantId)
                .update();
    }

    private record TargetStar(UUID targetId, int stars) {}

    private record RatingRow(UUID dishId, UUID variantId, int stars) {}
}

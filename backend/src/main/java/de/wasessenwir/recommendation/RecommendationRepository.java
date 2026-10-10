package de.wasessenwir.recommendation;

import de.wasessenwir.dish.Effort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationRepository {

    private final JdbcClient jdbc;

    public RecommendationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<DishRow> findDishes(String mealType) {
        return jdbc.sql("""
                select d.id, d.name, d.effort, d.meal_prep, d.homeoffice
                from dish d
                join dish_meal_type m on m.dish_id = d.id and m.meal_type = :mealType
                order by lower(d.name), d.id
                """)
                .param("mealType", mealType)
                .query((rs, row) -> new DishRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        effort(rs.getString("effort")),
                        rs.getObject("meal_prep", Boolean.class),
                        rs.getObject("homeoffice", Boolean.class)))
                .list();
    }

    public List<VariantRow> findVariants(List<UUID> dishIds) {
        if (dishIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                select id, dish_id, name, effort, meal_prep, homeoffice
                from dish_variant
                where dish_id in (:dishIds)
                order by dish_id, position, lower(name)
                """)
                .param("dishIds", dishIds)
                .query((rs, row) -> new VariantRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("dish_id", UUID.class),
                        rs.getString("name"),
                        effort(rs.getString("effort")),
                        rs.getObject("meal_prep", Boolean.class),
                        rs.getObject("homeoffice", Boolean.class)))
                .list();
    }

    public List<RatingEntry> findRatings(List<UUID> userIds, List<UUID> dishIds, List<UUID> variantIds) {
        if (userIds.isEmpty() || (dishIds.isEmpty() && variantIds.isEmpty())) {
            return List.of();
        }
        if (variantIds.isEmpty()) {
            return queryRatings("""
                    select user_id, dish_id, variant_id, stars
                    from rating
                    where user_id in (:userIds) and dish_id in (:dishIds)
                    """, userIds, dishIds, List.of());
        }
        if (dishIds.isEmpty()) {
            return queryRatings("""
                    select user_id, dish_id, variant_id, stars
                    from rating
                    where user_id in (:userIds) and variant_id in (:variantIds)
                    """, userIds, List.of(), variantIds);
        }
        return queryRatings("""
                select user_id, dish_id, variant_id, stars
                from rating
                where user_id in (:userIds)
                  and (dish_id in (:dishIds) or variant_id in (:variantIds))
                """, userIds, dishIds, variantIds);
    }

    private List<RatingEntry> queryRatings(String sql, List<UUID> userIds, List<UUID> dishIds, List<UUID> variantIds) {
        var statement = jdbc.sql(sql).param("userIds", userIds);
        if (!dishIds.isEmpty()) {
            statement = statement.param("dishIds", dishIds);
        }
        if (!variantIds.isEmpty()) {
            statement = statement.param("variantIds", variantIds);
        }
        return statement.query((rs, row) -> new RatingEntry(
                rs.getObject("user_id", UUID.class),
                rs.getObject("dish_id", UUID.class),
                rs.getObject("variant_id", UUID.class),
                rs.getInt("stars")))
                .list();
    }

    private static Effort effort(String value) {
        return value == null ? null : Effort.valueOf(value);
    }

    public record DishRow(UUID id, String name, Effort effort, Boolean mealPrep, Boolean homeOffice) {}

    public record VariantRow(
            UUID id,
            UUID dishId,
            String name,
            Effort effort,
            Boolean mealPrep,
            Boolean homeOffice) {}
}

package de.wasessenwir.dish;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DishRepository {

    private static final RowMapper<StoredDish> DISH_MAPPER = (rs, row) -> new StoredDish(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("description"),
            effort(rs.getString("effort")),
            rs.getString("recipe_text"),
            rs.getString("recipe_url"),
            rs.getString("recipe_source"),
            rs.getString("comments"),
            rs.getObject("meal_prep", Boolean.class),
            rs.getObject("homeoffice", Boolean.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private static final RowMapper<StoredVariant> VARIANT_MAPPER = (rs, row) -> new StoredVariant(
            rs.getObject("id", UUID.class),
            rs.getObject("dish_id", UUID.class),
            rs.getString("name"),
            rs.getString("description"),
            effort(rs.getString("effort")),
            rs.getBoolean("ingredients_specified"),
            rs.getString("recipe_text"),
            rs.getString("recipe_url"),
            rs.getString("recipe_source"),
            rs.getObject("meal_prep", Boolean.class),
            rs.getObject("homeoffice", Boolean.class),
            rs.getInt("position"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public DishRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertDish(StoredDish dish) {
        jdbc.sql("""
                insert into dish (
                    id, name, description, effort, recipe_text, recipe_url, recipe_source, comments,
                    meal_prep, homeoffice, created_at, updated_at
                ) values (
                    :id, :name, :description, :effort, :recipeText, :recipeUrl, :recipeSource, :comments,
                    :mealPrep, :homeOffice, :createdAt, :updatedAt
                )
                """)
                .params(dishParams(dish))
                .update();
    }

    public void updateDish(StoredDish dish) {
        jdbc.sql("""
                update dish set
                    name = :name,
                    description = :description,
                    effort = :effort,
                    recipe_text = :recipeText,
                    recipe_url = :recipeUrl,
                    recipe_source = :recipeSource,
                    comments = :comments,
                    meal_prep = :mealPrep,
                    homeoffice = :homeOffice,
                    updated_at = :updatedAt
                where id = :id
                """)
                .params(dishParams(dish))
                .update();
    }

    public int deleteDish(UUID id) {
        return jdbc.sql("delete from dish where id = :id").param("id", id).update();
    }

    public Optional<StoredDish> findDish(UUID id) {
        return jdbc.sql("""
                select id, name, description, effort, recipe_text, recipe_url, recipe_source, comments,
                       meal_prep, homeoffice, created_at, updated_at
                from dish
                where id = :id
                """)
                .param("id", id)
                .query(DISH_MAPPER)
                .optional();
    }

    public Optional<String> findDishName(UUID id) {
        return jdbc.sql("select name from dish where id = :id")
                .param("id", id)
                .query(String.class)
                .optional();
    }

    public Optional<String> findVariantName(UUID dishId, UUID variantId) {
        return jdbc.sql("select name from dish_variant where id = :id and dish_id = :dishId")
                .param("id", variantId)
                .param("dishId", dishId)
                .query(String.class)
                .optional();
    }

    public boolean dishNameExists(String name, UUID excludeId) {
        Long count = jdbc.sql("""
                select count(*)
                from dish
                where lower(name) = lower(:name)
                  and id is distinct from :excludeId
                """)
                .param("name", name)
                .param("excludeId", excludeId)
                .query(Long.class)
                .single();
        return count > 0;
    }

    public List<UUID> search(String query, MealType mealType, Effort effort, Suitability mealPrep, Suitability homeOffice) {
        StringBuilder sql = new StringBuilder("""
                select d.id, d.name
                from dish d
                left join dish_variant v on v.dish_id = d.id
                where 1 = 1
                """);
        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery) {
            sql.append("""
                     and (
                        lower(d.name) like :q escape '\\'
                        or lower(coalesce(v.name, '')) like :q escape '\\'
                     )
                    """);
        }
        if (mealType != null) {
            sql.append(" and exists (select 1 from dish_meal_type m where m.dish_id = d.id and m.meal_type = :mealType)");
        }
        if (effort != null) {
            sql.append(" and d.effort = :effort");
        }
        appendSuitability(sql, "d.meal_prep", mealPrep);
        appendSuitability(sql, "d.homeoffice", homeOffice);
        var statement = jdbc.sql(sql.toString());
        if (hasQuery) {
            statement = statement.param("q", likePattern(query));
        }
        if (mealType != null) {
            statement = statement.param("mealType", mealType.name());
        }
        if (effort != null) {
            statement = statement.param("effort", effort.name());
        }
        List<DishMatch> matches = statement.query((rs, row) -> new DishMatch(
                rs.getObject("id", UUID.class),
                rs.getString("name"))).list();
        matches.sort(Comparator.comparing(DishMatch::name, String.CASE_INSENSITIVE_ORDER).thenComparing(DishMatch::id));
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (DishMatch match : matches) {
            ids.add(match.id());
        }
        return List.copyOf(ids);
    }

    public void replaceMealTypes(UUID dishId, List<MealType> mealTypes) {
        jdbc.sql("delete from dish_meal_type where dish_id = :id").param("id", dishId).update();
        for (MealType mealType : mealTypes) {
            jdbc.sql("insert into dish_meal_type (dish_id, meal_type) values (:id, :mealType)")
                    .param("id", dishId)
                    .param("mealType", mealType.name())
                    .update();
        }
    }

    public List<MealType> findMealTypes(UUID dishId) {
        List<MealType> stored = jdbc.sql("select meal_type from dish_meal_type where dish_id = :id")
                .param("id", dishId)
                .query((rs, row) -> MealType.valueOf(rs.getString("meal_type")))
                .list();
        return stored.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }

    public void replaceCategories(UUID dishId, List<String> categories) {
        jdbc.sql("delete from dish_category where dish_id = :id").param("id", dishId).update();
        insertNames("insert into dish_category (dish_id, position, name) values (:ownerId, :position, :name)", dishId, categories);
    }

    public List<String> findCategories(UUID dishId) {
        return jdbc.sql("select name from dish_category where dish_id = :id order by position")
                .param("id", dishId)
                .query(STRING_COLUMN)
                .list();
    }

    public void replaceDishIngredients(UUID dishId, List<String> ingredients) {
        jdbc.sql("delete from ingredient where dish_id = :id").param("id", dishId).update();
        insertIngredients(dishId, null, ingredients);
    }

    public List<String> findDishIngredients(UUID dishId) {
        return jdbc.sql("select name from ingredient where dish_id = :id order by position")
                .param("id", dishId)
                .query(STRING_COLUMN)
                .list();
    }

    public void insertVariant(StoredVariant variant) {
        jdbc.sql("""
                insert into dish_variant (
                    id, dish_id, name, description, effort, ingredients_specified, recipe_text, recipe_url,
                    recipe_source, meal_prep, homeoffice, position, created_at, updated_at
                ) values (
                    :id, :dishId, :name, :description, :effort, :ingredientsSpecified, :recipeText, :recipeUrl,
                    :recipeSource, :mealPrep, :homeOffice, :position, :createdAt, :updatedAt
                )
                """)
                .params(variantParams(variant))
                .update();
    }

    public void updateVariant(StoredVariant variant) {
        jdbc.sql("""
                update dish_variant set
                    name = :name,
                    description = :description,
                    effort = :effort,
                    ingredients_specified = :ingredientsSpecified,
                    recipe_text = :recipeText,
                    recipe_url = :recipeUrl,
                    recipe_source = :recipeSource,
                    meal_prep = :mealPrep,
                    homeoffice = :homeOffice,
                    updated_at = :updatedAt
                where id = :id and dish_id = :dishId
                """)
                .params(variantParams(variant))
                .update();
    }

    public int deleteVariant(UUID dishId, UUID variantId) {
        return jdbc.sql("delete from dish_variant where id = :id and dish_id = :dishId")
                .param("id", variantId)
                .param("dishId", dishId)
                .update();
    }

    public Optional<StoredVariant> findVariant(UUID dishId, UUID variantId) {
        return jdbc.sql("""
                select id, dish_id, name, description, effort, ingredients_specified, recipe_text, recipe_url,
                       recipe_source, meal_prep, homeoffice, position, created_at, updated_at
                from dish_variant
                where id = :id and dish_id = :dishId
                """)
                .param("id", variantId)
                .param("dishId", dishId)
                .query(VARIANT_MAPPER)
                .optional();
    }

    public List<StoredVariant> findVariants(UUID dishId) {
        return jdbc.sql("""
                select id, dish_id, name, description, effort, ingredients_specified, recipe_text, recipe_url,
                       recipe_source, meal_prep, homeoffice, position, created_at, updated_at
                from dish_variant
                where dish_id = :dishId
                order by position, name
                """)
                .param("dishId", dishId)
                .query(VARIANT_MAPPER)
                .list();
    }

    public boolean variantNameExists(UUID dishId, String name, UUID excludeId) {
        Long count = jdbc.sql("""
                select count(*)
                from dish_variant
                where dish_id = :dishId
                  and lower(name) = lower(:name)
                  and id is distinct from :excludeId
                """)
                .param("dishId", dishId)
                .param("name", name)
                .param("excludeId", excludeId)
                .query(Long.class)
                .single();
        return count > 0;
    }

    public int nextVariantPosition(UUID dishId) {
        Integer max = jdbc.sql("select coalesce(max(position), -1) from dish_variant where dish_id = :dishId")
                .param("dishId", dishId)
                .query(Integer.class)
                .single();
        return max + 1;
    }

    public void replaceVariantIngredients(UUID variantId, List<String> ingredients) {
        jdbc.sql("delete from ingredient where variant_id = :id").param("id", variantId).update();
        insertIngredients(null, variantId, ingredients);
    }

    public List<String> findVariantIngredients(UUID variantId) {
        return jdbc.sql("select name from ingredient where variant_id = :id order by position")
                .param("id", variantId)
                .query(STRING_COLUMN)
                .list();
    }

    private void insertIngredients(UUID dishId, UUID variantId, List<String> ingredients) {
        int position = 0;
        for (String name : ingredients) {
            jdbc.sql("""
                    insert into ingredient (id, dish_id, variant_id, position, name)
                    values (:id, :dishId, :variantId, :position, :name)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("dishId", dishId)
                    .param("variantId", variantId)
                    .param("position", position)
                    .param("name", name)
                    .update();
            position++;
        }
    }

    private void insertNames(String sql, UUID ownerId, List<String> names) {
        int position = 0;
        for (String name : names) {
            jdbc.sql(sql)
                    .param("ownerId", ownerId)
                    .param("position", position)
                    .param("name", name)
                    .update();
            position++;
        }
    }

    private static java.util.Map<String, Object> dishParams(StoredDish dish) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", dish.id());
        params.put("name", dish.name());
        params.put("description", dish.description());
        params.put("effort", dish.effort() == null ? null : dish.effort().name());
        params.put("recipeText", dish.recipeText());
        params.put("recipeUrl", dish.recipeUrl());
        params.put("recipeSource", dish.recipeSource());
        params.put("comments", dish.comments());
        params.put("mealPrep", dish.mealPrep());
        params.put("homeOffice", dish.homeOffice());
        params.put("createdAt", dish.createdAt());
        params.put("updatedAt", dish.updatedAt());
        return params;
    }

    private static java.util.Map<String, Object> variantParams(StoredVariant variant) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", variant.id());
        params.put("dishId", variant.dishId());
        params.put("name", variant.name());
        params.put("description", variant.description());
        params.put("effort", variant.effort() == null ? null : variant.effort().name());
        params.put("ingredientsSpecified", variant.ingredientsSpecified());
        params.put("recipeText", variant.recipeText());
        params.put("recipeUrl", variant.recipeUrl());
        params.put("recipeSource", variant.recipeSource());
        params.put("mealPrep", variant.mealPrep());
        params.put("homeOffice", variant.homeOffice());
        params.put("position", variant.position());
        params.put("createdAt", variant.createdAt());
        params.put("updatedAt", variant.updatedAt());
        return params;
    }

    private static void appendSuitability(StringBuilder sql, String column, Suitability suitability) {
        if (suitability == null) {
            return;
        }
        switch (suitability) {
            case SUITABLE -> sql.append(" and ").append(column).append(" = true");
            case UNSUITABLE -> sql.append(" and ").append(column).append(" = false");
            case UNKNOWN -> sql.append(" and ").append(column).append(" is null");
        }
    }

    private static String likePattern(String query) {
        String escaped = query.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static Effort effort(String value) {
        return value == null ? null : Effort.valueOf(value);
    }

    private static final RowMapper<String> STRING_COLUMN = new RowMapper<>() {
        @Override
        public String mapRow(ResultSet rs, int rowNum) throws SQLException {
            return rs.getString(1);
        }
    };

    private record DishMatch(UUID id, String name) {
    }
}

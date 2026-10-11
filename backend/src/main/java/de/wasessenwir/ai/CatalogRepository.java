package de.wasessenwir.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public CatalogIndex load() {
        List<CatalogIndex.DishRef> dishes = jdbc.sql("select id, name from dish order by lower(name), id")
                .query((rs, row) -> new CatalogIndex.DishRef(rs.getObject("id", UUID.class), rs.getString("name")))
                .list();
        List<CatalogIndex.VariantRef> variants = jdbc.sql("""
                select id, dish_id, name
                from dish_variant
                order by dish_id, position, lower(name)
                """)
                .query((rs, row) -> new CatalogIndex.VariantRef(
                        rs.getObject("id", UUID.class),
                        rs.getObject("dish_id", UUID.class),
                        rs.getString("name")))
                .list();
        List<CatalogIndex.TagRef> categories = jdbc.sql("""
                select dish_id, name
                from dish_category
                order by dish_id, position
                """)
                .query((rs, row) -> new CatalogIndex.TagRef(rs.getObject("dish_id", UUID.class), rs.getString("name")))
                .list();
        List<CatalogIndex.IngredientRef> ingredients = jdbc.sql("""
                select dish_id, variant_id, name
                from ingredient
                order by position
                """)
                .query((rs, row) -> new CatalogIndex.IngredientRef(
                        rs.getObject("dish_id", UUID.class),
                        rs.getObject("variant_id", UUID.class),
                        rs.getString("name")))
                .list();
        return new CatalogIndex(dishes, variants, categories, ingredients);
    }
}

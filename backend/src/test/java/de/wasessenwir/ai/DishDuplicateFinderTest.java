package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DishDuplicateFinderTest {

    @Test
    void findsCloseNamesAndIgnoresUnrelatedDishes() {
        UUID bolognese = UUID.randomUUID();
        UUID gratin = UUID.randomUUID();
        CatalogIndex catalog = new CatalogIndex(
                List.of(
                        new CatalogIndex.DishRef(bolognese, "Spaghetti Bolognese"),
                        new CatalogIndex.DishRef(gratin, "Kartoffelgratin")),
                List.of(new CatalogIndex.VariantRef(UUID.randomUUID(), bolognese, "Mit Linsen")),
                List.of(),
                List.of());

        assertThat(DishDuplicateFinder.find("Spaghetti Bolo", catalog, null))
                .extracting(DishDraftResponse.SimilarDish::dishId)
                .contains(bolognese);
        assertThat(DishDuplicateFinder.find("Bolognese mit Spaghetti", catalog, null))
                .extracting(DishDraftResponse.SimilarDish::name)
                .contains("Spaghetti Bolognese");
        assertThat(DishDuplicateFinder.find("spaghetti bolognese", catalog, null)).isNotEmpty();
        assertThat(DishDuplicateFinder.find("Kartoffelgratin", catalog, null))
                .extracting(DishDraftResponse.SimilarDish::dishId)
                .contains(gratin)
                .doesNotContain(bolognese);
        assertThat(DishDuplicateFinder.find("Mit Linsen", catalog, null))
                .extracting(DishDraftResponse.SimilarDish::name)
                .anyMatch(name -> name.contains("Linsen"));
    }
}

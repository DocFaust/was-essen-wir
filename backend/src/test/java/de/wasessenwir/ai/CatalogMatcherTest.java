package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogMatcherTest {

    private final UUID pasta = UUID.randomUUID();
    private final UUID pizza = UUID.randomUUID();
    private final UUID salami = UUID.randomUUID();
    private final UUID soup = UUID.randomUUID();

    private final CatalogIndex index = new CatalogIndex(
            List.of(
                    new CatalogIndex.DishRef(pasta, "Spaghetti"),
                    new CatalogIndex.DishRef(pizza, "Pizza"),
                    new CatalogIndex.DishRef(soup, "Suppe"),
                    new CatalogIndex.DishRef(UUID.randomUUID(), "Preis")),
            List.of(new CatalogIndex.VariantRef(salami, pizza, "Salami")),
            List.of(new CatalogIndex.TagRef(soup, "Eintopf")),
            List.of(new CatalogIndex.IngredientRef(pasta, null, "Nudeln")));

    @Test
    void matchesStoredIngredientsVariantsAndCategoriesWithoutInferring() {
        CatalogMatcher.Match noodles = CatalogMatcher.match(index, List.of("Nudeln"), List.of());
        CatalogMatcher.Match salamiWish = CatalogMatcher.match(index, List.of("Salami"), List.of());
        CatalogMatcher.Match stew = CatalogMatcher.match(index, List.of("Eintopf"), List.of());
        CatalogMatcher.Match lasagne = CatalogMatcher.match(index, List.of("Lasagne"), List.of());
        CatalogMatcher.Match rice = CatalogMatcher.match(index, List.of("Reis"), List.of());

        assertThat(noodles.nothingFound()).isFalse();
        assertThat(noodles.selection().dishIds()).containsExactly(pasta);
        assertThat(salamiWish.selection().variantIds()).containsExactly(salami);
        assertThat(salamiWish.selection().dishIds()).isEmpty();
        assertThat(stew.selection().dishIds()).containsExactly(soup);
        assertThat(lasagne.nothingFound()).isTrue();
        assertThat(lasagne.limitations()).anyMatch(text -> text.contains("Lasagne"));
        assertThat(lasagne.matchedDishes()).isEmpty();
        assertThat(rice.nothingFound()).isTrue();
    }

    @Test
    void excludesAMatchingFamilyAndReportsUnknownAvoidance() {
        CatalogMatcher.Match match = CatalogMatcher.match(index, List.of(), List.of("Pizza", "Lasagne"));

        assertThat(match.excludedDishIds()).containsExactly(pizza);
        assertThat(match.limitations()).anyMatch(text -> text.contains("Lasagne"));
        assertThat(match.nothingFound()).isFalse();
        assertThat(match.selection().restricted()).isFalse();
    }
}

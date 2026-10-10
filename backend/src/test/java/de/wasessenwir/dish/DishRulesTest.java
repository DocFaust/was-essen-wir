package de.wasessenwir.dish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DishRulesTest {

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> DishValidator.dish(named("  ")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Der Name ist erforderlich.");
    }

    @Test
    void nameOnlyDishKeepsOptionalFieldsEmpty() {
        NormalizedDish dish = DishValidator.dish(named("  Suppe  "));

        assertThat(dish.name()).isEqualTo("Suppe");
        assertThat(dish.recipeSource()).isNull();
        assertThat(dish.ingredients()).isEmpty();
        assertThat(dish.mealPrep()).isEqualTo(Suitability.UNKNOWN);
        assertThat(dish.homeOffice()).isEqualTo(Suitability.UNKNOWN);
        assertThat(dish.confirmDuplicate()).isFalse();
    }

    @Test
    void recipeSourceIsTrimmedAndBlankBecomesUnset() {
        NormalizedDish dish = DishValidator.dish(new DishWriteRequest(
                "Suppe",
                null,
                null,
                null,
                null,
                null,
                null,
                "  https://example.test/rezept  ",
                "  Von Oma  ",
                null,
                Suitability.SUITABLE,
                Suitability.UNSUITABLE,
                null));

        assertThat(dish.recipeUrl()).isEqualTo("https://example.test/rezept");
        assertThat(dish.recipeSource()).isEqualTo("Von Oma");
        assertThat(dish.mealPrep()).isEqualTo(Suitability.SUITABLE);
        assertThat(dish.homeOffice()).isEqualTo(Suitability.UNSUITABLE);

        NormalizedVariant variant = DishValidator.variant(new VariantWriteRequest(
                "Mild",
                " ",
                null,
                false,
                List.of("Pfeffer"),
                null,
                null,
                " ",
                null,
                SuitabilitySetting.UNSUITABLE));

        assertThat(variant.recipeSource()).isNull();
        assertThat(variant.description()).isNull();
        assertThat(variant.ingredientsSpecified()).isFalse();
        assertThat(variant.ingredients()).isEmpty();
        assertThat(variant.mealPrep()).isEqualTo(SuitabilitySetting.INHERIT);
        assertThat(variant.homeOffice()).isEqualTo(SuitabilitySetting.UNSUITABLE);
    }

    @Test
    void recipeUrlMustBeHttp() {
        assertThatThrownBy(() -> DishValidator.dish(new DishWriteRequest(
                "Suppe",
                null,
                null,
                null,
                null,
                null,
                null,
                "ftp://example.test/rezept",
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Der Rezeptlink muss mit http:// oder https:// beginnen.");
    }

    @Test
    void variantWithoutOwnValuesInheritsFromDish() {
        assertThat(DishInheritance.text(null, "Chefkoch")).isEqualTo("Chefkoch");
        assertThat(DishInheritance.inherited((String) null)).isTrue();
        assertThat(DishInheritance.text("Von Oma", "Chefkoch")).isEqualTo("Von Oma");
        assertThat(DishInheritance.inherited("Von Oma")).isFalse();
        assertThat(DishInheritance.ingredients(false, List.of(), List.of("Linsen"))).containsExactly("Linsen");
        assertThat(DishInheritance.ingredients(true, List.of(), List.of("Linsen"))).isEmpty();
        assertThat(DishInheritance.suitability(SuitabilitySetting.INHERIT, Suitability.SUITABLE))
                .isEqualTo(Suitability.SUITABLE);
        assertThat(DishInheritance.inherited(SuitabilitySetting.INHERIT)).isTrue();
        assertThat(DishInheritance.suitability(SuitabilitySetting.UNSUITABLE, Suitability.SUITABLE))
                .isEqualTo(Suitability.UNSUITABLE);
        assertThat(DishInheritance.inherited(SuitabilitySetting.UNSUITABLE)).isFalse();
        assertThat(DishInheritance.suitability(SuitabilitySetting.INHERIT, Suitability.UNKNOWN))
                .isEqualTo(Suitability.UNKNOWN);
    }

    private static DishWriteRequest named(String name) {
        return new DishWriteRequest(name, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}

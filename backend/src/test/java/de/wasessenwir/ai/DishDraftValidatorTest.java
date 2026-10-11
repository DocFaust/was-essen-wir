package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DishDraftValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DishDraftValidator validator = new DishDraftValidator(mapper);

    @Test
    void keepsExplicitFactsAndDropsInventedOnes() {
        DishDraftFacts facts = validator.accept(
                """
                {"schemaVersion":1,"name":"Kartoffelgratin","description":null,"mealTypes":["ABENDESSEN"],
                "categories":["vegetarisch"],"effort":"EINFACH","ingredients":["Trüffel"],"removedIngredients":[],
                "recipeText":null,"recipeUrl":null,"recipeSource":null,"comments":null,
                "mealPrep":true,"homeOffice":true,"warnings":[]}
                """,
                "Wir machen manchmal Kartoffelgratin.",
                DraftKind.DISH);

        assertThat(facts.name()).isEqualTo("Kartoffelgratin");
        assertThat(facts.mealTypes()).isEmpty();
        assertThat(facts.categories()).isEmpty();
        assertThat(facts.effort()).isNull();
        assertThat(facts.ingredients()).isEmpty();
        assertThat(facts.mealPrep()).isNull();
        assertThat(facts.homeOffice()).isNull();
        assertThat(facts.warnings()).anyMatch(warning -> warning.contains("ohne Beleg"));
    }

    @Test
    void rejectsInvalidJsonMissingFieldsEnumsAndInventedIds() {
        assertThatThrownBy(() -> validator.accept("kein json", "Suppe", DraftKind.DISH))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.accept(template().replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "Suppe.", DraftKind.DISH))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.accept(template().replace("\"name\":\"Suppe\",", ""), "Suppe.", DraftKind.DISH))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.accept(template().replace("\"effort\":null", "\"effort\":\"SCHNELL\""), "Suppe.", DraftKind.DISH))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.accept(template().replace("\"mealTypes\":[]", "\"mealTypes\":[\"DINNER\"]"), "Suppe.", DraftKind.DISH))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.accept(template().replace("\"schemaVersion\":1", "\"id\":\"42\",\"schemaVersion\":1"), "Suppe.", DraftKind.DISH))
                .isInstanceOf(AiProviderException.class);
    }

    @Test
    void rejectsADescriptionWithoutADishName() {
        assertThatThrownBy(() -> validator.accept(blankName(), "bitte noch", DraftKind.DISH))
                .isInstanceOf(DishDraftRejectedException.class);
    }

    private static String template() {
        return """
                {"schemaVersion":1,"name":"Suppe","description":null,"mealTypes":[],"categories":[],
                "effort":null,"ingredients":[],"removedIngredients":[],"recipeText":null,"recipeUrl":null,
                "recipeSource":null,"comments":null,"mealPrep":null,"homeOffice":null,"warnings":[]}
                """;
    }

    private static String blankName() {
        return """
                {"schemaVersion":1,"name":null,"description":null,"mealTypes":[],"categories":[],
                "effort":null,"ingredients":[],"removedIngredients":[],"recipeText":null,"recipeUrl":null,
                "recipeSource":null,"comments":null,"mealPrep":null,"homeOffice":null,"warnings":[]}
                """;
    }
}

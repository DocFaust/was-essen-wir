package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.MealType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DishDraftValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DishDraftValidator validator = new DishDraftValidator(mapper);

    @Test
    void completesAKnownDishFromTheName() {
        DishDraftFacts facts = validator.accept(bolognese(), "Spaghetti Bolognese", DraftKind.DISH);

        assertThat(facts.name()).isEqualTo("Spaghetti Bolognese");
        assertThat(facts.description()).contains("Tomaten");
        assertThat(facts.mealTypes()).containsExactly(MealType.MITTAGESSEN, MealType.ABENDESSEN);
        assertThat(facts.categories()).containsExactly("klassisch");
        assertThat(facts.effort()).isEqualTo(Effort.MITTEL);
        assertThat(facts.ingredients()).contains("Hackfleisch", "Tomaten", "Zwiebeln");
        assertThat(facts.recipeText()).contains("anschwitzen");
        assertThat(facts.recipeSource()).isEqualTo("KI-generiert");
        assertThat(facts.recipeUrl()).isNull();
        assertThat(facts.comments()).isNull();
        assertThat(facts.mealPrep()).isTrue();
        assertThat(facts.homeOffice()).isFalse();
    }

    @Test
    void explicitStatementsOverrideCompletions() {
        DishDraftFacts facts = validator.accept(
                bolognese().replace("\"effort\":\"MITTEL\"", "\"effort\":\"AUFWENDIG\"")
                        .replace("\"mealPrep\":true", "\"mealPrep\":true")
                        .replace("\"ingredients\":[\"Spaghetti\",\"Hackfleisch\",\"Tomaten\",\"Zwiebeln\",\"Karotten\",\"Sellerie\"]",
                                "\"ingredients\":[\"Sellerie\",\"Sahne\"]"),
                "Spaghetti Bolognese. Dauert 20 Minuten. Zutaten: Hackfleisch und Tomaten.",
                DraftKind.DISH);

        assertThat(facts.effort()).isEqualTo(Effort.EINFACH);
        assertThat(facts.ingredients()).containsExactly("Hackfleisch", "Tomaten");
        assertThat(facts.description()).contains("Tomaten");
    }

    @Test
    void dropsExcludedIngredients() {
        DishDraftFacts facts = validator.accept(
                bolognese().replace("\"Sellerie\"", "\"Sellerie\",\"Staudensellerie\",\"200 g Hackfleisch\""),
                "Spaghetti Bolognese ohne Sellerie",
                DraftKind.DISH);

        assertThat(facts.name()).isEqualTo("Spaghetti Bolognese");
        assertThat(facts.ingredients()).contains("Hackfleisch").doesNotContain("Sellerie", "Staudensellerie");
        assertThat(facts.removedIngredients()).anyMatch(item -> item.equalsIgnoreCase("Sellerie"));
    }

    @Test
    void respectsExplicitNegativeSuitability() {
        String model = bolognese().replace("\"mealPrep\":true", "\"mealPrep\":true")
                .replace("\"homeOffice\":false", "\"homeOffice\":true");

        DishDraftFacts facts = validator.accept(
                model,
                "Spaghetti Bolognese. Ist nicht zum Vorkochen geeignet und nicht fürs Homeoffice.",
                DraftKind.DISH);

        assertThat(facts.mealPrep()).isFalse();
        assertThat(facts.homeOffice()).isFalse();
    }

    @Test
    void dropsInventedLinksSourcesAndPersonalComments() {
        String invented = bolognese()
                .replace("\"recipeUrl\":null", "\"recipeUrl\":\"https://example.com/bolognese\"")
                .replace("\"recipeSource\":\"KI-generiert\"", "\"recipeSource\":\"Chefkoch\"")
                .replace("\"comments\":null", "\"comments\":\"Die Familie isst das jeden Sonntag.\"");

        DishDraftFacts inventedFacts = validator.accept(invented, "Spaghetti Bolognese", DraftKind.DISH);
        DishDraftFacts stated = validator.accept(
                invented,
                "Spaghetti Bolognese. Quelle: Familienkochbuch. https://example.com/eigene",
                DraftKind.DISH);

        assertThat(inventedFacts.recipeUrl()).isNull();
        assertThat(inventedFacts.recipeSource()).isNull();
        assertThat(inventedFacts.comments()).isNull();
        assertThat(inventedFacts.recipeText()).isNotBlank();
        assertThat(stated.recipeUrl()).isEqualTo("https://example.com/eigene");
        assertThat(stated.recipeSource()).isEqualTo("Familienkochbuch");
        assertThat(stated.comments()).isNull();
    }

    @Test
    void unknownDishesDoNotKeepAnInventedRecipe() {
        String invented = bolognese()
                .replace("\"name\":\"Spaghetti Bolognese\"", "\"name\":\"Flarbenschmor\"")
                .replace("\"warnings\":[]", "\"warnings\":[\"unbekannt\"]");

        DishDraftFacts facts = validator.accept(invented, "Flarbenschmor. Dauert 20 Minuten.", DraftKind.DISH);

        assertThat(facts.name()).isEqualTo("Flarbenschmor");
        assertThat(facts.effort()).isEqualTo(Effort.EINFACH);
        assertThat(facts.description()).isNull();
        assertThat(facts.recipeText()).isNull();
        assertThat(facts.recipeSource()).isNull();
        assertThat(facts.ingredients()).isEmpty();
        assertThat(facts.mealTypes()).isEmpty();
        assertThat(facts.warnings()).anyMatch(warning -> warning.contains("nicht eindeutig"));
    }

    @Test
    void variantKeepsReplacementsAndDoesNotOverrideInheritance() {
        String model = """
                {"schemaVersion":1,"name":"Linsen-Bolognese","description":"Mit Linsen statt Hack.","mealTypes":["ABENDESSEN"],
                "categories":["vegetarisch"],"effort":"AUFWENDIG","ingredients":["Hackfleisch","rote Linsen","Sellerie"],
                "removedIngredients":["Hackfleisch"],"recipeText":"Linsen statt Hack anbraten.","recipeUrl":"https://example.com/linsen",
                "recipeSource":"KI-generiert","comments":"Schmeckt der Familie besser.","mealPrep":true,"homeOffice":true,"warnings":[]}
                """;

        DishDraftFacts facts = validator.accept(model, "Mit roten Linsen statt Hackfleisch.", DraftKind.VARIANT);

        assertThat(facts.name()).isEqualTo("Mit roten Linsen");
        assertThat(facts.effort()).isNull();
        assertThat(facts.mealPrep()).isNull();
        assertThat(facts.homeOffice()).isNull();
        assertThat(facts.mealTypes()).isEmpty();
        assertThat(facts.categories()).isEmpty();
        assertThat(facts.ingredients()).anyMatch(item -> item.toLowerCase().contains("linsen"));
        assertThat(facts.ingredients()).noneMatch(item -> item.toLowerCase().contains("hack"));
        assertThat(facts.removedIngredients()).anyMatch(item -> item.toLowerCase().contains("hack"));
        assertThat(facts.recipeUrl()).isNull();
        assertThat(facts.comments()).isNull();
        assertThat(facts.recipeText()).contains("Linsen");
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

    @Test
    void promptAsksForCompletionAndForbidsInventedPersonalDetails() {
        String dish = DishDraftPrompt.system(DraftKind.DISH, null, java.util.List.of());
        String variant = DishDraftPrompt.system(DraftKind.VARIANT, "Lasagne", java.util.List.of("Hackfleisch", "Tomaten"));

        assertThat(dish).contains("bekannten Gerichts").contains("KI-generiert").contains("unbekannt");
        assertThat(dish).contains("Keine Rezeptlinks").contains("persönlichen Kommentare");
        assertThat(variant).contains("Lasagne").contains("Hackfleisch").contains("removedIngredients");
    }

    private static String bolognese() {
        return """
                {"schemaVersion":1,"name":"Spaghetti Bolognese","description":"Nudeln mit einer Tomaten-Hackfleisch-Sauce.",
                "mealTypes":["MITTAGESSEN","ABENDESSEN"],"categories":["klassisch"],"effort":"MITTEL",
                "ingredients":["Spaghetti","Hackfleisch","Tomaten","Zwiebeln","Karotten","Sellerie"],"removedIngredients":[],
                "recipeText":"Zwiebeln und Karotten anschwitzen, Hackfleisch anbraten und mit Tomaten köcheln.","recipeUrl":null,
                "recipeSource":"KI-generiert","comments":null,"mealPrep":true,"homeOffice":false,"warnings":[]}
                """;
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

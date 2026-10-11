package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.MealType;
import org.junit.jupiter.api.Test;

class DishDraftFactsTest {

    @Test
    void readsASimpleDishWithoutInventingDetails() {
        DishDraftFacts facts = DishDraftFacts.read(
                "Leg Spaghetti Bolognese an. Dauert ungefähr 45 Minuten und eignet sich gut zum Vorkochen.",
                DraftKind.DISH);

        assertThat(facts.name()).isEqualTo("Spaghetti Bolognese");
        assertThat(facts.effort()).isEqualTo(Effort.MITTEL);
        assertThat(facts.mealPrep()).isTrue();
        assertThat(facts.homeOffice()).isNull();
        assertThat(facts.mealTypes()).isEmpty();
        assertThat(facts.ingredients()).isEmpty();
        assertThat(facts.categories()).isEmpty();
    }

    @Test
    void readsAnIncompleteDescriptionAsNameOnly() {
        DishDraftFacts facts = DishDraftFacts.read("Wir machen manchmal Kartoffelgratin.", DraftKind.DISH);

        assertThat(facts.name()).isEqualTo("Kartoffelgratin");
        assertThat(facts.effort()).isNull();
        assertThat(facts.mealPrep()).isNull();
        assertThat(facts.homeOffice()).isNull();
        assertThat(facts.ingredients()).isEmpty();
        assertThat(facts.mealTypes()).isEmpty();
    }

    @Test
    void readsAnExplicitMealAndEffortWord() {
        DishDraftFacts facts = DishDraftFacts.read(
                "Wir essen gerne Hähnchencurry mit Reis. Das ist ein Abendessen, dauert ungefähr eine Stunde, ist mittelschwer und lässt sich gut aufwärmen.",
                DraftKind.DISH);

        assertThat(facts.name()).isEqualTo("Hähnchencurry mit Reis");
        assertThat(facts.mealTypes()).containsExactly(MealType.ABENDESSEN);
        assertThat(facts.effort()).isEqualTo(Effort.MITTEL);
        assertThat(facts.ingredients()).isEmpty();
        assertThat(facts.comments()).isEqualTo("Lässt sich aufwärmen.");
        assertThat(facts.mealPrep()).isNull();
    }

    @Test
    void mapsShortDurationToSimpleEffort() {
        DishDraftFacts facts = DishDraftFacts.read("Dauert 20 Minuten. Omelett.", DraftKind.DISH);

        assertThat(facts.effort()).isEqualTo(Effort.EINFACH);
    }

    @Test
    void readsMealPrepYesNoAndUnknown() {
        assertThat(DishDraftFacts.read("Ist gut zum Vorkochen. Suppe.", DraftKind.DISH).mealPrep()).isTrue();
        assertThat(DishDraftFacts.read("Ist nicht zum Vorkochen geeignet. Suppe.", DraftKind.DISH).mealPrep()).isFalse();
        assertThat(DishDraftFacts.read("Suppe.", DraftKind.DISH).mealPrep()).isNull();
    }

    @Test
    void readsHomeOfficeYesNoAndUnknown() {
        assertThat(DishDraftFacts.read("Fürs Homeoffice geeignet. Suppe.", DraftKind.DISH).homeOffice()).isTrue();
        assertThat(DishDraftFacts.read("Nicht fürs Homeoffice. Suppe.", DraftKind.DISH).homeOffice()).isFalse();
        assertThat(DishDraftFacts.read("Suppe.", DraftKind.DISH).homeOffice()).isNull();
    }

    @Test
    void readsListedIngredientsAndIgnoresTheDishName() {
        DishDraftFacts listed = DishDraftFacts.read(
                "Leg Linsensuppe an. Zutaten: rote Linsen und Karotten.",
                DraftKind.DISH);
        DishDraftFacts named = DishDraftFacts.read("Leg Hähnchencurry mit Reis an.", DraftKind.DISH);

        assertThat(listed.ingredients()).containsExactly("rote Linsen", "Karotten");
        assertThat(named.ingredients()).isEmpty();
        assertThat(named.name()).isEqualTo("Hähnchencurry mit Reis");
    }

    @Test
    void readsAVariantSubstitutionWithoutCopyingOtherProperties() {
        DishDraftFacts facts = DishDraftFacts.read(
                "Mit roten Linsen statt Hackfleisch.",
                DraftKind.VARIANT);

        assertThat(facts.name()).isEqualTo("Mit roten Linsen");
        assertThat(facts.ingredients()).containsExactly("roten Linsen");
        assertThat(facts.removedIngredients()).containsExactly("Hackfleisch");
        assertThat(facts.effort()).isNull();
        assertThat(facts.mealPrep()).isNull();
        assertThat(facts.homeOffice()).isNull();
        assertThat(facts.mealTypes()).isEmpty();
    }

    @Test
    void overridesOnlyExplicitVariantProperties() {
        DishDraftFacts facts = DishDraftFacts.read(
                "Schärfere Variante, dauert 20 Minuten und ist nicht fürs Homeoffice.",
                DraftKind.VARIANT);

        assertThat(facts.name()).isEqualTo("Scharf");
        assertThat(facts.effort()).isEqualTo(Effort.EINFACH);
        assertThat(facts.homeOffice()).isFalse();
        assertThat(facts.mealPrep()).isNull();
        assertThat(facts.ingredientsMentioned()).isFalse();
    }

    @Test
    void keepsMealTypesOffVariants() {
        DishDraftFacts facts = DishDraftFacts.read(
                "Vegetarische Variante zum Abendessen.",
                DraftKind.VARIANT);

        assertThat(facts.name()).isEqualTo("Vegetarisch");
        assertThat(facts.mealTypes()).isEmpty();
        assertThat(facts.warnings()).anyMatch(warning -> warning.contains("Grundgericht"));
    }
}

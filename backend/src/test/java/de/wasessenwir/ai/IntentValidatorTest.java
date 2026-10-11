package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.wasessenwir.dish.MealType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IntentValidatorTest {

    private final IntentValidator validator = new IntentValidator(new ObjectMapper());

    @Test
    void acceptsAVersionedIntentAndIgnoresUnknownFields() {
        MealIntent intent = validator.parse("""
                {"version":1,"mealType":"MITTAGESSEN","date":"2026-10-21","participantNames":["Katja"],
                "onlyNamedParticipants":true,"preferSimple":true,"preferHomeOffice":false,"preferMealPrep":true,
                "dishSearchTerms":["Nudeln"],"avoidDishTerms":["Pizza"],"needsClarification":false,
                "clarificationQuestion":null,"score":99,"url":"https://example.invalid"}
                """);

        assertThat(intent.mealType()).isEqualTo(MealType.MITTAGESSEN);
        assertThat(intent.date()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(intent.participantNames()).containsExactly("Katja");
        assertThat(intent.onlyNamedParticipants()).isTrue();
        assertThat(intent.preferSimple()).isTrue();
        assertThat(intent.preferMealPrep()).isTrue();
        assertThat(intent.dishSearchTerms()).containsExactly("Nudeln");
        assertThat(intent.avoidDishTerms()).containsExactly("Pizza");
    }

    @Test
    void stripsFencesAndThinking() {
        MealIntent intent = validator.parse("""
                <think>intern</think>
                ```json
                {"version":1,"mealType":null,"date":null,"participantNames":[],"onlyNamedParticipants":false,
                "preferSimple":false,"preferHomeOffice":false,"preferMealPrep":false,"dishSearchTerms":[],
                "avoidDishTerms":[],"needsClarification":false,"clarificationQuestion":null}
                ```
                """);

        assertThat(intent.mealType()).isNull();
        assertThat(intent.date()).isNull();
    }

    @Test
    void rejectsInvalidJsonUnknownMealTypeIdsAndUrls() {
        assertThatThrownBy(() -> validator.parse("kein json")).isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.parse(valid("\"mealType\":\"BRUNCH\""))).isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.parse(valid("\"participantIds\":[\"00000000-0000-0000-0000-000000000099\"]")))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.parse(valid("\"dishSearchTerms\":[\"https://example.invalid\"]")))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.parse(valid("\"date\":\"gestern\""))).isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.parse(valid("\"preferSimple\":\"yes\""))).isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> validator.parse(valid("\"version\":2"))).isInstanceOf(AiProviderException.class);
    }

    private static String valid(String replacement) {
        String json = """
                {"version":1,"mealType":"ABENDESSEN","date":null,"participantNames":[],"onlyNamedParticipants":false,
                "preferSimple":false,"preferHomeOffice":false,"preferMealPrep":false,"dishSearchTerms":[],
                "avoidDishTerms":[],"needsClarification":false,"clarificationQuestion":null}
                """;
        if (replacement.startsWith("\"version\"")) {
            return json.replace("\"version\":1", replacement);
        }
        if (replacement.startsWith("\"mealType\"")) {
            return json.replace("\"mealType\":\"ABENDESSEN\"", replacement);
        }
        if (replacement.startsWith("\"date\"")) {
            return json.replace("\"date\":null", replacement);
        }
        if (replacement.startsWith("\"preferSimple\"")) {
            return json.replace("\"preferSimple\":false", replacement);
        }
        if (replacement.startsWith("\"participantIds\"")) {
            return json.replace("\"participantNames\":[]", replacement + ",\"participantNames\":[]");
        }
        return json.replace("\"dishSearchTerms\":[]", replacement);
    }
}

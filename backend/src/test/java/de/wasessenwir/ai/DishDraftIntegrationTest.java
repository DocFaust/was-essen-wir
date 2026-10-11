package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.dish.DishDetailResponse;
import de.wasessenwir.dish.DishWriteRequest;
import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.MealType;
import de.wasessenwir.dish.Suitability;
import de.wasessenwir.rating.RatingWriteRequest;
import de.wasessenwir.recommendation.RecommendationRequest;
import de.wasessenwir.security.LoginRequest;
import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.setup.SetupStatusResponse;
import de.wasessenwir.support.ApiSessions;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class DishDraftIntegrationTest {

    private static final String PASSWORD = "ein-langes-passwort";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        registry.add("app.ai.enabled", () -> "true");
        registry.add("app.ai.provider", () -> "fixture");
    }

    @Autowired
    private TestRestTemplate rawTemplate;

    @Autowired
    private CapturingSetupTokenPublisher tokens;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestRestTemplate restTemplate;

    @BeforeEach
    void signIn() {
        restTemplate = new ApiSessions(rawTemplate).open();
        SetupStatusResponse status = restTemplate.getForEntity("/api/setup/status", SetupStatusResponse.class).getBody();
        if (status != null && status.setupRequired()) {
            assertThat(restTemplate.postForEntity(
                    "/api/setup",
                    new SetupRequest("Ada", "Ada Lovelace", PASSWORD, PASSWORD, tokens.token()),
                    String.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        assertThat(restTemplate.postForEntity("/api/auth/login", new LoginRequest("Ada", PASSWORD), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        jdbcTemplate.update("delete from dish");
    }

    @Test
    void createsADishDraftFromNaturalLanguageWithoutWriting() {
        int before = count("dish");

        DishDraftResponse draft = draft(
                "Leg Spaghetti Bolognese an. Dauert ungefähr 45 Minuten und eignet sich gut zum Vorkochen.",
                DraftKind.DISH,
                null);

        assertThat(draft.outcome()).isEqualTo(DishDraftResponse.DraftOutcome.DRAFT);
        assertThat(draft.schemaVersion()).isEqualTo(1);
        assertThat(draft.name()).isEqualTo("Spaghetti Bolognese");
        assertThat(draft.effort()).isEqualTo(Effort.MITTEL);
        assertThat(draft.mealPrep()).isTrue();
        assertThat(draft.homeOffice()).isNull();
        assertThat(draft.mealTypes()).isEmpty();
        assertThat(draft.ingredients()).isEmpty();
        assertThat(draft.recognized()).contains("name", "effort", "mealPrep");
        assertThat(draft.unknown()).contains("homeOffice", "ingredients", "mealTypes");
        assertThat(count("dish")).isEqualTo(before);
    }

    @Test
    void leavesUnknownPropertiesEmpty() {
        DishDraftResponse draft = draft("Wir machen manchmal Kartoffelgratin.", DraftKind.DISH, null);

        assertThat(draft.name()).isEqualTo("Kartoffelgratin");
        assertThat(draft.effort()).isNull();
        assertThat(draft.mealPrep()).isNull();
        assertThat(draft.homeOffice()).isNull();
        assertThat(draft.ingredients()).isEmpty();
        assertThat(draft.unknown()).contains("effort", "mealPrep", "homeOffice", "ingredients");
    }

    @Test
    void readsMealEffortIngredientsAndSuitabilityFromTheText() {
        DishDraftResponse curry = draft(
                "Wir essen gerne Hähnchencurry mit Reis. Das ist ein Abendessen, dauert ungefähr eine Stunde, ist mittelschwer und lässt sich gut aufwärmen.",
                DraftKind.DISH,
                null);
        DishDraftResponse quick = draft("Omelett. Dauert 20 Minuten.", DraftKind.DISH, null);
        DishDraftResponse listed = draft("Leg Linsensuppe an. Zutaten: rote Linsen und Karotten.", DraftKind.DISH, null);
        DishDraftResponse namedOnly = draft("Leg Hähnchencurry mit Reis an.", DraftKind.DISH, null);
        DishDraftResponse prepNo = draft("Ist nicht zum Vorkochen geeignet. Suppe.", DraftKind.DISH, null);
        DishDraftResponse officeYes = draft("Fürs Homeoffice geeignet. Eintopf.", DraftKind.DISH, null);
        DishDraftResponse officeNo = draft("Nicht fürs Homeoffice. Eintopf.", DraftKind.DISH, null);

        assertThat(curry.mealTypes()).containsExactly(MealType.ABENDESSEN);
        assertThat(curry.effort()).isEqualTo(Effort.MITTEL);
        assertThat(curry.ingredients()).isEmpty();
        assertThat(curry.mealPrep()).isNull();
        assertThat(curry.comments()).isEqualTo("Lässt sich aufwärmen.");
        assertThat(quick.effort()).isEqualTo(Effort.EINFACH);
        assertThat(listed.ingredients()).containsExactly("rote Linsen", "Karotten");
        assertThat(namedOnly.ingredients()).isEmpty();
        assertThat(prepNo.mealPrep()).isFalse();
        assertThat(officeYes.homeOffice()).isTrue();
        assertThat(officeNo.homeOffice()).isFalse();
        assertThat(draft("Nur der Name Suppe.", DraftKind.DISH, null).homeOffice()).isNull();
    }

    @Test
    void preparesAVariantWithoutOverridingInheritedProperties() {
        DishDetailResponse base = createDish(
                "Lasagne", MealType.ABENDESSEN, Effort.MITTEL, Suitability.SUITABLE, Suitability.UNKNOWN,
                List.of("Hackfleisch", "Tomaten"));

        DishDraftResponse draft = draft("Mit roten Linsen statt Hackfleisch.", DraftKind.VARIANT, base.id());

        assertThat(draft.kind()).isEqualTo(DraftKind.VARIANT);
        assertThat(draft.baseDishId()).isEqualTo(base.id());
        assertThat(draft.name()).isEqualTo("Mit roten Linsen");
        assertThat(draft.ingredientsSpecified()).isTrue();
        assertThat(draft.ingredients()).containsExactly("Tomaten", "roten Linsen");
        assertThat(draft.effort()).isNull();
        assertThat(draft.mealPrep()).isNull();
        assertThat(draft.homeOffice()).isNull();
        assertThat(draft.mealTypes()).isEmpty();
        assertThat(draft.recognized()).contains("ingredients").doesNotContain("effort", "mealPrep");
    }

    @Test
    void overridesExplicitVariantPropertiesOnly() {
        DishDetailResponse base = createDish(
                "Curry", MealType.ABENDESSEN, Effort.MITTEL, Suitability.SUITABLE, Suitability.SUITABLE, List.of());

        DishDraftResponse draft = draft(
                "Schärfere Variante, dauert 20 Minuten und ist nicht fürs Homeoffice.",
                DraftKind.VARIANT,
                base.id());

        assertThat(draft.name()).isEqualTo("Scharf");
        assertThat(draft.effort()).isEqualTo(Effort.EINFACH);
        assertThat(draft.homeOffice()).isFalse();
        assertThat(draft.mealPrep()).isNull();
        assertThat(draft.ingredientsSpecified()).isFalse();
        assertThat(draft.ingredients()).isEmpty();
    }

    @Test
    void rejectsAnUnknownBaseDishAndAsksWhenSeveralMatch() {
        createDish("Bolognese", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of());
        createDish("Spaghetti Bolognese", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of());

        ResponseEntity<String> missing = restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest("Mit roten Linsen statt Hackfleisch.", DraftKind.VARIANT, UUID.randomUUID()),
                String.class);
        ResponseEntity<DishDraftResponse> ambiguous = restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest("Die Bolognese gibt es bei uns auch mit roten Linsen.", DraftKind.VARIANT, null),
                DishDraftResponse.class);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("BASE_DISH_NOT_FOUND");
        assertThat(ambiguous.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ambiguous.getBody().outcome()).isEqualTo(DishDraftResponse.DraftOutcome.NEEDS_BASE_DISH);
        assertThat(ambiguous.getBody().candidates()).hasSize(2);
        assertThat(count("dish")).isEqualTo(2);
    }

    @Test
    void showsDuplicatesButStillAllowsSaving() {
        createDish("Spaghetti Bolognese", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of());

        DishDraftResponse draft = draft("Leg Spaghetti Bolo an.", DraftKind.DISH, null);
        ResponseEntity<DishDetailResponse> saved = restTemplate.postForEntity(
                "/api/dishes",
                new DishWriteRequest(
                        "Spaghetti Bolo", null, List.of(), List.of(), null, List.of(),
                        null, null, null, null, Suitability.UNKNOWN, Suitability.UNKNOWN, true),
                DishDetailResponse.class);

        assertThat(draft.duplicates()).isNotEmpty();
        assertThat(draft.duplicates().getFirst().message()).contains("Spaghetti Bolognese");
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(count("dish")).isEqualTo(2);
    }

    @Test
    void rejectsAnonymousAccessAndMissingCsrf() {
        DishDraftRequest body = new DishDraftRequest("Wir machen manchmal Suppe.", DraftKind.DISH, null);
        ResponseEntity<String> primed = rawTemplate.getForEntity("/api/setup/status", String.class);
        String csrf = ApiSessions.setCookie(primed.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", java.net.URLDecoder.decode(csrf, StandardCharsets.UTF_8));

        ResponseEntity<String> withoutSession = rawTemplate.exchange(
                "/api/ai/dish-drafts", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        ResponseEntity<String> withoutCsrf = rawTemplate.postForEntity("/api/ai/dish-drafts", body, String.class);

        assertThat(withoutSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(withoutCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withoutCsrf.getBody()).contains("CSRF");
    }

    @Test
    void keepsRecommendationsWorkingAndHidesRatings() {
        DishDetailResponse soup = createDish(
                "Suppe", MealType.ABENDESSEN, Effort.EINFACH, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of());
        ResponseEntity<String> rating = restTemplate.exchange(
                "/api/dishes/" + soup.id() + "/rating",
                HttpMethod.PUT,
                new HttpEntity<>(new RatingWriteRequest(5)),
                String.class);
        UUID ada = jdbcTemplate.queryForObject("select id from app_user where username_normalized = 'ada'", UUID.class);

        ResponseEntity<String> draft = restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest("Wir machen oft Suppe.", DraftKind.DISH, null),
                String.class);
        ResponseEntity<AiRecommendationResponse> assistant = restTemplate.postForEntity(
                "/api/ai/recommendations",
                new AiRecommendationRequest("Heute Abend etwas Schnelles", 1L),
                AiRecommendationResponse.class);
        ResponseEntity<String> classic = restTemplate.postForEntity(
                "/api/recommendations",
                new RecommendationRequest(MealType.ABENDESSEN, List.of(ada), false, false, false, List.of(), 3L, null),
                String.class);

        assertThat(rating.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(draft.getHeaders().getCacheControl()).contains("no-store");
        assertThat(draft.getBody()).doesNotContain("stars").doesNotContain("Lovelace");
        assertThat(assistant.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assistant.getBody().suggestions()).isNotEmpty();
        assertThat(classic.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("rating")).isEqualTo(1);
    }

    private DishDraftResponse draft(String description, DraftKind kind, UUID baseDishId) {
        ResponseEntity<DishDraftResponse> response = restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest(description, kind, baseDishId),
                DishDraftResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        return response.getBody();
    }

    private DishDetailResponse createDish(
            String name,
            MealType mealType,
            Effort effort,
            Suitability mealPrep,
            Suitability homeOffice,
            List<String> ingredients) {
        ResponseEntity<DishDetailResponse> response = restTemplate.postForEntity(
                "/api/dishes",
                new DishWriteRequest(
                        name, null, List.of(mealType), List.of(), effort, ingredients,
                        null, null, null, null, mealPrep, homeOffice, true),
                DishDetailResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }
}

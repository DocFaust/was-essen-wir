package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.wasessenwir.dish.DishDetailResponse;
import de.wasessenwir.dish.DishWriteRequest;
import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.MealType;
import de.wasessenwir.dish.Suitability;
import de.wasessenwir.dish.VariantWriteRequest;
import de.wasessenwir.rating.RatingWriteRequest;
import de.wasessenwir.recommendation.RecommendationRequest;
import de.wasessenwir.recommendation.RecommendationResponse;
import de.wasessenwir.recommendation.SuggestionResponse;
import de.wasessenwir.security.LoginRequest;
import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.setup.SetupStatusResponse;
import de.wasessenwir.support.ApiSessions;
import de.wasessenwir.user.CreateUserRequest;
import de.wasessenwir.user.UserResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class AiOllamaIntegrationTest {

    private static final String PASSWORD = "ein-langes-passwort";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer SERVER = startServer();
    private static volatile Consumer<HttpExchange> behavior = exchange -> write(exchange, 200, wrapper("{}"));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        registry.add("app.ai.enabled", () -> "true");
        registry.add("app.ai.provider", () -> "ollama");
        registry.add("app.ai.ollama.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
        registry.add("app.ai.ollama.model", () -> "test-model");
        registry.add("app.ai.ollama.timeout", () -> "2s");
    }

    @Autowired
    private TestRestTemplate rawTemplate;

    @Autowired
    private CapturingSetupTokenPublisher tokens;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestRestTemplate restTemplate;

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @BeforeEach
    void signIn() {
        REQUESTS.clear();
        reply("{}");
        restTemplate = new ApiSessions(rawTemplate).open();
        SetupStatusResponse status = restTemplate.getForEntity("/api/setup/status", SetupStatusResponse.class).getBody();
        if (status != null && status.setupRequired()) {
            ResponseEntity<String> setup = restTemplate.postForEntity(
                    "/api/setup",
                    new SetupRequest("Ada", "Ada Lovelace", PASSWORD, PASSWORD, tokens.token()),
                    String.class);
            assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        ResponseEntity<String> login = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest("Ada", PASSWORD), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbcTemplate.update("delete from dish");
    }

    @Test
    void translatesAValidIntentIntoExistingSuggestions() {
        createDish("Suppe", MealType.ABENDESSEN, Effort.EINFACH, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        createDish("Braten", MealType.ABENDESSEN, Effort.AUFWENDIG, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        createDish("Müsli", MealType.FRUEHSTUECK, Effort.EINFACH, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        reply(intent("ABENDESSEN", null, "[]", false, false, false, false, "[]", "[]", false, null));

        ResponseEntity<String> raw = askRaw("Was könnten wir heute Abend essen?", 11L);
        AiRecommendationResponse body = ask("Was könnten wir heute Abend essen?", 11L);

        assertThat(raw.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(raw.getHeaders().getCacheControl()).contains("no-store");
        assertThat(body.suggestions()).isNotEmpty();
        assertThat(body.suggestions()).extracting(SuggestionResponse::dishName).doesNotContain("Müsli");
        assertThat(body.criteria().mealType()).isEqualTo(MealType.ABENDESSEN);
        assertThat(body.criteria().participantNames()).containsExactly("Ada Lovelace");
        assertThat(body.reply()).contains("Abendessen");
        assertThat(body.clarificationQuestion()).isNull();
        assertThat(raw.getBody()).doesNotContain("\"stars\"").doesNotContain("myRating").doesNotContain("\"score\"");
        assertThat(REQUESTS.getLast()).contains("\"model\":\"test-model\"").contains("Was könnten wir heute Abend essen?");
    }

    @Test
    void usesTodayAndAFutureReferenceDate() {
        DishDetailResponse roast = createDish(
                "Sonntagsbraten", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        LocalDate later = today.plusDays(10);
        ResponseEntity<String> plan = restTemplate.postForEntity(
                "/api/meal-plans",
                new de.wasessenwir.meal.MealEventWriteRequest(
                        later, MealType.ABENDESSEN, roast.id(), null, List.of(userId("ada")), null),
                String.class);
        assertThat(plan.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        reply(intent("ABENDESSEN", null, "[]", false, false, false, false, "[]", "[]", false, null));
        AiRecommendationResponse forToday = ask("Was essen wir heute?", 3L);
        reply(intent("ABENDESSEN", later.toString(), "[]", false, false, false, false, "[]", "[]", false, null));
        AiRecommendationResponse forLater = ask("Was essen wir in zehn Tagen?", 3L);

        assertThat(forToday.criteria().date()).isEqualTo(today);
        assertThat(forToday.suggestions().getFirst().reason()).doesNotContain("eingeplant");
        assertThat(forLater.criteria().date()).isEqualTo(later);
        assertThat(forLater.suggestions().getFirst().reason()).contains("eingeplant");
    }

    @Test
    void forwardsQuickHomeOfficeAndMealPrepToTheRecommendationService() {
        createDish("Leicht", MealType.ABENDESSEN, Effort.EINFACH, Suitability.SUITABLE, Suitability.SUITABLE, List.of(), List.of());
        createDish("Schwer", MealType.ABENDESSEN, Effort.AUFWENDIG, Suitability.UNSUITABLE, Suitability.UNSUITABLE, List.of(), List.of());
        createDish("Neutral", MealType.ABENDESSEN, Effort.MITTEL, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        UUID ada = userId("ada");

        assertPreference(ada, true, false, false);
        assertPreference(ada, false, true, false);
        assertPreference(ada, false, false, true);
    }

    @Test
    void matchesCatalogWishesAndDoesNotInventDishes() {
        createDish("Spaghetti", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of("Nudeln"));
        DishDetailResponse pizza = createDish(
                "Pizza", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        addVariant(pizza.id(), "Salami");
        createDish("Suppe", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of("Eintopf"), List.of());

        reply(intent("ABENDESSEN", null, "[]", false, false, false, false, "[\"Nudeln\"]", "[]", false, null));
        AiRecommendationResponse noodles = ask("Etwas mit Nudeln", 1L);
        reply(intent("ABENDESSEN", null, "[]", false, false, false, false, "[\"Salami\"]", "[]", false, null));
        AiRecommendationResponse salami = ask("Etwas wie Salami", 1L);
        reply(intent("ABENDESSEN", null, "[]", false, false, false, false, "[]", "[\"Pizza\"]", false, null));
        AiRecommendationResponse withoutPizza = ask("Keine Pizza", 1L);
        reply(intent("ABENDESSEN", null, "[]", false, false, false, false, "[\"Lasagne\"]", "[]", false, null));
        ResponseEntity<String> missing = askRaw("Etwas Ähnliches wie Lasagne", 1L);

        assertThat(noodles.suggestions()).extracting(SuggestionResponse::dishName).containsOnly("Spaghetti");
        assertThat(noodles.criteria().matchedDishes()).contains("Spaghetti");
        assertThat(salami.suggestions()).extracting(SuggestionResponse::variantName).containsOnly("Salami");
        assertThat(withoutPizza.suggestions()).extracting(SuggestionResponse::dishName).doesNotContain("Pizza");
        assertThat(withoutPizza.suggestions()).isNotEmpty();
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(missing.getBody()).contains("Lasagne").doesNotContain("Spaghetti");
        assertThat(missing.getBody()).contains("keinen passenden Vorschlag");
    }

    @Test
    void asksWhenAFamilyNameIsAmbiguousAndRejectsInventedIds() {
        createMember("alexmeier", "Alex Meier");
        createMember("alexschulz", "Alex Schulz");
        createDish("Suppe", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        reply(intent("ABENDESSEN", null, "[\"Alex\"]", false, false, false, false, "[]", "[]", false, null));

        AiRecommendationResponse ambiguous = ask("Was essen wir mit Alex?", 1L);

        assertThat(ambiguous.suggestions()).isEmpty();
        assertThat(ambiguous.clarificationQuestion()).contains("Alex Meier", "Alex Schulz");
        assertThat(REQUESTS.getLast()).doesNotContain("Meier").doesNotContain("Schulz");

        String unknown = "00000000-0000-0000-0000-000000000099";
        reply("{\"version\":1,\"mealType\":\"ABENDESSEN\",\"date\":null,\"participantIds\":[\"" + unknown
                + "\"],\"participantNames\":[],\"onlyNamedParticipants\":false,\"preferSimple\":false,"
                + "\"preferHomeOffice\":false,\"preferMealPrep\":false,\"dishSearchTerms\":[],\"avoidDishTerms\":[],"
                + "\"needsClarification\":false,\"clarificationQuestion\":null}");
        ResponseEntity<String> invented = askRaw("Mit einer erfundenen ID", 1L);
        assertThat(invented.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(invented.getBody()).contains("AI_INVALID").doesNotContain(unknown);
    }

    @Test
    void rejectsInvalidModelOutputWithoutChangingStoredData() {
        createDish("Suppe", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        rateDish(restTemplate, userDish(), 5);
        int dishes = count("dish");
        int ratings = count("rating");
        int meals = count("meal_event");
        int users = count("app_user");

        reply("nicht json");
        ResponseEntity<String> invalid = askRaw("Was essen wir?", 1L);
        reply(intent("BRUNCH", null, "[]", false, false, false, false, "[]", "[]", false, null));
        ResponseEntity<String> meal = askRaw("Brunch", 1L);

        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(invalid.getBody()).contains("nicht auswerten").doesNotContain("nicht json").doesNotContain("Exception");
        assertThat(meal.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(count("dish")).isEqualTo(dishes);
        assertThat(count("rating")).isEqualTo(ratings);
        assertThat(count("meal_event")).isEqualTo(meals);
        assertThat(count("app_user")).isEqualTo(users);
    }

    @Test
    void doesNotSendRatingsHistoryOrOtherFamilyMembers() {
        createMember("katjageheim", "Katja Sondername");
        DishDetailResponse secret = createDish(
                "GeheimesRagout", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        rateDish(restTemplate, secret.id(), 5);
        reply(intent(null, null, "[]", false, true, false, false, "[]", "[]", false, null));

        ResponseEntity<String> response = askRaw("Was könnten wir heute Abend essen?", 4L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("stars").doesNotContain("Sondername");
        String outbound = REQUESTS.getLast();
        assertThat(outbound).contains("FRUEHSTUECK").contains("Was könnten wir heute Abend essen?");
        assertThat(outbound).doesNotContain("Sondername").doesNotContain("Lovelace").doesNotContain("GeheimesRagout");
        assertThat(outbound).doesNotContain("stars").doesNotContain("password").doesNotContain("hash");
        assertThat(outbound.length()).isLessThan(6_000);
    }

    @Test
    void returnsTheModelQuestionWithoutRecommending() {
        createDish("Suppe", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        reply(intent(null, null, "[]", false, false, false, false, "[]", "[]", true, "Meinst du Mittag oder Abend?"));

        AiRecommendationResponse response = ask("Irgendwas", 1L);

        assertThat(response.clarificationQuestion()).isEqualTo("Meinst du Mittag oder Abend?");
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    void timesOutWithoutRetrying() {
        createDish("Suppe", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        behavior = exchange -> {
            try {
                Thread.sleep(5_000);
                write(exchange, 200, wrapper("{}"));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };

        ResponseEntity<String> response = askRaw("Was essen wir?", 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).contains("AI_TIMEOUT").doesNotContain("http://").doesNotContain("Exception");
        assertThat(REQUESTS).hasSize(1);
    }

    @Test
    void rejectsAnonymousAccessAndMissingCsrf() {
        reply(intent("ABENDESSEN", null, "[]", false, false, false, false, "[]", "[]", false, null));
        AiRecommendationRequest body = new AiRecommendationRequest("Was essen wir?", 1L);
        ResponseEntity<String> primed = rawTemplate.getForEntity("/api/setup/status", String.class);
        String csrf = ApiSessions.setCookie(primed.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", java.net.URLDecoder.decode(csrf, StandardCharsets.UTF_8));

        ResponseEntity<String> withoutSession = rawTemplate.exchange(
                "/api/ai/recommendations", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        ResponseEntity<String> withoutCsrf = rawTemplate.postForEntity("/api/ai/recommendations", body, String.class);

        assertThat(withoutSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(withoutCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withoutCsrf.getBody()).contains("CSRF");
    }

    @Test
    void draftsADishWithoutSendingRatingsAndRejectsABadModelReply() {
        createDish("GeheimesRagout", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of("Hackfleisch"));
        rateDish(restTemplate, userDish(), 5);
        reply("""
                {"schemaVersion":1,"name":"Kartoffelgratin","description":null,"mealTypes":["ABENDESSEN"],
                "categories":[],"effort":"EINFACH","ingredients":["Trüffel"],"removedIngredients":[],
                "recipeText":null,"recipeUrl":null,"recipeSource":null,"comments":null,
                "mealPrep":true,"homeOffice":null,"warnings":[]}
                """);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest("Wir machen manchmal Kartoffelgratin.", DraftKind.DISH, null),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).contains("Kartoffelgratin").doesNotContain("Trüffel").doesNotContain("ABENDESSEN");
        assertThat(response.getBody()).contains("\"mealPrep\":null");
        String outbound = REQUESTS.getLast();
        assertThat(outbound).contains("schemaVersion").contains("Wir machen manchmal Kartoffelgratin.");
        assertThat(outbound).doesNotContain("\"format\":\"json\"");
        assertThat(outbound).doesNotContain("stars").doesNotContain("Lovelace").doesNotContain("Hackfleisch");
        assertThat(outbound).doesNotContain("GeheimesRagout");
        assertThat(count("dish")).isEqualTo(1);

        reply("kein json");
        ResponseEntity<String> invalid = restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest("Wir machen manchmal Suppe.", DraftKind.DISH, null),
                String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(invalid.getBody()).contains("AI_INVALID").doesNotContain("Exception");

        reply(template().replace("\"name\":\"Suppe\",", ""));
        ResponseEntity<String> missing = restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest("Wir machen manchmal Suppe.", DraftKind.DISH, null),
                String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(missing.getBody()).contains("AI_INVALID");

        reply(template().replace("\"effort\":null", "\"effort\":\"SCHNELL\""));
        ResponseEntity<String> badEnum = restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest("Wir machen manchmal Suppe.", DraftKind.DISH, null),
                String.class);
        assertThat(badEnum.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(count("dish")).isEqualTo(1);
    }

    @Test
    void draftTimesOutAndReportsAnUnreachableProvider() {
        behavior = exchange -> {
            try {
                Thread.sleep(5_000);
                write(exchange, 200, wrapper("{}"));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        ResponseEntity<String> timeout = restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest("Wir machen manchmal Suppe.", DraftKind.DISH, null),
                String.class);
        assertThat(timeout.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(timeout.getBody()).contains("AI_TIMEOUT").doesNotContain("http://");
        assertThat(REQUESTS).hasSize(1);

        behavior = exchange -> write(exchange, 503, "{}");
        ResponseEntity<String> down = restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest("Wir machen manchmal Suppe.", DraftKind.DISH, null),
                String.class);
        assertThat(down.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(down.getBody()).contains("AI_UNAVAILABLE");
        assertThat(count("dish")).isZero();
    }

    @Test
    void keepsTheClassicRecommendationAvailable() {
        createDish("Suppe", MealType.ABENDESSEN, null, Suitability.UNKNOWN, Suitability.UNKNOWN, List.of(), List.of());
        UUID ada = userId("ada");

        ResponseEntity<RecommendationResponse> classic = restTemplate.postForEntity(
                "/api/recommendations",
                new RecommendationRequest(MealType.ABENDESSEN, List.of(ada), false, false, false, List.of(), 8L, null),
                RecommendationResponse.class);

        assertThat(classic.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(classic.getBody().suggestions()).extracting(SuggestionResponse::dishName).containsExactly("Suppe");
    }

    private void assertPreference(UUID ada, boolean simple, boolean homeOffice, boolean mealPrep) {
        Long seed = null;
        List<String> expected = List.of();
        for (long candidate = 1; candidate <= 40; candidate++) {
            List<String> enabled = names(recommend(ada, simple, homeOffice, mealPrep, candidate));
            List<String> disabled = names(recommend(ada, false, false, false, candidate));
            if (!enabled.equals(disabled)) {
                seed = candidate;
                expected = enabled;
                break;
            }
        }
        assertThat(seed).isNotNull();
        reply(intent("ABENDESSEN", null, "[]", false, simple, homeOffice, mealPrep, "[]", "[]", false, null));
        assertThat(names(ask("Bitte passend vorschlagen", seed))).containsExactlyElementsOf(expected);
    }

    private RecommendationResponse recommend(UUID ada, boolean simple, boolean homeOffice, boolean mealPrep, long seed) {
        ResponseEntity<RecommendationResponse> response = restTemplate.postForEntity(
                "/api/recommendations",
                new RecommendationRequest(
                        MealType.ABENDESSEN, List.of(ada), simple, homeOffice, mealPrep, List.of(), seed, null),
                RecommendationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private AiRecommendationResponse ask(String message, Long seed) {
        ResponseEntity<AiRecommendationResponse> response = restTemplate.postForEntity(
                "/api/ai/recommendations", new AiRecommendationRequest(message, seed), AiRecommendationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<String> askRaw(String message, Long seed) {
        return restTemplate.postForEntity(
                "/api/ai/recommendations", new AiRecommendationRequest(message, seed), String.class);
    }

    private DishDetailResponse createDish(
            String name,
            MealType mealType,
            Effort effort,
            Suitability mealPrep,
            Suitability homeOffice,
            List<String> categories,
            List<String> ingredients) {
        ResponseEntity<DishDetailResponse> response = restTemplate.postForEntity(
                "/api/dishes",
                new DishWriteRequest(
                        name, null, List.of(mealType), categories, effort, ingredients,
                        null, null, null, null, mealPrep, homeOffice, true),
                DishDetailResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void addVariant(UUID dishId, String name) {
        ResponseEntity<DishDetailResponse> response = restTemplate.postForEntity(
                "/api/dishes/" + dishId + "/variants",
                new VariantWriteRequest(name, null, null, null, null, null, null, null, null, null),
                DishDetailResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void rateDish(TestRestTemplate session, UUID dishId, int stars) {
        ResponseEntity<String> response = session.exchange(
                "/api/dishes/" + dishId + "/rating",
                HttpMethod.PUT,
                new HttpEntity<>(new RatingWriteRequest(stars)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UserResponse createMember(String username, String displayName) {
        ResponseEntity<UserResponse> created = restTemplate.postForEntity(
                "/api/users",
                new CreateUserRequest(username, displayName, "temporaer-pass", "MEMBER"),
                UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }

    private UUID userDish() {
        return jdbcTemplate.queryForObject("select id from dish", UUID.class);
    }

    private UUID userId(String usernameNormalized) {
        return jdbcTemplate.queryForObject(
                "select id from app_user where username_normalized = ?", UUID.class, usernameNormalized);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static List<String> names(RecommendationResponse response) {
        return names(response.suggestions());
    }

    private static List<String> names(AiRecommendationResponse response) {
        return names(response.suggestions());
    }

    private static List<String> names(List<SuggestionResponse> suggestions) {
        List<String> names = new ArrayList<>();
        for (SuggestionResponse suggestion : suggestions) {
            names.add(suggestion.dishId() + ":" + suggestion.variantId());
        }
        return names;
    }

    private static void reply(String content) {
        behavior = exchange -> write(exchange, 200, wrapper(content));
    }

    private static String intent(
            String mealType,
            String date,
            String names,
            boolean onlyNamed,
            boolean simple,
            boolean homeOffice,
            boolean mealPrep,
            String search,
            String avoid,
            boolean clarify,
            String question) {
        return """
                {"version":1,"mealType":%s,"date":%s,"participantNames":%s,"onlyNamedParticipants":%s,\
                "preferSimple":%s,"preferHomeOffice":%s,"preferMealPrep":%s,"dishSearchTerms":%s,\
                "avoidDishTerms":%s,"needsClarification":%s,"clarificationQuestion":%s}\
                """.formatted(
                mealType == null ? "null" : "\"" + mealType + "\"",
                date == null ? "null" : "\"" + date + "\"",
                names,
                onlyNamed,
                simple,
                homeOffice,
                mealPrep,
                search,
                avoid,
                clarify,
                question == null ? "null" : "\"" + question + "\"");
    }

    private static String template() {
        return """
                {"schemaVersion":1,"name":"Suppe","description":null,"mealTypes":[],"categories":[],
                "effort":null,"ingredients":[],"removedIngredients":[],"recipeText":null,"recipeUrl":null,
                "recipeSource":null,"comments":null,"mealPrep":null,"homeOffice":null,"warnings":[]}
                """;
    }

    private static String wrapper(String content) {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("message").put("content", content);
        return JSON.writeValueAsString(root);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                behavior.accept(exchange);
            });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void write(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (IOException exception) {
            // Der Client kann die Verbindung nach einem Timeout bereits geschlossen haben.
        }
    }
}

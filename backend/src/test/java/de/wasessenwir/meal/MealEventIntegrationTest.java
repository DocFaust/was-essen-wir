package de.wasessenwir.meal;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.dish.DishDetailResponse;
import de.wasessenwir.dish.DishWriteRequest;
import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.MealType;
import de.wasessenwir.dish.Suitability;
import de.wasessenwir.dish.VariantWriteRequest;
import de.wasessenwir.rating.OwnRatingResponse;
import de.wasessenwir.rating.RatingWriteRequest;
import de.wasessenwir.recommendation.Candidate;
import de.wasessenwir.recommendation.RatingLookup;
import de.wasessenwir.recommendation.RecommendationPreferences;
import de.wasessenwir.recommendation.RecommendationRequest;
import de.wasessenwir.recommendation.RecommendationResponse;
import de.wasessenwir.recommendation.RecommendationScorer;
import de.wasessenwir.recommendation.RepetitionIndex;
import de.wasessenwir.recommendation.SuggestionReasoner;
import de.wasessenwir.security.LoginRequest;
import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.setup.SetupStatusResponse;
import de.wasessenwir.support.ApiSessions;
import de.wasessenwir.user.CreateUserRequest;
import de.wasessenwir.user.PasswordChangeRequest;
import de.wasessenwir.user.UpdateUserRequest;
import de.wasessenwir.user.UserResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class MealEventIntegrationTest {

    private static final String PASSWORD = "ein-langes-passwort";
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private TestRestTemplate rawTemplate;

    @Autowired
    private CapturingSetupTokenPublisher tokens;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MealEventRepository mealEvents;

    @Autowired
    private Clock clock;

    private TestRestTemplate restTemplate;

    @BeforeEach
    void signInAndClearMeals() {
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
        jdbcTemplate.update("delete from meal_event");
        jdbcTemplate.update("delete from dish");
    }

    @Test
    void plansChangesMovesAndDeletesAMeal() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Berlin"));
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN);
        UUID salami = addVariant(pizza.id(), "Salami");
        UUID ada = userId("ada");
        UserResponse bea = createMember("BeaPlan");
        TestRestTemplate beaSession = memberSession(bea);

        ResponseEntity<String> createdRaw = beaSession.postForEntity(
                "/api/meal-plans",
                meal(MONDAY, MealType.ABENDESSEN, pizza.id(), salami, List.of(ada, bea.id()), "Knusprig"),
                String.class);
        MealEventResponse created = beaSession.postForEntity(
                "/api/meal-plans",
                meal(MONDAY, MealType.MITTAGESSEN, pizza.id(), null, List.of(bea.id()), null),
                MealEventResponse.class).getBody();

        assertThat(createdRaw.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdRaw.getHeaders().getCacheControl()).contains("no-store");
        assertThat(createdRaw.getBody()).contains("Pizza · Salami").doesNotContain("createdBy").doesNotContain("password");
        UUID dinnerId = jdbcTemplate.queryForObject(
                "select id from meal_event where meal_type = 'ABENDESSEN'", UUID.class);
        assertThat(jdbcTemplate.queryForObject("select created_by from meal_event where id = ?", UUID.class, dinnerId))
                .isEqualTo(bea.id());

        MealEventResponse moved = restTemplate.exchange(
                "/api/meal-plans/" + dinnerId,
                HttpMethod.PUT,
                new HttpEntity<>(meal(WEDNESDAY, MealType.ABENDESSEN, pizza.id(), salami, List.of(ada), "Mehr Käse")),
                MealEventResponse.class).getBody();
        assertThat(moved.mealDate()).isEqualTo(WEDNESDAY);
        assertThat(moved.comment()).isEqualTo("Mehr Käse");
        assertThat(moved.participants()).extracting(MealParticipantResponse::userId).containsExactly(ada);
        assertThat(plans()).extracting(MealEventResponse::mealDate).contains(WEDNESDAY, MONDAY);

        ResponseEntity<String> deleted = restTemplate.exchange(
                "/api/meal-plans/" + created.id(), HttpMethod.DELETE, null, String.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(plans()).extracting(MealEventResponse::id).contains(dinnerId).doesNotContain(created.id());
    }

    @Test
    void confirmingDoesNotCreateASecondHistoryRow() {
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN);
        UUID ada = userId("ada");
        rate(pizza.id(), 4);
        MealEventResponse planned = plan(meal(MONDAY, MealType.ABENDESSEN, pizza.id(), null, List.of(ada), null));

        MealEventResponse eaten = restTemplate.postForEntity(
                "/api/meal-plans/" + planned.id() + "/eaten", null, MealEventResponse.class).getBody();
        ResponseEntity<String> again = restTemplate.postForEntity(
                "/api/meal-plans/" + planned.id() + "/eaten", null, String.class);

        assertThat(eaten.id()).isEqualTo(planned.id());
        assertThat(eaten.status()).isEqualTo(MealStatus.EATEN);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbcTemplate.queryForObject("select count(*) from meal_event", Integer.class)).isEqualTo(1);
        assertThat(plans()).isEmpty();
        assertThat(history(null)).extracting(MealEventResponse::id).containsExactly(planned.id());
        assertThat(restTemplate.getForObject("/api/dishes/" + pizza.id() + "/rating", OwnRatingResponse.class).stars())
                .isEqualTo(4);
    }

    @Test
    void recordsCorrectsAndDeletesHistoryIncludingABackfill() {
        DishDetailResponse soup = createDish("Suppe", MealType.MITTAGESSEN);
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN);
        UUID ada = userId("ada");
        MealEventResponse backfill = eat(meal(MONDAY.minusDays(3), MealType.MITTAGESSEN, soup.id(), null, List.of(ada), null));

        MealEventResponse corrected = restTemplate.exchange(
                "/api/meal-history/" + backfill.id(),
                HttpMethod.PUT,
                new HttpEntity<>(meal(MONDAY.minusDays(2), MealType.ABENDESSEN, pizza.id(), null, List.of(ada), "Doch Pizza")),
                MealEventResponse.class).getBody();
        assertThat(corrected.mealDate()).isEqualTo(MONDAY.minusDays(2));
        assertThat(corrected.mealType()).isEqualTo(MealType.ABENDESSEN);
        assertThat(corrected.dishName()).isEqualTo("Pizza");
        assertThat(corrected.comment()).isEqualTo("Doch Pizza");
        assertThat(corrected.status()).isEqualTo(MealStatus.EATEN);

        ResponseEntity<String> deleted = restTemplate.exchange(
                "/api/meal-history/" + backfill.id(), HttpMethod.DELETE, null, String.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(history(null)).isEmpty();
        ResponseEntity<String> missing = restTemplate.exchange(
                "/api/meal-history/" + backfill.id(),
                HttpMethod.PUT,
                new HttpEntity<>(meal(MONDAY, MealType.ABENDESSEN, pizza.id(), null, List.of(ada), null)),
                String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void severalMealsOnOneDayCanHaveDifferentDiners() {
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN);
        DishDetailResponse soup = createDish("Suppe", MealType.MITTAGESSEN);
        UUID ada = userId("ada");
        UserResponse bea = createMember("BeaTisch");
        plan(meal(MONDAY, MealType.ABENDESSEN, pizza.id(), null, List.of(ada), null));
        plan(meal(MONDAY, MealType.ABENDESSEN, soup.id(), null, List.of(bea.id()), null));
        eat(meal(MONDAY, MealType.MITTAGESSEN, soup.id(), null, List.of(ada, bea.id()), null));

        assertThat(plans()).hasSize(2);
        assertThat(plans()).extracting(MealEventResponse::mealDate).containsOnly(MONDAY);
        assertThat(history(ada)).singleElement().extracting(MealEventResponse::mealType).isEqualTo(MealType.MITTAGESSEN);
        assertThat(history(bea.id())[0].participants()).hasSize(2);
        assertThat(history(UUID.randomUUID())).isEmpty();
    }

    @Test
    void rejectsUnknownDinersAndAVariantFromAnotherDish() {
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN);
        DishDetailResponse pasta = createDish("Pasta", MealType.ABENDESSEN);
        UUID salami = addVariant(pizza.id(), "Salami");
        UUID ada = userId("ada");
        UserResponse inactive = createMember("Inaktiv");
        restTemplate.exchange(
                "/api/users/" + inactive.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest(inactive.displayName(), "MEMBER", false)),
                UserResponse.class);

        assertThat(postPlan(meal(MONDAY, MealType.ABENDESSEN, pizza.id(), null, List.of(UUID.randomUUID()), null)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postPlan(meal(MONDAY, MealType.ABENDESSEN, pizza.id(), null, List.of(inactive.id()), null)).getBody())
                .contains("Nur aktive Familienmitglieder");
        assertThat(postPlan(meal(MONDAY, MealType.ABENDESSEN, pasta.id(), salami, List.of(ada), null)).getBody())
                .contains("Variante");
        assertThat(postPlan(meal(MONDAY, MealType.ABENDESSEN, pizza.id(), null, List.of(), null)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject("select count(*) from meal_event", Integer.class)).isZero();
    }

    @Test
    void historySurvivesADeletedDishAndARemovedVariant() {
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN);
        UUID salami = addVariant(pizza.id(), "Salami");
        UUID ada = userId("ada");
        MealEventResponse eaten = eat(meal(MONDAY, MealType.ABENDESSEN, pizza.id(), salami, List.of(ada), "Familienessen"));

        ResponseEntity<String> removed = restTemplate.exchange(
                "/api/dishes/" + pizza.id(), HttpMethod.DELETE, null, String.class);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        MealEventResponse stored = history(null)[0];
        assertThat(stored.id()).isEqualTo(eaten.id());
        assertThat(stored.dishId()).isNull();
        assertThat(stored.variantId()).isNull();
        assertThat(stored.dishName()).isEqualTo("Pizza");
        assertThat(stored.variantName()).isEqualTo("Salami");
        assertThat(stored.name()).isEqualTo("Pizza · Salami");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from meal_event where dish_id is null and dish_name = 'Pizza'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void historySurvivesADeactivatedAndADeletedUser() {
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN);
        UUID ada = userId("ada");
        UserResponse paused = createMember("Pausiert");
        UserResponse gone = createMember("Geloescht");
        MealEventResponse eaten = eat(meal(MONDAY, MealType.ABENDESSEN, pizza.id(), null, List.of(ada, paused.id(), gone.id()), null));
        restTemplate.exchange(
                "/api/users/" + paused.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest(paused.displayName(), "MEMBER", false)),
                UserResponse.class);
        jdbcTemplate.update("delete from app_user where id = ?", gone.id());

        MealEventResponse stored = history(null)[0];
        assertThat(stored.id()).isEqualTo(eaten.id());
        assertThat(stored.participants()).extracting(MealParticipantResponse::displayName)
                .contains(paused.displayName(), gone.displayName());
        assertThat(stored.participants()).filteredOn(person -> gone.displayName().equals(person.displayName()))
                .singleElement()
                .extracting(MealParticipantResponse::userId)
                .isNull();
        assertThat(postPlan(meal(WEDNESDAY, MealType.ABENDESSEN, pizza.id(), null, List.of(paused.id()), null)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        MealEventResponse kept = restTemplate.exchange(
                "/api/meal-history/" + eaten.id(),
                HttpMethod.PUT,
                new HttpEntity<>(meal(MONDAY, MealType.ABENDESSEN, pizza.id(), null, List.of(ada, paused.id()), "Weiter dabei")),
                MealEventResponse.class).getBody();
        assertThat(kept.participants()).extracting(MealParticipantResponse::userId).contains(paused.id());
    }

    @Test
    void eatenAndPlannedMealsChangeSuggestionsWithoutADoublePenalty() {
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN);
        createDish("Suppe", MealType.ABENDESSEN);
        UUID ada = userId("ada");
        LocalDate today = LocalDate.of(2026, 10, 10);
        eat(meal(today, MealType.ABENDESSEN, pizza.id(), null, List.of(ada), null));

        RecommendationResponse recent = recommend(today, 7L);
        RecommendationResponse later = recommend(today.plusDays(30), 7L);
        assertThat(recent.suggestions()).extracting(suggestion -> suggestion.dishName() + ":" + suggestion.reason())
                .contains("Pizza:" + SuggestionReasoner.RECENT);
        assertThat(later.suggestions()).extracting(suggestion -> suggestion.reason())
                .doesNotContain(SuggestionReasoner.RECENT);
        assertThat(recommend(today, 7L)).isEqualTo(recent);

        jdbcTemplate.update("delete from meal_event");
        MealEventResponse planned = plan(meal(today.plusDays(1), MealType.ABENDESSEN, pizza.id(), null, List.of(ada), null));
        assertThat(recommend(today, 7L).suggestions())
                .extracting(suggestion -> suggestion.dishName() + ":" + suggestion.reason())
                .contains("Pizza:" + SuggestionReasoner.PLANNED_SOON);

        MealEventResponse confirmed = restTemplate.postForEntity(
                "/api/meal-plans/" + planned.id() + "/eaten", null, MealEventResponse.class).getBody();
        Candidate candidate = new Candidate(pizza.id(), "Pizza", null, null, null, Suitability.UNKNOWN, Suitability.UNKNOWN);
        var scored = RecommendationScorer.score(
                candidate,
                List.of(ada),
                RatingLookup.empty(),
                RecommendationPreferences.none(),
                new RepetitionIndex(today.plusDays(1), mealEvents.findOccurrences(today.plusDays(1))));
        assertThat(confirmed.status()).isEqualTo(MealStatus.EATEN);
        assertThat(jdbcTemplate.queryForObject("select count(*) from meal_event", Integer.class)).isEqualTo(1);
        assertThat(RecommendationScorer.factor(scored, "recency")).isEqualTo(-8);
        assertThat(RecommendationScorer.factor(scored, "planned")).isZero();
    }

    @Test
    void anonymousCallsAndMissingCsrfAreRejected() {
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN);
        UUID ada = userId("ada");
        ResponseEntity<String> anonymous = rawTemplate.getForEntity(
                "/api/meal-plans?from=" + FROM + "&to=" + TO, String.class);
        ResponseEntity<String> history = rawTemplate.getForEntity(
                "/api/meal-history?from=" + FROM + "&to=" + TO, String.class);

        ResponseEntity<String> primed = rawTemplate.getForEntity("/api/setup/status", String.class);
        String csrf = ApiSessions.setCookie(primed.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        String before = ApiSessions.setCookie(primed.getHeaders().get(HttpHeaders.SET_COOKIE), "WAEW_SESSION");
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.add(HttpHeaders.COOKIE, "WAEW_SESSION=" + before + "; XSRF-TOKEN=" + csrf);
        loginHeaders.add("X-XSRF-TOKEN", java.net.URLDecoder.decode(csrf, java.nio.charset.StandardCharsets.UTF_8));
        ResponseEntity<String> login = rawTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("Ada", PASSWORD), loginHeaders),
                String.class);
        String session = ApiSessions.setCookie(login.getHeaders().get(HttpHeaders.SET_COOKIE), "WAEW_SESSION");
        HttpHeaders withoutToken = new HttpHeaders();
        withoutToken.setContentType(MediaType.APPLICATION_JSON);
        withoutToken.add(HttpHeaders.COOKIE, "WAEW_SESSION=" + session);
        ResponseEntity<String> rejected = rawTemplate.exchange(
                "/api/meal-plans",
                HttpMethod.POST,
                new HttpEntity<>(meal(MONDAY, MealType.ABENDESSEN, pizza.id(), null, List.of(ada), null), withoutToken),
                String.class);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody()).contains("UNAUTHENTICATED");
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejected.getBody()).contains("CSRF");
    }

    private MealEventResponse plan(MealEventWriteRequest request) {
        ResponseEntity<MealEventResponse> response = restTemplate.postForEntity("/api/meal-plans", request, MealEventResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<String> postPlan(MealEventWriteRequest request) {
        return restTemplate.postForEntity("/api/meal-plans", request, String.class);
    }

    private MealEventResponse eat(MealEventWriteRequest request) {
        ResponseEntity<MealEventResponse> response = restTemplate.postForEntity("/api/meal-history", request, MealEventResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private MealEventResponse[] plans() {
        ResponseEntity<MealEventResponse[]> response = restTemplate.getForEntity(
                "/api/meal-plans?from=" + FROM + "&to=" + TO, MealEventResponse[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private MealEventResponse[] history(UUID dinerId) {
        String diner = dinerId == null ? "" : "&dinerId=" + dinerId;
        ResponseEntity<MealEventResponse[]> response = restTemplate.getForEntity(
                "/api/meal-history?from=" + FROM + "&to=" + TO + diner, MealEventResponse[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private RecommendationResponse recommend(LocalDate referenceDate, long seed) {
        ResponseEntity<RecommendationResponse> response = restTemplate.postForEntity(
                "/api/recommendations",
                new RecommendationRequest(
                        MealType.ABENDESSEN,
                        List.of(userId("ada")),
                        false,
                        false,
                        false,
                        List.of(),
                        seed,
                        referenceDate),
                RecommendationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static MealEventWriteRequest meal(
            LocalDate date, MealType mealType, UUID dishId, UUID variantId, List<UUID> diners, String comment) {
        return new MealEventWriteRequest(date, mealType, dishId, variantId, diners, comment);
    }

    private DishDetailResponse createDish(String name, MealType mealType) {
        ResponseEntity<DishDetailResponse> response = restTemplate.postForEntity(
                "/api/dishes",
                new DishWriteRequest(
                        name, null, List.of(mealType), List.of(), Effort.EINFACH, List.of(),
                        null, null, null, null, Suitability.UNKNOWN, Suitability.UNKNOWN, true),
                DishDetailResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private UUID addVariant(UUID dishId, String name) {
        ResponseEntity<DishDetailResponse> response = restTemplate.postForEntity(
                "/api/dishes/" + dishId + "/variants",
                new VariantWriteRequest(name, null, null, null, null, null, null, null, null, null),
                DishDetailResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().variants().getLast().id();
    }

    private void rate(UUID dishId, int stars) {
        ResponseEntity<OwnRatingResponse> response = restTemplate.exchange(
                "/api/dishes/" + dishId + "/rating",
                HttpMethod.PUT,
                new HttpEntity<>(new RatingWriteRequest(stars)),
                OwnRatingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UserResponse createMember(String username) {
        ResponseEntity<UserResponse> created = restTemplate.postForEntity(
                "/api/users",
                new CreateUserRequest(username, username + " Beispiel", "temporaer-pass", "MEMBER"),
                UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }

    private TestRestTemplate memberSession(UserResponse member) {
        TestRestTemplate session = new ApiSessions(rawTemplate).open();
        session.getForEntity("/api/setup/status", String.class);
        ResponseEntity<String> login = session.postForEntity(
                "/api/auth/login", new LoginRequest(member.username(), "temporaer-pass"), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> changed = session.exchange(
                "/api/auth/password",
                HttpMethod.PUT,
                new HttpEntity<>(new PasswordChangeRequest("temporaer-pass", "mitglied-passwort", "mitglied-passwort")),
                String.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        return session;
    }

    private UUID userId(String usernameNormalized) {
        return jdbcTemplate.queryForObject(
                "select id from app_user where username_normalized = ?",
                UUID.class,
                usernameNormalized);
    }
}

package de.wasessenwir.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.dish.DishDetailResponse;
import de.wasessenwir.dish.DishWriteRequest;
import de.wasessenwir.dish.Effort;
import de.wasessenwir.dish.MealType;
import de.wasessenwir.dish.Suitability;
import de.wasessenwir.dish.VariantWriteRequest;
import de.wasessenwir.rating.RatingWriteRequest;
import de.wasessenwir.security.LoginRequest;
import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.setup.SetupStatusResponse;
import de.wasessenwir.support.ApiSessions;
import de.wasessenwir.user.CreateUserRequest;
import de.wasessenwir.user.MemberResponse;
import de.wasessenwir.user.PasswordChangeRequest;
import de.wasessenwir.user.UpdateUserRequest;
import de.wasessenwir.user.UserResponse;
import java.util.ArrayList;
import java.util.Arrays;
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
class RecommendationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private static final String PASSWORD = "ein-langes-passwort";

    @Autowired
    private TestRestTemplate rawTemplate;

    @Autowired
    private CapturingSetupTokenPublisher tokens;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestRestTemplate restTemplate;

    @BeforeEach
    void signInAndClearDishes() {
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
    void suggestsThreeDistinctFamiliesWithoutRatings() {
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN, Effort.MITTEL);
        addVariant(pizza.id(), "Margherita");
        addVariant(pizza.id(), "Salami");
        createDish("Suppe", MealType.ABENDESSEN, Effort.EINFACH);
        createDish("Auflauf", MealType.ABENDESSEN, Effort.AUFWENDIG);
        createDish("Pfanne", MealType.ABENDESSEN, null);
        createDish("Müsli", MealType.FRUEHSTUECK, Effort.EINFACH);
        UUID ada = userId("ada");
        rateDish(restTemplate, pizza.id(), 4);

        ResponseEntity<String> raw = post(restTemplate, request(MealType.ABENDESSEN, List.of(ada), List.of(), 11L));
        RecommendationResponse body = recommend(request(MealType.ABENDESSEN, List.of(ada), List.of(), 11L));

        assertThat(raw.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(raw.getHeaders().getCacheControl()).contains("no-store");
        assertThat(body.suggestions()).hasSize(3);
        assertThat(body.suggestions()).extracting(SuggestionResponse::dishId).doesNotHaveDuplicates();
        assertThat(body.suggestions()).extracting(SuggestionResponse::dishName).doesNotContain("Müsli");
        assertThat(body.explanation()).isNull();
        assertThat(raw.getBody())
                .doesNotContain("\"stars\"")
                .doesNotContain("myRating")
                .doesNotContain("selectionWeight")
                .doesNotContain("\"score\"")
                .doesNotContain("userId");
    }

    @Test
    void mealTypeIsAHardFilterAndSoftPreferencesDoNotEmptyTheResult() {
        createDish("Müsli", MealType.FRUEHSTUECK, Effort.EINFACH);
        createDish("Suppe", MealType.ABENDESSEN, Effort.EINFACH);
        createDish("Braten", MealType.ABENDESSEN, Effort.AUFWENDIG);
        UUID ada = userId("ada");

        RecommendationResponse dinner = recommend(request(
                MealType.ABENDESSEN,
                List.of(ada),
                List.of(),
                4L,
                true,
                true,
                true));

        assertThat(dinner.suggestions()).extracting(SuggestionResponse::dishName).containsExactlyInAnyOrder("Suppe", "Braten");
        assertThat(dinner.suggestions()).extracting(SuggestionResponse::dishName).doesNotContain("Müsli");
        assertThat(dinner.explanation()).contains("nur zwei");
    }

    @Test
    void exclusionSeedAndSmallCatalogsAreStable() {
        createDish("Suppe", MealType.ABENDESSEN, null);
        DishDetailResponse gratin = createDish("Gratin", MealType.ABENDESSEN, null);
        createDish("Pfanne", MealType.ABENDESSEN, null);
        createDish("Eintopf", MealType.ABENDESSEN, null);
        UUID ada = userId("ada");
        RecommendationRequest firstRequest = request(MealType.ABENDESSEN, List.of(ada), List.of(), 21L);

        RecommendationResponse first = recommend(firstRequest);
        RecommendationResponse repeated = recommend(firstRequest);
        RecommendationResponse withoutGratin = recommend(request(
                MealType.ABENDESSEN,
                List.of(ada),
                List.of(gratin.id()),
                21L));
        jdbcTemplate.update("delete from dish");
        createDish("Snack", MealType.SNACK, null);
        RecommendationResponse one = recommend(request(MealType.SNACK, List.of(ada), List.of(), 1L));
        jdbcTemplate.update("delete from dish");
        RecommendationResponse none = recommend(request(MealType.FRUEHSTUECK, List.of(ada), List.of(), 1L));

        assertThat(names(first)).containsExactlyElementsOf(names(repeated));
        assertThat(first.suggestions()).hasSize(3);
        assertThat(withoutGratin.suggestions()).extracting(SuggestionResponse::dishId).doesNotContain(gratin.id());
        assertThat(one.suggestions()).hasSize(1);
        assertThat(one.explanation()).contains("nur ein passendes Gericht");
        assertThat(none.suggestions()).isEmpty();
        assertThat(none.explanation()).contains("noch keine passenden Gerichte");
    }

    @Test
    void differentDinersAndVariantRatingsChangeTheDraw() {
        DishDetailResponse adaDish = createDish("Ada Teller", MealType.ABENDESSEN, null);
        DishDetailResponse beaDish = createDish("Bea Teller", MealType.ABENDESSEN, null);
        createDish("Neutral Eins", MealType.ABENDESSEN, null);
        createDish("Neutral Zwei", MealType.ABENDESSEN, null);
        TestRestTemplate bea = member("BeaVorschlag");
        UUID ada = userId("ada");
        UUID beaId = userId("beavorschlag");
        rateDish(restTemplate, adaDish.id(), 5);
        rateDish(restTemplate, beaDish.id(), 1);
        rateDish(bea, adaDish.id(), 1);
        rateDish(bea, beaDish.id(), 5);

        int adaLoved = countAppearances(adaDish.id(), List.of(ada));
        int adaHated = countAppearances(beaDish.id(), List.of(ada));
        int beaLoved = countAppearances(beaDish.id(), List.of(beaId));
        int beaHated = countAppearances(adaDish.id(), List.of(beaId));

        assertThat(adaLoved).isGreaterThan(adaHated);
        assertThat(beaLoved).isGreaterThan(beaHated);

        jdbcTemplate.update("delete from dish");
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN, null);
        UUID margherita = addVariant(pizza.id(), "Margherita");
        rateDish(restTemplate, pizza.id(), 1);
        rateVariant(restTemplate, pizza.id(), margherita, 5);
        int variantPicks = 0;
        for (int seed = 0; seed < 12; seed++) {
            RecommendationResponse result = recommend(request(MealType.ABENDESSEN, List.of(ada), List.of(), (long) seed));
            assertThat(result.suggestions()).hasSize(1);
            if (margherita.equals(result.suggestions().getFirst().variantId())) {
                variantPicks += 1;
            }
            assertThat(result.suggestions().getFirst().dishId()).isEqualTo(pizza.id());
        }
        assertThat(variantPicks).isGreaterThanOrEqualTo(10);
    }

    @Test
    void unratedVariantCanStillBeSuggested() {
        DishDetailResponse pizza = createDish("Pizza", MealType.ABENDESSEN, null);
        UUID salami = addVariant(pizza.id(), "Salami");
        UUID ada = userId("ada");
        rateDish(restTemplate, pizza.id(), 5);
        int variantPicks = 0;
        int basePicks = 0;
        for (int seed = 0; seed < 24; seed++) {
            SuggestionResponse suggestion = recommend(request(MealType.ABENDESSEN, List.of(ada), List.of(), (long) seed))
                    .suggestions()
                    .getFirst();
            if (salami.equals(suggestion.variantId())) {
                variantPicks += 1;
            }
            if (suggestion.variantId() == null) {
                basePicks += 1;
            }
        }
        assertThat(variantPicks).isPositive();
        assertThat(basePicks).isPositive();
    }

    @Test
    void anonymousAccessIsRejected() {
        createDish("Geheim", MealType.ABENDESSEN, null);
        rateDish(restTemplate, jdbcTemplate.queryForObject("select id from dish", UUID.class), 5);
        RecommendationRequest body = request(MealType.ABENDESSEN, List.of(userId("ada")), List.of(), 1L);
        ResponseEntity<String> primed = rawTemplate.getForEntity("/api/setup/status", String.class);
        String csrf = ApiSessions.setCookie(primed.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", java.net.URLDecoder.decode(csrf, java.nio.charset.StandardCharsets.UTF_8));

        ResponseEntity<String> withoutSession = rawTemplate.exchange(
                "/api/recommendations",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
        ResponseEntity<String> withoutCsrf = rawTemplate.postForEntity("/api/recommendations", body, String.class);
        ResponseEntity<String> members = rawTemplate.getForEntity("/api/members", String.class);

        assertThat(withoutSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(withoutSession.getBody()).contains("UNAUTHENTICATED").doesNotContain("\"stars\"").doesNotContain("Geheim");
        assertThat(withoutCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withoutCsrf.getBody()).doesNotContain("Geheim").doesNotContain("\"stars\"");
        assertThat(members.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidAndInactiveDinersAreRejected() {
        UserResponse guest = createMember("GastWeg");
        restTemplate.exchange(
                "/api/users/" + guest.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest(guest.displayName(), "MEMBER", false)),
                UserResponse.class);
        UUID ada = userId("ada");

        ResponseEntity<String> unknown = post(restTemplate, request(MealType.ABENDESSEN, List.of(UUID.randomUUID()), List.of(), 1L));
        ResponseEntity<String> inactive = post(restTemplate, request(MealType.ABENDESSEN, List.of(guest.id()), List.of(), 1L));
        ResponseEntity<String> empty = post(restTemplate, request(MealType.ABENDESSEN, List.of(), List.of(), 1L));
        ResponseEntity<String> missingMeal = restTemplate.postForEntity(
                "/api/recommendations",
                new RecommendationRequest(null, List.of(ada), false, false, false, List.of(), 1L),
                String.class);
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> invalidMeal = restTemplate.exchange(
                "/api/recommendations",
                HttpMethod.POST,
                new HttpEntity<>("{\"mealType\":\"LUNCH\",\"dinerIds\":[\"" + ada + "\"]}", json),
                String.class);

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknown.getBody()).contains("Nur aktive Familienmitglieder").doesNotContain("\"stars\"");
        assertThat(inactive.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(inactive.getBody()).contains("Nur aktive Familienmitglieder");
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(empty.getBody()).contains("Mindestens eine Person");
        assertThat(missingMeal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingMeal.getBody()).contains("Mahlzeit");
        assertThat(invalidMeal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void csrfRejectsUnsafeCalls() {
        createDish("Sitzung", MealType.ABENDESSEN, null);
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
                "/api/recommendations",
                HttpMethod.POST,
                new HttpEntity<>(
                        "{\"mealType\":\"ABENDESSEN\",\"dinerIds\":[\"" + userId("ada") + "\"]}",
                        withoutToken),
                String.class);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejected.getBody()).contains("CSRF").doesNotContain("\"stars\"");
    }

    @Test
    void membersListOnlyActiveDisplayNames() {
        UserResponse guest = createMember("Sichtbar");
        UserResponse hidden = createMember("Versteckt");
        restTemplate.exchange(
                "/api/users/" + hidden.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest(hidden.displayName(), "MEMBER", false)),
                UserResponse.class);

        ResponseEntity<String> raw = restTemplate.getForEntity("/api/members", String.class);
        MemberResponse[] members = restTemplate.getForObject("/api/members", MemberResponse[].class);

        assertThat(raw.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(raw.getHeaders().getCacheControl()).contains("no-store");
        assertThat(raw.getBody()).doesNotContain("username").doesNotContain("password").doesNotContain("role");
        assertThat(Arrays.asList(members)).extracting(MemberResponse::displayName).contains("Ada Lovelace", guest.displayName());
        assertThat(Arrays.asList(members)).extracting(MemberResponse::id).doesNotContain(hidden.id());
    }

    private int countAppearances(UUID dishId, List<UUID> diners) {
        int count = 0;
        for (int seed = 0; seed < 16; seed++) {
            RecommendationResponse result = recommend(request(MealType.ABENDESSEN, diners, List.of(), (long) seed));
            for (SuggestionResponse suggestion : result.suggestions()) {
                if (dishId.equals(suggestion.dishId())) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private RecommendationResponse recommend(RecommendationRequest request) {
        ResponseEntity<RecommendationResponse> response = restTemplate.postForEntity(
                "/api/recommendations", request, RecommendationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static ResponseEntity<String> post(TestRestTemplate session, RecommendationRequest request) {
        return session.postForEntity("/api/recommendations", request, String.class);
    }

    private RecommendationRequest request(MealType mealType, List<UUID> diners, List<UUID> excluded, Long seed) {
        return request(mealType, diners, excluded, seed, false, false, false);
    }

    private RecommendationRequest request(
            MealType mealType,
            List<UUID> diners,
            List<UUID> excluded,
            Long seed,
            boolean preferSimple,
            boolean preferHomeOffice,
            boolean preferMealPrep) {
        return new RecommendationRequest(mealType, diners, preferSimple, preferHomeOffice, preferMealPrep, excluded, seed);
    }

    private DishDetailResponse createDish(String name, MealType mealType, Effort effort) {
        ResponseEntity<DishDetailResponse> response = restTemplate.postForEntity(
                "/api/dishes",
                new DishWriteRequest(
                        name,
                        null,
                        List.of(mealType),
                        List.of(),
                        effort,
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        Suitability.UNKNOWN,
                        Suitability.UNKNOWN,
                        true),
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

    private static void rateDish(TestRestTemplate session, UUID dishId, int stars) {
        ResponseEntity<String> response = session.exchange(
                "/api/dishes/" + dishId + "/rating",
                HttpMethod.PUT,
                new HttpEntity<>(new RatingWriteRequest(stars)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static void rateVariant(TestRestTemplate session, UUID dishId, UUID variantId, int stars) {
        ResponseEntity<String> response = session.exchange(
                "/api/dishes/" + dishId + "/variants/" + variantId + "/rating",
                HttpMethod.PUT,
                new HttpEntity<>(new RatingWriteRequest(stars)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private TestRestTemplate member(String username) {
        createMember(username);
        TestRestTemplate session = new ApiSessions(rawTemplate).open();
        session.getForEntity("/api/setup/status", String.class);
        ResponseEntity<String> login = session.postForEntity(
                "/api/auth/login", new LoginRequest(username, "temporaer-pass"), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> changed = session.exchange(
                "/api/auth/password",
                HttpMethod.PUT,
                new HttpEntity<>(new PasswordChangeRequest("temporaer-pass", "mitglied-passwort", "mitglied-passwort")),
                String.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        return session;
    }

    private UserResponse createMember(String username) {
        ResponseEntity<UserResponse> created = restTemplate.postForEntity(
                "/api/users",
                new CreateUserRequest(username, username + " Beispiel", "temporaer-pass", "MEMBER"),
                UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }

    private UUID userId(String usernameNormalized) {
        return jdbcTemplate.queryForObject(
                "select id from app_user where username_normalized = ?",
                UUID.class,
                usernameNormalized);
    }

    private static List<String> names(RecommendationResponse response) {
        List<String> names = new ArrayList<>();
        for (SuggestionResponse suggestion : response.suggestions()) {
            names.add(suggestion.dishId() + ":" + suggestion.variantId());
        }
        return names;
    }
}

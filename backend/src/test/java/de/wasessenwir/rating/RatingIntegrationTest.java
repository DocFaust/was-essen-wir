package de.wasessenwir.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.wasessenwir.dish.DishDetailResponse;
import de.wasessenwir.dish.DishSummaryResponse;
import de.wasessenwir.dish.DishWriteRequest;
import de.wasessenwir.dish.VariantWriteRequest;
import de.wasessenwir.security.LoginRequest;
import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.setup.SetupStatusResponse;
import de.wasessenwir.support.ApiSessions;
import de.wasessenwir.user.CreateUserRequest;
import de.wasessenwir.user.PasswordChangeRequest;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
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
class RatingIntegrationTest {

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
    void signIn() {
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
    }

    @Test
    void flywayAppliesRatingMigration() {
        Long applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '4' and success", Long.class);

        assertThat(applied).isEqualTo(1L);
    }

    @Test
    void createsOneStarRatingAndLeavesMissingRatingsEmpty() {
        DishDetailResponse dish = createDish("Suppe");

        RatingResponse missing = getDishRating(restTemplate, dish.id());
        assertThat(missing.stars()).isNull();
        assertThat(restTemplate.getForEntity(dishRatingPath(dish.id()), String.class).getBody())
                .contains("\"stars\":null")
                .doesNotContain("\"stars\":3");

        RatingResponse created = putDishRating(restTemplate, dish.id(), 1);
        assertThat(created.stars()).isEqualTo(1);
        assertThat(getDishRating(restTemplate, dish.id()).stars()).isEqualTo(1);
        assertThat(count("select count(*) from rating where dish_id = ?", dish.id())).isEqualTo(1L);
    }

    @Test
    void createsFiveStarRating() {
        DishDetailResponse dish = createDish("Lieblingspasta");

        assertThat(putDishRating(restTemplate, dish.id(), 5).stars()).isEqualTo(5);
        assertThat(getDishRating(restTemplate, dish.id()).stars()).isEqualTo(5);
    }

    @Test
    void rejectsZeroAndSix() {
        DishDetailResponse dish = createDish("Grenzwert");

        ResponseEntity<String> zero = exchangeDishRating(restTemplate, dish.id(), 0);
        ResponseEntity<String> six = exchangeDishRating(restTemplate, dish.id(), 6);
        ResponseEntity<String> missing = restTemplate.exchange(
                dishRatingPath(dish.id()),
                HttpMethod.PUT,
                new HttpEntity<>(new RatingWriteRequest(null)),
                String.class);

        assertThat(zero.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(six.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(zero.getBody()).contains("zwischen 1 und 5");
        assertThat(count("select count(*) from rating where dish_id = ?", dish.id())).isZero();
    }

    @Test
    void updatesRating() {
        DishDetailResponse dish = createDish("Auflauf");
        putDishRating(restTemplate, dish.id(), 2);

        assertThat(putDishRating(restTemplate, dish.id(), 4).stars()).isEqualTo(4);
        assertThat(getDishRating(restTemplate, dish.id()).stars()).isEqualTo(4);
        assertThat(count("select count(*) from rating where dish_id = ?", dish.id())).isEqualTo(1L);
    }

    @Test
    void removesRating() {
        DishDetailResponse dish = createDish("Eintopf");
        putDishRating(restTemplate, dish.id(), 3);

        ResponseEntity<Void> deleted = restTemplate.exchange(
                dishRatingPath(dish.id()), HttpMethod.DELETE, null, Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getDishRating(restTemplate, dish.id()).stars()).isNull();
        assertThat(count("select count(*) from rating where dish_id = ?", dish.id())).isZero();

        ResponseEntity<Void> again = restTemplate.exchange(
                dishRatingPath(dish.id()), HttpMethod.DELETE, null, Void.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void repeatedPutDoesNotDuplicate() {
        DishDetailResponse dish = createDish("Pfanne");
        putDishRating(restTemplate, dish.id(), 4);
        OffsetDateTime original = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        jdbcTemplate.update(
                "update rating set created_at = ?, updated_at = ? where dish_id = ?",
                original,
                original,
                dish.id());

        assertThat(putDishRating(restTemplate, dish.id(), 4).stars()).isEqualTo(4);

        assertThat(count("select count(*) from rating where dish_id = ?", dish.id())).isEqualTo(1L);
        Boolean unchanged = jdbcTemplate.queryForObject(
                "select created_at = ? and updated_at > created_at from rating where dish_id = ?",
                Boolean.class,
                original,
                dish.id());
        assertThat(unchanged).isTrue();
    }

    @Test
    void ratesDishAndVariantIndependently() {
        DishDetailResponse dish = createDish("Pizza");
        UUID variantId = addVariant(dish.id(), "Margherita");
        UUID otherVariantId = addVariant(dish.id(), "Salami");

        assertThat(getVariantRating(restTemplate, dish.id(), variantId).stars()).isNull();
        putDishRating(restTemplate, dish.id(), 4);
        assertThat(getVariantRating(restTemplate, dish.id(), variantId).stars()).isNull();
        assertThat(getVariantRating(restTemplate, dish.id(), otherVariantId).stars()).isNull();

        putVariantRating(restTemplate, dish.id(), variantId, 5);

        assertThat(getDishRating(restTemplate, dish.id()).stars()).isEqualTo(4);
        assertThat(getVariantRating(restTemplate, dish.id(), variantId).stars()).isEqualTo(5);
        assertThat(getVariantRating(restTemplate, dish.id(), otherVariantId).stars()).isNull();
        DishDetailResponse detail = restTemplate.getForObject("/api/dishes/" + dish.id(), DishDetailResponse.class);
        assertThat(detail.myRating()).isEqualTo(4);
        assertThat(detail.variants()).filteredOn(variant -> variant.id().equals(variantId))
                .extracting(variant -> variant.myRating())
                .containsExactly(5);
        assertThat(detail.variants()).filteredOn(variant -> variant.id().equals(otherVariantId))
                .extracting(variant -> variant.myRating())
                .containsExactly((Integer) null);
    }

    @Test
    void twoUsersRateTheSameDishDifferently() {
        DishDetailResponse shared = createDish("Gemeinsam");
        DishDetailResponse onlyAda = createDish("Nur Ada");
        putDishRating(restTemplate, shared.id(), 5);
        putDishRating(restTemplate, onlyAda.id(), 2);
        TestRestTemplate bea = member("Bea");

        putDishRating(bea, shared.id(), 1);

        assertThat(getDishRating(restTemplate, shared.id()).stars()).isEqualTo(5);
        assertThat(getDishRating(bea, shared.id()).stars()).isEqualTo(1);
        assertThat(getDishRating(bea, onlyAda.id()).stars()).isNull();
        assertThat(count("select count(*) from rating where dish_id = ?", shared.id())).isEqualTo(2L);

        DishSummaryResponse[] adaList = restTemplate.getForObject("/api/dishes", DishSummaryResponse[].class);
        DishSummaryResponse[] beaList = bea.getForObject("/api/dishes", DishSummaryResponse[].class);
        assertThat(summary(adaList, shared.id()).myRating()).isEqualTo(5);
        assertThat(summary(beaList, shared.id()).myRating()).isEqualTo(1);
        assertThat(summary(adaList, onlyAda.id()).myRating()).isEqualTo(2);
        assertThat(summary(beaList, onlyAda.id()).myRating()).isNull();

        OwnRatingResponse[] adaRatings = restTemplate.getForObject("/api/ratings", OwnRatingResponse[].class);
        OwnRatingResponse[] beaRatings = bea.getForObject("/api/ratings", OwnRatingResponse[].class);
        assertThat(adaRatings)
                .filteredOn(rating -> shared.id().equals(rating.dishId()) || onlyAda.id().equals(rating.dishId()))
                .extracting(OwnRatingResponse::stars)
                .containsExactlyInAnyOrder(5, 2);
        assertThat(beaRatings).extracting(OwnRatingResponse::stars).containsExactly(1);
        assertThat(adaRatings).noneMatch(rating -> shared.id().equals(rating.dishId()) && rating.stars() != 5);
        assertThat(beaRatings).allSatisfy(rating -> assertThat(rating.variantId()).isNull());
    }

    @Test
    void userCannotChangeAnotherUsersRating() {
        DishDetailResponse dish = createDish("Geschützt");
        putDishRating(restTemplate, dish.id(), 4);
        UUID adaId = jdbcTemplate.queryForObject(
                "select id from app_user where username_normalized = 'ada'", UUID.class);
        TestRestTemplate bea = member("BeaSchutz");

        ResponseEntity<RatingResponse> overwritten = bea.exchange(
                dishRatingPath(dish.id()),
                HttpMethod.PUT,
                new HttpEntity<>(java.util.Map.of("stars", 1, "userId", adaId)),
                RatingResponse.class);

        assertThat(overwritten.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(overwritten.getBody().stars()).isEqualTo(1);
        assertThat(getDishRating(restTemplate, dish.id()).stars()).isEqualTo(4);
        UUID beaId = jdbcTemplate.queryForObject(
                "select id from app_user where username_normalized = 'beaschutz'", UUID.class);
        UUID ownerOfOneStar = jdbcTemplate.queryForObject(
                "select user_id from rating where dish_id = ? and stars = 1", UUID.class, dish.id());
        assertThat(ownerOfOneStar).isEqualTo(beaId).isNotEqualTo(adaId);

        ResponseEntity<Void> deleted = bea.exchange(dishRatingPath(dish.id()), HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getDishRating(restTemplate, dish.id()).stars()).isEqualTo(4);
        assertThat(count("select count(*) from rating where dish_id = ? and user_id = ?", dish.id(), adaId))
                .isEqualTo(1L);
    }

    @Test
    void anonymousAccessIsRejected() {
        DishDetailResponse dish = createDish("Privat");
        putDishRating(restTemplate, dish.id(), 5);

        assertThat(rawTemplate.getForEntity(dishRatingPath(dish.id()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> primed = rawTemplate.getForEntity("/api/setup/status", String.class);
        String csrf = ApiSessions.setCookie(primed.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", java.net.URLDecoder.decode(csrf, java.nio.charset.StandardCharsets.UTF_8));
        assertThat(rawTemplate.exchange(
                        dishRatingPath(dish.id()),
                        HttpMethod.PUT,
                        new HttpEntity<>(new RatingWriteRequest(1), headers),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rawTemplate.exchange(
                        dishRatingPath(dish.id()), HttpMethod.DELETE, new HttpEntity<>(headers), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> list = rawTemplate.getForEntity("/api/ratings", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(list.getBody()).contains("UNAUTHENTICATED").doesNotContain("\"stars\":5");
        assertThat(getDishRating(restTemplate, dish.id()).stars()).isEqualTo(5);
    }

    @Test
    void csrfRejectsUnsafeCalls() {
        DishDetailResponse dish = createDish("Sitzung");
        ResponseEntity<String> primed = rawTemplate.getForEntity("/api/setup/status", String.class);
        String csrf = ApiSessions.setCookie(primed.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        String before = ApiSessions.setCookie(primed.getHeaders().get(HttpHeaders.SET_COOKIE), "WAEW_SESSION");
        assertThat(csrf).isNotBlank();
        assertThat(before).isNotBlank();

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.add(HttpHeaders.COOKIE, "WAEW_SESSION=" + before + "; XSRF-TOKEN=" + csrf);
        loginHeaders.add("X-XSRF-TOKEN", java.net.URLDecoder.decode(csrf, java.nio.charset.StandardCharsets.UTF_8));
        ResponseEntity<String> login = rawTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("Ada", PASSWORD), loginHeaders),
                String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String session = ApiSessions.setCookie(login.getHeaders().get(HttpHeaders.SET_COOKIE), "WAEW_SESSION");
        assertThat(session).isNotBlank();

        HttpHeaders withoutToken = new HttpHeaders();
        withoutToken.setContentType(MediaType.APPLICATION_JSON);
        withoutToken.add(HttpHeaders.COOKIE, "WAEW_SESSION=" + session);
        ResponseEntity<String> rejected = rawTemplate.exchange(
                dishRatingPath(dish.id()),
                HttpMethod.PUT,
                new HttpEntity<>("{\"stars\":4}", withoutToken),
                String.class);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejected.getHeaders().getContentType().toString()).contains("json");
        assertThat(rejected.getBody()).contains("CSRF").doesNotContain("<html");
        assertThat(count("select count(*) from rating where dish_id = ?", dish.id())).isZero();
    }

    @Test
    void databaseConstraintsPreventDuplicatesAndInvalidTargets() {
        DishDetailResponse dish = createDish("Constraint");
        UUID variantId = addVariant(dish.id(), "Klein");
        UUID userId = jdbcTemplate.queryForObject(
                "select id from app_user where username_normalized = 'ada'", UUID.class);
        insertRating(userId, dish.id(), null, 4);

        assertThatThrownBy(() -> insertRating(userId, dish.id(), null, 2)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRating(userId, null, variantId, 0)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRating(userId, null, variantId, 6)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRating(userId, dish.id(), variantId, 3)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRating(userId, null, null, 3)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRating(userId, UUID.randomUUID(), null, 3)).isInstanceOf(DataAccessException.class);
        assertThat(count("select count(*) from rating where user_id = ? and dish_id = ?", userId, dish.id()))
                .isEqualTo(1L);
        assertThat(count("select count(*) from rating where variant_id = ?", variantId)).isZero();
    }

    @Test
    void deletingDishOrVariantRemovesRatings() {
        DishDetailResponse dish = createDish("Löschbar");
        UUID variantId = addVariant(dish.id(), "Weg");
        putDishRating(restTemplate, dish.id(), 4);
        putVariantRating(restTemplate, dish.id(), variantId, 5);

        ResponseEntity<DishDetailResponse> withoutVariant = restTemplate.exchange(
                "/api/dishes/" + dish.id() + "/variants/" + variantId,
                HttpMethod.DELETE,
                null,
                DishDetailResponse.class);
        assertThat(withoutVariant.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("select count(*) from rating where variant_id = ?", variantId)).isZero();
        assertThat(getDishRating(restTemplate, dish.id()).stars()).isEqualTo(4);

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/dishes/" + dish.id(), HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count("select count(*) from rating where dish_id = ?", dish.id())).isZero();
    }

    @Test
    void listResponsesAreNotCached() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/dishes", String.class);

        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    private TestRestTemplate member(String username) {
        ResponseEntity<String> created = restTemplate.postForEntity(
                "/api/users",
                new CreateUserRequest(username, username + " Beispiel", "temporaer-pass", "MEMBER"),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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

    private DishDetailResponse createDish(String name) {
        ResponseEntity<DishDetailResponse> response = restTemplate.postForEntity(
                "/api/dishes",
                new DishWriteRequest(name, null, null, null, null, null, null, null, null, null, null, null, null),
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

    private static RatingResponse putDishRating(TestRestTemplate session, UUID dishId, int stars) {
        ResponseEntity<RatingResponse> response = exchangeDishRating(session, dishId, stars, RatingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static ResponseEntity<String> exchangeDishRating(TestRestTemplate session, UUID dishId, int stars) {
        return exchangeDishRating(session, dishId, stars, String.class);
    }

    private static <T> ResponseEntity<T> exchangeDishRating(
            TestRestTemplate session, UUID dishId, int stars, Class<T> type) {
        return session.exchange(
                dishRatingPath(dishId),
                HttpMethod.PUT,
                new HttpEntity<>(new RatingWriteRequest(stars)),
                type);
    }

    private static RatingResponse getDishRating(TestRestTemplate session, UUID dishId) {
        return session.getForObject(dishRatingPath(dishId), RatingResponse.class);
    }

    private static void putVariantRating(TestRestTemplate session, UUID dishId, UUID variantId, int stars) {
        ResponseEntity<RatingResponse> response = session.exchange(
                variantRatingPath(dishId, variantId),
                HttpMethod.PUT,
                new HttpEntity<>(new RatingWriteRequest(stars)),
                RatingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().stars()).isEqualTo(stars);
    }

    private static RatingResponse getVariantRating(TestRestTemplate session, UUID dishId, UUID variantId) {
        return session.getForObject(variantRatingPath(dishId, variantId), RatingResponse.class);
    }

    private static String dishRatingPath(UUID dishId) {
        return "/api/dishes/" + dishId + "/rating";
    }

    private static String variantRatingPath(UUID dishId, UUID variantId) {
        return "/api/dishes/" + dishId + "/variants/" + variantId + "/rating";
    }

    private static DishSummaryResponse summary(DishSummaryResponse[] dishes, UUID id) {
        assertThat(dishes).isNotNull();
        return java.util.Arrays.stream(dishes).filter(dish -> dish.id().equals(id)).findFirst().orElseThrow();
    }

    private void insertRating(UUID userId, UUID dishId, UUID variantId, int stars) {
        jdbcTemplate.update(
                """
                insert into rating (id, user_id, dish_id, variant_id, stars, created_at, updated_at)
                values (?, ?, ?, ?, ?, now(), now())
                """,
                UUID.randomUUID(),
                userId,
                uuidOrNull(dishId),
                uuidOrNull(variantId),
                stars);
    }

    private static Object uuidOrNull(UUID id) {
        return id == null ? new SqlParameterValue(Types.OTHER, null) : id;
    }

    private long count(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }
}

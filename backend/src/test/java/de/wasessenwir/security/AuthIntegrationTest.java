package de.wasessenwir.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.WasEssenWirApplication;
import de.wasessenwir.dish.DishWriteRequest;
import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupBootstrap;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.setup.SetupStatusResponse;
import de.wasessenwir.support.ApiSessions;
import de.wasessenwir.user.CreateUserRequest;
import de.wasessenwir.user.PasswordChangeRequest;
import de.wasessenwir.user.PasswordResetRequest;
import de.wasessenwir.user.ProfileUpdateRequest;
import de.wasessenwir.user.UpdateUserRequest;
import com.zaxxer.hikari.HikariDataSource;
import de.wasessenwir.user.UserResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.security.login-max-attempts=3",
            "app.security.setup-max-attempts=3",
            "app.security.login-window=PT15M",
            "app.security.setup-window=PT15M"
        })
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class AuthIntegrationTest {

    private static final String ADMIN = "Ada";
    private static final String ADMIN_PASSWORD = "ein-langes-passwort";
    private static final String MEMBER_PASSWORD = "mitglied-passwort";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private TestRestTemplate raw;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CapturingSetupTokenPublisher tokens;

    @Autowired
    private SetupBootstrap bootstrap;

    @Autowired
    private AttemptLimiter limiter;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void freshInstall() {
        limiter.clear();
        jdbc.update("delete from app_user");
        jdbc.update("delete from app_setup");
        bootstrap.initialize();
    }

    @Test
    void setupStatusHidesTheTokenAndFlywayHasTheAuthMigration() {
        Long applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '3' and success", Long.class);
        assertThat(applied).isEqualTo(1L);

        ResponseEntity<String> status = raw.getForEntity("/api/setup/status", String.class);

        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody()).contains("\"setupRequired\":true");
        assertThat(status.getBody()).doesNotContain(tokens.token());
        byte[] hash = jdbc.queryForObject("select token_hash from app_setup", byte[].class);
        assertThat(new String(hash)).doesNotContain(tokens.token());
    }

    @Test
    void rejectsInvalidAndExpiredTokensAndAcceptsAReplacementAfterRestart() {
        TestRestTemplate api = session();
        ResponseEntity<String> invalid = api.postForEntity("/api/setup", setup("falsches-token"), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalid.getBody()).contains("SETUP_TOKEN_INVALID");
        assertThat(invalid.getBody()).doesNotContain("falsches-token");
        assertThat(countUsers()).isZero();

        jdbc.update("update app_setup set expires_at = ?", OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        ResponseEntity<String> expired = api.postForEntity("/api/setup", setup(tokens.token()), String.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(countUsers()).isZero();

        String previous = tokens.token();
        bootstrap.initialize();
        assertThat(tokens.token()).isNotEqualTo(previous);

        ResponseEntity<UserResponse> created = api.postForEntity("/api/setup", setup(tokens.token()), UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().role()).isEqualTo(de.wasessenwir.user.Role.ADMIN);
        assertThat(created.getBody().passwordChangeRequired()).isFalse();
        assertThat(created.getBody().username()).isEqualTo(ADMIN);
        assertThat(jdbc.queryForObject("select token_hash from app_setup", byte[].class)).isNull();
        assertThat(jdbc.queryForObject("select password_hash from app_user", String.class)).doesNotContain(ADMIN_PASSWORD);
        assertThat(created.getBody().toString()).doesNotContain(ADMIN_PASSWORD);
    }

    @Test
    void setupTokenCannotBeReusedAndARestartDoesNotCreateAnotherAdministrator() throws Exception {
        TestRestTemplate api = session();
        assertThat(api.postForEntity("/api/setup", setup(tokens.token()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> again = api.postForEntity("/api/setup", setup(tokens.token()), String.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody()).contains("SETUP_ALREADY_COMPLETED");
        assertThat(countUsers()).isEqualTo(1L);

        bootstrap.initialize();
        assertThat(countUsers()).isEqualTo(1L);
        assertThat(raw.getForEntity("/api/setup/status", SetupStatusResponse.class).getBody().setupRequired()).isFalse();

        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(WasEssenWirApplication.class)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + hikari.getJdbcUrl(),
                        "--spring.datasource.username=" + hikari.getUsername(),
                        "--spring.datasource.password=" + hikari.getPassword(),
                        "--app.setup.rotate-token=true")) {
            Long users = restarted.getBean(JdbcTemplate.class)
                    .queryForObject("select count(*) from app_user where role = 'ADMIN'", Long.class);
            assertThat(users).isEqualTo(1L);
        }
    }

    @Test
    void parallelSetupCreatesExactlyOneAdministrator() throws Exception {
        TestRestTemplate first = session();
        TestRestTemplate second = session();
        SetupRequest request = setup(tokens.token());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> left = pool.submit(() -> postWhenReady(first, request, ready, start));
            Future<ResponseEntity<String>> right = pool.submit(() -> postWhenReady(second, request, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<HttpStatus> statuses = List.of(
                    HttpStatus.valueOf(left.get(20, TimeUnit.SECONDS).getStatusCode().value()),
                    HttpStatus.valueOf(right.get(20, TimeUnit.SECONDS).getStatusCode().value()));
            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            assertThat(countUsers()).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void setupAndLoginAreRateLimitedAndCsrfRejectsUnsafeCalls() {
        TestRestTemplate api = session();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(api.postForEntity("/api/setup", setup("falsches-token"), String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        ResponseEntity<String> limited = api.postForEntity("/api/setup", setup(tokens.token()), String.class);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getBody()).contains("SETUP_RATE_LIMITED");
        assertThat(countUsers()).isZero();

        ResponseEntity<String> withoutCsrf = raw.postForEntity(
                "/api/auth/login", new LoginRequest(ADMIN, ADMIN_PASSWORD), String.class);
        assertThat(withoutCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withoutCsrf.getHeaders().getContentType().toString()).contains("json");
        assertThat(withoutCsrf.getBody()).contains("CSRF").doesNotContain("<html");

        ResponseEntity<String> dishes = raw.getForEntity("/api/dishes", String.class);
        assertThat(dishes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(dishes.getBody()).contains("UNAUTHENTICATED").doesNotContain("<html");
    }

    @Test
    void loginRenewsTheSessionAndLogoutInvalidatesIt() {
        TestRestTemplate api = session();
        assertThat(api.postForEntity("/api/setup", setup(tokens.token()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> primed = raw.getForEntity("/api/setup/status", String.class);
        String csrf = ApiSessions.setCookie(primed.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        String before = ApiSessions.setCookie(primed.getHeaders().get(HttpHeaders.SET_COOKIE), "WAEW_SESSION");
        assertThat(csrf).isNotBlank();
        assertThat(before).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "WAEW_SESSION=" + before + "; XSRF-TOKEN=" + csrf);
        headers.add("X-XSRF-TOKEN", csrf);
        ResponseEntity<UserResponse> login = raw.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("ada", ADMIN_PASSWORD), headers),
                UserResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().displayName()).isEqualTo("Ada Lovelace");
        String after = ApiSessions.setCookie(login.getHeaders().get(HttpHeaders.SET_COOKIE), "WAEW_SESSION");
        assertThat(after).isNotBlank().isNotEqualTo(before);

        HttpHeaders oldSession = new HttpHeaders();
        oldSession.add(HttpHeaders.COOKIE, "WAEW_SESSION=" + before);
        assertThat(raw.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(oldSession), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders newSession = new HttpHeaders();
        newSession.add(HttpHeaders.COOKIE, "WAEW_SESSION=" + after);
        assertThat(raw.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(newSession), UserResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> failed = api.postForEntity(
                "/api/auth/login", new LoginRequest(ADMIN, "falsches-passwort"), String.class);
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(failed.getBody()).contains("Benutzername oder Passwort ist falsch.");

        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.add(HttpHeaders.COOKIE, "WAEW_SESSION=" + after + "; XSRF-TOKEN=" + csrfFrom(login));
        logoutHeaders.add("X-XSRF-TOKEN", csrfFrom(login));
        ResponseEntity<Void> logout = raw.exchange(
                "/api/auth/logout", HttpMethod.POST, new HttpEntity<>(logoutHeaders), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(raw.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(newSession), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rolesPasswordsAndTheLastAdministratorAreEnforced() {
        TestRestTemplate admin = adminSession();
        ResponseEntity<UserResponse> created = admin.postForEntity(
                "/api/users",
                new CreateUserRequest("Bea", "Bea Beispiel", MEMBER_PASSWORD, "MEMBER"),
                UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().passwordChangeRequired()).isTrue();
        assertThat(created.getBody().toString()).doesNotContain(MEMBER_PASSWORD);
        UUID memberId = created.getBody().id();
        UUID adminId = admin.getForEntity("/api/auth/me", UserResponse.class).getBody().id();

        TestRestTemplate member = session();
        ResponseEntity<String> blocked = member.getForEntity("/api/dishes", String.class);
        member.postForEntity("/api/auth/login", new LoginRequest("Bea", MEMBER_PASSWORD), UserResponse.class);
        assertThat(member.getForEntity("/api/dishes", String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(member.getForEntity("/api/dishes", String.class).getBody()).contains("PASSWORD_CHANGE_REQUIRED");
        assertThat(member.getForEntity("/api/users", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<UserResponse> changed = member.exchange(
                "/api/auth/password",
                HttpMethod.PUT,
                new HttpEntity<>(new PasswordChangeRequest(MEMBER_PASSWORD, "neues-mitglied-pw", "neues-mitglied-pw")),
                UserResponse.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody().passwordChangeRequired()).isFalse();
        assertThat(member.getForEntity("/api/users", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> dish = member.postForEntity("/api/dishes", named("Eintopf"), String.class);
        assertThat(dish.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<UserResponse> profile = member.exchange(
                "/api/auth/profile",
                HttpMethod.PUT,
                new HttpEntity<>(new ProfileUpdateRequest("Bea Neu")),
                UserResponse.class);
        assertThat(profile.getBody().displayName()).isEqualTo("Bea Neu");
        assertThat(profile.getBody().role()).isEqualTo(de.wasessenwir.user.Role.MEMBER);

        ResponseEntity<String> stillAdmin = admin.exchange(
                "/api/users/" + adminId,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest("Ada Lovelace", "MEMBER", false)),
                String.class);
        assertThat(stillAdmin.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stillAdmin.getBody()).contains("LAST_ACTIVE_ADMIN");

        ResponseEntity<UserResponse> reset = admin.postForEntity(
                "/api/users/" + memberId + "/password",
                new PasswordResetRequest("temporaeres-pw"),
                UserResponse.class);
        assertThat(reset.getBody().passwordChangeRequired()).isTrue();
        assertThat(member.getForEntity("/api/auth/me", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        TestRestTemplate afterReset = session();
        assertThat(afterReset.postForEntity("/api/auth/login", new LoginRequest("Bea", MEMBER_PASSWORD), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(afterReset.postForEntity("/api/auth/login", new LoginRequest("Bea", "temporaeres-pw"), UserResponse.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        admin.exchange(
                "/api/users/" + memberId,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest("Bea Neu", "MEMBER", false)),
                UserResponse.class);
        assertThat(afterReset.getForEntity("/api/auth/me", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        TestRestTemplate disabled = session();
        ResponseEntity<String> disabledLogin = disabled.postForEntity(
                "/api/auth/login", new LoginRequest("Bea", "temporaeres-pw"), String.class);
        assertThat(disabledLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(disabledLogin.getBody()).contains("Benutzername oder Passwort ist falsch.");

        admin.exchange(
                "/api/users/" + memberId,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest("Bea Neu", "ADMIN", true)),
                UserResponse.class);
        ResponseEntity<UserResponse> demoted = admin.exchange(
                "/api/users/" + adminId,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest("Ada Lovelace", "MEMBER", true)),
                UserResponse.class);
        assertThat(demoted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getForEntity("/api/users", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void repeatedLoginFailuresAreLimited() {
        TestRestTemplate api = adminSession();
        TestRestTemplate attacker = session();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(attacker.postForEntity("/api/auth/login", new LoginRequest(ADMIN, "falsches-passwort"), String.class)
                    .getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        ResponseEntity<String> limited = attacker.postForEntity(
                "/api/auth/login", new LoginRequest("ada", ADMIN_PASSWORD), String.class);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(api.getForEntity("/api/auth/me", UserResponse.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private TestRestTemplate adminSession() {
        TestRestTemplate api = session();
        ResponseEntity<String> setup = api.postForEntity("/api/setup", setup(tokens.token()), String.class);
        assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> login = api.postForEntity("/api/auth/login", new LoginRequest(ADMIN, ADMIN_PASSWORD), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return api;
    }

    private TestRestTemplate session() {
        TestRestTemplate api = new ApiSessions(raw).open();
        api.getForEntity("/api/setup/status", String.class);
        return api;
    }

    private static SetupRequest setup(String token) {
        return new SetupRequest(ADMIN, "Ada Lovelace", ADMIN_PASSWORD, ADMIN_PASSWORD, token);
    }

    private static DishWriteRequest named(String name) {
        return new DishWriteRequest(name, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private long countUsers() {
        Long count = jdbc.queryForObject("select count(*) from app_user", Long.class);
        return count == null ? 0 : count;
    }

    private static String csrfFrom(ResponseEntity<?> response) {
        return ApiSessions.setCookie(response.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
    }

    private static ResponseEntity<String> postWhenReady(
            TestRestTemplate api, SetupRequest request, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Startsignal fehlt");
        }
        return api.postForEntity("/api/setup", request, String.class);
    }

}

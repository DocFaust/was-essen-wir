package de.wasessenwir.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.WasEssenWirApplication;
import de.wasessenwir.dish.DishWriteRequest;
import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupBootstrap;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.support.ApiSessions;
import de.wasessenwir.user.CreateUserRequest;
import de.wasessenwir.user.PasswordChangeRequest;
import de.wasessenwir.user.PasswordResetRequest;
import de.wasessenwir.user.UpdateUserRequest;
import de.wasessenwir.user.UserResponse;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class RememberMeIntegrationTest {

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
    private DataSource dataSource;

    @Autowired
    private AttemptLimiter limiter;

    @BeforeEach
    void freshInstall() {
        limiter.clear();
        jdbc.update("delete from remember_me_token");
        jdbc.update("delete from app_user");
        jdbc.update("delete from app_setup");
        bootstrap.initialize();
    }

    @Test
    void loginWithoutRememberMeKeepsOnlyTheSession() {
        assertThat(appliedMigration()).isEqualTo(1L);
        Primed primed = prime();
        ResponseEntity<String> login = login(primed, null, false, ADMIN, ADMIN_PASSWORD);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rememberHeader(login)).isNull();
        assertThat(countTokens()).isZero();
        String session = cookie(login, "WAEW_SESSION");
        assertThat(me(session, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me(null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> remembered = login(from(login), null, true, ADMIN, ADMIN_PASSWORD);
        String remember = cookieValue(rememberHeader(remembered));
        assertThat(countTokens()).isEqualTo(1L);
        ResponseEntity<String> optedOut = login(from(remembered), remember, false, ADMIN, ADMIN_PASSWORD);
        assertThat(rememberHeader(optedOut)).contains("Max-Age=0");
        assertThat(countTokens()).isZero();
        assertThat(me(null, remember).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me(cookie(optedOut, "WAEW_SESSION"), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void loginWithRememberMeStoresOnlyAHashInAnHttpOnlyCookie() {
        Primed primed = prime();
        ResponseEntity<String> login = login(primed, null, true, ADMIN, ADMIN_PASSWORD);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String header = rememberHeader(login);
        String remember = cookieValue(header);
        String secret = secretOf(remember);
        assertThat(header).contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=2592000");
        assertThat(header).doesNotContain("Secure");
        assertThat(login.getBody()).doesNotContain(secret).doesNotContain(selectorOf(remember));
        assertThat(jdbc.queryForObject("select octet_length(token_hash) from remember_me_token", Integer.class)).isEqualTo(32);
        assertThat(jdbc.queryForObject("select selector from remember_me_token", String.class)).isEqualTo(selectorOf(remember));
        assertThat(jdbc.queryForObject("select encode(token_hash, 'hex') from remember_me_token", String.class)).doesNotContain(secret);
        OffsetDateTime expires = jdbc.queryForObject("select expires_at from remember_me_token", OffsetDateTime.class);
        assertThat(expires.toInstant()).isAfter(Instant.now().plus(29, ChronoUnit.DAYS)).isBefore(Instant.now().plus(31, ChronoUnit.DAYS));

        ResponseEntity<String> wrong = login(from(login), remember, true, ADMIN, "falsches-passwort-x");
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody()).doesNotContain(secret);
        assertThat(countTokens()).isEqualTo(1L);
        assertThat(me(null, remember).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void restoresASessionAfterTheSessionCookieIsGoneAndAfterRestart() throws Exception {
        Primed primed = prime();
        ResponseEntity<String> login = login(primed, null, true, ADMIN, ADMIN_PASSWORD);
        ResponseEntity<String> restoredHere = me(null, cookieValue(rememberHeader(login)));
        String remember = cookieValue(rememberHeader(restoredHere));
        assertThat(restoredHere.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restoredHere.getBody()).contains("Ada Lovelace");

        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(WasEssenWirApplication.class)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + hikari.getJdbcUrl(),
                        "--spring.datasource.username=" + hikari.getUsername(),
                        "--spring.datasource.password=" + hikari.getPassword())) {
            int port = ((WebServerApplicationContext) restarted).getWebServer().getPort();
            HttpResponse<String> restored = HttpClient.newHttpClient()
                    .send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/me"))
                                    .header(HttpHeaders.COOKIE, RememberMeCookies.NAME + "=" + remember)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(restored.statusCode()).isEqualTo(200);
            assertThat(restored.body()).contains("Ada Lovelace").doesNotContain(secretOf(remember));
            assertThat(restored.headers().allValues(HttpHeaders.SET_COOKIE).toString()).contains("WAEW_SESSION=");
        }
    }

    @Test
    void rotatesTheSecretAndAllowsOneOverlappingReuse() throws Exception {
        Primed primed = prime();
        String first = cookieValue(rememberHeader(login(primed, null, true, ADMIN, ADMIN_PASSWORD)));
        ResponseEntity<String> rotated = me(null, first);
        String second = cookieValue(rememberHeader(rotated));
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second).isNotEqualTo(first);
        assertThat(selectorOf(second)).isEqualTo(selectorOf(first));

        ResponseEntity<String> overlap = me(null, first);
        assertThat(overlap.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rememberHeader(overlap)).isNull();
        ResponseEntity<String> confirmed = me(null, second);
        String current = cookieValue(rememberHeader(confirmed));
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(current).isNotEqualTo(second);
        assertThat(countTokens()).isEqualTo(1L);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> left = pool.submit(() -> meWhenReady(current, ready, start));
            Future<ResponseEntity<String>> right = pool.submit(() -> meWhenReady(current, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            ResponseEntity<String> firstResponse = left.get(20, TimeUnit.SECONDS);
            ResponseEntity<String> secondResponse = right.get(20, TimeUnit.SECONDS);
            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(countTokens()).isEqualTo(1L);
            String leftCookie = cookieValue(rememberHeader(firstResponse));
            String rightCookie = cookieValue(rememberHeader(secondResponse));
            String renewed = leftCookie == null ? rightCookie : leftCookie;
            assertThat(java.util.stream.Stream.of(leftCookie, rightCookie).filter(value -> value != null).count()).isEqualTo(1);
            assertThat(renewed).isNotEqualTo(current);
            assertThat(me(null, renewed).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void replayAfterTheGracePeriodRevokesEveryPersistentLogin() {
        Primed primed = prime();
        String first = cookieValue(rememberHeader(login(primed, null, true, ADMIN, ADMIN_PASSWORD)));
        String second = cookieValue(rememberHeader(me(null, first)));
        assertThat(me(null, first).getStatusCode()).isEqualTo(HttpStatus.OK);

        jdbc.update("update remember_me_token set rotated_at = ?", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        ResponseEntity<String> replay = me(null, first);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replay.getBody()).contains("UNAUTHENTICATED").doesNotContain(secretOf(first));
        assertThat(rememberHeader(replay)).contains("Max-Age=0");
        assertThat(countTokens()).isZero();
        assertThat(me(null, second).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenReturnsToLogin() {
        Primed primed = prime();
        String remember = cookieValue(rememberHeader(login(primed, null, true, ADMIN, ADMIN_PASSWORD)));
        jdbc.update("update remember_me_token set expires_at = ?", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        ResponseEntity<String> expired = me(null, remember);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(expired.getBody()).contains("Bitte melde dich an.");
        assertThat(rememberHeader(expired)).contains("Max-Age=0");
        assertThat(countTokens()).isZero();
        ResponseEntity<String> garbage = me(null, "kaputt");
        assertThat(garbage.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(garbage.getBody()).doesNotContain("kaputt");
        assertThat(rememberHeader(garbage)).contains("Max-Age=0");
    }

    @Test
    void logoutRevokesOnlyTheCurrentDevice() {
        Primed admin = prime();
        ResponseEntity<String> first = login(admin, null, true, ADMIN, ADMIN_PASSWORD);
        String firstRemember = cookieValue(rememberHeader(first));
        Primed other = primeAnonymous();
        ResponseEntity<String> second = login(other, null, true, ADMIN, ADMIN_PASSWORD);
        String secondRemember = cookieValue(rememberHeader(second));
        assertThat(countTokens()).isEqualTo(2L);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookies(cookie(second, "WAEW_SESSION"), cookie(second, "XSRF-TOKEN"), secondRemember));
        headers.add("X-XSRF-TOKEN", cookie(second, "XSRF-TOKEN"));
        ResponseEntity<Void> logout = raw.exchange("/api/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rememberHeader(logout)).contains("Max-Age=0");
        assertThat(me(cookie(second, "WAEW_SESSION"), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me(null, secondRemember).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me(cookie(first, "WAEW_SESSION"), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me(null, firstRemember).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countTokens()).isEqualTo(1L);
    }

    @Test
    void passwordChangeResetAndDeactivationRevokePersistentLogins() {
        Primed primed = prime();
        ResponseEntity<String> adminLogin = login(primed, null, true, ADMIN, ADMIN_PASSWORD);
        String adminRemember = cookieValue(rememberHeader(adminLogin));
        String adminSession = cookie(adminLogin, "WAEW_SESSION");
        String adminCsrf = cookie(adminLogin, "XSRF-TOKEN");

        ResponseEntity<UserResponse> created = raw.exchange(
                "/api/users",
                HttpMethod.POST,
                new HttpEntity<>(new CreateUserRequest("Bea", "Bea Beispiel", MEMBER_PASSWORD, "MEMBER"), json(adminSession, adminCsrf, null)),
                UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID memberId = created.getBody().id();

        ResponseEntity<String> memberLogin = login(primeAnonymous(), null, true, "Bea", MEMBER_PASSWORD);
        String memberRemember = cookieValue(rememberHeader(memberLogin));
        String memberSession = cookie(memberLogin, "WAEW_SESSION");
        assertThat(countTokens()).isEqualTo(2L);

        HttpHeaders passwordHeaders = json(adminSession, adminCsrf, adminRemember);
        ResponseEntity<String> changed = raw.exchange(
                "/api/auth/password",
                HttpMethod.PUT,
                new HttpEntity<>(new PasswordChangeRequest(ADMIN_PASSWORD, "neues-admin-passwort", "neues-admin-passwort"), passwordHeaders),
                String.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rememberHeader(changed)).contains("Max-Age=0");
        assertThat(me(adminSession, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me(null, adminRemember).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me(null, memberRemember).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<UserResponse> reset = raw.exchange(
                "/api/users/" + memberId + "/password",
                HttpMethod.POST,
                new HttpEntity<>(new PasswordResetRequest("temporaeres-pw"), json(adminSession, adminCsrf, null)),
                UserResponse.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me(memberSession, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me(null, memberRemember).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(tokensFor(memberId)).isZero();

        ResponseEntity<String> afterReset = login(primeAnonymous(), null, true, "Bea", "temporaeres-pw");
        String resetRemember = cookieValue(rememberHeader(afterReset));
        String resetSession = cookie(afterReset, "WAEW_SESSION");
        raw.exchange(
                "/api/users/" + memberId,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest("Bea Beispiel", "MEMBER", false), json(adminSession, adminCsrf, null)),
                UserResponse.class);
        assertThat(me(resetSession, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> disabledRestore = me(null, resetRemember);
        assertThat(disabledRestore.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rememberHeader(disabledRestore)).contains("Max-Age=0");
        assertThat(login(primeAnonymous(), null, true, "Bea", "temporaeres-pw").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(tokensFor(memberId)).isZero();
        assertThat(me(adminSession, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void manipulatedTokenDoesNotRevokeAnotherDevice() {
        Primed primed = prime();
        String kept = cookieValue(rememberHeader(login(primed, null, true, ADMIN, ADMIN_PASSWORD)));
        String other = cookieValue(rememberHeader(login(primeAnonymous(), null, true, ADMIN, ADMIN_PASSWORD)));
        String tampered = selectorOf(other) + "." + flipped(secretOf(other));
        ResponseEntity<String> rejected = me(null, tampered);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rejected.getBody()).doesNotContain(secretOf(other)).doesNotContain(tampered);
        assertThat(rememberHeader(rejected)).contains("Max-Age=0");
        assertThat(countTokens()).isEqualTo(1L);
        assertThat(me(null, kept).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me(null, other).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginDoesNotRebindAForeignRememberMeSeries() {
        Primed primed = prime();
        ResponseEntity<String> adminLogin = login(primed, null, true, ADMIN, ADMIN_PASSWORD);
        String adminRemember = cookieValue(rememberHeader(adminLogin));
        ResponseEntity<String> renewed = login(from(adminLogin), adminRemember, true, ADMIN, ADMIN_PASSWORD);
        String adminRenewed = cookieValue(rememberHeader(renewed));
        assertThat(selectorOf(adminRenewed)).isEqualTo(selectorOf(adminRemember));
        assertThat(secretOf(adminRenewed)).isNotEqualTo(secretOf(adminRemember));
        assertThat(countTokens()).isEqualTo(1L);
        ResponseEntity<String> overlap = me(null, adminRemember);
        assertThat(overlap.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(overlap.getBody()).contains("Ada Lovelace");
        assertThat(rememberHeader(overlap)).isNull();
        String adminHash = tokenHashFor(selectorOf(adminRenewed));

        String adminSession = cookie(renewed, "WAEW_SESSION");
        String adminCsrf = cookie(renewed, "XSRF-TOKEN");
        ResponseEntity<UserResponse> created = raw.exchange(
                "/api/users",
                HttpMethod.POST,
                new HttpEntity<>(new CreateUserRequest("Bea", "Bea Beispiel", MEMBER_PASSWORD, "MEMBER"), json(adminSession, adminCsrf, null)),
                UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> memberLogin = login(primeAnonymous(), adminRenewed, true, "Bea", MEMBER_PASSWORD);
        String memberRemember = cookieValue(rememberHeader(memberLogin));
        assertThat(memberLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memberLogin.getBody()).contains("Bea Beispiel");
        assertThat(selectorOf(memberRemember)).isNotEqualTo(selectorOf(adminRenewed));
        assertThat(countTokens()).isEqualTo(2L);
        assertThat(tokenHashFor(selectorOf(adminRenewed))).isEqualTo(adminHash);

        ResponseEntity<String> adminRestore = me(null, adminRenewed);
        assertThat(adminRestore.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminRestore.getBody()).contains("Ada Lovelace").doesNotContain("Bea Beispiel");
        ResponseEntity<String> memberRestore = me(null, memberRemember);
        assertThat(memberRestore.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memberRestore.getBody()).contains("Bea Beispiel").doesNotContain("Ada Lovelace");
    }

    @Test
    void csrfStillProtectsStateChangingRequests() {
        Primed primed = prime();
        ResponseEntity<String> login = login(primed, null, true, ADMIN, ADMIN_PASSWORD);
        ResponseEntity<String> restored = me(null, cookieValue(rememberHeader(login)));
        String session = cookie(restored, "WAEW_SESSION");
        String csrf = cookie(restored, "XSRF-TOKEN");
        String remember = cookieValue(rememberHeader(restored));
        String hash = tokenHash();

        HttpHeaders sessionOnly = new HttpHeaders();
        sessionOnly.setContentType(MediaType.APPLICATION_JSON);
        sessionOnly.add(HttpHeaders.COOKIE, cookies(session, null, null));
        ResponseEntity<String> missing = raw.exchange(
                "/api/dishes", HttpMethod.POST, new HttpEntity<>(named("Eintopf"), sessionOnly), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(missing.getBody()).contains("CSRF");
        assertThat(tokenHash()).isEqualTo(hash);

        HttpHeaders rememberOnly = new HttpHeaders();
        rememberOnly.setContentType(MediaType.APPLICATION_JSON);
        rememberOnly.add(HttpHeaders.COOKIE, cookies(null, null, remember));
        ResponseEntity<String> crossSite = raw.exchange(
                "/api/dishes", HttpMethod.POST, new HttpEntity<>(named("Suppe"), rememberOnly), String.class);
        assertThat(crossSite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(crossSite.getBody()).contains("CSRF");
        assertThat(tokenHash()).isEqualTo(hash);

        ResponseEntity<String> created = raw.exchange(
                "/api/dishes",
                HttpMethod.POST,
                new HttpEntity<>(named("Auflauf"), json(session, csrf, null)),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> restoredPost = raw.exchange(
                "/api/dishes",
                HttpMethod.POST,
                new HttpEntity<>(named("Gratin"), json(null, csrf, remember)),
                String.class);
        assertThat(restoredPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(tokenHash()).isNotEqualTo(hash);
    }

    @Test
    void limitsPersistentLoginsPerUser() {
        Primed primed = prime();
        String session = primed.session();
        String csrf = primed.csrf();
        for (int index = 0; index < RememberMeService.MAX_TOKENS_PER_USER + 1; index++) {
            ResponseEntity<String> login = login(new Primed(session, csrf), null, true, ADMIN, ADMIN_PASSWORD);
            assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
            session = cookie(login, "WAEW_SESSION");
            csrf = cookie(login, "XSRF-TOKEN");
        }
        assertThat(countTokens()).isEqualTo(RememberMeService.MAX_TOKENS_PER_USER);
    }

    private Primed prime() {
        TestRestTemplate api = new ApiSessions(raw).open();
        api.getForEntity("/api/setup/status", String.class);
        assertThat(api.postForEntity("/api/setup", setup(), String.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return primeAnonymous();
    }

    private Primed primeAnonymous() {
        ResponseEntity<String> primed = raw.getForEntity("/api/setup/status", String.class);
        return new Primed(cookie(primed, "WAEW_SESSION"), cookie(primed, "XSRF-TOKEN"));
    }

    private ResponseEntity<String> login(Primed primed, String remember, boolean rememberMe, String username, String password) {
        return raw.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(username, password, rememberMe), json(primed.session(), primed.csrf(), remember)),
                String.class);
    }

    private ResponseEntity<String> me(String session, String remember) {
        HttpHeaders headers = new HttpHeaders();
        String cookieHeader = cookies(session, null, remember);
        if (!cookieHeader.isBlank()) {
            headers.add(HttpHeaders.COOKIE, cookieHeader);
        }
        return raw.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> meWhenReady(String remember, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Startsignal fehlt");
        }
        return me(null, remember);
    }

    private static HttpHeaders json(String session, String csrf, String remember) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String cookieHeader = cookies(session, csrf, remember);
        if (!cookieHeader.isBlank()) {
            headers.add(HttpHeaders.COOKIE, cookieHeader);
        }
        if (csrf != null) {
            headers.add("X-XSRF-TOKEN", csrf);
        }
        return headers;
    }

    private static String cookies(String session, String csrf, String remember) {
        List<String> parts = new ArrayList<>();
        if (session != null) {
            parts.add("WAEW_SESSION=" + session);
        }
        if (csrf != null) {
            parts.add("XSRF-TOKEN=" + csrf);
        }
        if (remember != null) {
            parts.add(RememberMeCookies.NAME + "=" + remember);
        }
        return String.join("; ", parts);
    }

    private static Primed from(ResponseEntity<?> response) {
        return new Primed(cookie(response, "WAEW_SESSION"), cookie(response, "XSRF-TOKEN"));
    }

    private static String cookie(ResponseEntity<?> response, String name) {
        return ApiSessions.setCookie(response.getHeaders().get(HttpHeaders.SET_COOKIE), name);
    }

    private static String rememberHeader(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return null;
        }
        String found = null;
        for (String cookie : cookies) {
            if (cookie.startsWith(RememberMeCookies.NAME + "=")) {
                found = cookie;
            }
        }
        return found;
    }

    private static String cookieValue(String header) {
        if (header == null) {
            return null;
        }
        int start = header.indexOf('=') + 1;
        int end = header.indexOf(';');
        String value = end < 0 ? header.substring(start) : header.substring(start, end);
        return value.isBlank() ? null : value;
    }

    private static String selectorOf(String cookie) {
        return cookie.substring(0, cookie.indexOf('.'));
    }

    private static String secretOf(String cookie) {
        return cookie.substring(cookie.indexOf('.') + 1);
    }

    private static String flipped(String secret) {
        char last = secret.charAt(secret.length() - 1);
        return secret.substring(0, secret.length() - 1) + (last == 'a' ? 'b' : 'a');
    }

    private SetupRequest setup() {
        return new SetupRequest(ADMIN, "Ada Lovelace", ADMIN_PASSWORD, ADMIN_PASSWORD, tokens.token());
    }

    private static DishWriteRequest named(String name) {
        return new DishWriteRequest(name, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private long countTokens() {
        Long count = jdbc.queryForObject("select count(*) from remember_me_token", Long.class);
        return count == null ? 0 : count;
    }

    private long tokensFor(UUID userId) {
        Long count = jdbc.queryForObject("select count(*) from remember_me_token where user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    private String tokenHash() {
        return jdbc.queryForObject("select encode(token_hash, 'hex') from remember_me_token", String.class);
    }

    private String tokenHashFor(String selector) {
        return jdbc.queryForObject(
                "select encode(token_hash, 'hex') from remember_me_token where selector = ?",
                String.class,
                selector);
    }

    private long appliedMigration() {
        Long applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '6' and success", Long.class);
        return applied == null ? 0 : applied;
    }

    private record Primed(String session, String csrf) {}
}

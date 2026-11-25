package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.security.LoginRequest;
import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.setup.SetupStatusResponse;
import de.wasessenwir.support.ApiSessions;
import de.wasessenwir.user.CreateUserRequest;
import de.wasessenwir.user.PasswordChangeRequest;
import de.wasessenwir.user.UserResponse;
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
import org.springframework.http.ResponseEntity;
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
class AiRateLimitIntegrationTest {

    private static final String PASSWORD = "ein-langes-passwort";
    private static final String MEMBER_PASSWORD = "mitglied-passwort";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        registry.add("app.ai.enabled", () -> "true");
        registry.add("app.ai.provider", () -> "fixture");
        registry.add("app.ai.rate-limit.per-user-per-minute", () -> "2");
        registry.add("app.ai.rate-limit.per-user-per-day", () -> "50");
        registry.add("app.ai.rate-limit.global-per-day", () -> "150");
        registry.add("app.ai.rate-limit.max-concurrent-per-user", () -> "1");
        registry.add("app.ai.rate-limit.max-concurrent-global", () -> "3");
    }

    @Autowired
    private TestRestTemplate rawTemplate;

    @Autowired
    private CapturingSetupTokenPublisher tokens;

    @Autowired
    private AiUsageLimiter limiter;

    private TestRestTemplate restTemplate;

    @BeforeEach
    void signIn() {
        limiter.clear();
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
    }

    @Test
    void sharesMinuteLimitAcrossAiEndpointsAndReturnsRetryAfter() {
        assertThat(recommend().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(draft().getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> limited = recommend();
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getBody()).contains("AI_RATE_LIMITED").contains("zu oft genutzt");
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(Integer.parseInt(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isPositive();

        ResponseEntity<String> draftLimited = draft();
        assertThat(draftLimited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(draftLimited.getBody()).contains("AI_RATE_LIMITED");
    }

    @Test
    void keepsUserLimitsIndependent() {
        assertThat(recommend().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recommend().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recommend().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        TestRestTemplate member = createMemberSession("Bea");
        ResponseEntity<String> memberOk = member.postForEntity(
                "/api/ai/recommendations",
                new AiRecommendationRequest("Heute Abend etwas Schnelles", null),
                String.class);
        assertThat(memberOk.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void leavesNonAiEndpointsUsableWhenAiIsLimited() {
        assertThat(recommend().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recommend().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recommend().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        ResponseEntity<AiStatusResponse> status = restTemplate.getForEntity("/api/ai/status", AiStatusResponse.class);
        ResponseEntity<String> me = restTemplate.getForEntity("/api/auth/me", String.class);

        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody().enabled()).isTrue();
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> recommend() {
        return restTemplate.postForEntity(
                "/api/ai/recommendations",
                new AiRecommendationRequest("Heute Abend etwas Schnelles", null),
                String.class);
    }

    private ResponseEntity<String> draft() {
        return restTemplate.postForEntity(
                "/api/ai/dish-drafts",
                new DishDraftRequest("Wir machen oft Kartoffelsuppe.", DraftKind.DISH, null),
                String.class);
    }

    private TestRestTemplate createMemberSession(String username) {
        ResponseEntity<UserResponse> created = restTemplate.postForEntity(
                "/api/users",
                new CreateUserRequest(username, username + " Beispiel", "temporaer-pass", "MEMBER"),
                UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        TestRestTemplate member = new ApiSessions(rawTemplate).open();
        member.getForEntity("/api/setup/status", String.class);
        assertThat(member.postForEntity("/api/auth/login", new LoginRequest(username, "temporaer-pass"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(member.exchange(
                "/api/auth/password",
                HttpMethod.PUT,
                new HttpEntity<>(new PasswordChangeRequest("temporaer-pass", MEMBER_PASSWORD, MEMBER_PASSWORD)),
                String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        return member;
    }
}

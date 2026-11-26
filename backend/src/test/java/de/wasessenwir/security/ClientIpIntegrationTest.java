package de.wasessenwir.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupBootstrap;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.support.ApiSessions;
import de.wasessenwir.user.Role;
import de.wasessenwir.user.UserResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.security.setup-max-attempts=2",
            "app.security.setup-window=PT15M",
            "app.security.forward-client-ip=true",
            "app.security.trusted-proxies=127.0.0.1/32,0:0:0:0:0:0:0:1/128"
        })
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class ClientIpIntegrationTest {

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

    @BeforeEach
    void freshInstall() {
        limiter.clear();
        jdbc.update("delete from app_user");
        jdbc.update("delete from app_setup");
        bootstrap.initialize();
    }

    @Test
    void spoofedForwardedHeaderFromTrustedLoopbackAffectsSetupRateLimitKey() {
        AtomicReference<String> forwarded = new AtomicReference<>("203.0.113.10");
        TestRestTemplate api = sessionWithForwarded(forwarded);

        assertThat(api.postForEntity("/api/setup", setup("bad-1"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(api.postForEntity("/api/setup", setup("bad-2"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(api.postForEntity("/api/setup", setup("bad-3"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        forwarded.set("203.0.113.11");
        ResponseEntity<String> otherIp = api.postForEntity("/api/setup", setup("bad-4"), String.class);
        assertThat(otherIp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(otherIp.getBody()).contains("SETUP_TOKEN_INVALID");
    }

    @Test
    void successfulSetupStillWorksWithForwardedClientIp() {
        AtomicReference<String> forwarded = new AtomicReference<>("198.51.100.40");
        TestRestTemplate api = sessionWithForwarded(forwarded);
        ResponseEntity<UserResponse> created =
                api.postForEntity("/api/setup", setup(tokens.token()), UserResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().role()).isEqualTo(Role.ADMIN);
    }

    private TestRestTemplate sessionWithForwarded(AtomicReference<String> forwarded) {
        TestRestTemplate api = new ApiSessions(raw).open();
        api.getForEntity("/api/setup/status", String.class);
        api.getRestTemplate().getInterceptors().add(new ForwardedInterceptor(forwarded));
        return api;
    }

    private static SetupRequest setup(String token) {
        return new SetupRequest("Ada", "Ada Lovelace", "ein-langes-passwort", "ein-langes-passwort", token);
    }

    private record ForwardedInterceptor(AtomicReference<String> forwarded) implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            String value = forwarded.get();
            if (value != null && !value.isBlank()) {
                request.getHeaders().set("X-Forwarded-For", value);
            }
            return execution.execute(request, body);
        }
    }
}

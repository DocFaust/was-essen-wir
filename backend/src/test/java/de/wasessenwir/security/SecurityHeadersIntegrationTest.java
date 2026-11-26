package de.wasessenwir.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.support.ApiSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
class SecurityHeadersIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private TestRestTemplate raw;

    @Test
    void apiResponsesCarrySecurityHeadersWithoutHstsOnPlainHttp() {
        ResponseEntity<String> response = raw.getForEntity("/api/status", String.class);

        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(headers.getFirst("Permissions-Policy")).contains("camera=()");
        assertThat(headers.getFirst("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(headers.getFirst("Strict-Transport-Security")).isNull();
        assertThat(response.getBody()).contains("was-essen-wir");
    }

    @Test
    void csrfCookieRemainsReadableForTheFrontend() {
        TestRestTemplate api = new ApiSessions(raw).open();
        ResponseEntity<String> response = api.getForEntity("/api/setup/status", String.class);

        String cookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).toString();
        assertThat(cookies).contains("XSRF-TOKEN=");
        assertThat(cookies).contains("Path=/");
        assertThat(cookies.toLowerCase()).doesNotContain("httponly");
    }
}

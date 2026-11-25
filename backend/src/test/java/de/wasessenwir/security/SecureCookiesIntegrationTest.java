package de.wasessenwir.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupBootstrap;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.user.UserResponse;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.servlet.session.cookie.secure=true",
            "app.security.require-secure-cookies=true"
        })
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class SecureCookiesIntegrationTest {

    private static final String ADMIN = "Ada";
    private static final String ADMIN_PASSWORD = "ein-langes-passwort";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CapturingSetupTokenPublisher tokens;

    @Autowired
    private SetupBootstrap bootstrap;

    @Autowired
    private AttemptLimiter limiter;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void freshInstall() {
        limiter.clear();
        jdbc.update("delete from app_user");
        jdbc.update("delete from app_setup");
        bootstrap.initialize();
    }

    @Test
    void sessionRememberMeAndCsrfCookiesCarrySecureFlag() throws Exception {
        List<String> jar = new ArrayList<>();

        HttpResponse<String> primed = exchange(
                HttpRequest.newBuilder(uri("/api/setup/status")).GET().build(), jar);
        assertThat(primed.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(jar.stream().anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=") && cookie.contains("Secure")))
                .isTrue();
        assertThat(jar.stream().anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=") && cookie.toLowerCase().contains("httponly")))
                .isFalse();

        String setupBody = json.writeValueAsString(
                new SetupRequest(ADMIN, "Ada Lovelace", ADMIN_PASSWORD, ADMIN_PASSWORD, tokens.token()));
        HttpResponse<String> setup = exchange(
                HttpRequest.newBuilder(uri("/api/setup"))
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .header("X-XSRF-TOKEN", cookieValue(jar, "XSRF-TOKEN"))
                        .POST(HttpRequest.BodyPublishers.ofString(setupBody))
                        .build(),
                jar);
        assertThat(setup.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(json.readValue(setup.body(), UserResponse.class).username()).isEqualTo(ADMIN);

        String loginBody = json.writeValueAsString(new LoginRequest(ADMIN, ADMIN_PASSWORD, true));
        HttpResponse<String> login = exchange(
                HttpRequest.newBuilder(uri("/api/auth/login"))
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .header("X-XSRF-TOKEN", cookieValue(jar, "XSRF-TOKEN"))
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                        .build(),
                jar);
        assertThat(login.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(jar.stream().anyMatch(cookie -> cookie.startsWith("WAEW_SESSION=") && cookie.contains("Secure")))
                .isTrue();
        assertThat(jar.stream().anyMatch(cookie -> cookie.startsWith("WAEW_SESSION=") && cookie.contains("HttpOnly")))
                .isTrue();
        assertThat(jar.stream().anyMatch(cookie -> cookie.startsWith("WAEW_SESSION=") && cookie.contains("SameSite=Lax")))
                .isTrue();
        assertThat(jar.stream().anyMatch(cookie -> cookie.startsWith("WAEW_REMEMBER=") && cookie.contains("Secure")))
                .isTrue();
        assertThat(jar.stream().anyMatch(cookie -> cookie.startsWith("WAEW_REMEMBER=") && cookie.contains("HttpOnly")))
                .isTrue();
        assertThat(jar.stream().anyMatch(cookie -> cookie.startsWith("WAEW_REMEMBER=") && cookie.contains("SameSite=Lax")))
                .isTrue();
    }

    private HttpResponse<String> exchange(HttpRequest request, List<String> jar) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri()).method(
                request.method(),
                request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        request.headers().map().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        if (!jar.isEmpty()) {
            builder.header(
                    HttpHeaders.COOKIE,
                    jar.stream().map(cookie -> cookie.split(";", 2)[0]).collect(Collectors.joining("; ")));
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        List<String> setCookies = response.headers().allValues(HttpHeaders.SET_COOKIE);
        for (String setCookie : setCookies) {
            String name = setCookie.split("=", 2)[0];
            jar.removeIf(existing -> existing.startsWith(name + "="));
            jar.add(setCookie);
        }
        return response;
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String cookieValue(List<String> jar, String name) {
        String prefix = name + "=";
        for (String cookie : jar) {
            if (cookie.startsWith(prefix)) {
                String value = cookie.substring(prefix.length()).split(";", 2)[0];
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Cookie fehlt: " + name);
    }
}

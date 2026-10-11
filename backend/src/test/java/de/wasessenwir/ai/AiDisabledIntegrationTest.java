package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import de.wasessenwir.dish.DishWriteRequest;
import de.wasessenwir.dish.MealType;
import de.wasessenwir.dish.Suitability;
import de.wasessenwir.recommendation.RecommendationRequest;
import de.wasessenwir.recommendation.RecommendationResponse;
import de.wasessenwir.security.LoginRequest;
import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.setup.SetupStatusResponse;
import de.wasessenwir.support.ApiSessions;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
class AiDisabledIntegrationTest {

    private static final String PASSWORD = "ein-langes-passwort";
    private static final AtomicInteger HITS = new AtomicInteger();
    private static final HttpServer SERVER = startServer();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        registry.add("app.ai.enabled", () -> "false");
        registry.add("app.ai.provider", () -> "ollama");
        registry.add("app.ai.ollama.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
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
        jdbcTemplate.update("delete from dish");
    }

    @Test
    void staysAvailableWithoutCallingAProvider() {
        restTemplate.postForEntity(
                "/api/dishes",
                new DishWriteRequest(
                        "Suppe", null, List.of(MealType.ABENDESSEN), List.of(), null, List.of(),
                        null, null, null, null, Suitability.UNKNOWN, Suitability.UNKNOWN, true),
                String.class);
        UUID ada = jdbcTemplate.queryForObject("select id from app_user where username_normalized = 'ada'", UUID.class);

        ResponseEntity<AiStatusResponse> status = restTemplate.getForEntity("/api/ai/status", AiStatusResponse.class);
        ResponseEntity<String> assistant = restTemplate.postForEntity(
                "/api/ai/recommendations", new AiRecommendationRequest("Was essen wir?", null), String.class);
        ResponseEntity<RecommendationResponse> classic = restTemplate.postForEntity(
                "/api/recommendations",
                new RecommendationRequest(MealType.ABENDESSEN, List.of(ada), false, false, false, List.of(), 1L, null),
                RecommendationResponse.class);

        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody().enabled()).isFalse();
        assertThat(status.getBody().provider()).isNull();
        assertThat(assistant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(assistant.getBody()).contains("AI_DISABLED").doesNotContain("Exception");
        assertThat(classic.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(classic.getBody().suggestions()).hasSize(1);
        assertThat(HITS).hasValue(0);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                HITS.incrementAndGet();
                byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

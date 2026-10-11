package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import de.wasessenwir.dish.DishDetailResponse;
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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class AiOpenAiIntegrationTest {

    private static final String PASSWORD = "ein-langes-passwort";
    private static final String API_KEY = "sk-test-not-a-secret";
    private static final List<String> AUTHORIZATIONS = new CopyOnWriteArrayList<>();
    private static final HttpServer SERVER = startServer();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        registry.add("app.ai.enabled", () -> "true");
        registry.add("app.ai.provider", () -> "openai");
        registry.add("app.ai.openai.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
        registry.add("app.ai.openai.api-key", () -> API_KEY);
        registry.add("app.ai.openai.model", () -> "gpt-test");
        registry.add("app.ai.openai.timeout", () -> "2s");
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
        AUTHORIZATIONS.clear();
        restTemplate = new ApiSessions(rawTemplate).open();
        SetupStatusResponse status = restTemplate.getForEntity("/api/setup/status", SetupStatusResponse.class).getBody();
        if (status != null && status.setupRequired()) {
            ResponseEntity<String> setup = restTemplate.postForEntity(
                    "/api/setup",
                    new SetupRequest("Ada", "Ada Lovelace", PASSWORD, PASSWORD, tokens.token()),
                    String.class);
            assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        assertThat(restTemplate.postForEntity("/api/auth/login", new LoginRequest("Ada", PASSWORD), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        jdbcTemplate.update("delete from dish");
    }

    @Test
    void selectsOpenAiFromConfigurationWithoutExposingTheKey() {
        restTemplate.postForEntity(
                "/api/dishes",
                new DishWriteRequest(
                        "Suppe", null, List.of(MealType.ABENDESSEN), List.of(), null, List.of(),
                        null, null, null, null, Suitability.UNKNOWN, Suitability.UNKNOWN, true),
                DishDetailResponse.class);
        UUID ada = jdbcTemplate.queryForObject("select id from app_user where username_normalized = 'ada'", UUID.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/ai/recommendations",
                new AiRecommendationRequest("Heute Abend etwas Schnelles", 5L),
                String.class);
        ResponseEntity<AiStatusResponse> status = restTemplate.getForEntity("/api/ai/status", AiStatusResponse.class);
        ResponseEntity<RecommendationResponse> classic = restTemplate.postForEntity(
                "/api/recommendations",
                new RecommendationRequest(MealType.ABENDESSEN, List.of(ada), true, false, false, List.of(), 5L, null),
                RecommendationResponse.class);

        assertThat(status.getBody().enabled()).isTrue();
        assertThat(status.getBody().provider()).isEqualTo("openai");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).contains("Suppe").doesNotContain(API_KEY).doesNotContain("\"stars\"");
        assertThat(AUTHORIZATIONS).containsExactly("Bearer " + API_KEY);
        assertThat(classic.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(classic.getBody().suggestions()).hasSize(1);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                exchange.getRequestBody().readAllBytes();
                AUTHORIZATIONS.add(exchange.getRequestHeaders().getFirst("Authorization"));
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.createObjectNode();
                root.putArray("choices").addObject().putObject("message").put("content", """
                        {"version":1,"mealType":"ABENDESSEN","date":null,"participantNames":[],"onlyNamedParticipants":false,\
                        "preferSimple":true,"preferHomeOffice":false,"preferMealPrep":false,"dishSearchTerms":[],\
                        "avoidDishTerms":[],"needsClarification":false,"clarificationQuestion":null}\
                        """);
                byte[] bytes = mapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class AiProviderHttpTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ollamaReadsStructuredContentAndSurvivesConnectionProblems() throws Exception {
        start("/api/chat", 200, ollamaBody("{\"version\":1}"));
        AiProperties.Ollama properties = ollama(url(), "test-model", Duration.ofSeconds(2));

        String content = new OllamaAiProvider(properties, 1024, mapper).interpret("System", "Heute Abend");

        assertThat(content).isEqualTo("{\"version\":1}");
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().path()).isEqualTo("/api/chat");
        assertThat(requests.getFirst().body())
                .contains("\"model\":\"test-model\"")
                .contains("Heute Abend")
                .contains("\"format\":\"json\"")
                .contains("\"num_predict\":1024");
        assertThat(requests.getFirst().authorization()).isNull();

        assertThatThrownBy(() -> new OllamaAiProvider(
                        ollama("http://127.0.0.1:" + closedPort(), "test-model", Duration.ofMillis(500)), 1024, mapper)
                .interpret("System", "Heute"))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).kind())
                .isEqualTo(AiProviderException.Kind.UNREACHABLE);
    }

    @Test
    void ollamaTimesOut() throws Exception {
        start("/api/chat", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            write(exchange, 200, ollamaBody("{}"));
        });
        AiProperties.Ollama properties = ollama(url(), "test-model", Duration.ofMillis(300));

        assertThatThrownBy(() -> new OllamaAiProvider(properties, 1024, mapper).interpret("System", "Heute"))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).kind())
                .isEqualTo(AiProviderException.Kind.TIMEOUT);
    }

    @Test
    void openAiUsesChatCompletionsAndKeepsTheKeyOutOfTheResult() throws Exception {
        start("/v1/chat/completions", 200, openAiBody("{\"version\":1}"));
        AiProperties.OpenAi properties = openAi(url(), "sk-test-not-a-secret", "gpt-test");

        String content = new OpenAiAiProvider(properties, 1024, mapper).interpret("System JSON", "Nudeln");

        assertThat(content).isEqualTo("{\"version\":1}").doesNotContain("sk-test-not-a-secret");
        assertThat(requests.getFirst().path()).isEqualTo("/v1/chat/completions");
        assertThat(requests.getFirst().authorization()).isEqualTo("Bearer sk-test-not-a-secret");
        assertThat(requests.getFirst().body())
                .contains("\"type\":\"json_schema\"")
                .contains("\"max_tokens\":1024")
                .contains("Nudeln")
                .doesNotContain("sk-test-not-a-secret");
    }

    @Test
    void openAiClassifiesQuotaAuthRateLimitAndOtherProviderErrors() throws Exception {
        start("/v1/chat/completions", 429, "{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"billing detail\"}}");
        assertKind(AiProviderException.Kind.QUOTA, "billing detail");

        stop();
        requests.clear();
        start("/v1/chat/completions", 429, "{\"error\":{\"type\":\"credit_balance_exhausted\",\"message\":\"balance\"}}");
        assertKind(AiProviderException.Kind.QUOTA, "balance");

        stop();
        requests.clear();
        start("/v1/chat/completions", 401, "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"sk-live-secret\"}}");
        assertKind(AiProviderException.Kind.UNAUTHORIZED, "sk-live-secret");

        stop();
        requests.clear();
        start("/v1/chat/completions", 429, "{\"error\":{\"code\":\"rate_limit_exceeded\"}}");
        assertKind(AiProviderException.Kind.RATE_LIMITED, "rate_limit_exceeded");

        stop();
        requests.clear();
        start("/v1/chat/completions", 500, "{\"error\":{\"message\":\"internal\"}}");
        assertKind(AiProviderException.Kind.PROVIDER, "internal");

        assertThatThrownBy(() -> new OpenAiAiProvider(openAi("http://127.0.0.1:9", " ", "gpt-test"), 128, mapper)
                        .draft("System", "Suppe"))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).kind())
                .isEqualTo(AiProviderException.Kind.UNAUTHORIZED);
    }

    private void assertKind(AiProviderException.Kind kind, String secret) {
        assertThatThrownBy(() -> new OpenAiAiProvider(openAi(url(), "sk-test-not-a-secret", "gpt-test"), 128, mapper)
                        .draft("System", "Suppe"))
                .isInstanceOf(AiProviderException.class)
                .satisfies(exception -> {
                    AiProviderException providerException = (AiProviderException) exception;
                    assertThat(providerException.kind()).isEqualTo(kind);
                    assertThat(providerException.getMessage()).doesNotContain(secret).doesNotContain("sk-test");
                });
    }

    @Test
    void fixtureTranslatesTheEveningExampleWithoutNetwork() {
        String json = new FixtureAiProvider(java.time.Clock.systemUTC(), mapper)
                .interpret("System", "Heute Abend etwas Schnelles");
        MealIntent intent = new IntentValidator(mapper).parse(json);

        assertThat(intent.mealType()).isEqualTo(de.wasessenwir.dish.MealType.ABENDESSEN);
        assertThat(intent.preferSimple()).isTrue();
        assertThat(intent.participantNames()).isEmpty();
        assertThat(intent.date()).isNull();
    }

    private void start(String path, int status, String body) throws IOException {
        start(path, exchange -> write(exchange, status, body));
    }

    private void start(String path, com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            requests.add(new Recorded(
                    exchange.getRequestURI().getPath(),
                    new String(bytes, StandardCharsets.UTF_8),
                    exchange.getRequestHeaders().getFirst("Authorization")));
            handler.handle(exchange);
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String ollamaBody(String content) {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("message").put("content", content);
        return mapper.writeValueAsString(root);
    }

    private String openAiBody(String content) {
        ObjectNode root = mapper.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        return mapper.writeValueAsString(root);
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static AiProperties.Ollama ollama(String baseUrl, String model, Duration timeout) {
        AiProperties.Ollama properties = new AiProperties.Ollama();
        properties.setBaseUrl(baseUrl);
        properties.setModel(model);
        properties.setTimeout(timeout);
        return properties;
    }

    private static AiProperties.OpenAi openAi(String baseUrl, String apiKey, String model) {
        AiProperties.OpenAi properties = new AiProperties.OpenAi();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(apiKey);
        properties.setModel(model);
        properties.setTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record Recorded(String path, String body, String authorization) {}
}

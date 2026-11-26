package de.wasessenwir.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.support.ApiSessions;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.max-request-size=2KB")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class RequestSizeLimitIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate raw;

    @Test
    void rejectsOversizedJsonBodiesWithProblemDetails() {
        TestRestTemplate api = new ApiSessions(raw).open();
        String oversized = "\"" + "x".repeat(3000) + "\"";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = api.postForEntity(
                "/api/auth/login", new HttpEntity<>("{\"username\":" + oversized + "}", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).contains("REQUEST_TOO_LARGE");
        assertThat(response.getBody()).contains("Die Anfrage ist zu groß.");
    }

    @Test
    void rejectsChunkedBodiesWithoutContentLength() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> primed =
                client.send(HttpRequest.newBuilder(uri("/api/setup/status")).GET().build(), HttpResponse.BodyHandlers.ofString());
        String setCookie = primed.headers().firstValue(HttpHeaders.SET_COOKIE).orElseThrow();
        String csrfCookie = setCookie.split(";", 2)[0];
        String csrf = URLDecoder.decode(csrfCookie.substring("XSRF-TOKEN=".length()), StandardCharsets.UTF_8);

        byte[] body = ("{\"username\":\"" + "x".repeat(3000) + "\"}").getBytes(StandardCharsets.UTF_8);
        HttpRequest chunked = HttpRequest.newBuilder(uri("/api/auth/login"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-XSRF-TOKEN", csrf)
                .header(HttpHeaders.COOKIE, csrfCookie)
                .POST(chunkedPublisher(body))
                .build();

        HttpResponse<String> response = client.send(chunked, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("REQUEST_TOO_LARGE");
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static HttpRequest.BodyPublisher chunkedPublisher(byte[] body) {
        int part = Math.max(1, body.length / 4);
        List<byte[]> chunks = new ArrayList<>();
        for (int offset = 0; offset < body.length; offset += part) {
            int end = Math.min(body.length, offset + part);
            byte[] chunk = new byte[end - offset];
            System.arraycopy(body, offset, chunk, 0, chunk.length);
            chunks.add(chunk);
        }
        return HttpRequest.BodyPublishers.ofByteArrays(chunks);
    }
}

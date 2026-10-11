package de.wasessenwir.ai;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

final class ProviderHttp {

    static final int MAX_BYTES = 32_768;

    private ProviderHttp() {}

    static Duration timeout(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            return Duration.ofSeconds(30);
        }
        if (value.compareTo(Duration.ofMinutes(2)) > 0) {
            return Duration.ofMinutes(2);
        }
        return value;
    }

    static URI endpoint(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw AiProviderException.unreachable();
        }
        String base = baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        try {
            return URI.create(base + path);
        } catch (IllegalArgumentException exception) {
            throw AiProviderException.unreachable();
        }
    }

    static String post(HttpClient client, URI uri, Duration timeout, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        try {
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    throw AiProviderException.invalid();
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw AiProviderException.unreachable();
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (AiProviderException exception) {
            throw exception;
        } catch (HttpConnectTimeoutException exception) {
            throw AiProviderException.unreachable();
        } catch (HttpTimeoutException exception) {
            throw AiProviderException.timeout();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw AiProviderException.unreachable();
        } catch (IOException | IllegalArgumentException exception) {
            throw AiProviderException.unreachable();
        }
    }
}

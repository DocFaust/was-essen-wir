package de.wasessenwir.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.http.client.HttpCookieHandling;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public final class ApiSessions {

    private final TestRestTemplate raw;

    public ApiSessions(TestRestTemplate raw) {
        this.raw = raw;
    }

    public TestRestTemplate open() {
        AtomicReference<String> csrf = new AtomicReference<>();
        TestRestTemplate session = raw.withCookieHandling(HttpCookieHandling.ENABLE);
        session.getRestTemplate().getInterceptors().add(new CsrfHeaderInterceptor(csrf));
        return session;
    }

    public static String setCookie(List<String> cookies, String name) {
        if (cookies == null) {
            return null;
        }
        String prefix = name + "=";
        String found = null;
        for (String cookie : cookies) {
            if (cookie.startsWith(prefix)) {
                String value = cookie.substring(prefix.length());
                int separator = value.indexOf(';');
                found = separator >= 0 ? value.substring(0, separator) : value;
            }
        }
        return found;
    }

    private record CsrfHeaderInterceptor(AtomicReference<String> csrf) implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            String method = request.getMethod().name();
            if (csrf.get() != null && !method.equals("GET") && !method.equals("HEAD")) {
                request.getHeaders().set("X-XSRF-TOKEN", csrf.get());
            }
            ClientHttpResponse response = execution.execute(request, body);
            List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            String token = setCookie(cookies, "XSRF-TOKEN");
            if (token != null) {
                csrf.set(java.net.URLDecoder.decode(token, StandardCharsets.UTF_8));
            }
            return response;
        }
    }
}

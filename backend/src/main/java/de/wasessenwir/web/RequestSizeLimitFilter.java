package de.wasessenwir.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Begrenzt eingehende Request-Bodies auf {@code app.max-request-size}. Wirkt für bekannte
 * {@code Content-Length}-Angaben und für Chunked-/Streaming-Bodies (Body wird begrenzt eingelesen).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final String PROBLEM_JSON =
            "{\"title\":\"Payload Too Large\",\"status\":413,"
                    + "\"detail\":\"Die Anfrage ist zu groß.\",\"code\":\"REQUEST_TOO_LARGE\"}";

    private final long maxBytes;

    public RequestSizeLimitFilter(@Value("${app.max-request-size:256KB}") DataSize maxRequestSize) {
        this.maxBytes = maxRequestSize.toBytes();
    }

    long maxBytes() {
        return maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            writeTooLarge(response);
            return;
        }
        if (!mayHaveBody(request) || contentLength == 0) {
            filterChain.doFilter(request, response);
            return;
        }
        byte[] body;
        try {
            body = readAtMost(request.getInputStream(), maxBytes);
        } catch (RequestTooLargeException exception) {
            writeTooLarge(response);
            return;
        }
        filterChain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    static boolean mayHaveBody(HttpServletRequest request) {
        String method = request.getMethod();
        return !"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"DELETE".equalsIgnoreCase(method)
                && !"TRACE".equalsIgnoreCase(method);
    }

    static byte[] readAtMost(ServletInputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8_192];
        long total = 0;
        int read;
        while ((read = input.read(chunk)) >= 0) {
            total += read;
            if (total > maxBytes) {
                throw new RequestTooLargeException();
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    static void writeTooLarge(HttpServletResponse response) throws IOException {
        response.resetBuffer();
        response.setStatus(413);
        response.setContentType("application/problem+json");
        response.getWriter().write(PROBLEM_JSON);
    }

    static final class RequestTooLargeException extends RuntimeException {

        RequestTooLargeException() {
            super("REQUEST_TOO_LARGE");
        }
    }

    static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return stream.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return stream.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {}
            };
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}

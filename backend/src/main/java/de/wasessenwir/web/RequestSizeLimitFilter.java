package de.wasessenwir.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lehnt Anfragen mit zu großem {@code Content-Length} frühzeitig ab. Chunked Requests ohne
 * Content-Length werden vom vorgeschalteten Proxy (Nginx {@code client_max_body_size}) begrenzt.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestSizeLimitFilter(@Value("${app.max-request-size:256KB}") DataSize maxRequestSize) {
        this.maxBytes = maxRequestSize.toBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            response.setStatus(413);
            response.setContentType("application/problem+json");
            response.getWriter()
                    .write(
                            "{\"title\":\"Payload Too Large\",\"status\":413,"
                                    + "\"detail\":\"Die Anfrage ist zu groß.\",\"code\":\"REQUEST_TOO_LARGE\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}

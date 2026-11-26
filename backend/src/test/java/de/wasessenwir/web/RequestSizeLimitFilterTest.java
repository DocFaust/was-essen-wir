package de.wasessenwir.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.wasessenwir.web.RequestSizeLimitFilter.RequestTooLargeException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

class RequestSizeLimitFilterTest {

    @Test
    void rejectsKnownContentLengthBeforeReading() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(DataSize.ofKilobytes(1));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[2048]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Filterkette darf bei zu großem Content-Length nicht laufen");
        });

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
    }

    @Test
    void rejectsChunkedBodiesWhileBuffering() throws Exception {
        byte[] payload = "x".repeat(100).getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> RequestSizeLimitFilter.readAtMost(new DelegatingServletInputStream(payload), 50))
                .isInstanceOf(RequestTooLargeException.class);
    }

    @Test
    void allowsBodiesWithinTheLimit() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(DataSize.ofKilobytes(1));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContent("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = {false};

        filter.doFilter(request, response, (req, res) -> {
            called[0] = true;
            assertThat(req.getInputStream().readAllBytes()).isEqualTo("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
            assertThat(req.getContentLengthLong()).isEqualTo(11);
        });

        assertThat(called[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static final class DelegatingServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private DelegatingServletInputStream(byte[] payload) {
            this.delegate = new ByteArrayInputStream(payload);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {}
    }
}

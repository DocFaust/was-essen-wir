package de.wasessenwir.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersWhenDisabled() {
        ClientIpResolver resolver = resolver(false, "10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.9");
        request.addHeader("X-Real-IP", "198.51.100.7");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.5");
    }

    @Test
    void ignoresSpoofedHeadersFromUntrustedPeers() {
        ClientIpResolver resolver = resolver(true, "10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");
        request.addHeader("X-Forwarded-For", "198.51.100.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
    }

    @Test
    void usesForwardedHeaderOnlyFromTrustedProxy() {
        ClientIpResolver resolver = resolver(true, "10.0.0.0/8,172.16.0.0/12");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void fallsBackToRealIpThenRemoteAddr() {
        ClientIpResolver resolver = resolver(true, "172.16.0.0/12");
        MockHttpServletRequest withRealIp = new MockHttpServletRequest();
        withRealIp.setRemoteAddr("172.18.0.4");
        withRealIp.addHeader("X-Real-IP", "198.51.100.20");
        assertThat(resolver.resolve(withRealIp)).isEqualTo("198.51.100.20");

        MockHttpServletRequest bare = new MockHttpServletRequest();
        bare.setRemoteAddr("172.18.0.4");
        assertThat(resolver.resolve(bare)).isEqualTo("172.18.0.4");
    }

    @Test
    void rejectsEmptyTrustedProxyListEvenWhenForwardingEnabled() {
        ClientIpResolver resolver = resolver(true, "");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.5");
    }

    private static ClientIpResolver resolver(boolean forward, String trustedProxies) {
        return new ClientIpResolver(new SecurityProperties(
                8,
                Duration.ofMinutes(15),
                8,
                Duration.ofMinutes(15),
                100,
                false,
                forward,
                trustedProxies));
    }
}

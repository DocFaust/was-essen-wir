package de.wasessenwir.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RememberMeCookiesTest {

    @Test
    void writesAnHttpOnlyCookieAndHonorsTheSecureFlag() {
        MockHttpServletResponse secure = new MockHttpServletResponse();
        RememberMeCookies.write(secure, "selektor.geheimnis-geheimnis-geheimnis-geheimnis", Duration.ofDays(30), true);
        String secureHeader = secure.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(secureHeader)
                .contains("WAEW_REMEMBER=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("Max-Age=2592000");

        MockHttpServletResponse plain = new MockHttpServletResponse();
        RememberMeCookies.write(plain, "selektor.geheimnis-geheimnis-geheimnis-geheimnis", Duration.ofDays(30), false);
        assertThat(plain.getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Secure");

        MockHttpServletResponse cleared = new MockHttpServletResponse();
        RememberMeCookies.clear(cleared, false);
        assertThat(cleared.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0").contains("HttpOnly");
    }

    @Test
    void readsAndParsesOnlyWellFormedCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(RememberMeCookies.NAME, "aaaaaaaaaaaaaaaaaaaaaa.bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        assertThat(RememberMeCookies.read(request)).contains(".");
        assertThat(RememberMeCookies.parse("kurz.auchkurz")).isNull();
        assertThat(RememberMeCookies.parse("manipuliert wert")).isNull();
        assertThat(RememberMeCookies.parse(null)).isNull();

        MockHttpServletRequest empty = new MockHttpServletRequest();
        assertThat(RememberMeCookies.read(empty)).isNull();
    }
}

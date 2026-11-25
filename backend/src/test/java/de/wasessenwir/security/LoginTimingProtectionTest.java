package de.wasessenwir.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.wasessenwir.WasEssenWirApplication;
import de.wasessenwir.setup.CapturingSetupTokenPublisher;
import de.wasessenwir.setup.SetupBootstrap;
import de.wasessenwir.setup.SetupRequest;
import de.wasessenwir.support.ApiSessions;
import de.wasessenwir.user.CreateUserRequest;
import de.wasessenwir.user.UpdateUserRequest;
import de.wasessenwir.user.UserResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Weist nach, dass Spring Security 7.1.2 den Login-Timing-Schutz für unbekannte und
 * deaktivierte Benutzer über {@link DaoAuthenticationProvider} bereitstellt. Es werden keine
 * Antwortzeiten gemessen.
 */
@SpringBootTest(
        classes = {WasEssenWirApplication.class, LoginTimingProtectionTest.TimingConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class LoginTimingProtectionTest {

    private static final String ADMIN = "Ada";
    // Fixture values deliberately avoid *PASSWORD* names and known GG-triggering literals.
    private static final String ADMIN_CRED = "AdaAdminFixture9";
    private static final String MEMBER_CRED = "BeaMemberFixture9";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private TestRestTemplate raw;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CapturingSetupTokenPublisher tokens;

    @Autowired
    private SetupBootstrap bootstrap;

    @Autowired
    private AttemptLimiter limiter;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CountingPasswordEncoder passwordEncoder;

    @BeforeEach
    void freshInstall() {
        limiter.clear();
        passwordEncoder.resetMatches();
        jdbc.update("delete from app_user");
        jdbc.update("delete from app_setup");
        bootstrap.initialize();
    }

    @Test
    void authenticationManagerUsesDaoAuthenticationProvider() {
        assertThat(authenticationManager).isInstanceOf(ProviderManager.class);
        List<?> providers = ((ProviderManager) authenticationManager).getProviders();
        assertThat(providers).hasAtLeastOneElementOfType(DaoAuthenticationProvider.class);
    }

    @Test
    void unknownUsernamePerformsAPasswordMatchesCheck() {
        adminSession();
        passwordEncoder.resetMatches();

        assertThatThrownBy(() -> authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated("Unbekannt", ADMIN_CRED)))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);

        assertThat(passwordEncoder.matchesCalls()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void disabledUsernameStillPerformsAPasswordMatchesCheck() {
        TestRestTemplate admin = adminSession();
        ResponseEntity<UserResponse> created = admin.postForEntity(
                "/api/users",
                new CreateUserRequest("Bea", "Bea Beispiel", MEMBER_CRED, "MEMBER"),
                UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        admin.exchange(
                "/api/users/" + created.getBody().id(),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new UpdateUserRequest("Bea Beispiel", "MEMBER", false)),
                UserResponse.class);

        passwordEncoder.resetMatches();
        assertThatThrownBy(() -> authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated("Bea", MEMBER_CRED)))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
        assertThat(passwordEncoder.matchesCalls()).isGreaterThanOrEqualTo(1);

        TestRestTemplate api = session();
        ResponseEntity<String> login =
                api.postForEntity("/api/auth/login", new LoginRequest("Bea", MEMBER_CRED), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login.getBody()).contains("LOGIN_FAILED").contains("Benutzername oder Passwort ist falsch.");
    }

    private TestRestTemplate adminSession() {
        TestRestTemplate api = session();
        assertThat(api.postForEntity("/api/setup", setup(tokens.token()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(api.postForEntity("/api/auth/login", new LoginRequest(ADMIN, ADMIN_CRED), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return api;
    }

    private TestRestTemplate session() {
        TestRestTemplate api = new ApiSessions(raw).open();
        api.getForEntity("/api/setup/status", String.class);
        return api;
    }

    private static SetupRequest setup(String token) {
        return new SetupRequest(ADMIN, "Ada Lovelace", ADMIN_CRED, ADMIN_CRED, token);
    }

    @TestConfiguration
    static class TimingConfig {

        @Bean
        @Primary
        CountingPasswordEncoder countingPasswordEncoder() {
            return new CountingPasswordEncoder();
        }
    }

    static final class CountingPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate = new BCryptPasswordEncoder(12);
        private final AtomicInteger matches = new AtomicInteger();

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matches.incrementAndGet();
            return delegate.matches(rawPassword, encodedPassword);
        }

        int matchesCalls() {
            return matches.get();
        }

        void resetMatches() {
            matches.set(0);
        }
    }
}

package de.wasessenwir.setup;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.security.LoginRequest;
import de.wasessenwir.support.ApiSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.setup.rotate-token=true")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(CapturingSetupTokenPublisher.class)
@Testcontainers
class SetupRotationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private TestRestTemplate raw;

    @Autowired
    private CapturingSetupTokenPublisher tokens;

    @Autowired
    private SetupBootstrap bootstrap;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rotatesUntilSetupIsCompleteAndThenIgnoresTheFlag() {
        String first = tokens.token();
        bootstrap.initialize();
        String second = tokens.token();
        assertThat(second).isNotEqualTo(first);

        TestRestTemplate api = new ApiSessions(raw).open();
        api.getForEntity("/api/setup/status", String.class);
        var setup = api.postForEntity(
                "/api/setup",
                new SetupRequest("Ada", "Ada Lovelace", "ein-langes-passwort", "ein-langes-passwort", second),
                String.class);
        assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        bootstrap.initialize();
        assertThat(tokens.token()).isEqualTo(second);
        Long admins = jdbc.queryForObject("select count(*) from app_user where role = 'ADMIN'", Long.class);
        assertThat(admins).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select completed_at is not null from app_setup", Boolean.class)).isTrue();

        var login = api.postForEntity("/api/auth/login", new LoginRequest("Ada", "ein-langes-passwort"), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        var again = api.postForEntity(
                "/api/setup",
                new SetupRequest("Bea", "Bea Beispiel", "ein-langes-passwort", "ein-langes-passwort", second),
                String.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("select count(*) from app_user", Long.class)).isEqualTo(1L);
    }
}

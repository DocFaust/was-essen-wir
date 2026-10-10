package de.wasessenwir;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.status.StatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ApplicationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextStartsAgainstPostgreSql() {
        Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);

        assertThat(result).isEqualTo(1);
    }

    @Test
    void flywayAppliesBaselineMigration() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success",
                Integer.class);

        assertThat(applied).isEqualTo(1);
    }

    @Test
    void statusEndpointReturnsPublicPayload() {
        var response = restTemplate.getForEntity("/api/status", StatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new StatusResponse("UP", "was-essen-wir"));
    }
}

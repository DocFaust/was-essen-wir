package de.wasessenwir.status;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatusServiceTest {

    @Test
    void currentStatusReportsApplicationAsUp() {
        var service = new StatusService("was-essen-wir");

        assertThat(service.currentStatus()).isEqualTo(new StatusResponse("UP", "was-essen-wir"));
    }
}

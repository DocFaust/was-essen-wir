package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiFailuresTest {

    @Test
    void quotaIsNotReportedAsANetworkProblem() {
        var quota = AiFailures.toApi(AiProviderException.quota());
        var network = AiFailures.toApi(AiProviderException.unreachable());
        var key = AiFailures.toApi(AiProviderException.unauthorized());
        var providerLimit = AiFailures.toApi(AiProviderException.rateLimited());
        var other = AiFailures.toApi(AiProviderException.provider());

        assertThat(quota.code()).isEqualTo("AI_QUOTA");
        assertThat(quota.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(quota.getMessage()).contains("Guthaben").contains("administrierende Person");
        assertThat(quota.getMessage()).doesNotContain("nicht erreichbar").doesNotContain("insufficient_quota");
        assertThat(network.code()).isEqualTo("AI_UNAVAILABLE");
        assertThat(network.getMessage()).contains("nicht erreichbar");
        assertThat(key.code()).isEqualTo("AI_PROVIDER_AUTH");
        assertThat(key.getMessage()).contains("administrierende Person").doesNotContain("sk-");
        assertThat(providerLimit.code()).isEqualTo("AI_PROVIDER_RATE_LIMIT");
        assertThat(providerLimit.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(other.code()).isEqualTo("AI_PROVIDER_ERROR");
        assertThat(other.getMessage()).doesNotContain("nicht erreichbar");
    }
}

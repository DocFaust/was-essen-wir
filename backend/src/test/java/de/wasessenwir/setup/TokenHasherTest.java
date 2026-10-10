package de.wasessenwir.setup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    @Test
    void storesOnlyAMatchingHash() {
        String token = TokenHasher.generate();

        assertThat(token).hasSizeGreaterThanOrEqualTo(32);
        assertThat(TokenHasher.matches(TokenHasher.sha256(token), token)).isTrue();
        assertThat(TokenHasher.matches(TokenHasher.sha256(token), token + "x")).isFalse();
        assertThat(TokenHasher.matches(null, token)).isFalse();
        assertThat(new String(TokenHasher.sha256(token))).doesNotContain(token);
    }
}

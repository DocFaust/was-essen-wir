package de.wasessenwir.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsernamesTest {

    @Test
    void normalizesCaseAndUnicodeConsistently() {
        assertThat(Usernames.normalize(" Änna ")).isEqualTo(Usernames.normalize("änna"));
        assertThat(Usernames.canonicalize("Änna")).isEqualTo("Änna");
    }

    @Test
    void rejectsBlankAndUnsafeUsernames() {
        assertThatThrownBy(() -> Usernames.canonicalize(" A")).isInstanceOf(de.wasessenwir.web.ApiException.class);
        assertThatThrownBy(() -> Usernames.canonicalize("a b")).isInstanceOf(de.wasessenwir.web.ApiException.class);
        assertThatThrownBy(() -> Usernames.displayName(" ")).isInstanceOf(de.wasessenwir.web.ApiException.class);
        assertThatThrownBy(() -> Passwords.check("kurz")).isInstanceOf(de.wasessenwir.web.ApiException.class);
        assertThatThrownBy(() -> Passwords.confirm("ein-langes-passwort", "anderes-passwort"))
                .isInstanceOf(de.wasessenwir.web.ApiException.class);
    }
}

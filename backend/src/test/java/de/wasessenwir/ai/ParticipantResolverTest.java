package de.wasessenwir.ai;

import static org.assertj.core.api.Assertions.assertThat;

import de.wasessenwir.user.Role;
import de.wasessenwir.user.StoredUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantResolverTest {

    private final UUID ada = UUID.randomUUID();
    private final UUID alexOne = UUID.randomUUID();
    private final UUID alexTwo = UUID.randomUUID();

    @Test
    void usesTheCurrentUserWhenNobodyIsNamed() {
        ParticipantResolver.Resolution resolution = ParticipantResolver.resolve(List.of(), family(), ada, false);

        assertThat(resolution.needsClarification()).isFalse();
        assertThat(resolution.dinerIds()).containsExactly(ada);
        assertThat(resolution.names()).containsExactly("Ada Lovelace");
    }

    private final UUID katja = UUID.randomUUID();

    @Test
    void addsAUniquelyNamedPerson() {
        ParticipantResolver.Resolution resolution = ParticipantResolver.resolve(List.of("Katja"), withKatja(), ada, false);

        assertThat(resolution.dinerIds()).containsExactly(ada, katja);
        assertThat(resolution.names()).containsExactly("Ada Lovelace", "Katja Beispiel");
    }

    @Test
    void keepsOnlyTheNamedPersonWhenRequested() {
        ParticipantResolver.Resolution resolution = ParticipantResolver.resolve(List.of("Katja"), withKatja(), ada, true);

        assertThat(resolution.dinerIds()).containsExactly(katja);
    }

    @Test
    void asksWhenANameIsAmbiguousOrUnknown() {
        ParticipantResolver.Resolution ambiguous = ParticipantResolver.resolve(List.of("Alex"), family(), ada, false);
        ParticipantResolver.Resolution unknown = ParticipantResolver.resolve(List.of("Niemand"), family(), ada, false);

        assertThat(ambiguous.clarification()).contains("Alex Meier", "Alex Schulz");
        assertThat(ambiguous.dinerIds()).isEmpty();
        assertThat(unknown.clarification()).contains("Niemand");
    }

    private List<StoredUser> withKatja() {
        return List.of(user(ada, "ada", "Ada Lovelace"), user(katja, "katja", "Katja Beispiel"));
    }

    private List<StoredUser> family() {
        return List.of(
                user(ada, "ada", "Ada Lovelace"),
                user(alexOne, "alexmeier", "Alex Meier"),
                user(alexTwo, "alexschulz", "Alex Schulz"));
    }

    private static StoredUser user(UUID id, String username, String displayName) {
        return new StoredUser(id, username, username, displayName, "hash", Role.MEMBER, true, false, null, null);
    }
}

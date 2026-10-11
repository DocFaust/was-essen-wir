package de.wasessenwir.ai;

import de.wasessenwir.user.StoredUser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public final class ParticipantResolver {

    private ParticipantResolver() {}

    public record Resolution(List<UUID> dinerIds, List<String> names, String clarification) {

        public boolean needsClarification() {
            return clarification != null;
        }
    }

    public static Resolution resolve(
            List<String> spokenNames, List<StoredUser> active, UUID currentUserId, boolean onlyNamedParticipants) {
        List<StoredUser> resolved = new ArrayList<>();
        for (String spoken : spokenNames) {
            List<StoredUser> matches = match(spoken, active);
            if (matches.isEmpty()) {
                return new Resolution(
                        List.of(),
                        List.of(),
                        "Ich finde kein aktives Familienmitglied zu „" + spoken + "“. Wen meinst du?");
            }
            if (matches.size() > 1) {
                String names = String.join(", ", matches.stream().map(StoredUser::displayName).toList());
                return new Resolution(
                        List.of(),
                        List.of(),
                        "Mehrere Personen passen zu „" + spoken + "“: " + names + ". Wen meinst du?");
            }
            resolved.add(matches.getFirst());
        }
        StoredUser current = active.stream()
                .filter(user -> user.id().equals(currentUserId))
                .findFirst()
                .orElse(null);
        if (current == null) {
            return new Resolution(List.of(), List.of(), "Die angemeldete Person ist nicht mehr aktiv.");
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        List<String> names = new ArrayList<>();
        if (!onlyNamedParticipants || resolved.isEmpty()) {
            ids.add(current.id());
            names.add(current.displayName());
        }
        for (StoredUser user : resolved) {
            if (ids.add(user.id())) {
                names.add(user.displayName());
            }
        }
        return new Resolution(List.copyOf(ids), List.copyOf(names), null);
    }

    private static List<StoredUser> match(String spoken, List<StoredUser> active) {
        String needle = TextMatch.fold(spoken);
        List<StoredUser> exact = active.stream()
                .filter(user -> TextMatch.fold(user.displayName()).equals(needle)
                        || TextMatch.fold(user.username()).equals(needle))
                .toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return active.stream()
                .filter(user -> TextMatch.tokens(TextMatch.fold(user.displayName())).contains(needle)
                        || TextMatch.tokens(TextMatch.fold(user.username())).contains(needle))
                .toList();
    }
}

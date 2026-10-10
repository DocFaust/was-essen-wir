package de.wasessenwir.meal;

import de.wasessenwir.dish.MealType;
import de.wasessenwir.meal.StoredMealEvent.StoredParticipant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MealEventRepository {

    private final JdbcClient jdbc;

    public MealEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(StoredMealEvent event) {
        jdbc.sql("""
                insert into meal_event (
                    id, meal_date, meal_type, status, dish_id, variant_id, dish_name, variant_name,
                    comment, created_by, created_at, updated_at
                ) values (
                    :id, :mealDate, :mealType, :status, :dishId, :variantId, :dishName, :variantName,
                    :comment, :createdBy, :createdAt, :updatedAt
                )
                """)
                .param("id", event.id())
                .param("mealDate", event.mealDate())
                .param("mealType", event.mealType().name())
                .param("status", event.status().name())
                .param("dishId", event.dishId())
                .param("variantId", event.variantId())
                .param("dishName", event.dishName())
                .param("variantName", event.variantName())
                .param("comment", event.comment())
                .param("createdBy", event.createdBy())
                .param("createdAt", event.createdAt())
                .param("updatedAt", event.updatedAt())
                .update();
        insertParticipants(event.id(), event.participants());
    }

    public void update(StoredMealEvent event) {
        jdbc.sql("""
                update meal_event set
                    meal_date = :mealDate,
                    meal_type = :mealType,
                    dish_id = :dishId,
                    variant_id = :variantId,
                    dish_name = :dishName,
                    variant_name = :variantName,
                    comment = :comment,
                    updated_at = :updatedAt
                where id = :id
                """)
                .param("id", event.id())
                .param("mealDate", event.mealDate())
                .param("mealType", event.mealType().name())
                .param("dishId", event.dishId())
                .param("variantId", event.variantId())
                .param("dishName", event.dishName())
                .param("variantName", event.variantName())
                .param("comment", event.comment())
                .param("updatedAt", event.updatedAt())
                .update();
        replaceParticipants(event.id(), event.participants());
    }

    public void markEaten(UUID id, OffsetDateTime updatedAt) {
        jdbc.sql("""
                update meal_event
                set status = 'EATEN', updated_at = :updatedAt
                where id = :id
                """)
                .param("id", id)
                .param("updatedAt", updatedAt)
                .update();
    }

    public int delete(UUID id) {
        return jdbc.sql("delete from meal_event where id = :id").param("id", id).update();
    }

    public Optional<StoredMealEvent> find(UUID id) {
        List<StoredMealEvent> events = group(query("""
                select e.id, e.meal_date, e.meal_type, e.status, e.dish_id, e.variant_id,
                       e.dish_name, e.variant_name, e.comment, e.created_by, e.created_at, e.updated_at,
                       p.id as participant_row_id, p.user_id, p.display_name as participant_name
                from meal_event e
                left join meal_participant p on p.meal_event_id = e.id
                where e.id = :id
                order by lower(p.display_name), p.id
                """, id, null, null, null, null));
        return events.stream().findFirst();
    }

    public List<StoredMealEvent> list(MealStatus status, LocalDate from, LocalDate to, UUID dinerId) {
        String order = status == MealStatus.EATEN
                ? "e.meal_date desc, meal_order, e.created_at desc, e.id desc, lower(p.display_name), p.id"
                : "e.meal_date, meal_order, e.created_at, e.id, lower(p.display_name), p.id";
        String dinerClause = dinerId == null
                ? ""
                : """
                 and exists (
                    select 1 from meal_participant filter_participant
                    where filter_participant.meal_event_id = e.id
                      and filter_participant.user_id = :dinerId
                 )
                """;
        return group(query("""
                select e.id, e.meal_date, e.meal_type, e.status, e.dish_id, e.variant_id,
                       e.dish_name, e.variant_name, e.comment, e.created_by, e.created_at, e.updated_at,
                       case e.meal_type
                           when 'FRUEHSTUECK' then 1
                           when 'MITTAGESSEN' then 2
                           when 'ABENDESSEN' then 3
                           else 4
                       end as meal_order,
                       p.id as participant_row_id, p.user_id, p.display_name as participant_name
                from meal_event e
                left join meal_participant p on p.meal_event_id = e.id
                where e.status = :status
                  and e.meal_date >= :fromDate
                  and e.meal_date <= :toDate
                """ + dinerClause + " order by " + order, null, status, from, to, dinerId));
    }

    public List<MealOccurrence> findOccurrences(LocalDate referenceDate) {
        LocalDate eatenFrom = referenceDate.minusDays(14);
        LocalDate plannedUntil = referenceDate.plusDays(7);
        List<OccurrenceRow> rows = jdbc.sql("""
                select e.id, e.dish_id, e.variant_id, e.variant_name, e.meal_date, e.status, p.user_id
                from meal_event e
                left join meal_participant p on p.meal_event_id = e.id
                where e.dish_id is not null
                  and (
                        (e.status = 'EATEN' and e.meal_date >= :eatenFrom and e.meal_date <= :referenceDate)
                     or (e.status = 'PLANNED' and e.meal_date >= :referenceDate and e.meal_date <= :plannedUntil)
                  )
                order by e.meal_date, e.id
                """)
                .param("eatenFrom", eatenFrom)
                .param("referenceDate", referenceDate)
                .param("plannedUntil", plannedUntil)
                .query((rs, row) -> new OccurrenceRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("dish_id", UUID.class),
                        rs.getObject("variant_id", UUID.class),
                        rs.getString("variant_name"),
                        rs.getObject("meal_date", LocalDate.class),
                        MealStatus.valueOf(rs.getString("status")),
                        rs.getObject("user_id", UUID.class)))
                .list();
        Map<UUID, OccurrenceBuilder> grouped = new LinkedHashMap<>();
        for (OccurrenceRow row : rows) {
            OccurrenceBuilder builder = grouped.computeIfAbsent(row.id(), id -> new OccurrenceBuilder(row));
            if (row.userId() != null) {
                builder.participants().add(row.userId());
            }
        }
        List<MealOccurrence> occurrences = new ArrayList<>();
        for (OccurrenceBuilder builder : grouped.values()) {
            occurrences.add(builder.toOccurrence());
        }
        return List.copyOf(occurrences);
    }

    private void replaceParticipants(UUID eventId, List<StoredParticipant> participants) {
        jdbc.sql("delete from meal_participant where meal_event_id = :id").param("id", eventId).update();
        insertParticipants(eventId, participants);
    }

    private void insertParticipants(UUID eventId, List<StoredParticipant> participants) {
        for (StoredParticipant participant : participants) {
            jdbc.sql("""
                    insert into meal_participant (id, meal_event_id, user_id, display_name)
                    values (:id, :mealEventId, :userId, :displayName)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("mealEventId", eventId)
                    .param("userId", participant.userId())
                    .param("displayName", participant.displayName())
                    .update();
        }
    }

    private List<EventRow> query(String sql, UUID id, MealStatus status, LocalDate from, LocalDate to, UUID dinerId) {
        var statement = jdbc.sql(sql);
        if (id != null) {
            statement = statement.param("id", id);
        }
        if (status != null) {
            statement = statement.param("status", status.name());
        }
        if (from != null) {
            statement = statement.param("fromDate", from);
        }
        if (to != null) {
            statement = statement.param("toDate", to);
        }
        if (dinerId != null) {
            statement = statement.param("dinerId", dinerId);
        }
        return statement.query((rs, row) -> new EventRow(
                rs.getObject("id", UUID.class),
                rs.getObject("meal_date", LocalDate.class),
                MealType.valueOf(rs.getString("meal_type")),
                MealStatus.valueOf(rs.getString("status")),
                rs.getObject("dish_id", UUID.class),
                rs.getObject("variant_id", UUID.class),
                rs.getString("dish_name"),
                rs.getString("variant_name"),
                rs.getString("comment"),
                rs.getObject("created_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("participant_row_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("participant_name")))
                .list();
    }

    private static List<StoredMealEvent> group(List<EventRow> rows) {
        Map<UUID, EventBuilder> grouped = new LinkedHashMap<>();
        for (EventRow row : rows) {
            EventBuilder builder = grouped.computeIfAbsent(row.id(), id -> new EventBuilder(row));
            if (row.participantRowId() != null) {
                builder.participants().add(new StoredParticipant(row.userId(), row.participantName()));
            }
        }
        List<StoredMealEvent> events = new ArrayList<>();
        for (EventBuilder builder : grouped.values()) {
            events.add(builder.toEvent());
        }
        return events;
    }

    private record EventRow(
            UUID id,
            LocalDate mealDate,
            MealType mealType,
            MealStatus status,
            UUID dishId,
            UUID variantId,
            String dishName,
            String variantName,
            String comment,
            UUID createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            UUID participantRowId,
            UUID userId,
            String participantName) {}

    private static final class EventBuilder {
        private final EventRow row;
        private final List<StoredParticipant> participants = new ArrayList<>();

        private EventBuilder(EventRow row) {
            this.row = row;
        }

        private List<StoredParticipant> participants() {
            return participants;
        }

        private StoredMealEvent toEvent() {
            return new StoredMealEvent(
                    row.id(),
                    row.mealDate(),
                    row.mealType(),
                    row.status(),
                    row.dishId(),
                    row.variantId(),
                    row.dishName(),
                    row.variantName(),
                    row.comment(),
                    row.createdBy(),
                    row.createdAt(),
                    row.updatedAt(),
                    List.copyOf(participants));
        }
    }

    private record OccurrenceRow(
            UUID id,
            UUID dishId,
            UUID variantId,
            String variantName,
            LocalDate mealDate,
            MealStatus status,
            UUID userId) {}

    private static final class OccurrenceBuilder {
        private final OccurrenceRow row;
        private final Set<UUID> participants = new LinkedHashSet<>();

        private OccurrenceBuilder(OccurrenceRow row) {
            this.row = row;
        }

        private Set<UUID> participants() {
            return participants;
        }

        private MealOccurrence toOccurrence() {
            boolean variantSpecified = row.variantId() != null || (row.variantName() != null && !row.variantName().isBlank());
            return new MealOccurrence(
                    row.dishId(),
                    row.variantId(),
                    variantSpecified,
                    row.mealDate(),
                    row.status(),
                    Set.copyOf(participants));
        }
    }
}

package de.wasessenwir.meal;

import de.wasessenwir.dish.DishRepository;
import de.wasessenwir.dish.InvalidRequestException;
import de.wasessenwir.dish.MealType;
import de.wasessenwir.dish.NotFoundException;
import de.wasessenwir.meal.StoredMealEvent.StoredParticipant;
import de.wasessenwir.security.CurrentUser;
import de.wasessenwir.user.StoredUser;
import de.wasessenwir.user.UserRepository;
import de.wasessenwir.web.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealEventService {

    static final int MAX_COMMENT = 500;
    static final int MAX_DINERS = 24;
    static final int MAX_RANGE_DAYS = 400;
    static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);

    private final MealEventRepository events;
    private final DishRepository dishes;
    private final UserRepository users;
    private final Clock clock;

    public MealEventService(MealEventRepository events, DishRepository dishes, UserRepository users, Clock clock) {
        this.events = events;
        this.dishes = dishes;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public MealEventResponse plan(MealEventWriteRequest request) {
        UUID createdBy = CurrentUser.requireId();
        return response(insert(MealStatus.PLANNED, request, createdBy, Set.of()));
    }

    @Transactional
    public MealEventResponse updatePlan(UUID id, MealEventWriteRequest request) {
        CurrentUser.requireId();
        return response(revise(id, MealStatus.PLANNED, request, "Diese Planung wurde nicht gefunden."));
    }

    @Transactional
    public void deletePlan(UUID id) {
        CurrentUser.requireId();
        delete(id, MealStatus.PLANNED, "Diese Planung wurde nicht gefunden.");
    }

    @Transactional
    public MealEventResponse confirm(UUID id) {
        CurrentUser.requireId();
        StoredMealEvent existing = require(id, MealStatus.PLANNED, "Diese Planung wurde nicht gefunden.");
        events.markEaten(existing.id(), OffsetDateTime.now(clock));
        return response(events.find(existing.id()).orElseThrow());
    }

    @Transactional
    public MealEventResponse recordEaten(MealEventWriteRequest request) {
        UUID createdBy = CurrentUser.requireId();
        return response(insert(MealStatus.EATEN, request, createdBy, Set.of()));
    }

    @Transactional
    public MealEventResponse updateEaten(UUID id, MealEventWriteRequest request) {
        CurrentUser.requireId();
        return response(revise(id, MealStatus.EATEN, request, "Dieser Historieneintrag wurde nicht gefunden."));
    }

    @Transactional
    public void deleteEaten(UUID id) {
        CurrentUser.requireId();
        delete(id, MealStatus.EATEN, "Dieser Historieneintrag wurde nicht gefunden.");
    }

    @Transactional(readOnly = true)
    public List<MealEventResponse> listPlans(LocalDate from, LocalDate to) {
        CurrentUser.requireId();
        range(from, to);
        return events.list(MealStatus.PLANNED, from, to, null).stream().map(MealEventService::response).toList();
    }

    @Transactional(readOnly = true)
    public List<MealEventResponse> listHistory(LocalDate from, LocalDate to, UUID dinerId) {
        CurrentUser.requireId();
        range(from, to);
        return events.list(MealStatus.EATEN, from, to, dinerId).stream().map(MealEventService::response).toList();
    }

    private StoredMealEvent insert(MealStatus status, MealEventWriteRequest request, UUID createdBy, Set<UUID> alreadyPresent) {
        NormalizedMeal meal = normalize(request);
        ResolvedDish dish = resolveDish(meal.dishId(), meal.variantId());
        List<StoredParticipant> participants = participants(meal.dinerIds(), alreadyPresent);
        OffsetDateTime now = OffsetDateTime.now(clock);
        StoredMealEvent event = new StoredMealEvent(
                UUID.randomUUID(),
                meal.mealDate(),
                meal.mealType(),
                status,
                dish.dishId(),
                dish.variantId(),
                dish.dishName(),
                dish.variantName(),
                meal.comment(),
                createdBy,
                now,
                now,
                participants);
        events.insert(event);
        return events.find(event.id()).orElseThrow();
    }

    private StoredMealEvent revise(UUID id, MealStatus status, MealEventWriteRequest request, String missingMessage) {
        StoredMealEvent existing = require(id, status, missingMessage);
        NormalizedMeal meal = normalize(request);
        ResolvedDish dish = resolveDish(meal.dishId(), meal.variantId());
        Set<UUID> alreadyPresent = existing.participants().stream()
                .map(StoredParticipant::userId)
                .filter(userId -> userId != null)
                .collect(Collectors.toSet());
        StoredMealEvent updated = new StoredMealEvent(
                existing.id(),
                meal.mealDate(),
                meal.mealType(),
                existing.status(),
                dish.dishId(),
                dish.variantId(),
                dish.dishName(),
                dish.variantName(),
                meal.comment(),
                existing.createdBy(),
                existing.createdAt(),
                OffsetDateTime.now(clock),
                participants(meal.dinerIds(), alreadyPresent));
        events.update(updated);
        return events.find(existing.id()).orElseThrow();
    }

    private void delete(UUID id, MealStatus status, String missingMessage) {
        require(id, status, missingMessage);
        events.delete(id);
    }

    private StoredMealEvent require(UUID id, MealStatus status, String missingMessage) {
        StoredMealEvent event = events.find(id).orElseThrow(() -> new NotFoundException(missingMessage));
        if (event.status() != status) {
            String message = status == MealStatus.PLANNED
                    ? "Diese Mahlzeit ist bereits als gegessen erfasst."
                    : "Diese Mahlzeit ist nur geplant und steht noch nicht in der Historie.";
            throw new ApiException(HttpStatus.CONFLICT, message, "MEAL_STATUS");
        }
        return event;
    }

    private ResolvedDish resolveDish(UUID dishId, UUID variantId) {
        String dishName = dishes.findDishName(dishId)
                .orElseThrow(() -> new NotFoundException("Das Gericht wurde nicht gefunden."));
        if (variantId == null) {
            return new ResolvedDish(dishId, null, dishName, null);
        }
        String variantName = dishes.findVariantName(dishId, variantId)
                .orElseThrow(() -> new InvalidRequestException("Die Variante gehört nicht zu diesem Gericht."));
        return new ResolvedDish(dishId, variantId, dishName, variantName);
    }

    private List<StoredParticipant> participants(List<UUID> dinerIds, Set<UUID> alreadyPresent) {
        Map<UUID, StoredUser> found = users.findByIds(dinerIds).stream()
                .collect(Collectors.toMap(StoredUser::id, Function.identity()));
        List<StoredParticipant> participants = new ArrayList<>();
        for (UUID dinerId : dinerIds) {
            StoredUser user = found.get(dinerId);
            if (user == null) {
                throw new InvalidRequestException("Die Mitessenden sind ungültig.");
            }
            if (!user.active() && !alreadyPresent.contains(dinerId)) {
                throw new InvalidRequestException("Nur aktive Familienmitglieder können mitessen.");
            }
            participants.add(new StoredParticipant(user.id(), user.displayName()));
        }
        return List.copyOf(participants);
    }

    private static NormalizedMeal normalize(MealEventWriteRequest request) {
        if (request == null || request.mealDate() == null) {
            throw new InvalidRequestException("Bitte wähle ein Datum.");
        }
        if (request.mealDate().isBefore(MIN_DATE) || request.mealDate().isAfter(MAX_DATE)) {
            throw new InvalidRequestException("Das Datum liegt außerhalb des zulässigen Bereichs.");
        }
        if (request.mealType() == null) {
            throw new InvalidRequestException("Bitte wähle eine Mahlzeit.");
        }
        if (request.dishId() == null) {
            throw new InvalidRequestException("Bitte wähle ein Gericht.");
        }
        String comment = request.comment() == null ? null : request.comment().trim();
        if (comment != null && comment.isEmpty()) {
            comment = null;
        }
        if (comment != null && comment.length() > MAX_COMMENT) {
            throw new InvalidRequestException("Der Kommentar darf höchstens 500 Zeichen haben.");
        }
        return new NormalizedMeal(request.mealDate(), request.mealType(), request.dishId(), request.variantId(), diners(request.dinerIds()), comment);
    }

    private static List<UUID> diners(List<UUID> dinerIds) {
        if (dinerIds == null || dinerIds.isEmpty()) {
            throw new InvalidRequestException("Mindestens eine Person muss ausgewählt sein.");
        }
        if (dinerIds.size() > MAX_DINERS) {
            throw new InvalidRequestException("Es können höchstens 24 Mitessende ausgewählt werden.");
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (UUID dinerId : dinerIds) {
            if (dinerId == null) {
                throw new InvalidRequestException("Die Mitessenden sind ungültig.");
            }
            unique.add(dinerId);
        }
        return List.copyOf(unique);
    }

    private static void range(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new InvalidRequestException("Bitte wähle einen Zeitraum.");
        }
        if (to.isBefore(from)) {
            throw new InvalidRequestException("Das Enddatum liegt vor dem Startdatum.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new InvalidRequestException("Der Zeitraum darf höchstens 400 Tage umfassen.");
        }
    }

    static MealEventResponse response(StoredMealEvent event) {
        return new MealEventResponse(
                event.id(),
                event.mealDate(),
                event.mealType(),
                event.status(),
                event.dishId(),
                event.variantId(),
                event.dishName(),
                event.variantName(),
                displayName(event.dishName(), event.variantName()),
                event.comment(),
                event.participants().stream()
                        .map(participant -> new MealParticipantResponse(participant.userId(), participant.displayName()))
                        .toList(),
                event.createdAt(),
                event.updatedAt());
    }

    static String displayName(String dishName, String variantName) {
        if (variantName == null || variantName.isBlank()) {
            return dishName;
        }
        return dishName + " · " + variantName;
    }

    private record NormalizedMeal(
            LocalDate mealDate,
            MealType mealType,
            UUID dishId,
            UUID variantId,
            List<UUID> dinerIds,
            String comment) {}

    private record ResolvedDish(UUID dishId, UUID variantId, String dishName, String variantName) {}
}

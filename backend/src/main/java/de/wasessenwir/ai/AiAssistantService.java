package de.wasessenwir.ai;

import de.wasessenwir.dish.InvalidRequestException;
import de.wasessenwir.dish.MealType;
import de.wasessenwir.recommendation.RecommendationRequest;
import de.wasessenwir.recommendation.RecommendationResponse;
import de.wasessenwir.recommendation.RecommendationService;
import de.wasessenwir.security.CurrentUser;
import de.wasessenwir.user.UserRepository;
import de.wasessenwir.web.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d. MMMM uuuu", Locale.GERMAN);
    private static final int HARD_INPUT_LIMIT = 2_000;

    private final AiProperties properties;
    private final AiProvider provider;
    private final IntentValidator validator;
    private final UserRepository users;
    private final CatalogRepository catalog;
    private final RecommendationService recommendations;
    private final Clock clock;

    public AiAssistantService(
            AiProperties properties,
            AiProvider provider,
            IntentValidator validator,
            UserRepository users,
            CatalogRepository catalog,
            RecommendationService recommendations,
            Clock clock) {
        this.properties = properties;
        this.provider = provider;
        this.validator = validator;
        this.users = users;
        this.catalog = catalog;
        this.recommendations = recommendations;
        this.clock = clock;
    }

    public AiStatusResponse status() {
        CurrentUser.requireId();
        if (!properties.enabled()) {
            return new AiStatusResponse(false, null);
        }
        String providerName = properties.provider() == null ? "" : properties.provider().trim().toLowerCase(Locale.ROOT);
        return new AiStatusResponse(true, providerName);
    }

    public AiRecommendationResponse recommend(AiRecommendationRequest request) {
        UUID currentUserId = CurrentUser.requireId();
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Der Essensassistent ist nicht aktiviert.", "AI_DISABLED");
        }
        String message = message(request);
        LocalDate today = LocalDate.now(clock);
        MealIntent intent;
        try {
            intent = validator.parse(interpret(message, today));
        } catch (AiProviderException exception) {
            throw failure(exception);
        }
        MealType mealType = intent.mealType() == null ? MealType.ABENDESSEN : intent.mealType();
        LocalDate date = intent.date() == null ? today : intent.date();
        if (intent.needsClarification() && intent.clarificationQuestion() != null) {
            return new AiRecommendationResponse(
                    intent.clarificationQuestion(),
                    intent.clarificationQuestion(),
                    criteria(mealType, date, List.of(), intent.participantNames(), intent, List.of(), List.of()),
                    List.of(),
                    null);
        }
        ParticipantResolver.Resolution people = ParticipantResolver.resolve(
                intent.participantNames(), users.findActive(), currentUserId, intent.onlyNamedParticipants());
        if (people.needsClarification()) {
            return new AiRecommendationResponse(
                    people.clarification(),
                    people.clarification(),
                    criteria(mealType, date, List.of(), List.of(), intent, List.of(), List.of()),
                    List.of(),
                    null);
        }
        CatalogMatcher.Match match = CatalogMatcher.match(catalog.load(), intent.dishSearchTerms(), intent.avoidDishTerms());
        List<String> limitations = new ArrayList<>(match.limitations());
        if (match.nothingFound()) {
            return new AiRecommendationResponse(
                    reply(mealType, date, people.names(), intent, match.matchedDishes(), true),
                    null,
                    criteria(mealType, date, people.dinerIds(), people.names(), intent, match.matchedDishes(), limitations),
                    List.of(),
                    "In der Sammlung gibt es dazu kein passendes Gericht.");
        }
        List<UUID> excluded = new ArrayList<>(match.excludedDishIds());
        if (excluded.size() > 100) {
            excluded = new ArrayList<>(excluded.subList(0, 100));
            limitations.add("Es wurden höchstens 100 Gerichte ausgeschlossen.");
        }
        RecommendationResponse result = recommendations.recommend(
                new RecommendationRequest(
                        mealType,
                        people.dinerIds(),
                        intent.preferSimple(),
                        intent.preferHomeOffice(),
                        intent.preferMealPrep(),
                        List.copyOf(excluded),
                        request.seed(),
                        date),
                match.selection());
        boolean empty = result.suggestions().isEmpty();
        return new AiRecommendationResponse(
                reply(mealType, date, people.names(), intent, match.matchedDishes(), empty),
                null,
                criteria(mealType, date, people.dinerIds(), people.names(), intent, match.matchedDishes(), limitations),
                result.suggestions(),
                result.explanation());
    }

    private String interpret(String message, LocalDate today) {
        try {
            return provider.interpret(IntentPrompt.system(today), message);
        } catch (AiProviderException exception) {
            throw failure(exception);
        }
    }

    private ApiException failure(AiProviderException exception) {
        log.warn("KI-Anfrage fehlgeschlagen: {}", exception.kind());
        return switch (exception.kind()) {
            case TIMEOUT -> new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Der Essensassistent hat zu lange gebraucht. Bitte versuche es erneut.",
                    "AI_TIMEOUT");
            case UNREACHABLE -> new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Der Essensassistent ist gerade nicht erreichbar.",
                    "AI_UNAVAILABLE");
            case INVALID -> new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Der Essensassistent konnte die Anfrage nicht auswerten.",
                    "AI_INVALID");
        };
    }

    private String message(AiRecommendationRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new InvalidRequestException("Bitte formuliere einen Wunsch.");
        }
        String message = request.message().trim();
        int limit = properties.maxInputLength();
        if (limit < 1 || limit > HARD_INPUT_LIMIT) {
            limit = 500;
        }
        if (message.length() > limit) {
            throw new InvalidRequestException("Die Anfrage darf höchstens " + limit + " Zeichen haben.");
        }
        if (message.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t')) {
            throw new InvalidRequestException("Die Anfrage enthält ungültige Zeichen.");
        }
        return message;
    }

    private static InterpretedCriteria criteria(
            MealType mealType,
            LocalDate date,
            List<UUID> dinerIds,
            List<String> names,
            MealIntent intent,
            List<String> matchedDishes,
            List<String> limitations) {
        return new InterpretedCriteria(
                mealType,
                date,
                dinerIds,
                names,
                intent.preferSimple(),
                intent.preferHomeOffice(),
                intent.preferMealPrep(),
                intent.dishSearchTerms(),
                intent.avoidDishTerms(),
                matchedDishes,
                limitations);
    }

    private static String reply(
            MealType mealType,
            LocalDate date,
            List<String> names,
            MealIntent intent,
            List<String> matchedDishes,
            boolean nothingFound) {
        StringBuilder text = new StringBuilder("Ich habe nach ");
        text.append(intent.preferSimple() ? "einem schnellen oder einfachen " : "einem ");
        text.append(mealLabel(mealType));
        if (intent.preferHomeOffice()) {
            text.append(" fürs Homeoffice");
        }
        if (intent.preferMealPrep()) {
            text.append(" zum Vorbereiten");
        }
        text.append(" am ").append(DAY.format(date));
        text.append(" für ").append(people(names));
        text.append(" gesucht");
        if (!intent.dishSearchTerms().isEmpty()) {
            text.append(", mit Bezug auf ").append(String.join(", ", intent.dishSearchTerms()));
        }
        if (!intent.avoidDishTerms().isEmpty()) {
            text.append(", ohne ").append(String.join(", ", intent.avoidDishTerms()));
        }
        text.append(".");
        if (!matchedDishes.isEmpty()) {
            text.append(" In der Sammlung passt dazu ").append(String.join(", ", matchedDishes)).append(".");
        }
        if (nothingFound) {
            text.append(" Dazu gibt es in der Sammlung gerade keinen passenden Vorschlag.");
        } else {
            text.append(" Berücksichtigt wird, was ihr zuletzt gegessen oder schon geplant habt.");
        }
        return text.toString();
    }

    private static String people(List<String> names) {
        if (names.isEmpty()) {
            return "die ausgewählten Personen";
        }
        if (names.size() == 1) {
            return names.getFirst();
        }
        if (names.size() == 2) {
            return names.get(0) + " und " + names.get(1);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " und " + names.getLast();
    }

    private static String mealLabel(MealType mealType) {
        return switch (mealType) {
            case FRUEHSTUECK -> "Frühstück";
            case MITTAGESSEN -> "Mittagessen";
            case ABENDESSEN -> "Abendessen";
            case SNACK -> "Snack";
        };
    }
}

package de.wasessenwir.ai;

import de.wasessenwir.dish.DishRepository;
import de.wasessenwir.dish.InvalidRequestException;
import de.wasessenwir.security.CurrentUser;
import de.wasessenwir.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DishDraftService {

    private static final Logger log = LoggerFactory.getLogger(DishDraftService.class);
    private static final int HARD_INPUT_LIMIT = 2_000;
    private static final List<String> DISH_FIELDS = List.of(
            "name", "description", "mealTypes", "categories", "effort", "ingredients",
            "recipeText", "recipeUrl", "recipeSource", "comments", "mealPrep", "homeOffice");
    private static final List<String> VARIANT_FIELDS = List.of(
            "name", "description", "effort", "ingredients", "recipeText", "recipeUrl",
            "recipeSource", "comments", "mealPrep", "homeOffice");

    private final AiProperties properties;
    private final AiProvider provider;
    private final DishDraftValidator validator;
    private final CatalogRepository catalog;
    private final DishRepository dishes;

    public DishDraftService(
            AiProperties properties,
            AiProvider provider,
            DishDraftValidator validator,
            CatalogRepository catalog,
            DishRepository dishes) {
        this.properties = properties;
        this.provider = provider;
        this.validator = validator;
        this.catalog = catalog;
        this.dishes = dishes;
    }

    @Transactional(readOnly = true)
    public DishDraftResponse create(DishDraftRequest request) {
        CurrentUser.requireId();
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Der Essensassistent ist nicht aktiviert.", "AI_DISABLED");
        }
        if (request == null || request.kind() == null) {
            throw new InvalidRequestException("Bitte wähle Gericht oder Variante.");
        }
        String description = description(request.description());
        CatalogIndex index = catalog.load();
        BaseSelection base = selectBase(request, description, index);
        if (base.ambiguous()) {
            return needsBaseDish(base.candidates());
        }
        DishDraftFacts facts;
        try {
            facts = validator.accept(
                    provider.draft(DishDraftPrompt.system(request.kind(), base.name(), base.ingredients()), description),
                    description,
                    request.kind());
        } catch (AiProviderException exception) {
            throw failure(exception);
        } catch (DishDraftRejectedException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), "AI_UNINTERPRETABLE");
        }
        IngredientChoice ingredients = ingredients(request.kind(), facts, base.ingredients());
        List<String> warnings = new ArrayList<>(facts.warnings());
        warnings.addAll(ingredients.warnings());
        if (warnings.size() > 8) {
            warnings = new ArrayList<>(warnings.subList(0, 8));
        }
        List<String> recognized = recognized(request.kind(), facts, ingredients);
        return new DishDraftResponse(
                DishDraftValidator.VERSION,
                DishDraftResponse.DraftOutcome.DRAFT,
                request.kind(),
                base.id(),
                base.name(),
                facts.name(),
                facts.description(),
                facts.mealTypes(),
                facts.categories(),
                facts.effort(),
                ingredients.values(),
                request.kind() == DraftKind.VARIANT ? ingredients.specified() : null,
                facts.recipeText(),
                facts.recipeUrl(),
                facts.recipeSource(),
                facts.comments(),
                facts.mealPrep(),
                facts.homeOffice(),
                recognized,
                unknown(request.kind(), recognized),
                List.copyOf(warnings),
                DishDuplicateFinder.find(facts.name(), index, base.id()),
                List.of());
    }

    private BaseSelection selectBase(DishDraftRequest request, String description, CatalogIndex index) {
        if (request.kind() != DraftKind.VARIANT) {
            return BaseSelection.none();
        }
        if (request.baseDishId() != null) {
            String name = dishes.findDishName(request.baseDishId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "Das Grundgericht wurde nicht gefunden.",
                            "BASE_DISH_NOT_FOUND"));
            return BaseSelection.selected(request.baseDishId(), name, dishes.findDishIngredients(request.baseDishId()));
        }
        List<CatalogIndex.DishRef> matches = BaseDishResolver.sorted(BaseDishResolver.resolve(description, index.dishes()));
        if (matches.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Das Grundgericht wurde nicht gefunden. Bitte wähle es in der Gerichteverwaltung aus.",
                    "BASE_DISH_NOT_FOUND");
        }
        if (matches.size() > 1) {
            return BaseSelection.ambiguous(matches.stream()
                    .limit(8)
                    .map(dish -> new DishDraftResponse.BaseDishChoice(dish.id(), dish.name()))
                    .toList());
        }
        CatalogIndex.DishRef match = matches.getFirst();
        return BaseSelection.selected(match.id(), match.name(), dishes.findDishIngredients(match.id()));
    }

    private static DishDraftResponse needsBaseDish(List<DishDraftResponse.BaseDishChoice> candidates) {
        return new DishDraftResponse(
                DishDraftValidator.VERSION,
                DishDraftResponse.DraftOutcome.NEEDS_BASE_DISH,
                DraftKind.VARIANT,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of("name"),
                List.of("Mehrere Gerichte passen. Bitte wähle das Grundgericht."),
                List.of(),
                candidates);
    }

    private static IngredientChoice ingredients(DraftKind kind, DishDraftFacts facts, List<String> baseIngredients) {
        if (kind != DraftKind.VARIANT) {
            return new IngredientChoice(facts.ingredients(), false, List.of());
        }
        if (!facts.ingredientsMentioned()) {
            return new IngredientChoice(List.of(), false, List.of());
        }
        if (facts.removedIngredients().isEmpty()) {
            return new IngredientChoice(
                    facts.ingredients(),
                    true,
                    List.of("Es wurden nur die genannten Zutaten übernommen. Bitte prüfe die Liste."));
        }
        boolean resolved = facts.removedIngredients().stream()
                .allMatch(removed -> baseIngredients.stream().anyMatch(item -> sameIngredient(item, removed)));
        if (!resolved) {
            return new IngredientChoice(
                    facts.ingredients(),
                    true,
                    List.of("Die Ersetzung ließ sich nicht eindeutig auf die Zutaten des Grundgerichts anwenden. Bitte prüfe die Liste."));
        }
        List<String> merged = new ArrayList<>();
        for (String item : baseIngredients) {
            if (facts.removedIngredients().stream().noneMatch(removed -> sameIngredient(item, removed))) {
                merged.add(item);
            }
        }
        for (String item : facts.ingredients()) {
            if (merged.stream().noneMatch(existing -> DraftText.fold(existing).equals(DraftText.fold(item)))) {
                merged.add(item);
            }
        }
        return new IngredientChoice(
                List.copyOf(merged),
                true,
                List.of("Bitte prüfe die Zutaten. Übernommen wurde die beschriebene Ersetzung."));
    }

    private static boolean sameIngredient(String stored, String mentioned) {
        String left = DraftText.fold(stored);
        String right = DraftText.fold(mentioned);
        if (left.equals(right)) {
            return true;
        }
        return right.length() >= 5 && left.contains(right) || left.length() >= 5 && right.contains(left);
    }

    private static List<String> recognized(DraftKind kind, DishDraftFacts facts, IngredientChoice ingredients) {
        List<String> keys = new ArrayList<>();
        if (facts.name() != null) {
            keys.add("name");
        }
        if (facts.description() != null) {
            keys.add("description");
        }
        if (kind == DraftKind.DISH && !facts.mealTypes().isEmpty()) {
            keys.add("mealTypes");
        }
        if (kind == DraftKind.DISH && !facts.categories().isEmpty()) {
            keys.add("categories");
        }
        if (facts.effort() != null) {
            keys.add("effort");
        }
        boolean ingredientsKnown = kind == DraftKind.VARIANT ? ingredients.specified() : !ingredients.values().isEmpty();
        if (ingredientsKnown) {
            keys.add("ingredients");
        }
        if (facts.recipeText() != null) {
            keys.add("recipeText");
        }
        if (facts.recipeUrl() != null) {
            keys.add("recipeUrl");
        }
        if (facts.recipeSource() != null) {
            keys.add("recipeSource");
        }
        if (facts.comments() != null) {
            keys.add("comments");
        }
        if (facts.mealPrep() != null) {
            keys.add("mealPrep");
        }
        if (facts.homeOffice() != null) {
            keys.add("homeOffice");
        }
        return List.copyOf(keys);
    }

    private static List<String> unknown(DraftKind kind, List<String> recognized) {
        List<String> fields = kind == DraftKind.VARIANT ? VARIANT_FIELDS : DISH_FIELDS;
        List<String> unknown = new ArrayList<>();
        for (String field : fields) {
            if (!recognized.contains(field)) {
                unknown.add(field);
            }
        }
        return List.copyOf(unknown);
    }

    private String description(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Bitte beschreibe das Gericht.");
        }
        String description = value.trim();
        int limit = properties.maxInputLength();
        if (limit < 1 || limit > HARD_INPUT_LIMIT) {
            limit = 500;
        }
        if (description.length() > limit) {
            throw new InvalidRequestException("Die Beschreibung darf höchstens " + limit + " Zeichen haben.");
        }
        if (description.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t')) {
            throw new InvalidRequestException("Die Beschreibung enthält ungültige Zeichen.");
        }
        return description;
    }

    private ApiException failure(AiProviderException exception) {
        log.warn("Gerichteentwurf fehlgeschlagen: {}", exception.kind());
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
                    "Der Essensassistent konnte die Beschreibung nicht auswerten.",
                    "AI_INVALID");
        };
    }

    private record IngredientChoice(List<String> values, boolean specified, List<String> warnings) {
    }

    private record BaseSelection(
            UUID id,
            String name,
            List<String> ingredients,
            List<DishDraftResponse.BaseDishChoice> candidates,
            boolean ambiguous) {

        static BaseSelection none() {
            return new BaseSelection(null, null, List.of(), List.of(), false);
        }

        static BaseSelection selected(UUID id, String name, List<String> ingredients) {
            return new BaseSelection(id, name, ingredients, List.of(), false);
        }

        static BaseSelection ambiguous(List<DishDraftResponse.BaseDishChoice> candidates) {
            return new BaseSelection(null, null, List.of(), candidates, true);
        }
    }
}

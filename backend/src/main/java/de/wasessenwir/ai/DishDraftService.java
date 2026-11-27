package de.wasessenwir.ai;

import de.wasessenwir.dish.DishRepository;
import de.wasessenwir.dish.InvalidRequestException;
import de.wasessenwir.security.CurrentUser;
import de.wasessenwir.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DishDraftService {

    private static final int HARD_INPUT_LIMIT = 2_000;
    private static final List<String> DISH_FIELDS = List.of(
            "name", "description", "mealTypes", "categories", "effort", "ingredients",
            "recipeText", "recipeUrl", "recipeSource", "comments", "mealPrep", "homeOffice");
    private static final List<String> VARIANT_FIELDS = List.of(
            "name", "description", "effort", "ingredients", "recipeText", "recipeUrl",
            "recipeSource", "comments", "mealPrep", "homeOffice");

    private final AiProperties properties;
    private final AiProviderGateway provider;
    private final DishDraftValidator validator;
    private final CatalogRepository catalog;
    private final DishRepository dishes;

    public DishDraftService(
            AiProperties properties,
            AiProviderGateway provider,
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
        UUID userId = CurrentUser.requireId();
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
                    provider.draft(
                            userId,
                            DishDraftPrompt.system(request.kind(), base.name(), base.ingredients()),
                            description),
                    description,
                    request.kind());
        } catch (AiProviderException exception) {
            throw AiFailures.toApi(exception);
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
        List<String> additions = withoutRemoved(facts.ingredients(), facts.removedIngredients());
        if (kind != DraftKind.VARIANT) {
            return new IngredientChoice(additions, false, List.of());
        }
        if (facts.exclusiveIngredients()) {
            return new IngredientChoice(
                    additions,
                    true,
                    List.of("Es wurden nur die genannten Zutaten übernommen. Bitte prüfe die Liste."));
        }
        if (!facts.ingredientsMentioned() && additions.isEmpty() && facts.removedIngredients().isEmpty()) {
            return new IngredientChoice(List.of(), false, List.of());
        }
        boolean resolved = facts.removedIngredients().stream()
                .allMatch(removed -> baseIngredients.stream().anyMatch(item -> DraftText.sameIngredient(item, removed)));
        if (facts.ingredientsMentioned() && !facts.removedIngredients().isEmpty() && !resolved) {
            return new IngredientChoice(
                    additions,
                    true,
                    List.of("Die Ersetzung ließ sich nicht eindeutig auf die Zutaten des Grundgerichts anwenden. Bitte prüfe die Liste."));
        }
        List<String> merged = new ArrayList<>();
        for (String item : baseIngredients) {
            if (facts.removedIngredients().stream().noneMatch(removed -> DraftText.sameIngredient(item, removed))) {
                merged.add(item);
            }
        }
        for (String item : additions) {
            if (merged.stream().noneMatch(existing -> DraftText.fold(existing).equals(DraftText.fold(item)))) {
                merged.add(item);
            }
        }
        String warning = !facts.removedIngredients().isEmpty() && facts.ingredientsMentioned()
                ? "Bitte prüfe die Zutaten. Übernommen wurde die beschriebene Ersetzung."
                : facts.ingredientsMentioned()
                        ? "Bitte prüfe die Zutaten. Die genannten Zutaten wurden zum Grundgericht ergänzt."
                        : "Bitte prüfe die vorgeschlagenen Zutaten.";
        return new IngredientChoice(List.copyOf(merged), true, List.of(warning));
    }

    private static List<String> withoutRemoved(List<String> values, List<String> removed) {
        List<String> kept = new ArrayList<>();
        for (String value : values) {
            if (removed.stream().noneMatch(item -> DraftText.sameIngredient(value, item))) {
                kept.add(value);
            }
        }
        return List.copyOf(kept);
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

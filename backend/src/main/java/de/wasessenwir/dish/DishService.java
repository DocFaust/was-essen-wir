package de.wasessenwir.dish;

import de.wasessenwir.rating.DishRatingView;
import de.wasessenwir.rating.RatingRepository;
import de.wasessenwir.security.CurrentUser;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DishService {

    private final DishRepository repository;
    private final RatingRepository ratings;

    public DishService(DishRepository repository, RatingRepository ratings) {
        this.repository = repository;
        this.ratings = ratings;
    }

    @Transactional
    public DishDetailResponse create(DishWriteRequest request) {
        NormalizedDish dish = DishValidator.dish(request);
        ensureDishNameAvailable(dish.name(), null, dish.confirmDuplicate());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID id = UUID.randomUUID();
        repository.insertDish(storedDish(id, dish, now, now));
        repository.replaceMealTypes(id, dish.mealTypes());
        repository.replaceCategories(id, dish.categories());
        repository.replaceDishIngredients(id, dish.ingredients());
        return load(id);
    }

    @Transactional
    public DishDetailResponse update(UUID id, DishWriteRequest request) {
        StoredDish existing = repository.findDish(id)
                .orElseThrow(() -> new NotFoundException("Gericht wurde nicht gefunden."));
        NormalizedDish dish = DishValidator.dish(request);
        ensureDishNameAvailable(dish.name(), id, dish.confirmDuplicate());
        repository.updateDish(storedDish(id, dish, existing.createdAt(), OffsetDateTime.now(ZoneOffset.UTC)));
        repository.replaceMealTypes(id, dish.mealTypes());
        repository.replaceCategories(id, dish.categories());
        repository.replaceDishIngredients(id, dish.ingredients());
        return load(id);
    }

    @Transactional
    public void delete(UUID id) {
        if (repository.deleteDish(id) == 0) {
            throw new NotFoundException("Gericht wurde nicht gefunden.");
        }
    }

    @Transactional(readOnly = true)
    public DishDetailResponse get(UUID id) {
        if (repository.findDish(id).isEmpty()) {
            throw new NotFoundException("Gericht wurde nicht gefunden.");
        }
        return load(id);
    }

    @Transactional(readOnly = true)
    public List<DishSummaryResponse> list(
            String query,
            MealType mealType,
            Effort effort,
            Suitability mealPrep,
            Suitability homeOffice) {
        List<UUID> ids = repository.search(blankToNull(query), mealType, effort, mealPrep, homeOffice);
        Map<UUID, Integer> ownRatings = ratings.findDishStars(CurrentUser.requireId(), ids);
        return ids.stream().map(id -> summary(id, ownRatings.get(id))).toList();
    }

    @Transactional
    public DishDetailResponse addVariant(UUID dishId, VariantWriteRequest request) {
        requireDish(dishId);
        NormalizedVariant variant = DishValidator.variant(request);
        ensureVariantNameAvailable(dishId, variant.name(), null);
        UUID variantId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            repository.insertVariant(storedVariant(
                    variantId,
                    dishId,
                    variant,
                    repository.nextVariantPosition(dishId),
                    now,
                    now));
            repository.replaceVariantIngredients(variantId, variant.ingredients());
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateVariantException("Diese Variante gibt es bei dem Gericht schon.");
        }
        return load(dishId);
    }

    @Transactional
    public DishDetailResponse updateVariant(UUID dishId, UUID variantId, VariantWriteRequest request) {
        StoredVariant existing = repository.findVariant(dishId, variantId)
                .orElseThrow(() -> new NotFoundException("Variante wurde nicht gefunden."));
        NormalizedVariant variant = DishValidator.variant(request);
        ensureVariantNameAvailable(dishId, variant.name(), variantId);
        try {
            repository.updateVariant(storedVariant(
                    variantId,
                    dishId,
                    variant,
                    existing.position(),
                    existing.createdAt(),
                    OffsetDateTime.now(ZoneOffset.UTC)));
            repository.replaceVariantIngredients(variantId, variant.ingredients());
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateVariantException("Diese Variante gibt es bei dem Gericht schon.");
        }
        return load(dishId);
    }

    @Transactional
    public DishDetailResponse deleteVariant(UUID dishId, UUID variantId) {
        requireDish(dishId);
        if (repository.deleteVariant(dishId, variantId) == 0) {
            throw new NotFoundException("Variante wurde nicht gefunden.");
        }
        return load(dishId);
    }

    private DishDetailResponse load(UUID id) {
        StoredDish dish = repository.findDish(id)
                .orElseThrow(() -> new NotFoundException("Gericht wurde nicht gefunden."));
        List<String> ingredients = repository.findDishIngredients(id);
        DishRatingView ownRatings = ratings.findForDish(CurrentUser.requireId(), id);
        List<VariantResponse> variants = repository.findVariants(id).stream()
                .map(variant -> toVariant(dish, ingredients, variant, ownRatings.variantStars(variant.id())))
                .toList();
        return new DishDetailResponse(
                dish.id(),
                dish.name(),
                dish.description(),
                repository.findMealTypes(id),
                repository.findCategories(id),
                dish.effort(),
                ingredients,
                dish.recipeText(),
                dish.recipeUrl(),
                dish.recipeSource(),
                dish.comments(),
                suitability(dish.mealPrep()),
                suitability(dish.homeOffice()),
                ownRatings.dishStars(),
                variants);
    }

    private DishSummaryResponse summary(UUID id, Integer myRating) {
        StoredDish dish = repository.findDish(id)
                .orElseThrow(() -> new NotFoundException("Gericht wurde nicht gefunden."));
        return new DishSummaryResponse(
                dish.id(),
                dish.name(),
                repository.findMealTypes(id),
                dish.effort(),
                suitability(dish.mealPrep()),
                suitability(dish.homeOffice()),
                repository.findVariants(id).stream().map(StoredVariant::name).toList(),
                myRating);
    }

    private VariantResponse toVariant(
            StoredDish dish, List<String> dishIngredients, StoredVariant variant, Integer myRating) {
        List<String> ownIngredients = repository.findVariantIngredients(variant.id());
        Suitability dishMealPrep = suitability(dish.mealPrep());
        Suitability dishHomeOffice = suitability(dish.homeOffice());
        SuitabilitySetting mealPrep = setting(variant.mealPrep());
        SuitabilitySetting homeOffice = setting(variant.homeOffice());
        ResolvedProperties resolved = new ResolvedProperties(
                DishInheritance.text(variant.description(), dish.description()),
                DishInheritance.inherited(variant.description()),
                DishInheritance.effort(variant.effort(), dish.effort()),
                DishInheritance.inherited(variant.effort()),
                DishInheritance.ingredients(variant.ingredientsSpecified(), ownIngredients, dishIngredients),
                !variant.ingredientsSpecified(),
                DishInheritance.text(variant.recipeText(), dish.recipeText()),
                DishInheritance.inherited(variant.recipeText()),
                DishInheritance.text(variant.recipeUrl(), dish.recipeUrl()),
                DishInheritance.inherited(variant.recipeUrl()),
                DishInheritance.text(variant.recipeSource(), dish.recipeSource()),
                DishInheritance.inherited(variant.recipeSource()),
                DishInheritance.suitability(mealPrep, dishMealPrep),
                DishInheritance.inherited(mealPrep),
                DishInheritance.suitability(homeOffice, dishHomeOffice),
                DishInheritance.inherited(homeOffice));
        return new VariantResponse(
                variant.id(),
                variant.name(),
                variant.description(),
                variant.effort(),
                variant.ingredientsSpecified(),
                ownIngredients,
                variant.recipeText(),
                variant.recipeUrl(),
                variant.recipeSource(),
                mealPrep,
                homeOffice,
                resolved,
                myRating);
    }

    private void ensureDishNameAvailable(String name, UUID excludeId, boolean confirmDuplicate) {
        if (!confirmDuplicate && repository.dishNameExists(name, excludeId)) {
            throw new DuplicateDishException("Es gibt bereits ein Gericht mit diesem Namen.");
        }
    }

    private void ensureVariantNameAvailable(UUID dishId, String name, UUID excludeId) {
        if (repository.variantNameExists(dishId, name, excludeId)) {
            throw new DuplicateVariantException("Diese Variante gibt es bei dem Gericht schon.");
        }
    }

    private void requireDish(UUID dishId) {
        if (repository.findDish(dishId).isEmpty()) {
            throw new NotFoundException("Gericht wurde nicht gefunden.");
        }
    }

    private static StoredDish storedDish(UUID id, NormalizedDish dish, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new StoredDish(
                id,
                dish.name(),
                dish.description(),
                dish.effort(),
                dish.recipeText(),
                dish.recipeUrl(),
                dish.recipeSource(),
                dish.comments(),
                flag(dish.mealPrep()),
                flag(dish.homeOffice()),
                createdAt,
                updatedAt);
    }

    private static StoredVariant storedVariant(
            UUID id,
            UUID dishId,
            NormalizedVariant variant,
            int position,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        return new StoredVariant(
                id,
                dishId,
                variant.name(),
                variant.description(),
                variant.effort(),
                variant.ingredientsSpecified(),
                variant.recipeText(),
                variant.recipeUrl(),
                variant.recipeSource(),
                flag(variant.mealPrep()),
                flag(variant.homeOffice()),
                position,
                createdAt,
                updatedAt);
    }

    private static Suitability suitability(Boolean stored) {
        if (stored == null) {
            return Suitability.UNKNOWN;
        }
        return stored ? Suitability.SUITABLE : Suitability.UNSUITABLE;
    }

    private static SuitabilitySetting setting(Boolean stored) {
        if (stored == null) {
            return SuitabilitySetting.INHERIT;
        }
        return stored ? SuitabilitySetting.SUITABLE : SuitabilitySetting.UNSUITABLE;
    }

    private static Boolean flag(Suitability suitability) {
        return switch (suitability) {
            case UNKNOWN -> null;
            case SUITABLE -> Boolean.TRUE;
            case UNSUITABLE -> Boolean.FALSE;
        };
    }

    private static Boolean flag(SuitabilitySetting setting) {
        return switch (setting) {
            case INHERIT -> null;
            case SUITABLE -> Boolean.TRUE;
            case UNSUITABLE -> Boolean.FALSE;
        };
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

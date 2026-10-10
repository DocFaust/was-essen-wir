package de.wasessenwir.dish;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dishes")
public class DishController {

    private final DishService dishService;

    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    @GetMapping
    public List<DishSummaryResponse> list(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "mealType", required = false) MealType mealType,
            @RequestParam(name = "effort", required = false) Effort effort,
            @RequestParam(name = "mealPrep", required = false) Suitability mealPrep,
            @RequestParam(name = "homeOffice", required = false) Suitability homeOffice) {
        return dishService.list(query, mealType, effort, mealPrep, homeOffice);
    }

    @PostMapping
    public ResponseEntity<DishDetailResponse> create(@RequestBody DishWriteRequest request) {
        DishDetailResponse created = dishService.create(request);
        return ResponseEntity.created(URI.create("/api/dishes/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public DishDetailResponse get(@PathVariable UUID id) {
        return dishService.get(id);
    }

    @PutMapping("/{id}")
    public DishDetailResponse update(@PathVariable UUID id, @RequestBody DishWriteRequest request) {
        return dishService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        dishService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/variants")
    public DishDetailResponse addVariant(@PathVariable UUID id, @RequestBody VariantWriteRequest request) {
        return dishService.addVariant(id, request);
    }

    @PutMapping("/{id}/variants/{variantId}")
    public DishDetailResponse updateVariant(
            @PathVariable UUID id,
            @PathVariable UUID variantId,
            @RequestBody VariantWriteRequest request) {
        return dishService.updateVariant(id, variantId, request);
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    public DishDetailResponse deleteVariant(@PathVariable UUID id, @PathVariable UUID variantId) {
        return dishService.deleteVariant(id, variantId);
    }
}

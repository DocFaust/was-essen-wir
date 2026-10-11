package de.wasessenwir.meal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meal-plans")
public class MealPlanController {

    private final MealEventService meals;

    public MealPlanController(MealEventService meals) {
        this.meals = meals;
    }

    @GetMapping
    List<MealEventResponse> list(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return meals.listPlans(from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MealEventResponse create(@RequestBody MealEventWriteRequest request) {
        return meals.plan(request);
    }

    @PutMapping("/{id}")
    MealEventResponse update(@PathVariable UUID id, @RequestBody MealEventWriteRequest request) {
        return meals.updatePlan(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        meals.deletePlan(id);
    }

    @PostMapping("/{id}/eaten")
    MealEventResponse confirm(@PathVariable UUID id) {
        return meals.confirm(id);
    }
}

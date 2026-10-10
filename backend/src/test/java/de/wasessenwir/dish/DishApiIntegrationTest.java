package de.wasessenwir.dish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class DishApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesDishMigration() {
        Long applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '2' and success",
                Long.class);

        assertThat(applied).isEqualTo(1L);
    }

    @Test
    void createsDishWithNameOnly() {
        DishDetailResponse created = create(named("Brotsuppe"));

        assertThat(created.name()).isEqualTo("Brotsuppe");
        assertThat(created.description()).isNull();
        assertThat(created.mealTypes()).isEmpty();
        assertThat(created.categories()).isEmpty();
        assertThat(created.effort()).isNull();
        assertThat(created.ingredients()).isEmpty();
        assertThat(created.recipeText()).isNull();
        assertThat(created.recipeUrl()).isNull();
        assertThat(created.recipeSource()).isNull();
        assertThat(created.comments()).isNull();
        assertThat(created.mealPrep()).isEqualTo(Suitability.UNKNOWN);
        assertThat(created.homeOffice()).isEqualTo(Suitability.UNKNOWN);
        assertThat(created.variants()).isEmpty();

        DishDetailResponse loaded = restTemplate.getForEntity("/api/dishes/" + created.id(), DishDetailResponse.class)
                .getBody();
        assertThat(loaded).isEqualTo(created);
    }

    @Test
    void rejectsMissingNameAndInvalidRecipeUrl() {
        ResponseEntity<String> missingName = restTemplate.postForEntity("/api/dishes", named(" "), String.class);
        assertThat(missingName.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingName.getBody()).contains("Der Name ist erforderlich.");

        ResponseEntity<String> invalidUrl = restTemplate.postForEntity(
                "/api/dishes",
                new DishWriteRequest("Suppe", null, null, null, null, null, null, "notaurl", null, null, null, null, null),
                String.class);
        assertThat(invalidUrl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidUrl.getBody()).contains("Der Rezeptlink muss mit http:// oder https:// beginnen.");
    }

    @Test
    void updatesDishAndManagesIngredients() {
        DishDetailResponse created = create(new DishWriteRequest(
                "Linsensuppe",
                "Schnell",
                List.of(MealType.ABENDESSEN, MealType.MITTAGESSEN),
                List.of("Eintopf"),
                Effort.EINFACH,
                List.of("Linsen", "Zwiebel"),
                "Köcheln lassen",
                "https://example.test/linsen",
                "Eigenkreation",
                "Schmeckt am nächsten Tag",
                Suitability.SUITABLE,
                Suitability.UNKNOWN,
                null));

        DishDetailResponse updated = restTemplate.exchange(
                "/api/dishes/" + created.id(),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new DishWriteRequest(
                        "Linsensuppe",
                        "Schnell",
                        List.of(MealType.ABENDESSEN),
                        List.of("Eintopf"),
                        Effort.MITTEL,
                        List.of("Linsen"),
                        "Köcheln lassen",
                        "https://example.test/linsen",
                        "Von Oma",
                        null,
                        Suitability.SUITABLE,
                        Suitability.UNSUITABLE,
                        null)),
                DishDetailResponse.class).getBody();

        assertThat(updated).isNotNull();
        assertThat(updated.recipeSource()).isEqualTo("Von Oma");
        assertThat(updated.ingredients()).containsExactly("Linsen");
        assertThat(updated.mealTypes()).containsExactly(MealType.ABENDESSEN);
        assertThat(updated.effort()).isEqualTo(Effort.MITTEL);
        assertThat(updated.homeOffice()).isEqualTo(Suitability.UNSUITABLE);
        assertThat(updated.comments()).isNull();
    }

    @Test
    void variantInheritsRecipeSourceAndSuitabilityUntilOverridden() {
        DishDetailResponse created = create(new DishWriteRequest(
                "Pizza",
                "Ofen",
                List.of(MealType.ABENDESSEN),
                List.of(),
                Effort.MITTEL,
                List.of("Teig", "Tomatensauce"),
                null,
                null,
                "Chefkoch",
                null,
                Suitability.SUITABLE,
                Suitability.UNKNOWN,
                null));

        DishDetailResponse withVariant = restTemplate.postForEntity(
                "/api/dishes/" + created.id() + "/variants",
                new VariantWriteRequest("Margherita", null, null, null, null, null, null, null, null, null),
                DishDetailResponse.class).getBody();

        assertThat(withVariant).isNotNull();
        VariantResponse inherited = withVariant.variants().getFirst();
        assertThat(inherited.name()).isEqualTo("Margherita");
        assertThat(inherited.recipeSource()).isNull();
        assertThat(inherited.mealPrep()).isEqualTo(SuitabilitySetting.INHERIT);
        assertThat(inherited.resolved().recipeSource()).isEqualTo("Chefkoch");
        assertThat(inherited.resolved().recipeSourceInherited()).isTrue();
        assertThat(inherited.resolved().ingredients()).containsExactly("Teig", "Tomatensauce");
        assertThat(inherited.resolved().ingredientsInherited()).isTrue();
        assertThat(inherited.resolved().effort()).isEqualTo(Effort.MITTEL);
        assertThat(inherited.resolved().mealPrep()).isEqualTo(Suitability.SUITABLE);
        assertThat(inherited.resolved().mealPrepInherited()).isTrue();
        assertThat(inherited.resolved().homeOffice()).isEqualTo(Suitability.UNKNOWN);

        DishDetailResponse overridden = restTemplate.exchange(
                "/api/dishes/" + created.id() + "/variants/" + inherited.id(),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new VariantWriteRequest(
                        "Margherita",
                        "Ohne Käse",
                        Effort.EINFACH,
                        true,
                        List.of("Teig"),
                        null,
                        "https://example.test/pizza",
                        "Eigenkreation",
                        SuitabilitySetting.UNSUITABLE,
                        SuitabilitySetting.SUITABLE)),
                DishDetailResponse.class).getBody();

        VariantResponse own = overridden.variants().getFirst();
        assertThat(own.recipeSource()).isEqualTo("Eigenkreation");
        assertThat(own.resolved().recipeSourceInherited()).isFalse();
        assertThat(own.resolved().ingredients()).containsExactly("Teig");
        assertThat(own.resolved().ingredientsInherited()).isFalse();
        assertThat(own.resolved().mealPrep()).isEqualTo(Suitability.UNSUITABLE);
        assertThat(own.resolved().mealPrepInherited()).isFalse();
        assertThat(own.resolved().homeOffice()).isEqualTo(Suitability.SUITABLE);
        assertThat(own.ingredientsSpecified()).isTrue();

        DishDetailResponse renamedParent = restTemplate.exchange(
                "/api/dishes/" + created.id(),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new DishWriteRequest(
                        "Pizza",
                        "Ofen",
                        List.of(MealType.ABENDESSEN),
                        List.of(),
                        Effort.MITTEL,
                        List.of("Teig", "Tomatensauce"),
                        null,
                        null,
                        "Von Oma",
                        null,
                        Suitability.UNSUITABLE,
                        Suitability.UNKNOWN,
                        null)),
                DishDetailResponse.class).getBody();
        assertThat(renamedParent.variants().getFirst().resolved().recipeSource()).isEqualTo("Eigenkreation");

        DishDetailResponse cleared = restTemplate.exchange(
                "/api/dishes/" + created.id() + "/variants/" + inherited.id(),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new VariantWriteRequest(
                        "Margherita",
                        null,
                        null,
                        false,
                        List.of("Ignoriert"),
                        null,
                        null,
                        " ",
                        SuitabilitySetting.INHERIT,
                        SuitabilitySetting.INHERIT)),
                DishDetailResponse.class).getBody();
        VariantResponse again = cleared.variants().getFirst();
        assertThat(again.resolved().recipeSource()).isEqualTo("Von Oma");
        assertThat(again.resolved().recipeSourceInherited()).isTrue();
        assertThat(again.resolved().ingredients()).containsExactly("Teig", "Tomatensauce");
        assertThat(again.resolved().mealPrep()).isEqualTo(Suitability.UNSUITABLE);
        assertThat(again.resolved().mealPrepInherited()).isTrue();
    }

    @Test
    void duplicateDishRequiresConfirmationAndVariantNamesStayUnique() {
        create(named("Auflauf"));
        ResponseEntity<String> conflict = restTemplate.postForEntity("/api/dishes", named(" auflauf "), String.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("DUPLICATE_DISH_NAME");

        DishDetailResponse confirmed = create(new DishWriteRequest(
                "Auflauf", null, null, null, null, null, null, null, null, null, null, null, true));
        assertThat(confirmed.name()).isEqualTo("Auflauf");

        DishDetailResponse dish = create(named("Salat " + UUID.randomUUID()));
        restTemplate.postForEntity(
                "/api/dishes/" + dish.id() + "/variants",
                new VariantWriteRequest("Grün", null, null, null, null, null, null, null, null, null),
                DishDetailResponse.class);
        ResponseEntity<String> variantConflict = restTemplate.postForEntity(
                "/api/dishes/" + dish.id() + "/variants",
                new VariantWriteRequest(" grün ", null, null, null, null, null, null, null, null, null),
                String.class);
        assertThat(variantConflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(variantConflict.getBody()).contains("DUPLICATE_VARIANT_NAME");
    }

    @Test
    void deleteRemovesVariantsAndIngredients() {
        DishDetailResponse created = create(new DishWriteRequest(
                "Eintopf " + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                List.of("Karotte"),
                null,
                null,
                "Rezeptbuch",
                null,
                null,
                null,
                null));
        DishDetailResponse withVariant = restTemplate.postForEntity(
                "/api/dishes/" + created.id() + "/variants",
                new VariantWriteRequest(
                        "Scharf",
                        null,
                        null,
                        true,
                        List.of("Chili"),
                        null,
                        null,
                        null,
                        null,
                        null),
                DishDetailResponse.class).getBody();
        UUID variantId = withVariant.variants().getFirst().id();

        ResponseEntity<DishDetailResponse> withoutVariant = restTemplate.exchange(
                "/api/dishes/" + created.id() + "/variants/" + variantId,
                org.springframework.http.HttpMethod.DELETE,
                null,
                DishDetailResponse.class);
        assertThat(withoutVariant.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withoutVariant.getBody().variants()).isEmpty();
        assertThat(count("select count(*) from ingredient where variant_id = ?", variantId)).isZero();
        assertThat(count("select count(*) from ingredient where dish_id = ?", created.id())).isEqualTo(1L);

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/dishes/" + created.id(),
                org.springframework.http.HttpMethod.DELETE,
                null,
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count("select count(*) from dish where id = ?", created.id())).isZero();
        assertThat(count("select count(*) from dish_variant where dish_id = ?", created.id())).isZero();
        assertThat(count("select count(*) from ingredient where dish_id = ?", created.id())).isZero();

        ResponseEntity<String> missing = restTemplate.getForEntity("/api/dishes/" + created.id(), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("Gericht wurde nicht gefunden.");
    }

    @Test
    void searchesByNameAndFiltersByMealEffortAndSuitability() {
        String token = UUID.randomUUID().toString().substring(0, 8);
        create(new DishWriteRequest(
                "Fundstück " + token,
                null,
                List.of(MealType.SNACK),
                null,
                Effort.EINFACH,
                null,
                null,
                null,
                null,
                null,
                Suitability.SUITABLE,
                Suitability.UNSUITABLE,
                null));
        DishDetailResponse other = create(new DishWriteRequest(
                "Anderes " + token,
                null,
                List.of(MealType.FRUEHSTUECK),
                null,
                Effort.AUFWENDIG,
                null,
                null,
                null,
                null,
                null,
                Suitability.UNKNOWN,
                null,
                null));
        restTemplate.postForEntity(
                "/api/dishes/" + other.id() + "/variants",
                new VariantWriteRequest("Klein " + token, null, null, null, null, null, null, null, null, null),
                DishDetailResponse.class);

        DishSummaryResponse[] byDishName = restTemplate.getForEntity(
                searchUrl(token, MealType.SNACK, Effort.EINFACH, Suitability.SUITABLE, Suitability.UNSUITABLE),
                DishSummaryResponse[].class).getBody();
        assertThat(byDishName).extracting(DishSummaryResponse::name).containsExactly("Fundstück " + token);

        DishSummaryResponse[] byVariantName = restTemplate.getForEntity(
                UriComponentsBuilder.fromPath("/api/dishes").queryParam("q", "Klein " + token).build().toUriString(),
                DishSummaryResponse[].class).getBody();
        assertThat(byVariantName).extracting(DishSummaryResponse::name).containsExactly("Anderes " + token);
        assertThat(byVariantName[0].variantNames()).containsExactly("Klein " + token);

        ResponseEntity<String> invalidFilter = restTemplate.getForEntity("/api/dishes?mealType=brunch", String.class);
        assertThat(invalidFilter.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private DishDetailResponse create(DishWriteRequest request) {
        ResponseEntity<DishDetailResponse> response = restTemplate.postForEntity("/api/dishes", request, DishDetailResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/dishes/" + response.getBody().id());
        return response.getBody();
    }

    private static DishWriteRequest named(String name) {
        return new DishWriteRequest(name, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static String searchUrl(
            String query,
            MealType mealType,
            Effort effort,
            Suitability mealPrep,
            Suitability homeOffice) {
        return UriComponentsBuilder.fromPath("/api/dishes")
                .queryParam("q", query)
                .queryParam("mealType", mealType.name())
                .queryParam("effort", effort.name())
                .queryParam("mealPrep", mealPrep.name())
                .queryParam("homeOffice", homeOffice.name())
                .build()
                .toUriString();
    }

    private long count(String sql, UUID id) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
        return count == null ? 0 : count;
    }
}

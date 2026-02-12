package org.javai.punit.spec.baseline.covariate;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.api.Covariate;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.DayGroup;
import org.javai.punit.api.RegionGroup;
import org.javai.punit.api.UseCase;
import org.javai.punit.model.CovariateDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UseCaseCovariateExtractor}.
 */
@DisplayName("UseCaseCovariateExtractor")
class UseCaseCovariateExtractorTest {

    private final UseCaseCovariateExtractor extractor = new UseCaseCovariateExtractor();

    @Nested
    @DisplayName("no covariates")
    class NoCovariatesTests {

        @Test
        @DisplayName("should return EMPTY for class without @UseCase")
        void shouldReturnEmptyForClassWithoutUseCase() {
            var declaration = extractor.extractDeclaration(ClassWithoutUseCase.class);

            assertThat(declaration.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should return EMPTY for @UseCase without covariates")
        void shouldReturnEmptyForUseCaseWithoutCovariates() {
            var declaration = extractor.extractDeclaration(UseCaseWithoutCovariates.class);

            assertThat(declaration.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("covariateTimezone")
    class TimezoneTests {

        @Test
        @DisplayName("should enable timezone when true")
        void shouldEnableTimezoneWhenTrue() {
            var declaration = extractor.extractDeclaration(UseCaseWithTimezone.class);

            assertThat(declaration.timezoneEnabled()).isTrue();
            assertThat(declaration.contains(CovariateDeclaration.KEY_TIMEZONE)).isTrue();
        }

        @Test
        @DisplayName("should not enable timezone by default")
        void shouldNotEnableTimezoneByDefault() {
            var declaration = extractor.extractDeclaration(UseCaseWithoutCovariates.class);

            assertThat(declaration.timezoneEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("custom covariates")
    class CustomCovariateTests {

        @Test
        @DisplayName("should extract custom covariates with categories")
        void shouldExtractCustomCovariatesWithCategories() {
            var declaration = extractor.extractDeclaration(UseCaseWithCustomCovariates.class);

            assertThat(declaration.customCovariates()).hasSize(2);
            assertThat(declaration.getCategory("llm_model")).isEqualTo(CovariateCategory.CONFIGURATION);
            assertThat(declaration.getCategory("cache_state")).isEqualTo(CovariateCategory.DATA_STATE);
        }
    }

    @Nested
    @DisplayName("combined covariates")
    class CombinedCovariateTests {

        @Test
        @DisplayName("should extract all covariate types together")
        void shouldExtractAllCovariateTypesTogether() {
            var declaration = extractor.extractDeclaration(UseCaseWithAllCovariates.class);

            assertThat(declaration.dayGroups()).isNotEmpty();
            assertThat(declaration.timePeriods()).isNotEmpty();
            assertThat(declaration.regionGroups()).isNotEmpty();
            assertThat(declaration.timezoneEnabled()).isTrue();
            assertThat(declaration.customCovariates()).isNotEmpty();
        }

        @Test
        @DisplayName("should order keys as standard first then custom")
        void shouldOrderKeysAsStandardFirstThenCustom() {
            var declaration = extractor.extractDeclaration(UseCaseWithAllCovariates.class);

            assertThat(declaration.allKeys())
                .containsExactly("day_of_week", "time_of_day", "region", "timezone", "llm_model");
        }
    }

    // -- Test fixtures --

    static class ClassWithoutUseCase {
    }

    @UseCase("test.no.covariates")
    static class UseCaseWithoutCovariates {
    }

    @UseCase(
        value = "test.timezone",
        covariateTimezone = true
    )
    static class UseCaseWithTimezone {
    }

    @UseCase(
        value = "test.custom.covariates",
        covariates = {
            @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
            @Covariate(key = "cache_state", category = CovariateCategory.DATA_STATE)
        }
    )
    static class UseCaseWithCustomCovariates {
    }

    @UseCase(
        value = "test.all.covariates",
        covariateDayOfWeek = {
            @DayGroup({SATURDAY, SUNDAY})
        },
        covariateTimeOfDay = { "08:00/2h" },
        covariateRegion = {
            @RegionGroup({"US"})
        },
        covariateTimezone = true,
        covariates = {
            @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION)
        }
    )
    static class UseCaseWithAllCovariates {
    }
}

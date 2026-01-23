package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.api.Covariate;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.api.UseCase;
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
    @DisplayName("extractDeclaration()")
    class ExtractDeclarationTests {

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

        @Test
        @DisplayName("should extract standard covariates")
        void shouldExtractStandardCovariates() {
            var declaration = extractor.extractDeclaration(UseCaseWithStandardCovariates.class);
            
            assertThat(declaration.standardCovariates())
                .containsExactly(StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.REGION);
            assertThat(declaration.customCovariates()).isEmpty();
        }

        @Test
        @DisplayName("should extract legacy custom covariates as OPERATIONAL")
        void shouldExtractLegacyCustomCovariatesAsOperational() {
            var declaration = extractor.extractDeclaration(UseCaseWithLegacyCustomCovariates.class);
            
            assertThat(declaration.standardCovariates()).isEmpty();
            assertThat(declaration.customCovariates()).hasSize(2);
            assertThat(declaration.getCategory("feature_flag")).isEqualTo(CovariateCategory.OPERATIONAL);
            assertThat(declaration.getCategory("environment")).isEqualTo(CovariateCategory.OPERATIONAL);
        }

        @Test
        @DisplayName("should extract categorized custom covariates")
        void shouldExtractCategorizedCustomCovariates() {
            var declaration = extractor.extractDeclaration(UseCaseWithCategorizedCovariates.class);
            
            assertThat(declaration.standardCovariates()).isEmpty();
            assertThat(declaration.customCovariates()).hasSize(2);
            assertThat(declaration.getCategory("llm_model")).isEqualTo(CovariateCategory.CONFIGURATION);
            assertThat(declaration.getCategory("run_id")).isEqualTo(CovariateCategory.INFORMATIONAL);
        }

        @Test
        @DisplayName("should extract both standard and custom covariates")
        void shouldExtractBothStandardAndCustomCovariates() {
            var declaration = extractor.extractDeclaration(UseCaseWithBothCovariates.class);
            
            assertThat(declaration.standardCovariates())
                .containsExactly(StandardCovariate.TIME_OF_DAY);
            assertThat(declaration.customCovariates()).hasSize(1);
            assertThat(declaration.allKeys())
                .containsExactly("time_of_day", "custom1");
            // Legacy custom covariates get OPERATIONAL category
            assertThat(declaration.getCategory("custom1")).isEqualTo(CovariateCategory.OPERATIONAL);
        }

        @Test
        @DisplayName("categorized covariates should override legacy covariates with same key")
        void categorizedShouldOverrideLegacy() {
            var declaration = extractor.extractDeclaration(UseCaseWithOverlappingCovariates.class);
            
            // The categorized covariate should win
            assertThat(declaration.getCategory("shared_key")).isEqualTo(CovariateCategory.CONFIGURATION);
        }
    }

    // Test fixtures

    static class ClassWithoutUseCase {
    }

    @UseCase("test.no.covariates")
    static class UseCaseWithoutCovariates {
    }

    @UseCase(
        value = "test.standard.covariates",
        covariates = { StandardCovariate.WEEKDAY_VERSUS_WEEKEND, StandardCovariate.REGION }
    )
    static class UseCaseWithStandardCovariates {
    }

    @UseCase(
        value = "test.legacy.custom.covariates",
        customCovariates = { "feature_flag", "environment" }
    )
    static class UseCaseWithLegacyCustomCovariates {
    }

    @UseCase(
        value = "test.categorized.covariates",
        categorizedCovariates = {
            @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
            @Covariate(key = "run_id", category = CovariateCategory.INFORMATIONAL)
        }
    )
    static class UseCaseWithCategorizedCovariates {
    }

    @UseCase(
        value = "test.both.covariates",
        covariates = { StandardCovariate.TIME_OF_DAY },
        customCovariates = { "custom1" }
    )
    static class UseCaseWithBothCovariates {
    }

    @UseCase(
        value = "test.overlapping.covariates",
        customCovariates = { "shared_key" },
        categorizedCovariates = {
            @Covariate(key = "shared_key", category = CovariateCategory.CONFIGURATION)
        }
    )
    static class UseCaseWithOverlappingCovariates {
    }
}

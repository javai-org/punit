package org.javai.punit.engine.covariate;

import static org.assertj.core.api.Assertions.assertThat;

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
        @DisplayName("should extract custom covariates")
        void shouldExtractCustomCovariates() {
            var declaration = extractor.extractDeclaration(UseCaseWithCustomCovariates.class);
            
            assertThat(declaration.standardCovariates()).isEmpty();
            assertThat(declaration.customCovariates())
                .containsExactly("feature_flag", "environment");
        }

        @Test
        @DisplayName("should extract both standard and custom covariates")
        void shouldExtractBothStandardAndCustomCovariates() {
            var declaration = extractor.extractDeclaration(UseCaseWithBothCovariates.class);
            
            assertThat(declaration.standardCovariates())
                .containsExactly(StandardCovariate.TIME_OF_DAY);
            assertThat(declaration.customCovariates())
                .containsExactly("custom1");
            assertThat(declaration.allKeys())
                .containsExactly("time_of_day", "custom1");
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
        value = "test.custom.covariates",
        customCovariates = { "feature_flag", "environment" }
    )
    static class UseCaseWithCustomCovariates {
    }

    @UseCase(
        value = "test.both.covariates",
        covariates = { StandardCovariate.TIME_OF_DAY },
        customCovariates = { "custom1" }
    )
    static class UseCaseWithBothCovariates {
    }
}


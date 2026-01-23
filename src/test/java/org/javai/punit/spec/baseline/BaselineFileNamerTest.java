package org.javai.punit.spec.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.javai.punit.model.CovariateProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BaselineFileNamer}.
 */
@DisplayName("BaselineFileNamer")
class BaselineFileNamerTest {

    private final BaselineFileNamer namer = new BaselineFileNamer();

    @Nested
    @DisplayName("generateFilename()")
    class GenerateFilenameTests {

        @Test
        @DisplayName("should generate filename without covariates")
        void shouldGenerateFilenameWithoutCovariates() {
            var filename = namer.generateFilename("ShoppingUseCase", "a1b2c3d4e5f6");
            
            assertThat(filename).isEqualTo("ShoppingUseCase-a1b2.yaml");
        }

        @Test
        @DisplayName("should generate filename with one covariate")
        void shouldGenerateFilenameWithOneCovariate() {
            var profile = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            
            var filename = namer.generateFilename("ShoppingUseCase", "a1b2c3d4", profile);
            
            assertThat(filename).startsWith("ShoppingUseCase-a1b2-");
            assertThat(filename).endsWith(".yaml");
            assertThat(filename.split("-")).hasSize(3); // name, footprint, covariate
        }

        @Test
        @DisplayName("should generate filename with multiple covariates")
        void shouldGenerateFilenameWithMultipleCovariates() {
            var profile = CovariateProfile.builder()
                .put("region", "EU")
                .put("timezone", "Europe/London")
                .build();
            
            var filename = namer.generateFilename("ShoppingUseCase", "a1b2c3d4", profile);
            
            assertThat(filename.split("-")).hasSize(4); // name, footprint, cov1, cov2
        }

        @Test
        @DisplayName("should sanitize special characters in use case name")
        void shouldSanitizeSpecialCharactersInUseCaseName() {
            var filename = namer.generateFilename("shopping.product.search", "a1b2c3d4");
            
            assertThat(filename).startsWith("shopping_product_search-");
        }

        @Test
        @DisplayName("should truncate footprint hash to 4 characters")
        void shouldTruncateFootprintHashTo4Characters() {
            var filename = namer.generateFilename("UseCase", "a1b2c3d4e5f6g7h8");
            
            assertThat(filename).isEqualTo("UseCase-a1b2.yaml");
        }
    }

    @Nested
    @DisplayName("parse()")
    class ParseTests {

        @Test
        @DisplayName("should parse filename without covariates")
        void shouldParseFilenameWithoutCovariates() {
            var parsed = namer.parse("ShoppingUseCase-a1b2.yaml");
            
            assertThat(parsed.useCaseName()).isEqualTo("ShoppingUseCase");
            assertThat(parsed.footprintHash()).isEqualTo("a1b2");
            assertThat(parsed.hasCovariates()).isFalse();
            assertThat(parsed.covariateCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should parse filename with covariates")
        void shouldParseFilenameWithCovariates() {
            var parsed = namer.parse("ShoppingUseCase-a1b2-c3d4-e5f6.yaml");
            
            assertThat(parsed.useCaseName()).isEqualTo("ShoppingUseCase");
            assertThat(parsed.footprintHash()).isEqualTo("a1b2");
            assertThat(parsed.hasCovariates()).isTrue();
            assertThat(parsed.covariateCount()).isEqualTo(2);
            assertThat(parsed.covariateHashes()).containsExactly("c3d4", "e5f6");
        }

        @Test
        @DisplayName("should handle .yml extension")
        void shouldHandleYmlExtension() {
            var parsed = namer.parse("UseCase-a1b2.yml");
            
            assertThat(parsed.useCaseName()).isEqualTo("UseCase");
            assertThat(parsed.footprintHash()).isEqualTo("a1b2");
        }

        @Test
        @DisplayName("should throw for invalid format")
        void shouldThrowForInvalidFormat() {
            assertThatThrownBy(() -> namer.parse("InvalidFilename.yaml"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("round-trip")
    class RoundTripTests {

        @Test
        @DisplayName("generated filename should parse correctly")
        void generatedFilenameShouldParseCorrectly() {
            var profile = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            
            var filename = namer.generateFilename("MyUseCase", "abcd1234", profile);
            var parsed = namer.parse(filename);
            
            assertThat(parsed.useCaseName()).isEqualTo("MyUseCase");
            assertThat(parsed.footprintHash()).isEqualTo("abcd");
            assertThat(parsed.hasCovariates()).isTrue();
        }
    }

    @Nested
    @DisplayName("covariate hashing behavior")
    class CovariateHashingBehaviorTests {

        @Test
        @DisplayName("filename should be stable when covariate names AND values are identical")
        void filenameShouldBeStableWhenCovariateNamesAndValuesAreIdentical() {
            // Two profiles with identical covariates (same names and values)
            var profile1 = CovariateProfile.builder()
                .put("region", "EU")
                .put("time_of_day", "09:00-10:00 Europe/London")
                .build();
            
            var profile2 = CovariateProfile.builder()
                .put("region", "EU")
                .put("time_of_day", "09:00-10:00 Europe/London")
                .build();
            
            var filename1 = namer.generateFilename("ShoppingUseCase", "a1b2c3d4", profile1);
            var filename2 = namer.generateFilename("ShoppingUseCase", "a1b2c3d4", profile2);
            
            // Filenames MUST be identical - same names AND same values
            assertThat(filename1)
                .as("Filename should be stable when covariate names and values are identical")
                .isEqualTo(filename2);
        }

        @Test
        @DisplayName("filename should differ when covariate VALUES differ")
        void filenameShouldDifferWhenCovariateValuesDiffer() {
            // Two profiles with same covariate NAMES but different VALUES
            var euProfile = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            
            var usProfile = CovariateProfile.builder()
                .put("region", "US")
                .build();
            
            var euFilename = namer.generateFilename("ShoppingUseCase", "a1b2c3d4", euProfile);
            var usFilename = namer.generateFilename("ShoppingUseCase", "a1b2c3d4", usProfile);
            
            // Filenames should differ - different values enable baseline selection
            // based on current environmental circumstances
            assertThat(euFilename)
                .as("Filename should differ when covariate values differ (for baseline selection)")
                .isNotEqualTo(usFilename);
        }

        @Test
        @DisplayName("filename should differ when covariate NAMES differ")
        void filenameShouldDifferWhenCovariateNamesDiffer() {
            var profile1 = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            
            var profile2 = CovariateProfile.builder()
                .put("timezone", "Europe/London")
                .build();
            
            var filename1 = namer.generateFilename("ShoppingUseCase", "a1b2c3d4", profile1);
            var filename2 = namer.generateFilename("ShoppingUseCase", "a1b2c3d4", profile2);
            
            // Filenames should differ because covariate NAMES are different
            assertThat(filename1)
                .as("Filename should differ when covariate names differ")
                .isNotEqualTo(filename2);
        }

        @Test
        @DisplayName("filename should differ when covariate declaration order differs")
        void filenameShouldDifferWhenCovariateOrderDiffers() {
            var profile1 = CovariateProfile.builder()
                .put("region", "EU")
                .put("timezone", "Europe/London")
                .build();
            
            var profile2 = CovariateProfile.builder()
                .put("timezone", "Europe/London")
                .put("region", "EU")
                .build();
            
            var filename1 = namer.generateFilename("ShoppingUseCase", "a1b2c3d4", profile1);
            var filename2 = namer.generateFilename("ShoppingUseCase", "a1b2c3d4", profile2);
            
            // Filenames should differ because covariate order is different
            // (declaration order matters for baseline selection priority)
            assertThat(filename1)
                .as("Filename should differ when covariate declaration order differs")
                .isNotEqualTo(filename2);
        }

        @Test
        @DisplayName("single experiment with stable covariate resolution produces one filename")
        void singleExperimentWithStableCovariateResolutionProducesOneFilename() {
            // Simulates a single MEASURE experiment run where covariate resolution
            // happens ONCE with fixed experiment timing (start/end), producing
            // a stable profile that is reused throughout the experiment.
            
            // The covariate profile is resolved once at experiment completion,
            // using the experiment's fixed start and end times.
            var stableProfile = CovariateProfile.builder()
                .put("weekday_vs_weekend", "Mo-Fr")
                .put("time_of_day", "14:30-14:45 Europe/London")  // Fixed window
                .put("region", "EU")
                .build();
            
            // Multiple calls with the SAME resolved profile should produce identical filenames
            var filename1 = namer.generateFilename("ShoppingUseCase", "footprint", stableProfile);
            var filename2 = namer.generateFilename("ShoppingUseCase", "footprint", stableProfile);
            var filename3 = namer.generateFilename("ShoppingUseCase", "footprint", stableProfile);
            
            assertThat(filename1)
                .isEqualTo(filename2)
                .isEqualTo(filename3);
        }

        @Test
        @DisplayName("different environmental circumstances produce different baselines")
        void differentEnvironmentalCircumstancesProduceDifferentBaselines() {
            // Experiments run under different circumstances should produce
            // different baseline files for later selection
            
            var weekdayMorningEU = CovariateProfile.builder()
                .put("weekday_vs_weekend", "Mo-Fr")
                .put("time_of_day", "09:00-09:15 Europe/London")
                .put("region", "EU")
                .build();
            
            var weekdayAfternoonEU = CovariateProfile.builder()
                .put("weekday_vs_weekend", "Mo-Fr")
                .put("time_of_day", "14:00-14:15 Europe/London")
                .put("region", "EU")
                .build();
            
            var weekendEU = CovariateProfile.builder()
                .put("weekday_vs_weekend", "Sa-So")
                .put("time_of_day", "14:00-14:15 Europe/London")
                .put("region", "EU")
                .build();
            
            var weekdayMorningUS = CovariateProfile.builder()
                .put("weekday_vs_weekend", "Mo-Fr")
                .put("time_of_day", "09:00-09:15 America/New_York")
                .put("region", "US")
                .build();
            
            var file1 = namer.generateFilename("ShoppingUseCase", "fp", weekdayMorningEU);
            var file2 = namer.generateFilename("ShoppingUseCase", "fp", weekdayAfternoonEU);
            var file3 = namer.generateFilename("ShoppingUseCase", "fp", weekendEU);
            var file4 = namer.generateFilename("ShoppingUseCase", "fp", weekdayMorningUS);
            
            // All files should be different - enabling baseline selection
            assertThat(file1).isNotEqualTo(file2).isNotEqualTo(file3).isNotEqualTo(file4);
            assertThat(file2).isNotEqualTo(file3).isNotEqualTo(file4);
            assertThat(file3).isNotEqualTo(file4);
        }
    }
}


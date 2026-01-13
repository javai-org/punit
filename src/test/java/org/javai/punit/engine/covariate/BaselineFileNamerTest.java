package org.javai.punit.engine.covariate;

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
}


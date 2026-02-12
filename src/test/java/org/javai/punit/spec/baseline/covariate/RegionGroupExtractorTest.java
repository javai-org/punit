package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.javai.punit.api.RegionGroup;
import org.javai.punit.api.UseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RegionGroupExtractor}.
 */
@DisplayName("RegionGroupExtractor")
class RegionGroupExtractorTest {

    private final RegionGroupExtractor extractor = new RegionGroupExtractor();

    @Nested
    @DisplayName("extract")
    class ExtractTests {

        @Test
        @DisplayName("should return empty list for empty input")
        void shouldReturnEmptyListForEmptyInput() {
            var result = extractor.extract(regionGroupsFrom(EmptyRegionGroups.class));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should extract region groups")
        void shouldExtractRegionGroups() {
            var result = extractor.extract(regionGroupsFrom(TwoGroups.class));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should normalize region codes to uppercase")
        void shouldNormalizeRegionCodesToUppercase() {
            var result = extractor.extract(regionGroupsFrom(LowercaseRegions.class));

            assertThat(result.get(0).regions())
                .containsExactlyInAnyOrder("FR", "DE");
        }

        @Test
        @DisplayName("should auto-derive label from sorted region codes")
        void shouldAutoDeriveLabelFromSortedRegionCodes() {
            var result = extractor.extract(regionGroupsFrom(TwoGroups.class));

            assertThat(result)
                .anyMatch(g -> g.label().equals("DE_FR"))
                .anyMatch(g -> g.label().equals("GB_IE"));
        }

        @Test
        @DisplayName("should use custom label when provided")
        void shouldUseCustomLabelWhenProvided() {
            var result = extractor.extract(regionGroupsFrom(LabeledRegions.class));

            assertThat(result)
                .anyMatch(g -> g.label().equals("Europe"));
        }
    }

    @Nested
    @DisplayName("validation")
    class ValidationTests {

        @Test
        @DisplayName("should reject invalid ISO country codes")
        void shouldRejectInvalidIsoCountryCodes() {
            assertThatThrownBy(() -> extractor.extract(regionGroupsFrom(InvalidCode.class)))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("Invalid ISO 3166-1 alpha-2 country code");
        }

        @Test
        @DisplayName("should reject duplicate region codes across groups")
        void shouldRejectDuplicateRegionCodes() {
            assertThatThrownBy(() -> extractor.extract(regionGroupsFrom(DuplicateRegions.class)))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("more than one @RegionGroup");
        }
    }

    @Nested
    @DisplayName("deriveRegionGroupLabel")
    class DeriveLabelTests {

        @Test
        @DisplayName("should join sorted region codes")
        void shouldJoinSortedRegionCodes() {
            assertThat(RegionGroupExtractor.deriveRegionGroupLabel(java.util.Set.of("DE", "FR")))
                .isEqualTo("DE_FR");
        }

        @Test
        @DisplayName("should handle single region code")
        void shouldHandleSingleRegionCode() {
            assertThat(RegionGroupExtractor.deriveRegionGroupLabel(java.util.Set.of("US")))
                .isEqualTo("US");
        }
    }

    // -- Helpers --

    private static RegionGroup[] regionGroupsFrom(Class<?> clazz) {
        return clazz.getAnnotation(UseCase.class).covariateRegion();
    }

    // -- Fixtures --

    @UseCase("test.empty")
    private static class EmptyRegionGroups {}

    @UseCase(
        value = "test.two.groups",
        covariateRegion = {
            @RegionGroup({"FR", "DE"}),
            @RegionGroup({"GB", "IE"})
        }
    )
    private static class TwoGroups {}

    @UseCase(
        value = "test.lowercase",
        covariateRegion = {
            @RegionGroup({"fr", "de"})
        }
    )
    private static class LowercaseRegions {}

    @UseCase(
        value = "test.labeled",
        covariateRegion = {
            @RegionGroup(value = {"FR", "DE"}, label = "Europe")
        }
    )
    private static class LabeledRegions {}

    @UseCase(
        value = "test.invalid.code",
        covariateRegion = {
            @RegionGroup({"XX"})
        }
    )
    private static class InvalidCode {}

    @UseCase(
        value = "test.duplicate",
        covariateRegion = {
            @RegionGroup({"FR", "DE"}),
            @RegionGroup({"DE", "IT"})
        }
    )
    private static class DuplicateRegions {}
}

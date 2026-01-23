package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.ConformanceDetail;
import org.javai.punit.spec.baseline.covariate.CovariateMatcher.MatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateWarningRenderer}.
 */
@DisplayName("CovariateWarningRenderer")
class CovariateWarningRendererTest {

    private final CovariateWarningRenderer renderer = new CovariateWarningRenderer();

    @Nested
    @DisplayName("render()")
    class RenderTests {

        @Test
        @DisplayName("should return empty string for full conformance")
        void shouldReturnEmptyStringForFullConformance() {
            var result = renderer.render(List.of(), false);
            
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should render single non-conformance")
        void shouldRenderSingleNonConformance() {
            var detail = new ConformanceDetail(
                "region",
                new CovariateValue.StringValue("EU"),
                new CovariateValue.StringValue("US"),
                MatchResult.DOES_NOT_CONFORM
            );
            
            var result = renderer.render(List.of(detail), false);
            
            assertThat(result).contains("COVARIATE NON-CONFORMANCE");
            assertThat(result).contains("region");
            assertThat(result).contains("baseline=EU");
            assertThat(result).contains("test=US");
        }

        @Test
        @DisplayName("should render multiple non-conformances")
        void shouldRenderMultipleNonConformances() {
            var detail1 = new ConformanceDetail(
                "region",
                new CovariateValue.StringValue("EU"),
                new CovariateValue.StringValue("US"),
                MatchResult.DOES_NOT_CONFORM
            );
            var detail2 = new ConformanceDetail(
                "timezone",
                new CovariateValue.StringValue("Europe/London"),
                new CovariateValue.StringValue("America/New_York"),
                MatchResult.DOES_NOT_CONFORM
            );
            
            var result = renderer.render(List.of(detail1, detail2), false);
            
            assertThat(result).contains("region");
            assertThat(result).contains("timezone");
        }

        @Test
        @DisplayName("should render ambiguity warning")
        void shouldRenderAmbiguityWarning() {
            var result = renderer.render(List.of(), true);
            
            assertThat(result).contains("equally-suitable baselines");
        }

        @Test
        @DisplayName("should render both non-conformance and ambiguity")
        void shouldRenderBothNonConformanceAndAmbiguity() {
            var detail = new ConformanceDetail(
                "region",
                new CovariateValue.StringValue("EU"),
                new CovariateValue.StringValue("US"),
                MatchResult.DOES_NOT_CONFORM
            );
            
            var result = renderer.render(List.of(detail), true);
            
            assertThat(result).contains("NON-CONFORMANCE");
            assertThat(result).contains("equally-suitable baselines");
        }
    }

    @Nested
    @DisplayName("renderShort()")
    class RenderShortTests {

        @Test
        @DisplayName("should return empty string for no non-conformance")
        void shouldReturnEmptyStringForNoNonConformance() {
            var result = renderer.renderShort(List.of());
            
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should list non-conforming covariate keys")
        void shouldListNonConformingCovariateKeys() {
            var detail1 = new ConformanceDetail(
                "region",
                new CovariateValue.StringValue("EU"),
                new CovariateValue.StringValue("US"),
                MatchResult.DOES_NOT_CONFORM
            );
            var detail2 = new ConformanceDetail(
                "timezone",
                new CovariateValue.StringValue("Europe/London"),
                new CovariateValue.StringValue("America/New_York"),
                MatchResult.DOES_NOT_CONFORM
            );
            
            var result = renderer.renderShort(List.of(detail1, detail2));
            
            assertThat(result).contains("region, timezone");
        }
    }
}


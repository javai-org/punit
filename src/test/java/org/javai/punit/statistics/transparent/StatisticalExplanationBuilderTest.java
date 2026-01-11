package org.javai.punit.statistics.transparent;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StatisticalExplanationBuilder}.
 */
class StatisticalExplanationBuilderTest {

    private StatisticalExplanationBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new StatisticalExplanationBuilder();
    }

    @Nested
    @DisplayName("build() with baseline")
    class BuildWithBaselineTests {

        @Test
        @DisplayName("creates explanation with correct test name")
        void setsTestName() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "shouldReturnValidJson",
                    100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.testName()).isEqualTo("shouldReturnValidJson");
        }

        @Test
        @DisplayName("creates hypothesis statement for threshold")
        void createsHypothesisStatement() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.hypothesis().testType())
                    .isEqualTo("One-sided binomial proportion test");
            assertThat(explanation.hypothesis().nullHypothesis())
                    .contains("0.85");
            assertThat(explanation.hypothesis().alternativeHypothesis())
                    .contains("0.85");
        }

        @Test
        @DisplayName("captures observed data correctly")
        void capturesObservedData() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.observed().sampleSize()).isEqualTo(100);
            assertThat(explanation.observed().successes()).isEqualTo(87);
            assertThat(explanation.observed().observedRate()).isCloseTo(0.87, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("includes baseline reference from baseline data")
        void includesBaselineReference() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.baseline().hasBaselineData()).isTrue();
            assertThat(explanation.baseline().baselineSamples()).isEqualTo(1000);
            assertThat(explanation.baseline().baselineSuccesses()).isEqualTo(870);
            assertThat(explanation.baseline().baselineRate()).isCloseTo(0.87, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("calculates statistical inference")
        void calculatesStatisticalInference() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.inference().standardError()).isGreaterThan(0);
            assertThat(explanation.inference().ciLower()).isLessThan(0.87);
            assertThat(explanation.inference().ciUpper()).isGreaterThan(0.87);
            assertThat(explanation.inference().confidenceLevel()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("creates passing verdict interpretation")
        void createsPassingVerdict() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.verdict().passed()).isTrue();
            assertThat(explanation.verdict().technicalResult()).isEqualTo("PASS");
            assertThat(explanation.verdict().plainEnglish()).isNotEmpty();
        }

        @Test
        @DisplayName("creates failing verdict interpretation")
        void createsFailingVerdict() {
            BaselineData baseline = createBaseline(1000, 950);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 80, baseline, 0.90, false, 0.95
            );
            
            assertThat(explanation.verdict().passed()).isFalse();
            assertThat(explanation.verdict().technicalResult()).isEqualTo("FAIL");
            assertThat(explanation.verdict().plainEnglish()).contains("falls below");
        }

        @Test
        @DisplayName("includes caveats for small sample sizes")
        void includesCaveatsForSmallSamples() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 20, 17, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.toLowerCase().contains("sample size"));
        }
        
        @Test
        @DisplayName("handles null baseline gracefully")
        void handlesNullBaseline() {
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, null, 0.85, true, 0.95
            );
            
            assertThat(explanation.baseline().hasBaselineData()).isFalse();
            assertThat(explanation.baseline().sourceFile()).contains("inline");
        }
    }

    @Nested
    @DisplayName("buildWithInlineThreshold()")
    class BuildWithInlineThresholdTests {

        @Test
        @DisplayName("creates explanation without baseline data")
        void createsExplanationWithoutBaseline() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "inlineTest", 100, 87, 0.85, true
            );
            
            assertThat(explanation.testName()).isEqualTo("inlineTest");
            assertThat(explanation.baseline().hasBaselineData()).isFalse();
            assertThat(explanation.baseline().sourceFile()).contains("inline");
        }

        @Test
        @DisplayName("includes inline threshold caveat")
        void includesInlineThresholdCaveat() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "inlineTest", 100, 87, 0.85, true
            );
            
            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.toLowerCase().contains("inline threshold"));
        }
    }

    private BaselineData createBaseline(int samples, int successes) {
        return new BaselineData(
                "TestUseCase.yaml",
                Instant.now(),
                samples,
                successes
        );
    }
}

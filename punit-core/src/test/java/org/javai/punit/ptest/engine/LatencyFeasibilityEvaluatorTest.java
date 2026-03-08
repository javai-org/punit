package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LatencyFeasibilityEvaluator}.
 */
@DisplayName("LatencyFeasibilityEvaluator")
class LatencyFeasibilityEvaluatorTest {

    @Nested
    @DisplayName("Feasible configurations")
    class FeasibleConfigurations {

        @Test
        @DisplayName("should be feasible when no latency requested")
        void shouldBeFeasibleWhenNoLatencyRequested() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, false);

            var result = LatencyFeasibilityEvaluator.evaluate(config, 100, 0.90);

            assertThat(result.feasible()).isTrue();
        }

        @Test
        @DisplayName("should be feasible with null config")
        void shouldBeFeasibleWithNullConfig() {
            var result = LatencyFeasibilityEvaluator.evaluate(null, 100, 0.90);

            assertThat(result.feasible()).isTrue();
        }

        @Test
        @DisplayName("should be feasible for p95 with 25 expected successes")
        void shouldBeFeasibleForP95WithEnoughSamples() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, -1, false);

            // 25 planned * 1.0 success rate = 25 expected successes >= 20 (p95 minimum)
            var result = LatencyFeasibilityEvaluator.evaluate(config, 25, 1.0);

            assertThat(result.feasible()).isTrue();
        }

        @Test
        @DisplayName("should be feasible for p99 with 100 expected successes")
        void shouldBeFeasibleForP99WithEnoughSamples() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, 1000, false);

            // 110 planned * 0.95 success rate = 104 expected successes >= 100 (p99 minimum)
            var result = LatencyFeasibilityEvaluator.evaluate(config, 110, 0.95);

            assertThat(result.feasible()).isTrue();
        }

        @Test
        @DisplayName("should be feasible for all percentiles with large sample size")
        void shouldBeFeasibleForAllPercentilesWithLargeSampleSize() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(200, 500, 700, 1000, false);

            var result = LatencyFeasibilityEvaluator.evaluate(config, 200, 0.90);

            assertThat(result.feasible()).isTrue();
        }
    }

    @Nested
    @DisplayName("Infeasible configurations")
    class InfeasibleConfigurations {

        @Test
        @DisplayName("should be infeasible for p99 with too few expected successes")
        void shouldBeInfeasibleForP99() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, 1000, false);

            // 50 planned * 0.90 = 45 expected successes < 100 (p99 minimum)
            var result = LatencyFeasibilityEvaluator.evaluate(config, 50, 0.90);

            assertThat(result.feasible()).isFalse();
            assertThat(result.message()).contains("p99");
            assertThat(result.message()).contains("at least 100");
        }

        @Test
        @DisplayName("should be infeasible for p95 with very few samples")
        void shouldBeInfeasibleForP95() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, -1, false);

            // 10 planned * 1.0 = 10 expected successes < 20 (p95 minimum)
            var result = LatencyFeasibilityEvaluator.evaluate(config, 10, 1.0);

            assertThat(result.feasible()).isFalse();
            assertThat(result.message()).contains("p95");
            assertThat(result.message()).contains("at least 20");
        }

        @Test
        @DisplayName("should report highest percentile requirement first")
        void shouldReportHighestPercentileFirst() {
            // Both p95 and p99 are infeasible — p99 should be reported since it's checked first
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, 1000, false);

            var result = LatencyFeasibilityEvaluator.evaluate(config, 10, 1.0);

            assertThat(result.feasible()).isFalse();
            assertThat(result.message()).contains("p99");
        }

        @Test
        @DisplayName("should account for expected success rate")
        void shouldAccountForExpectedSuccessRate() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, 1000, false);

            // 200 planned * 0.40 = 80 expected successes < 100 (p99 minimum)
            var result = LatencyFeasibilityEvaluator.evaluate(config, 200, 0.40);

            assertThat(result.feasible()).isFalse();
            assertThat(result.message()).contains("p99");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should be feasible when config has no explicit thresholds")
        void shouldBeFeasibleWhenNoExplicitThresholds() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, true);

            var result = LatencyFeasibilityEvaluator.evaluate(config, 10, 0.90);

            assertThat(result.feasible()).isTrue();
        }

        @Test
        @DisplayName("should handle zero expected success rate")
        void shouldHandleZeroExpectedSuccessRate() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, -1, false);

            var result = LatencyFeasibilityEvaluator.evaluate(config, 100, 0.0);

            assertThat(result.feasible()).isFalse();
        }
    }
}

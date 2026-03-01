package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.statistics.LatencyDistribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LatencyAssertionEvaluator")
class LatencyAssertionEvaluatorTest {

    private final LatencyAssertionEvaluator evaluator = new LatencyAssertionEvaluator();

    @Nested
    @DisplayName("When latency is not requested")
    class NotRequested {

        @Test
        @DisplayName("should return not-requested result when no thresholds set")
        void shouldReturnNotRequestedWhenNoThresholds() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, false);

            LatencyAssertionResult result = evaluator.evaluate(config, null, 0);

            assertThat(result.passed()).isTrue();
            assertThat(result.wasEvaluated()).isFalse();
            assertThat(result.percentileResults()).isEmpty();
        }
    }

    @Nested
    @DisplayName("When no successful samples")
    class NoSuccessfulSamples {

        @Test
        @DisplayName("should skip when zero successful samples")
        void shouldSkipWithZeroSuccesses() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, -1, false);

            LatencyAssertionResult result = evaluator.evaluate(config, null, 0);

            assertThat(result.passed()).isTrue();
            assertThat(result.skipped()).isTrue();
            assertThat(result.caveats()).hasSize(1);
            assertThat(result.caveats().get(0)).contains("No successful samples");
        }
    }

    @Nested
    @DisplayName("Passing assertions")
    class PassingAssertions {

        @Test
        @DisplayName("should pass when all percentiles are within thresholds")
        void shouldPassWhenWithinThresholds() {
            long[] millis = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
            LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 2000, 3000, false);

            LatencyAssertionResult result = evaluator.evaluate(config, dist, 10);

            assertThat(result.passed()).isTrue();
            assertThat(result.wasEvaluated()).isTrue();
            assertThat(result.successfulSampleCount()).isEqualTo(10);
            assertThat(result.percentileResults()).hasSize(2);
        }

        @Test
        @DisplayName("should pass when observed equals threshold exactly")
        void shouldPassWhenObservedEqualsThreshold() {
            long[] millis = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
            LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
            // p95 is 950, p99 is 1000 for this data set
            LatencyAssertionConfig config = new LatencyAssertionConfig(
                    -1, -1, dist.p95Ms(), dist.p99Ms(), false);

            LatencyAssertionResult result = evaluator.evaluate(config, dist, 10);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("should evaluate only asserted percentiles")
        void shouldEvaluateOnlyAssertedPercentiles() {
            long[] millis = {100, 200, 300, 400, 500};
            LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 1000, -1, false);

            LatencyAssertionResult result = evaluator.evaluate(config, dist, 5);

            assertThat(result.percentileResults()).hasSize(1);
            assertThat(result.percentileResults().get(0).label()).isEqualTo("p95");
        }
    }

    @Nested
    @DisplayName("Failing assertions")
    class FailingAssertions {

        @Test
        @DisplayName("should fail when a percentile exceeds threshold")
        void shouldFailWhenPercentileExceedsThreshold() {
            long[] millis = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
            LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
            // p99 is 1000ms, threshold is 500ms → fail
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, 500, false);

            LatencyAssertionResult result = evaluator.evaluate(config, dist, 10);

            assertThat(result.passed()).isFalse();
            assertThat(result.percentileResults()).hasSize(1);
            assertThat(result.percentileResults().get(0).passed()).isFalse();
            assertThat(result.percentileResults().get(0).label()).isEqualTo("p99");
            assertThat(result.percentileResults().get(0).observedMs()).isGreaterThan(500);
        }

        @Test
        @DisplayName("should fail if any one percentile breaches even if others pass")
        void shouldFailIfAnyPercentileBreaches() {
            long[] millis = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
            LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
            // p50 has generous threshold, p99 is too tight
            LatencyAssertionConfig config = new LatencyAssertionConfig(
                    5000, -1, -1, 500, false);

            LatencyAssertionResult result = evaluator.evaluate(config, dist, 10);

            assertThat(result.passed()).isFalse();
            assertThat(result.percentileResults()).hasSize(2);
            // p50 should pass
            assertThat(result.percentileResults().get(0).passed()).isTrue();
            // p99 should fail
            assertThat(result.percentileResults().get(1).passed()).isFalse();
        }
    }

    @Nested
    @DisplayName("All four percentiles")
    class AllFourPercentiles {

        @Test
        @DisplayName("should evaluate all four when all are set")
        void shouldEvaluateAllFour() {
            long[] millis = new long[100];
            for (int i = 0; i < 100; i++) {
                millis[i] = (i + 1) * 10;
            }
            LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
            LatencyAssertionConfig config = new LatencyAssertionConfig(
                    5000, 5000, 5000, 5000, false);

            LatencyAssertionResult result = evaluator.evaluate(config, dist, 100);

            assertThat(result.passed()).isTrue();
            assertThat(result.percentileResults()).hasSize(4);
            assertThat(result.percentileResults()).extracting("label")
                    .containsExactly("p50", "p90", "p95", "p99");
        }
    }

    @Nested
    @DisplayName("Indicative flag")
    class IndicativeFlag {

        @Test
        @DisplayName("should mark p99 as indicative with small sample size")
        void shouldMarkP99AsIndicativeWithSmallSample() {
            // Only 5 samples, but p99 recommended minimum is 100
            long[] millis = {100, 200, 300, 400, 500};
            LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, 5000, false);

            LatencyAssertionResult result = evaluator.evaluate(config, dist, 5);

            assertThat(result.percentileResults().get(0).indicative()).isTrue();
            assertThat(result.caveats()).anyMatch(c -> c.contains("indicative"));
        }

        @Test
        @DisplayName("should not mark p50 as indicative with moderate sample size")
        void shouldNotMarkP50AsIndicativeWithModerateSample() {
            long[] millis = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
            LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
            LatencyAssertionConfig config = new LatencyAssertionConfig(5000, -1, -1, -1, false);

            LatencyAssertionResult result = evaluator.evaluate(config, dist, 10);

            assertThat(result.percentileResults().get(0).indicative()).isFalse();
            assertThat(result.caveats()).isEmpty();
        }
    }
}

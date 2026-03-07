package org.javai.punit.ptest.bernoulli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SampleResultAggregator per-dimension tracking")
class SampleResultAggregatorDimensionTest {

    @Nested
    @DisplayName("initially")
    class Initial {

        @Test
        @DisplayName("no dimensions are asserted")
        void noDimensionsAsserted() {
            var aggregator = new SampleResultAggregator(100);

            assertThat(aggregator.isFunctionalAsserted()).isFalse();
            assertThat(aggregator.isLatencyAsserted()).isFalse();
        }

        @Test
        @DisplayName("per-dimension counts return empty")
        void perDimensionCountsEmpty() {
            var aggregator = new SampleResultAggregator(100);

            assertThat(aggregator.functionalSuccesses()).isEmpty();
            assertThat(aggregator.functionalFailures()).isEmpty();
            assertThat(aggregator.latencySuccesses()).isEmpty();
            assertThat(aggregator.latencyFailures()).isEmpty();
        }
    }

    @Nested
    @DisplayName("functional dimension")
    class FunctionalDimension {

        @Test
        @DisplayName("records functional successes")
        void recordsFunctionalSuccesses() {
            var aggregator = new SampleResultAggregator(100);

            aggregator.recordFunctionalResult(true);
            aggregator.recordFunctionalResult(true);
            aggregator.recordFunctionalResult(false);

            assertThat(aggregator.isFunctionalAsserted()).isTrue();
            assertThat(aggregator.functionalSuccesses()).hasValue(2);
            assertThat(aggregator.functionalFailures()).hasValue(1);
        }
    }

    @Nested
    @DisplayName("latency dimension")
    class LatencyDimension {

        @Test
        @DisplayName("records latency successes")
        void recordsLatencySuccesses() {
            var aggregator = new SampleResultAggregator(100);

            aggregator.recordLatencyResult(true);
            aggregator.recordLatencyResult(false);
            aggregator.recordLatencyResult(false);

            assertThat(aggregator.isLatencyAsserted()).isTrue();
            assertThat(aggregator.latencySuccesses()).hasValue(1);
            assertThat(aggregator.latencyFailures()).hasValue(2);
        }
    }

    @Nested
    @DisplayName("both dimensions")
    class BothDimensions {

        @Test
        @DisplayName("tracks dimensions independently")
        void tracksDimensionsIndependently() {
            var aggregator = new SampleResultAggregator(100);

            aggregator.recordFunctionalResult(true);
            aggregator.recordLatencyResult(false);

            assertThat(aggregator.isFunctionalAsserted()).isTrue();
            assertThat(aggregator.isLatencyAsserted()).isTrue();
            assertThat(aggregator.functionalSuccesses()).hasValue(1);
            assertThat(aggregator.functionalFailures()).hasValue(0);
            assertThat(aggregator.latencySuccesses()).hasValue(0);
            assertThat(aggregator.latencyFailures()).hasValue(1);
        }

        @Test
        @DisplayName("does not affect composite pass/fail counts")
        void doesNotAffectCompositeCountsDirectly() {
            var aggregator = new SampleResultAggregator(100);

            // Record composite results (the existing mechanism)
            aggregator.recordSuccess();
            aggregator.recordFailure(null);

            // Record dimension results (the new mechanism)
            aggregator.recordFunctionalResult(true);
            aggregator.recordLatencyResult(false);

            // Composite counts unchanged
            assertThat(aggregator.getSuccesses()).isEqualTo(1);
            assertThat(aggregator.getFailures()).isEqualTo(1);
        }
    }
}

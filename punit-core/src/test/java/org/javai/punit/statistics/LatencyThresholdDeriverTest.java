package org.javai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LatencyThresholdDeriver")
class LatencyThresholdDeriverTest {

    @Nested
    @DisplayName("Upper bound computation")
    class UpperBoundComputation {

        @Test
        @DisplayName("should return baseline value when stddev is zero")
        void shouldReturnBaselineWhenStdDevIsZero() {
            long result = LatencyThresholdDeriver.deriveUpperBound(500, 0, 100, 0.95);

            assertThat(result).isEqualTo(500);
        }

        @Test
        @DisplayName("should return value greater than or equal to baseline")
        void shouldReturnValueGreaterThanOrEqualToBaseline() {
            long result = LatencyThresholdDeriver.deriveUpperBound(500, 100, 100, 0.95);

            assertThat(result).isGreaterThanOrEqualTo(500);
        }

        @Test
        @DisplayName("should increase with higher confidence")
        void shouldIncreaseWithHigherConfidence() {
            long result95 = LatencyThresholdDeriver.deriveUpperBound(500, 100, 100, 0.95);
            long result99 = LatencyThresholdDeriver.deriveUpperBound(500, 100, 100, 0.99);

            assertThat(result99).isGreaterThanOrEqualTo(result95);
        }

        @Test
        @DisplayName("should decrease with larger sample size")
        void shouldDecreaseWithLargerSampleSize() {
            long resultSmall = LatencyThresholdDeriver.deriveUpperBound(500, 100, 10, 0.95);
            long resultLarge = LatencyThresholdDeriver.deriveUpperBound(500, 100, 1000, 0.95);

            assertThat(resultLarge).isLessThanOrEqualTo(resultSmall);
        }

        @Test
        @DisplayName("should compute reasonable upper bound with known values")
        void shouldComputeReasonableUpperBound() {
            // baseline=500ms, stddev=100ms, n=100, confidence=0.95
            // z ≈ 1.645, stdErr = 100/10 = 10, upperBound ≈ 500 + 1.645*10 ≈ 517
            long result = LatencyThresholdDeriver.deriveUpperBound(500, 100, 100, 0.95);

            assertThat(result).isBetween(510L, 525L);
        }

        @Test
        @DisplayName("should increase with higher standard deviation")
        void shouldIncreaseWithHigherStdDev() {
            long resultLowStdDev = LatencyThresholdDeriver.deriveUpperBound(500, 50, 100, 0.95);
            long resultHighStdDev = LatencyThresholdDeriver.deriveUpperBound(500, 200, 100, 0.95);

            assertThat(resultHighStdDev).isGreaterThanOrEqualTo(resultLowStdDev);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject zero sample count")
        void shouldRejectZeroSampleCount() {
            assertThatThrownBy(() -> LatencyThresholdDeriver.deriveUpperBound(500, 100, 0, 0.95))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baselineSampleCount");
        }

        @Test
        @DisplayName("should reject negative sample count")
        void shouldRejectNegativeSampleCount() {
            assertThatThrownBy(() -> LatencyThresholdDeriver.deriveUpperBound(500, 100, -1, 0.95))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject confidence of zero")
        void shouldRejectConfidenceZero() {
            assertThatThrownBy(() -> LatencyThresholdDeriver.deriveUpperBound(500, 100, 100, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("confidence");
        }

        @Test
        @DisplayName("should reject confidence of one")
        void shouldRejectConfidenceOne() {
            assertThatThrownBy(() -> LatencyThresholdDeriver.deriveUpperBound(500, 100, 100, 1.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

package org.javai.punit.controls.pacing;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.annotation.Annotation;
import org.javai.punit.api.Pacing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PacingCalculator}.
 */
class PacingCalculatorTest {

    private PacingCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PacingCalculator();
    }

    @Nested
    @DisplayName("Effective Delay Calculation")
    class EffectiveDelayTests {

        @Test
        @DisplayName("No constraints returns zero delay")
        void noConstraints_returnsZeroDelay() {
            long delay = calculator.computeEffectiveDelay(0, 0, 0, 0);
            assertThat(delay).isEqualTo(0);
        }

        @Test
        @DisplayName("minMsPerSample is used directly")
        void minMsPerSample_usedDirectly() {
            long delay = calculator.computeEffectiveDelay(0, 0, 0, 500);
            assertThat(delay).isEqualTo(500);
        }

        @Test
        @DisplayName("maxRequestsPerSecond of 2 implies 500ms delay")
        void maxRps_impliesDelay() {
            long delay = calculator.computeEffectiveDelay(2.0, 0, 0, 0);
            assertThat(delay).isEqualTo(500);
        }

        @Test
        @DisplayName("maxRequestsPerMinute of 60 implies 1000ms delay")
        void maxRpm_impliesDelay() {
            long delay = calculator.computeEffectiveDelay(0, 60, 0, 0);
            assertThat(delay).isEqualTo(1000);
        }

        @Test
        @DisplayName("maxRequestsPerMinute of 120 implies 500ms delay")
        void maxRpm_120_impliesDelay() {
            long delay = calculator.computeEffectiveDelay(0, 120, 0, 0);
            assertThat(delay).isEqualTo(500);
        }

        @Test
        @DisplayName("maxRequestsPerHour of 3600 implies 1000ms delay")
        void maxRph_impliesDelay() {
            long delay = calculator.computeEffectiveDelay(0, 0, 3600, 0);
            assertThat(delay).isEqualTo(1000);
        }

        @Test
        @DisplayName("Most restrictive constraint wins")
        void mostRestrictiveWins() {
            // RPS = 10 → 100ms
            // RPM = 60 → 1000ms (most restrictive)
            // minMs = 500
            long delay = calculator.computeEffectiveDelay(10, 60, 0, 500);
            assertThat(delay).isEqualTo(1000);
        }

        @Test
        @DisplayName("Fractional RPS rounds up")
        void fractionalRps_roundsUp() {
            // 0.5 RPS → 2000ms
            long delay = calculator.computeEffectiveDelay(0.5, 0, 0, 0);
            assertThat(delay).isEqualTo(2000);
        }

        @Test
        @DisplayName("Fractional RPM rounds up")
        void fractionalRpm_roundsUp() {
            // 0.5 RPM → 120000ms (2 minutes)
            long delay = calculator.computeEffectiveDelay(0, 0.5, 0, 0);
            assertThat(delay).isEqualTo(120000);
        }
    }

    @Nested
    @DisplayName("Effective Concurrency Calculation")
    class EffectiveConcurrencyTests {

        @Test
        @DisplayName("Zero maxConcurrent defaults to 1 (sequential)")
        void zeroConcurrency_defaultsToOne() {
            int concurrency = calculator.computeEffectiveConcurrency(0, 1000, 0, 0, 0, 0);
            assertThat(concurrency).isEqualTo(1);
        }

        @Test
        @DisplayName("One maxConcurrent stays at 1")
        void oneConcurrency_staysAtOne() {
            int concurrency = calculator.computeEffectiveConcurrency(1, 1000, 0, 0, 0, 0);
            assertThat(concurrency).isEqualTo(1);
        }

        @Test
        @DisplayName("Requested concurrency used when no rate limits")
        void noRateLimits_usesRequestedConcurrency() {
            int concurrency = calculator.computeEffectiveConcurrency(5, 0, 0, 0, 0, 0);
            assertThat(concurrency).isEqualTo(5);
        }

        @Test
        @DisplayName("Requested concurrency used when latency unknown")
        void unknownLatency_usesRequestedConcurrency() {
            int concurrency = calculator.computeEffectiveConcurrency(5, 1000, 10, 0, 0, 0);
            assertThat(concurrency).isEqualTo(5);
        }

        @Test
        @DisplayName("Concurrency throttled by rate limit and latency")
        void throttledByConcurrency() {
            // maxRps = 2, latency = 1000ms
            // Max sustainable = 2 * 1000 / 1000 = 2
            int concurrency = calculator.computeEffectiveConcurrency(5, 500, 2, 0, 0, 1000);
            assertThat(concurrency).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Effective RPS Calculation")
    class EffectiveRpsTests {

        @Test
        @DisplayName("Zero delay gives max RPS")
        void zeroDelay_givesMaxRps() {
            double rps = calculator.computeEffectiveRps(0, 1);
            assertThat(rps).isEqualTo(Double.MAX_VALUE);
        }

        @Test
        @DisplayName("1000ms delay with 1 worker = 1 RPS")
        void sequential_1000msDelay() {
            double rps = calculator.computeEffectiveRps(1000, 1);
            assertThat(rps).isEqualTo(1.0);
        }

        @Test
        @DisplayName("500ms delay with 1 worker = 2 RPS")
        void sequential_500msDelay() {
            double rps = calculator.computeEffectiveRps(500, 1);
            assertThat(rps).isEqualTo(2.0);
        }

        @Test
        @DisplayName("1000ms delay with 3 workers = 3 RPS")
        void concurrent_1000msDelay() {
            double rps = calculator.computeEffectiveRps(1000, 3);
            assertThat(rps).isEqualTo(3.0);
        }
    }

    @Nested
    @DisplayName("Rate Limit Clamping")
    class RateLimitClampingTests {

        @Test
        @DisplayName("RPS clamped to maxRps")
        void clampToMaxRps() {
            double rps = calculator.clampToRateLimits(10.0, 5.0, 0, 0);
            assertThat(rps).isEqualTo(5.0);
        }

        @Test
        @DisplayName("RPS clamped to maxRpm")
        void clampToMaxRpm() {
            // 10 RPS, max 120 RPM = 2 RPS
            double rps = calculator.clampToRateLimits(10.0, 0, 120, 0);
            assertThat(rps).isEqualTo(2.0);
        }

        @Test
        @DisplayName("RPS clamped to maxRph")
        void clampToMaxRph() {
            // 10 RPS, max 3600 RPH = 1 RPS
            double rps = calculator.clampToRateLimits(10.0, 0, 0, 3600);
            assertThat(rps).isEqualTo(1.0);
        }

        @Test
        @DisplayName("No clamping when under limits")
        void noClamping_whenUnderLimits() {
            double rps = calculator.clampToRateLimits(1.0, 10.0, 600, 36000);
            assertThat(rps).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Estimated Duration Calculation")
    class EstimatedDurationTests {

        @Test
        @DisplayName("Zero samples gives zero duration")
        void zeroSamples_zeroDuration() {
            long duration = calculator.computeEstimatedDuration(0, 1.0, 0);
            assertThat(duration).isEqualTo(0);
        }

        @Test
        @DisplayName("100 samples at 1 RPS = 100 seconds")
        void hundredSamples_atOneRps() {
            long duration = calculator.computeEstimatedDuration(100, 1.0, 0);
            assertThat(duration).isEqualTo(100_000);
        }

        @Test
        @DisplayName("200 samples at 60 RPM = 200 seconds")
        void twoHundredSamples_atSixtyRpm() {
            // 60 RPM = 1 RPS
            long duration = calculator.computeEstimatedDuration(200, 1.0, 0);
            assertThat(duration).isEqualTo(200_000);
        }

        @Test
        @DisplayName("Uses latency estimate when no rate limit")
        void usesLatency_whenNoRateLimit() {
            long duration = calculator.computeEstimatedDuration(100, Double.MAX_VALUE, 500);
            assertThat(duration).isEqualTo(50_000); // 100 * 500ms
        }
    }

    @Nested
    @DisplayName("Full Computation")
    class FullComputationTests {

        @Test
        @DisplayName("Null pacing returns noPacing configuration")
        void nullPacing_returnsNoPacing() {
            PacingConfiguration config = calculator.compute(100, null);
            
            assertThat(config.hasPacing()).isFalse();
            assertThat(config.effectiveMinDelayMs()).isEqualTo(0);
            assertThat(config.effectiveConcurrency()).isEqualTo(1);
        }

        @Test
        @DisplayName("60 RPM with 200 samples computes correctly")
        void sixtyRpm_twoHundredSamples() {
            PacingConfiguration config = calculator.compute(
                    200, 0, 60, 0, 0, 0, 0);

            assertThat(config.hasPacing()).isTrue();
            assertThat(config.effectiveMinDelayMs()).isEqualTo(1000);
            assertThat(config.effectiveConcurrency()).isEqualTo(1);
            assertThat(config.effectiveRps()).isEqualTo(1.0);
            assertThat(config.estimatedDurationMs()).isEqualTo(200_000); // 3m 20s
        }

        @Test
        @DisplayName("Combined constraints with concurrency")
        void combinedConstraints_withConcurrency() {
            // 60 RPM, 3 concurrent, 200 samples
            PacingConfiguration config = calculator.compute(
                    200, 0, 60, 0, 3, 0, 0);

            assertThat(config.hasPacing()).isTrue();
            assertThat(config.effectiveMinDelayMs()).isEqualTo(1000);
            assertThat(config.effectiveConcurrency()).isEqualTo(3);
            // With 3 workers at 1000ms delay, effective RPS = 3
            // But clamped to 60 RPM = 1 RPS
            assertThat(config.effectiveRps()).isEqualTo(1.0);
            assertThat(config.estimatedDurationMs()).isEqualTo(200_000);
        }

        @Test
        @DisplayName("minMsPerSample only")
        void minMsPerSampleOnly() {
            PacingConfiguration config = calculator.compute(
                    100, 0, 0, 0, 0, 500, 0);

            assertThat(config.hasPacing()).isTrue();
            assertThat(config.effectiveMinDelayMs()).isEqualTo(500);
            assertThat(config.effectiveConcurrency()).isEqualTo(1);
            assertThat(config.effectiveRps()).isEqualTo(2.0);
            assertThat(config.estimatedDurationMs()).isEqualTo(50_000);
        }
    }

    @Nested
    @DisplayName("Pacing Annotation Integration")
    class PacingAnnotationTests {

        @Test
        @DisplayName("Computes from Pacing annotation")
        void computesFromAnnotation() {
            Pacing pacing = createPacing(0, 60, 0, 0, 0);
            PacingConfiguration config = calculator.compute(100, pacing);

            assertThat(config.hasPacing()).isTrue();
            assertThat(config.maxRequestsPerMinute()).isEqualTo(60);
            assertThat(config.effectiveMinDelayMs()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Handles all annotation fields")
        void handlesAllFields() {
            Pacing pacing = createPacing(2.0, 120, 7200, 3, 250);
            PacingConfiguration config = calculator.compute(100, pacing);

            assertThat(config.maxRequestsPerSecond()).isEqualTo(2.0);
            assertThat(config.maxRequestsPerMinute()).isEqualTo(120);
            assertThat(config.maxRequestsPerHour()).isEqualTo(7200);
            assertThat(config.maxConcurrentRequests()).isEqualTo(3);
            assertThat(config.minMsPerSample()).isEqualTo(250);

            // Most restrictive: 2 RPS = 500ms
            assertThat(config.effectiveMinDelayMs()).isEqualTo(500);
        }
    }

    // Helper to create a Pacing annotation instance
    private Pacing createPacing(double rps, double rpm, double rph, int concurrent, long minMs) {
        return new Pacing() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Pacing.class;
            }

            @Override
            public double maxRequestsPerSecond() {
                return rps;
            }

            @Override
            public double maxRequestsPerMinute() {
                return rpm;
            }

            @Override
            public double maxRequestsPerHour() {
                return rph;
            }

            @Override
            public int maxConcurrentRequests() {
                return concurrent;
            }

            @Override
            public long minMsPerSample() {
                return minMs;
            }
        };
    }
}


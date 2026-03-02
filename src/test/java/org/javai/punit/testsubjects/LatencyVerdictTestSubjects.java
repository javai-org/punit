package org.javai.punit.testsubjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.api.Latency;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;

/**
 * Test subject classes for latency verdict integration tests:
 * verbose (transparent stats) output and feasibility gate.
 * These classes are used by LatencyVerdictIntegrationTest via TestKit
 * and are NOT meant to be run directly.
 */
public class LatencyVerdictTestSubjects {

    private LatencyVerdictTestSubjects() {}

    /**
     * Test with transparent stats enabled and latency thresholds.
     * Should pass and produce verbose output including LATENCY ANALYSIS section.
     */
    public static class TransparentStatsWithLatencyTest {
        @ProbabilisticTest(
                samples = 10,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                transparentStats = true,
                latency = @Latency(p95Ms = 5000, p99Ms = 10000)
        )
        void fastExecutionWithVerboseLatency() {
            // Sub-millisecond — passes both pass rate and latency
            assertThat(true).isTrue();
        }
    }

    /**
     * VERIFICATION intent with p99 latency assertion and 50 samples.
     * Pass-rate feasibility: N_min ≈ 11 for p₀=0.8 at 95% confidence → feasible.
     * Latency feasibility: expected successes = floor(50 * 0.8) = 40 &lt; 100 (p99 minimum) → infeasible.
     * Should fail with ExtensionConfigurationException from the latency gate.
     */
    public static class VerificationUndersizedLatencyTest {
        @ProbabilisticTest(
                samples = 50,
                minPassRate = 0.8,
                intent = TestIntent.VERIFICATION,
                latency = @Latency(p99Ms = 10000)
        )
        void shouldNotExecute() {
            assertThat(true).isTrue();
        }
    }

    /**
     * SMOKE intent with the same undersized p99 assertion.
     * SMOKE bypasses the feasibility gate, so this should run and pass.
     */
    public static class SmokeUndersizedLatencyTest {
        @ProbabilisticTest(
                samples = 5,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                latency = @Latency(p99Ms = 10000)
        )
        void shouldExecuteNormally() {
            assertThat(true).isTrue();
        }
    }

    /**
     * Test with a latency threshold that will be breached (p95 = 0ms on a test that sleeps).
     * In advisory mode (default), the test should pass despite the latency breach.
     */
    public static class AdvisoryLatencyBreachTest {
        @ProbabilisticTest(
                samples = 5,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                latency = @Latency(p95Ms = 0)
        )
        void shouldBreachLatencyButPassInAdvisoryMode() throws InterruptedException {
            Thread.sleep(5);
            assertThat(true).isTrue();
        }
    }
}

package org.javai.punit.testsubjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.api.Latency;
import org.javai.punit.api.legacy.ProbabilisticTest;
import org.javai.punit.api.TestIntent;

/**
 * Test subject classes for latency assertion integration tests.
 * These classes are used by LatencyIntegrationTest via TestKit
 * and are NOT meant to be run directly.
 */
public class LatencyTestSubjects {

    private LatencyTestSubjects() {}

    /**
     * Test that always passes with very fast execution — latency thresholds should pass.
     */
    public static class LatencyPassingTest {
        @ProbabilisticTest(
                samples = 10,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                latency = @Latency(p95Ms = 5000, p99Ms = 10000)
        )
        void fastExecution() {
            // Sub-millisecond execution — all latency thresholds should pass
            assertThat(true).isTrue();
        }
    }

    /**
     * Test with impossibly tight latency threshold — should fail on latency.
     * Note: Even sub-ms operations will often register 0ms or 1ms, so a threshold
     * of 0ms should cause a failure when any sample takes >= 1ms.
     * We use a small sleep to guarantee measurable latency.
     */
    public static class LatencyFailingTest {
        @ProbabilisticTest(
                samples = 5,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                latency = @Latency(p50Ms = 0)
        )
        void slowExecution() throws InterruptedException {
            // Sleep 10ms to guarantee measurable latency above threshold
            Thread.sleep(10);
            assertThat(true).isTrue();
        }
    }

    /**
     * Test with no latency thresholds — should behave as before.
     */
    public static class NoLatencyTest {
        @ProbabilisticTest(
                samples = 10,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE
        )
        void noLatencyAsserted() {
            assertThat(true).isTrue();
        }
    }
}

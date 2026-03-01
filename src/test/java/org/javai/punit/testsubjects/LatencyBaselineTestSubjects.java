package org.javai.punit.testsubjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.api.Latency;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.UseCase;

/**
 * Test subject classes for latency baseline integration tests (Phase 3).
 * These classes are used by LatencyBaselineIntegrationTest via TestKit
 * and are NOT meant to be run directly.
 */
public class LatencyBaselineTestSubjects {

    private LatencyBaselineTestSubjects() {}

    /**
     * Use case class that maps to the LatencyBaselineUseCase.yaml spec file.
     */
    @UseCase("LatencyBaselineUseCase")
    public static class LatencyBaselineUseCase {}

    /**
     * Test with latencyBaseline=true — thresholds derived purely from baseline.
     * The spec has p95=750ms and stddev=120, so derived upper bound will be slightly above 750.
     * Our fast test execution should easily pass.
     */
    public static class BaselineOnlyTest {
        @ProbabilisticTest(
                samples = 10,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                useCase = LatencyBaselineUseCase.class,
                latencyBaseline = true
        )
        void fastExecutionWithBaselineThresholds() {
            // Sub-millisecond — well within any baseline-derived threshold
            assertThat(true).isTrue();
        }
    }

    /**
     * Test with mixed mode: explicit p99 ceiling + baseline fills the rest.
     * Explicit p99=5000ms is looser than baseline-derived, so baseline should win for p99.
     * All other percentiles come from baseline.
     */
    public static class MixedModeTest {
        @ProbabilisticTest(
                samples = 10,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                useCase = LatencyBaselineUseCase.class,
                latency = @Latency(p99Ms = 5000)
        )
        void mixedModeExecution() {
            // Sub-millisecond — well within thresholds
            assertThat(true).isTrue();
        }
    }

    /**
     * Test with latencyBaseline=true but NO use case (no spec) — should fail with config error.
     */
    public static class BaselineWithoutSpecTest {
        @ProbabilisticTest(
                samples = 5,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                latencyBaseline = true
        )
        void shouldFailBecauseNoBaseline() {
            assertThat(true).isTrue();
        }
    }
}

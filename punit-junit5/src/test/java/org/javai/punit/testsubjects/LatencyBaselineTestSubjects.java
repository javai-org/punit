package org.javai.punit.testsubjects;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.api.Latency;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.UseCase;

/**
 * Test subject classes for latency baseline integration tests.
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
     * Test with automatic baseline-derived latency thresholds.
     * The spec has latency data, so PUnit derives thresholds automatically.
     * Our fast test execution should easily pass.
     */
    public static class BaselineOnlyTest {
        @ProbabilisticTest(
                samples = 10,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                useCase = LatencyBaselineUseCase.class
        )
        void fastExecutionWithBaselineThresholds() {
            // Sub-millisecond — well within any baseline-derived threshold
            assertThat(true).isTrue();
        }
    }

    /**
     * Test with latency disabled via @Latency(disabled = true).
     * Even though the baseline has latency data, no latency assertions are made.
     */
    public static class DisabledLatencyTest {
        @ProbabilisticTest(
                samples = 10,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                useCase = LatencyBaselineUseCase.class,
                latency = @Latency(disabled = true)
        )
        void disabledLatencyExecution() {
            // Latency assertions are suppressed
            assertThat(true).isTrue();
        }
    }

    /**
     * Test with explicit @Latency thresholds AND a baseline that has latency data.
     * Explicit thresholds override baseline-derived thresholds.
     */
    public static class ExplicitWithBaselineTest {
        @ProbabilisticTest(
                samples = 10,
                minPassRate = 0.8,
                intent = TestIntent.SMOKE,
                useCase = LatencyBaselineUseCase.class,
                latency = @Latency(p95Ms = 500)
        )
        void explicitOverridesBaseline() {
            // Sub-millisecond — well within the explicit 500ms p95 threshold
            assertThat(true).isTrue();
        }
    }
}

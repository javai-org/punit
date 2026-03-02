package org.javai.punit.testsubjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.UseCase;

/**
 * Test subject classes for baseline-derived threshold integration tests.
 * These verify that the verdict is correct when minPassRate is derived
 * from a baseline spec rather than specified explicitly on the annotation.
 *
 * <p>These classes are used by BaselineDerivedThresholdIntegrationTest via TestKit
 * and are NOT meant to be run directly.
 */
public class BaselineDerivedThresholdSubjects {

    private BaselineDerivedThresholdSubjects() {}

    /**
     * Use case class that maps to the BaselineDerivedThresholdUseCase.yaml spec file.
     * The spec declares minPassRate: 0.8000.
     */
    @UseCase("BaselineDerivedThresholdUseCase")
    public static class BaselineDerivedThresholdUseCase {}

    /**
     * Always-passing test with NO explicit minPassRate — threshold is derived from baseline.
     * The baseline spec declares minPassRate=0.80, and all samples pass (100% >= 80%),
     * so the verdict MUST be PASS.
     *
     * <p>Before the fix, BernoulliTrialsConfig retained NaN, causing an unconditional
     * FAIL verdict even though the display showed the correct derived threshold and
     * a passing rate.
     */
    public static class AlwaysPassingWithDerivedThresholdTest {
        @ProbabilisticTest(
                samples = 10,
                intent = TestIntent.SMOKE,
                useCase = BaselineDerivedThresholdUseCase.class
        )
        void shouldPassWhenAllSamplesSucceed() {
            assertThat(true).isTrue();
        }
    }

    /**
     * Partially-failing test with NO explicit minPassRate — threshold derived from baseline.
     * The baseline spec declares minPassRate=0.80. This test passes 70% of samples (7/10),
     * which is below the 80% threshold, so the verdict MUST be FAIL.
     */
    public static class BarelyFailingWithDerivedThresholdTest {
        private static final AtomicInteger counter = new AtomicInteger(0);

        public static void resetCounter() {
            counter.set(0);
        }

        @ProbabilisticTest(
                samples = 10,
                intent = TestIntent.SMOKE,
                useCase = BaselineDerivedThresholdUseCase.class
        )
        void shouldFailWhenBelowDerivedThreshold() {
            int count = counter.incrementAndGet();
            // Fail on 8, 9, 10 → 70% pass rate, below derived 80% threshold
            assertThat(count <= 7).isTrue();
        }
    }
}

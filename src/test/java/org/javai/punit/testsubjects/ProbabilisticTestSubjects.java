package org.javai.punit.testsubjects;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.atomic.AtomicInteger;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ProbabilisticTestBudget;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.TokenChargeRecorder;

/**
 * Test subject classes for integration tests.
 * These classes are used by ProbabilisticTestIntegrationTest via TestKit
 * and are NOT meant to be run directly.
 * 
 * Note: These are intentionally not annotated with @Nested or any JUnit
 * container annotations to avoid direct discovery.
 */
public class ProbabilisticTestSubjects {

    // Prevent instantiation
    private ProbabilisticTestSubjects() {}

    public static class AlwaysPassingTest {
        @ProbabilisticTest(samples = 10, minPassRate = 0.8)
        void alwaysPasses() {
            assertThat(true).isTrue();
        }
    }

    public static class AlwaysFailingTest {
        @ProbabilisticTest(samples = 10, minPassRate = 0.8)
        void alwaysFails() {
            assertThat(false).isTrue();
        }
    }

    public static class PartiallyFailingTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        
        public static void resetCounter() {
            counter.set(0);
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.5)
        void failsHalf() {
            int count = counter.incrementAndGet();
            // Fail on odd numbers (1, 3, 5, 7, 9) = 50% failure rate
            assertThat(count % 2 == 0).isTrue();
        }
    }

    public static class BarelyPassingTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        
        public static void resetCounter() {
            counter.set(0);
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.8)
        void passes80Percent() {
            int count = counter.incrementAndGet();
            // Fail on 9, 10 = 80% pass rate (exactly meets threshold)
            assertThat(count <= 8).isTrue();
        }
    }

    public static class BarelyFailingTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        
        public static void resetCounter() {
            counter.set(0);
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.8)
        void passes70Percent() {
            int count = counter.incrementAndGet();
            // Fail on 8, 9, 10 = 70% pass rate (below 80% threshold)
            assertThat(count <= 7).isTrue();
        }
    }

    public static class ExceptionThrowingTest {
        @ProbabilisticTest(samples = 5, minPassRate = 1.0)
        void throwsException() {
            throw new RuntimeException("Unexpected exception");
        }
    }

    public static class MinPassRateZeroTest {
        @ProbabilisticTest(samples = 10, minPassRate = 0.0)
        void canFailCompletely() {
            assertThat(false).isTrue();
        }
    }

    public static class SingleSampleTest {
        @ProbabilisticTest(samples = 1, minPassRate = 1.0)
        void singleSamplePasses() {
            assertThat(true).isTrue();
        }
    }

    // ========== Phase 5: Budget Scope Test Subjects ==========

    /**
     * Test class with class-level token budget that will be exhausted.
     */
    @ProbabilisticTestBudget(tokenBudget = 100)
    public static class ClassTokenBudgetTest {
        private static final AtomicInteger tokensRecorded = new AtomicInteger(0);

        public static void reset() {
            tokensRecorded.set(0);
        }

        public static int getTotalTokensRecorded() {
            return tokensRecorded.get();
        }

        @ProbabilisticTest(samples = 10, minPassRate = 0.5)
        void consumesTokens(TokenChargeRecorder recorder) {
            // Each sample consumes 30 tokens; after 4 samples we'll hit 120 > 100
            recorder.recordTokens(30);
            tokensRecorded.addAndGet(30);
            assertThat(true).isTrue();
        }
    }

    /**
     * Test class with class-level time budget.
     */
    @ProbabilisticTestBudget(timeBudgetMs = 50)
    public static class ClassTimeBudgetTest {
        private static final AtomicInteger samplesExecuted = new AtomicInteger(0);

        public static void reset() {
            samplesExecuted.set(0);
        }

        public static int getSamplesExecuted() {
            return samplesExecuted.get();
        }

        @ProbabilisticTest(samples = 100, minPassRate = 0.5)
        void slowTest() throws InterruptedException {
            samplesExecuted.incrementAndGet();
            Thread.sleep(20); // Each sample takes 20ms
            assertThat(true).isTrue();
        }
    }

    /**
     * Test class with EVALUATE_PARTIAL behavior.
     */
    @ProbabilisticTestBudget(tokenBudget = 50, onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL)
    public static class EvaluatePartialBudgetTest {
        @ProbabilisticTest(samples = 10, minPassRate = 0.5)
        void consumesTokens(TokenChargeRecorder recorder) {
            recorder.recordTokens(30);
            assertThat(true).isTrue();
        }
    }

    // ========== Phase 3: Configuration Override Test Subjects ==========

    /**
     * Test with small samples that can be scaled by multiplier.
     * Used to verify system property overrides work.
     */
    public static class ConfigurableSamplesTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        
        public static void resetCounter() {
            counter.set(0);
        }
        
        public static int getSamplesExecuted() {
            return counter.get();
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.5)
        void configurableTest() {
            counter.incrementAndGet();
            assertThat(true).isTrue();
        }
    }

    // ========== Phase 2: Early Termination Test Subjects ==========

    /**
     * Test that should trigger early termination due to impossibility.
     * With 100 samples and 95% required, after 6 consecutive failures
     * it becomes impossible to reach 95 successes (max possible = 94).
     */
    public static class EarlyTerminationByImpossibilityTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        private static int samplesActuallyExecuted = 0;
        
        public static void resetCounter() {
            counter.set(0);
            samplesActuallyExecuted = 0;
        }
        
        public static int getSamplesActuallyExecuted() {
            return samplesActuallyExecuted;
        }
        
        @ProbabilisticTest(samples = 100, minPassRate = 0.95)
        void triggersEarlyTermination() {
            samplesActuallyExecuted = counter.incrementAndGet();
            // Always fails - should terminate after 6 failures
            assertThat(false).isTrue();
        }
    }

    /**
     * Test with 100% pass rate requirement - should terminate on first failure.
     */
    public static class TerminateOnFirstFailureTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        private static int samplesActuallyExecuted = 0;
        
        public static void resetCounter() {
            counter.set(0);
            samplesActuallyExecuted = 0;
        }
        
        public static int getSamplesActuallyExecuted() {
            return samplesActuallyExecuted;
        }
        
        @ProbabilisticTest(samples = 100, minPassRate = 1.0)
        void terminatesOnFirstFailure() {
            samplesActuallyExecuted = counter.incrementAndGet();
            // First sample fails
            assertThat(false).isTrue();
        }
    }

    /**
     * Test that passes before all samples are executed.
     * Even though some samples fail, the test should still run all samples
     * if it's still possible to pass (no early termination for passing tests).
     */
    public static class PassesWithSomeFailuresTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        
        public static void resetCounter() {
            counter.set(0);
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.7)
        void passesWithThreeFailures() {
            int count = counter.incrementAndGet();
            // Fail on 1, 2, 3 = 70% pass rate (exactly meets 70% threshold)
            assertThat(count > 3).isTrue();
        }
    }

    /**
     * Test that triggers SUCCESS_GUARANTEED early termination.
     * With 50% pass rate required on 10 samples, after 5 consecutive successes,
     * we've already guaranteed success and can skip the remaining 5 samples.
     */
    public static class SuccessGuaranteedEarlyTerminationTest {
        private static final AtomicInteger counter = new AtomicInteger(0);
        private static int samplesActuallyExecuted = 0;
        
        public static void resetCounter() {
            counter.set(0);
            samplesActuallyExecuted = 0;
        }
        
        public static int getSamplesActuallyExecuted() {
            return samplesActuallyExecuted;
        }
        
        @ProbabilisticTest(samples = 10, minPassRate = 0.5)
        void alwaysPasses() {
            samplesActuallyExecuted = counter.incrementAndGet();
            // All samples pass - after 5 successes, we've met the 50% threshold
            assertThat(true).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROVENANCE TEST SUBJECTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Test with both thresholdOrigin and contractRef specified.
     */
    public static class ProvenanceWithBothTest {
        @ProbabilisticTest(
            samples = 5,
            minPassRate = 0.8,
            thresholdOrigin = ThresholdOrigin.SLA,
            contractRef = "Acme API SLA v3.2 §2.1"
        )
        void testWithFullProvenance() {
            assertThat(true).isTrue();
        }
    }

    /**
     * Test with only thresholdOrigin specified.
     */
    public static class ProvenanceThresholdOriginOnlyTest {
        @ProbabilisticTest(
            samples = 5,
            minPassRate = 0.8,
            thresholdOrigin = ThresholdOrigin.SLO
        )
        void testWithThresholdOriginOnly() {
            assertThat(true).isTrue();
        }
    }

    /**
     * Test with only contractRef specified.
     */
    public static class ProvenanceContractRefOnlyTest {
        @ProbabilisticTest(
            samples = 5,
            minPassRate = 0.8,
            contractRef = "Internal Policy DOC-001"
        )
        void testWithContractRefOnly() {
            assertThat(true).isTrue();
        }
    }

    /**
     * Test with explicit UNSPECIFIED thresholdOrigin (should not print provenance).
     */
    public static class ProvenanceUnspecifiedTest {
        @ProbabilisticTest(
            samples = 5,
            minPassRate = 0.8,
            thresholdOrigin = ThresholdOrigin.UNSPECIFIED
        )
        void testWithUnspecifiedSource() {
            assertThat(true).isTrue();
        }
    }

    /**
     * Test each target source value.
     */
    public static class ProvenanceSlaSourceTest {
        @ProbabilisticTest(samples = 3, minPassRate = 0.6, thresholdOrigin = ThresholdOrigin.SLA)
        void testSlaSource() {
            assertThat(true).isTrue();
        }
    }

    public static class ProvenanceSloSourceTest {
        @ProbabilisticTest(samples = 3, minPassRate = 0.6, thresholdOrigin = ThresholdOrigin.SLO)
        void testSloSource() {
            assertThat(true).isTrue();
        }
    }

    public static class ProvenancePolicySourceTest {
        @ProbabilisticTest(samples = 3, minPassRate = 0.6, thresholdOrigin = ThresholdOrigin.POLICY)
        void testPolicySource() {
            assertThat(true).isTrue();
        }
    }

    public static class ProvenanceEmpiricalSourceTest {
        @ProbabilisticTest(samples = 3, minPassRate = 0.6, thresholdOrigin = ThresholdOrigin.EMPIRICAL)
        void testEmpiricalSource() {
            assertThat(true).isTrue();
        }
    }
}


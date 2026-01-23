package org.javai.punit;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.AlwaysFailingTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.AlwaysPassingTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.BarelyFailingTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.BarelyPassingTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ClassTimeBudgetTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ClassTokenBudgetTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ConfigurableSamplesTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.EarlyTerminationByImpossibilityTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.EvaluatePartialBudgetTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ExceptionThrowingTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.MinPassRateZeroTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.PartiallyFailingTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.PassesWithSomeFailuresTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.SingleSampleTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.SuccessGuaranteedEarlyTerminationTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.TerminateOnFirstFailureTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests for the probabilistic testing framework.
 * 
 * These tests use JUnit Platform TestKit to execute and verify
 * probabilistic tests defined in the testsubjects package.
 */
class ProbabilisticTestIntegrationTest {

	public static final String JUNIT_ENGINE_ID = "junit-jupiter";

	@Test
    void alwaysPassingTestPasses() {
        // With 80% of 10 = 8 required, after 8 consecutive successes
        // SUCCESS_GUARANTEED triggers and we skip remaining 2 samples
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(AlwaysPassingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(8)
                        .succeeded(8)
                        .failed(0));
    }

    @Test
    void alwaysFailingTestFails() {
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(AlwaysFailingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // With 80% of 10 = 8 required, after 3 failures max possible = 0 + 7 = 7 < 8
                        // So early termination kicks in after 3 samples
                        .started(3)
                        .failed(3));   // All 3 samples fail (❌), 3rd has final verdict
    }

    @Test
    void partiallyFailingTestWithLowThresholdPasses() {
        PartiallyFailingTest.resetCounter();
        
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(PartiallyFailingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(10)
                        .succeeded(5)   // 5 samples passed (even numbers)
                        .failed(5));    // 5 samples failed (❌), overall test passes (50% >= 50%)
    }

    @Test
    void barelyPassingTestPasses() {
        BarelyPassingTest.resetCounter();
        
        // With 80% of 10 = 8 required, after 8 consecutive successes
        // SUCCESS_GUARANTEED triggers before we reach samples 9-10 that would fail
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(BarelyPassingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(8)
                        .succeeded(8)   // Samples 1-8 pass, then early termination
                        .failed(0));    // Samples 9-10 never run
    }

    @Test
    void barelyFailingTestFails() {
        BarelyFailingTest.resetCounter();
        
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(BarelyFailingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(10)
                        .succeeded(7)   // Samples 1-7 pass
                        .failed(3));    // Samples 8-10 fail (❌), sample 10 has final verdict (70% < 80%)
    }

    @Test
    void exceptionsTreatedAsFailures() {
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(ExceptionThrowingTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // With 100% of 5 = 5 required, after 1 failure max possible = 0 + 4 = 4 < 5
                        // So early termination kicks in after 1 sample
                        .started(1)
                        .failed(1));   // First sample fails, terminates immediately
    }

    @Test
    void minPassRateZeroTerminatesAfterFirstSample() {
        // With minPassRate=0, we need 0 successes, so SUCCESS_GUARANTEED triggers
        // after the first sample (whether it passes or fails).
        // When SUCCESS_GUARANTEED triggers, we don't re-throw sample failures
        // because the test has already passed.
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(MinPassRateZeroTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(1)     // Only 1 sample runs (SUCCESS_GUARANTEED after first)
                        .succeeded(1)   // Sample completes without throwing (test passed)
                        .failed(0));
    }

    @Test
    void singleSampleTestWorks() {
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(SingleSampleTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .started(1)
                        .succeeded(1)
                        .failed(0));
    }

    // ========== Phase 3: Configuration Override Tests ==========

    @Test
    void systemPropertyOverridesSamples() {
        ConfigurableSamplesTest.resetCounter();
        
        // Set system property to override samples from 10 to 5
        // With 50% of 5 = 3 required, after 3 successes SUCCESS_GUARANTEED triggers
        System.setProperty("punit.samples", "5");
        try {
            EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(ConfigurableSamplesTest.class))
                    .execute()
                    .testEvents()
                    .assertStatistics(stats -> stats
                            .started(3)  // SUCCESS_GUARANTEED after 3 successes
                            .succeeded(3)
                            .failed(0));
            
            org.assertj.core.api.Assertions.assertThat(
                    ConfigurableSamplesTest.getSamplesExecuted())
                    .isEqualTo(3);
        } finally {
            System.clearProperty("punit.samples");
        }
    }

    @Test
    void samplesMultiplierScalesSamples() {
        ConfigurableSamplesTest.resetCounter();
        
        // Set multiplier to 0.5 (halves the samples from 10 to 5)
        // With 50% of 5 = 3 required, after 3 successes SUCCESS_GUARANTEED triggers
        System.setProperty("punit.samplesMultiplier", "0.5");
        try {
            EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(ConfigurableSamplesTest.class))
                    .execute()
                    .testEvents()
                    .assertStatistics(stats -> stats
                            .started(3)  // SUCCESS_GUARANTEED after 3 successes
                            .succeeded(3)
                            .failed(0));
            
            org.assertj.core.api.Assertions.assertThat(
                    ConfigurableSamplesTest.getSamplesExecuted())
                    .isEqualTo(3);
        } finally {
            System.clearProperty("punit.samplesMultiplier");
        }
    }

    @Test
    void minPassRateOverrideWorks() {
        BarelyFailingTest.resetCounter();
        
        // BarelyFailingTest passes 70% but requires 80%, so it fails
        // Override minPassRate to 0.6 so it should pass
        // With 60% of 10 = 6 required, after 6 successes SUCCESS_GUARANTEED triggers
        System.setProperty("punit.minPassRate", "0.6");
        try {
            EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(BarelyFailingTest.class))
                    .execute()
                    .testEvents()
                    .assertStatistics(stats -> stats
                            .started(6)     // SUCCESS_GUARANTEED after 6 successes
                            .succeeded(6)   // Samples 1-6 pass
                            .failed(0));    // Samples 7-10 never run
        } finally {
            System.clearProperty("punit.minPassRate");
        }
    }

    // ========== Phase 2: Early Termination Tests ==========

    @Test
    void earlyTerminationByImpossibility() {
        EarlyTerminationByImpossibilityTest.resetCounter();
        
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(EarlyTerminationByImpossibilityTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // With 95% of 100 = 95 required, after 6 failures max possible = 94
                        // So only 6 samples should run, not 100
                        .started(6)
                        .failed(6)); // All 6 samples fail (❌), 6th has final verdict
        
        // Verify only 6 samples actually executed
        org.assertj.core.api.Assertions.assertThat(
                EarlyTerminationByImpossibilityTest.getSamplesActuallyExecuted())
                .isEqualTo(6);
    }

    @Test
    void terminatesOnFirstFailureWith100PercentRequired() {
        TerminateOnFirstFailureTest.resetCounter();
        
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(TerminateOnFirstFailureTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // With 100% required, first failure makes it impossible
                        .started(1)
                        .failed(1));
        
        // Verify only 1 sample actually executed
        org.assertj.core.api.Assertions.assertThat(
                TerminateOnFirstFailureTest.getSamplesActuallyExecuted())
                .isEqualTo(1);
    }

    @Test
    void passesWithSomeFailuresNoEarlyTermination() {
        PassesWithSomeFailuresTest.resetCounter();
        
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(PassesWithSomeFailuresTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // All 10 samples should run (no early termination for passing tests)
                        .started(10)
                        .succeeded(7)   // Samples 4-10 pass
                        .failed(3));    // Samples 1-3 fail (❌), overall passes (70% >= 70%)
    }

    @Test
    void successGuaranteedEarlyTermination() {
        SuccessGuaranteedEarlyTerminationTest.resetCounter();
        
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(SuccessGuaranteedEarlyTerminationTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        // With 50% of 10 = 5 required, after 5 consecutive successes
                        // we've guaranteed success and can skip the remaining 5 samples
                        .started(5)
                        .succeeded(5)
                        .failed(0));
        
        // Verify only 5 samples actually executed (not 10)
        org.assertj.core.api.Assertions.assertThat(
                SuccessGuaranteedEarlyTerminationTest.getSamplesActuallyExecuted())
                .isEqualTo(5);
    }

    // ========== Phase 5: Budget Scope Tests ==========

    @Test
    void classTokenBudgetExhaustion() {
        ClassTokenBudgetTest.reset();
        
        // Class has 100 token budget, each sample uses 30 tokens
        // After 4 samples: 120 tokens > 100 budget
        // With FAIL behavior, test should fail when budget exhausted
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(ClassTokenBudgetTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .failed(1));  // Test fails due to budget exhaustion
        
        // Verify only ~4 samples ran (budget exhausted after 4th sample)
        int tokensRecorded = ClassTokenBudgetTest.getTotalTokensRecorded();
        org.assertj.core.api.Assertions.assertThat(tokensRecorded)
                .isGreaterThanOrEqualTo(90)  // At least 3 samples
                .isLessThanOrEqualTo(150);   // At most 5 samples
    }

    @Test
    void classTimeBudgetExhaustion() {
        ClassTimeBudgetTest.reset();
        
        // Class has 50ms time budget, each sample takes 20ms
        // Should run ~2-3 samples before budget exhausted
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(ClassTimeBudgetTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .failed(1));  // Test fails due to budget exhaustion
        
        // Verify fewer than 100 samples ran
        int samplesExecuted = ClassTimeBudgetTest.getSamplesExecuted();
        org.assertj.core.api.Assertions.assertThat(samplesExecuted)
                .isLessThan(10);  // Much less than 100 samples
    }

    @Test
    void evaluatePartialBehaviorPasses() {
        // With EVALUATE_PARTIAL, test should pass if enough samples passed
        // before budget exhaustion (50 token budget, 30 per sample = 2 samples run)
        // 2/2 = 100% >= 50% required, so test passes
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(EvaluatePartialBudgetTest.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats
                        .succeeded(2)   // 2 samples succeeded before budget exhausted
                        .failed(0));    // No samples failed
    }

    // ========== Sample Failure Message Tests ==========

    @Test
    void sampleFailureMessageShowsReason() {
        // Verify that sample failures show the actual assertion reason (concise)
        // without verbose stack traces or suppressed exceptions
        var events = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(AlwaysFailingTest.class))
                .execute()
                .allEvents()
                .list();

        // Find a sample failure event (not the final verdict)
        var failureEvents = events.stream()
                .filter(e -> e.getPayload(org.junit.platform.engine.TestExecutionResult.class)
                        .map(r -> r.getStatus() == org.junit.platform.engine.TestExecutionResult.Status.FAILED)
                        .orElse(false))
                .toList();

        assertThat(failureEvents).isNotEmpty();

        // Check that at least one failure shows the actual assertion reason
        var hasReasonMessage = failureEvents.stream()
                .map(e -> e.getPayload(org.junit.platform.engine.TestExecutionResult.class).orElseThrow())
                .map(r -> r.getThrowable().orElseThrow())
                .anyMatch(ex -> {
                    String msg = ex.getMessage();
                    // Should contain the actual assertion reason OR PUnit verdict
                    return msg != null && (msg.contains("Expecting") || msg.contains("PUnit"));
                });

        assertThat(hasReasonMessage)
                .as("At least one failure should show the assertion reason or PUnit verdict")
                .isTrue();
    }

    @Test
    void sampleFailureHasNoSuppressedExceptions() {
        // Verify that sample failures don't have suppressed exceptions
        // (which caused verbose nested output)
        var events = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(AlwaysFailingTest.class))
                .execute()
                .allEvents()
                .list();

        // Find sample failures (not final verdict)
        var sampleFailures = events.stream()
                .filter(e -> e.getPayload(org.junit.platform.engine.TestExecutionResult.class)
                        .flatMap(r -> r.getThrowable())
                        .map(t -> !t.getMessage().contains("PUnit"))  // Exclude final verdict
                        .orElse(false))
                .filter(e -> e.getPayload(org.junit.platform.engine.TestExecutionResult.class)
                        .map(r -> r.getStatus() == org.junit.platform.engine.TestExecutionResult.Status.FAILED)
                        .orElse(false))
                .toList();

        // None should have suppressed exceptions
        sampleFailures.forEach(event -> {
            var result = event.getPayload(org.junit.platform.engine.TestExecutionResult.class).orElseThrow();
            var exception = result.getThrowable().orElseThrow();

            assertThat(exception.getSuppressed())
                    .as("Sample failures should not have suppressed exceptions")
                    .isEmpty();
        });
    }
}

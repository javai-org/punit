package org.javai.punit.ptest.bernoulli;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Optional;
import org.javai.punit.model.TerminationReason;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EarlyTerminationEvaluator}.
 */
class EarlyTerminationEvaluatorTest {

    @Test
    void shouldNotTerminateWhenStillPossibleToPass() {
        // 95% of 100 = 95 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.95);
        
        // 90 successes, 5 failures = 95 executed, 5 remaining
        // Max possible = 90 + 5 = 95 >= 95 required, so no termination
        Optional<TerminationReason> result = evaluator.shouldTerminate(90, 95);
        
        assertThat(result).isEmpty();
    }

    @Test
    void shouldTerminateWhenImpossibleToPass() {
        // 95% of 100 = 95 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.95);
        
        // 0 successes, 6 failures = 6 executed, 94 remaining
        // Max possible = 0 + 94 = 94 < 95 required, so terminate
        Optional<TerminationReason> result = evaluator.shouldTerminate(0, 6);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(TerminationReason.IMPOSSIBILITY);
    }

    @Test
    void shouldTerminateOnFirstFailureWhenMinPassRateIsOne() {
        // 100% required = 100 successes out of 100
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 1.0);
        
        // 0 successes, 1 failure = 1 executed, 99 remaining
        // Max possible = 0 + 99 = 99 < 100 required
        Optional<TerminationReason> result = evaluator.shouldTerminate(0, 1);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(TerminationReason.IMPOSSIBILITY);
    }

    @Test
    void shouldNotTerminateForImpossibilityWhenMinPassRateIsZero() {
        // 0% required = 0 successes needed
        // This test verifies that IMPOSSIBILITY is never triggered with minPassRate=0
        // (Note: SUCCESS_GUARANTEED will trigger after the first sample - see separate test)
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.0);
        
        // All failures, all samples complete - normal completion, no early termination
        Optional<TerminationReason> result = evaluator.shouldTerminate(0, 100);
        
        assertThat(result).isEmpty();
    }

    @Test
    void shouldTerminateExactlyAtBoundary() {
        // 80% of 10 = 8 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(10, 0.8);
        
        // 5 successes, 2 failures = 7 executed, 3 remaining
        // Max possible = 5 + 3 = 8 >= 8 required, no termination yet
        assertThat(evaluator.shouldTerminate(5, 7)).isEmpty();
        
        // 5 successes, 3 failures = 8 executed, 2 remaining
        // Max possible = 5 + 2 = 7 < 8 required, terminate!
        assertThat(evaluator.shouldTerminate(5, 8)).isPresent();
    }

    @Test
    void calculateRequiredSuccessesRoundsUp() {
        assertThat(EarlyTerminationEvaluator.calculateRequiredSuccesses(100, 0.95))
                .isEqualTo(95);
        assertThat(EarlyTerminationEvaluator.calculateRequiredSuccesses(10, 0.95))
                .isEqualTo(10); // ceil(9.5) = 10
        assertThat(EarlyTerminationEvaluator.calculateRequiredSuccesses(7, 0.80))
                .isEqualTo(6);  // ceil(5.6) = 6
    }

    @Test
    void getFailuresUntilImpossibilityCalculatedCorrectly() {
        // 80% of 10 = 8 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(10, 0.8);
        
        // At start: 0 successes, 0 executed, 10 remaining
        // Max possible = 0 + 10 = 10, buffer = 10 - 8 = 2
        assertThat(evaluator.getFailuresUntilImpossibility(0, 0)).isEqualTo(2);
        
        // After 1 success: 1 success, 1 executed, 9 remaining
        // Max possible = 1 + 9 = 10, buffer = 10 - 8 = 2
        assertThat(evaluator.getFailuresUntilImpossibility(1, 1)).isEqualTo(2);
        
        // After 2 failures: 0 successes, 2 executed, 8 remaining
        // Max possible = 0 + 8 = 8, buffer = 8 - 8 = 0
        assertThat(evaluator.getFailuresUntilImpossibility(0, 2)).isEqualTo(0);
    }

    @Test
    void buildImpossibilityExplanationIncludesDetails() {
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.95);
        
        String explanation = evaluator.buildImpossibilityExplanation(0, 6);
        
        assertThat(explanation)
                .contains("6 samples")
                .contains("0 successes")
                .contains("94")  // remaining
                .contains("95"); // required
    }

    @Test
    void gettersReturnCorrectValues() {
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.95);
        
        assertThat(evaluator.getTotalSamples()).isEqualTo(100);
        assertThat(evaluator.getRequiredSuccesses()).isEqualTo(95);
    }

    // ========== Success Guaranteed Tests ==========

    @Test
    void shouldTerminateWhenSuccessGuaranteed() {
        // 80% of 10 = 8 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(10, 0.8);
        
        // 8 successes, 0 failures = 8 executed, 2 remaining
        // Already have 8 >= 8 required, success guaranteed!
        Optional<TerminationReason> result = evaluator.shouldTerminate(8, 8);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(TerminationReason.SUCCESS_GUARANTEED);
    }

    @Test
    void shouldTerminateEarlyWhenSuccessGuaranteedWithExcessSuccesses() {
        // 80% of 10 = 8 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(10, 0.8);
        
        // 9 successes, 0 failures = 9 executed, 1 remaining
        // Have 9 > 8 required, success guaranteed!
        Optional<TerminationReason> result = evaluator.shouldTerminate(9, 9);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(TerminationReason.SUCCESS_GUARANTEED);
    }

    @Test
    void shouldNotTerminateSuccessGuaranteedIfAllSamplesComplete() {
        // 80% of 10 = 8 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(10, 0.8);
        
        // 10 successes, 0 failures = 10 executed, 0 remaining
        // All samples done - this is normal completion, not early termination
        Optional<TerminationReason> result = evaluator.shouldTerminate(10, 10);
        
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotTerminateWhenNotYetGuaranteed() {
        // 80% of 10 = 8 required
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(10, 0.8);
        
        // 7 successes, 0 failures = 7 executed, 3 remaining
        // Only 7 successes, need 8, not yet guaranteed
        Optional<TerminationReason> result = evaluator.shouldTerminate(7, 7);
        
        assertThat(result).isEmpty();
    }

    @Test
    void shouldTerminateSuccessGuaranteedWithMinPassRateZeroAfterFirstSample() {
        // 0% required = 0 successes needed
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, 0.0);
        
        // After first sample (even if it fails), 0 >= 0 required, success guaranteed!
        Optional<TerminationReason> result = evaluator.shouldTerminate(0, 1);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(TerminationReason.SUCCESS_GUARANTEED);
    }

    @Test
    void buildSuccessGuaranteedExplanationIncludesDetails() {
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(10, 0.8);
        
        String explanation = evaluator.buildSuccessGuaranteedExplanation(8, 8);
        
        assertThat(explanation)
                .contains("8 samples")
                .contains("8 successes")
                .contains("100.0%")  // pass rate
                .contains("2 remaining"); // skipped samples
    }

    @Test
    void buildExplanationDispatchesToCorrectMethod() {
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(10, 0.8);
        
        String impossibilityExplanation = evaluator.buildExplanation(
                TerminationReason.IMPOSSIBILITY, 0, 3);
        assertThat(impossibilityExplanation).contains("maximum possible successes");
        
        String successExplanation = evaluator.buildExplanation(
                TerminationReason.SUCCESS_GUARANTEED, 8, 8);
        assertThat(successExplanation).contains("already met");
    }

    @Test
    void nanMinPassRateNeverClaimsSuccessGuaranteed() {
        // When minPassRate is NaN (should not happen in normal operation),
        // requiredSuccesses becomes MAX_VALUE, preventing false SUCCESS_GUARANTEED.
        // This is a defensive measure - NaN should be resolved to default before reaching here.
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(100, Double.NaN);
        
        assertThat(evaluator.getRequiredSuccesses()).isEqualTo(Integer.MAX_VALUE);
        
        // The critical bug was: NaN → 0 required → SUCCESS_GUARANTEED after just 1 success.
        // With MAX_VALUE, even 100% success will never trigger SUCCESS_GUARANTEED.
        // It may trigger IMPOSSIBILITY, but that's safer than a false positive success.
        Optional<TerminationReason> result = evaluator.shouldTerminate(1, 1);
        
        // Should NOT be SUCCESS_GUARANTEED (the bug we're preventing)
        assertThat(result).isNotEqualTo(Optional.of(TerminationReason.SUCCESS_GUARANTEED));
    }

    @Test
    void calculateRequiredSuccessesHandlesNaN() {
        // Direct test of the static method
        int result = EarlyTerminationEvaluator.calculateRequiredSuccesses(100, Double.NaN);
        assertThat(result).isEqualTo(Integer.MAX_VALUE);
    }
}


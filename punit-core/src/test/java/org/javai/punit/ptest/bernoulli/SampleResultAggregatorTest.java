package org.javai.punit.ptest.bernoulli;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.model.TerminationReason;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SampleResultAggregator}.
 */
class SampleResultAggregatorTest {

    @Test
    void newAggregatorHasZeroSuccessesAndFailures() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        
        assertThat(aggregator.getSuccesses()).isZero();
        assertThat(aggregator.getFailures()).isZero();
        assertThat(aggregator.getSamplesExecuted()).isZero();
        assertThat(aggregator.getTotalSamples()).isEqualTo(100);
    }

    @Test
    void recordSuccessIncrementsSuccessCount() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        aggregator.recordSuccess();
        aggregator.recordSuccess();
        aggregator.recordSuccess();
        
        assertThat(aggregator.getSuccesses()).isEqualTo(3);
        assertThat(aggregator.getFailures()).isZero();
        assertThat(aggregator.getSamplesExecuted()).isEqualTo(3);
    }

    @Test
    void recordFailureIncrementsFailureCount() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        aggregator.recordFailure(new AssertionError("test"));
        aggregator.recordFailure(new AssertionError("test2"));
        
        assertThat(aggregator.getSuccesses()).isZero();
        assertThat(aggregator.getFailures()).isEqualTo(2);
        assertThat(aggregator.getSamplesExecuted()).isEqualTo(2);
    }

    @Test
    void observedPassRateCalculatedCorrectly() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        
        // 8 successes, 2 failures = 80% pass rate
        for (int i = 0; i < 8; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 2; i++) {
            aggregator.recordFailure(null);
        }
        
        assertThat(aggregator.getObservedPassRate()).isEqualTo(0.8);
    }

    @Test
    void observedPassRateIsZeroWhenNoSamplesExecuted() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        
        assertThat(aggregator.getObservedPassRate()).isZero();
    }

    @Test
    void exampleFailuresAreCapturedUpToMaximum() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100, 3);
        
        aggregator.recordFailure(new AssertionError("failure 1"));
        aggregator.recordFailure(new AssertionError("failure 2"));
        aggregator.recordFailure(new AssertionError("failure 3"));
        aggregator.recordFailure(new AssertionError("failure 4")); // Should not be captured
        aggregator.recordFailure(new AssertionError("failure 5")); // Should not be captured
        
        assertThat(aggregator.getExampleFailures()).hasSize(3);
        assertThat(aggregator.getFailures()).isEqualTo(5);
    }

    @Test
    void nullFailureCauseIsHandledGracefully() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        aggregator.recordFailure(null);
        
        assertThat(aggregator.getFailures()).isEqualTo(1);
        assertThat(aggregator.getExampleFailures()).isEmpty();
    }

    @Test
    void remainingSamplesCalculatedCorrectly() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        
        assertThat(aggregator.getRemainingSamples()).isEqualTo(100);
        
        aggregator.recordSuccess();
        aggregator.recordSuccess();
        aggregator.recordFailure(null);
        
        assertThat(aggregator.getRemainingSamples()).isEqualTo(97);
    }

    @Test
    void elapsedTimeIsPositive() throws InterruptedException {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        Thread.sleep(10);
        
        assertThat(aggregator.getElapsedMs()).isGreaterThan(0);
    }

    // ========== Termination Reason Tests ==========

    @Test
    void terminationReasonIsEmptyByDefault() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        assertThat(aggregator.getTerminationReason()).isEmpty();
    }

    @Test
    void setTerminatedSetsReasonAndDetails() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        aggregator.setTerminated(TerminationReason.IMPOSSIBILITY, "Cannot reach threshold");
        
        assertThat(aggregator.getTerminationReason()).contains(TerminationReason.IMPOSSIBILITY);
        assertThat(aggregator.getTerminationDetails()).isEqualTo("Cannot reach threshold");
    }

    @Test
    void setCompletedSetsCompletedReason() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        aggregator.setCompleted();
        
        assertThat(aggregator.getTerminationReason()).contains(TerminationReason.COMPLETED);
    }

    // ========== Forced Failure Tests ==========

    @Test
    void forcedFailureIsFalseByDefault() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        assertThat(aggregator.isForcedFailure()).isFalse();
    }

    @Test
    void setForcedFailureSetsFlag() {
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        aggregator.setForcedFailure(true);
        
        assertThat(aggregator.isForcedFailure()).isTrue();
    }

    @Test
    void forcedFailureIsIndependentOfTerminationReason() {
        // forcedFailure is set when budget exhaustion occurs with FAIL behavior.
        // It is NOT set for EVALUATE_PARTIAL behavior, even though budget was exhausted.
        // This test verifies the two concepts are independent.
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        // Set termination reason (budget exhausted) but NOT forced failure (EVALUATE_PARTIAL)
        aggregator.setTerminated(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED, "Budget exhausted");
        
        // forcedFailure should still be false
        assertThat(aggregator.isForcedFailure()).isFalse();
        assertThat(aggregator.getTerminationReason()).contains(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED);
    }

    @Test
    void forcedFailureWithBudgetExhaustion() {
        // When budget exhaustion occurs with FAIL behavior, both are set
        SampleResultAggregator aggregator = new SampleResultAggregator(10);
        
        aggregator.setTerminated(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED, "Budget exhausted");
        aggregator.setForcedFailure(true);  // FAIL behavior
        
        assertThat(aggregator.isForcedFailure()).isTrue();
        assertThat(aggregator.getTerminationReason())
                .hasValueSatisfying(r -> assertThat(r.isBudgetExhaustion()).isTrue());
    }
}


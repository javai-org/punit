package org.javai.punit.ptest.bernoulli;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FinalVerdictDecider}.
 */
class FinalVerdictDeciderTest {

    private final FinalVerdictDecider decider = new FinalVerdictDecider();

    @Test
    void isPassingReturnsTrueWhenPassRateMetExactly() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 95; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 5; i++) {
            aggregator.recordFailure(null);
        }
        
        assertThat(decider.isPassing(aggregator, 0.95)).isTrue();
    }

    @Test
    void isPassingReturnsTrueWhenPassRateExceeded() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 98; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 2; i++) {
            aggregator.recordFailure(null);
        }
        
        assertThat(decider.isPassing(aggregator, 0.95)).isTrue();
    }

    @Test
    void isPassingReturnsFalseWhenPassRateNotMet() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 90; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 10; i++) {
            aggregator.recordFailure(null);
        }
        
        assertThat(decider.isPassing(aggregator, 0.95)).isFalse();
    }

    @Test
    void isPassingReturnsTrueWhenMinPassRateIsZero() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 100; i++) {
            aggregator.recordFailure(null);
        }
        
        assertThat(decider.isPassing(aggregator, 0.0)).isTrue();
    }

    @Test
    void isPassingReturnsFalseWhenMinPassRateIsOneAndAnyFailure() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 99; i++) {
            aggregator.recordSuccess();
        }
        aggregator.recordFailure(null);
        
        assertThat(decider.isPassing(aggregator, 1.0)).isFalse();
    }

    @Test
    void calculateRequiredSuccessesRoundsUp() {
        // 95% of 10 = 9.5, should round up to 10
        assertThat(decider.calculateRequiredSuccesses(10, 0.95)).isEqualTo(10);
        
        // 95% of 100 = 95
        assertThat(decider.calculateRequiredSuccesses(100, 0.95)).isEqualTo(95);
        
        // 90% of 100 = 90
        assertThat(decider.calculateRequiredSuccesses(100, 0.90)).isEqualTo(90);
        
        // 80% of 7 = 5.6, should round up to 6
        assertThat(decider.calculateRequiredSuccesses(7, 0.80)).isEqualTo(6);
    }

    @Test
    void buildSuccessMessageIncludesStatistics() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 98; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 2; i++) {
            aggregator.recordFailure(null);
        }
        
        String message = decider.buildSuccessMessage(aggregator, 0.95);
        
        assertThat(message)
                .contains("98.00%")
                .contains("95.00%")
                .contains("98/100");
    }

    @Test
    void buildFailureMessageWithStatisticalContext_includesFullQualification() {
        SampleResultAggregator aggregator = new SampleResultAggregator(100);
        for (int i = 0; i < 87; i++) {
            aggregator.recordSuccess();
        }
        for (int i = 0; i < 13; i++) {
            aggregator.recordFailure(new AssertionError("test failure"));
        }
        
        BernoulliFailureMessages.StatisticalContext context = new BernoulliFailureMessages.StatisticalContext(
                0.95,      // confidence
                0.87,      // observedRate
                87,        // successes
                100,       // samples
                0.916,     // threshold
                0.951,     // baselineRate
                1000,      // baselineSamples
                "json.generation:v3"
        );
        
        String message = decider.buildFailureMessage(aggregator, context);
        
        assertThat(message)
                .contains("PUnit FAILED with 95.0% confidence")
                .contains("alpha=0.050")
                .contains("87.0%")
                .contains("(87/100)")
                .contains("min pass rate=91.6%")
                .contains("Baseline=95.1%")
                .contains("N=1000")
                .contains("spec=json.generation:v3")
                .contains("Samples executed:")
                .contains("Successes:")
                .contains("Failures:");
    }
}


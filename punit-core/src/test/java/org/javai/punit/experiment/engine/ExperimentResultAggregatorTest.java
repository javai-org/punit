package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.javai.outcome.Outcome;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.statistics.BinomialProportionEstimator;
import org.javai.punit.statistics.ProportionEstimate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the empirical baseline pipeline produces correct confidence
 * intervals by using {@link BinomialProportionEstimator} (Wilson score),
 * not the Wald approximation that collapses at boundary proportions.
 */
@DisplayName("ExperimentResultAggregator → BinomialProportionEstimator integration")
class ExperimentResultAggregatorTest {

    private final BinomialProportionEstimator estimator = new BinomialProportionEstimator();

    @Nested
    @DisplayName("Wilson confidence interval for baseline derivation")
    class WilsonConfidenceInterval {

        @Test
        @DisplayName("perfect baseline (200/200) produces lower bound < 1.0, consistent with rule of 3")
        void perfectBaselineProducesLowerBoundBelowOne() {
            ExperimentResultAggregator aggregator = createAggregatorWithSuccesses(200, 200);

            ProportionEstimate ci = estimator.estimate(
                    aggregator.getSuccesses(), aggregator.getSamplesExecuted(), 0.95);

            // Rule of 3: 1 - 3/200 = 0.985
            assertThat(ci.lowerBound())
                    .as("Wilson lower bound for 200/200 should be < 1.0")
                    .isLessThan(1.0);
            assertThat(ci.lowerBound())
                    .as("Wilson lower bound for 200/200 should be close to rule-of-3 estimate")
                    .isCloseTo(0.985, within(0.005));
            assertThat(ci.upperBound())
                    .as("Upper bound should be 1.0")
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("perfect baseline (1000/1000) produces tighter lower bound")
        void perfectBaselineWith1000SamplesProducesTighterBound() {
            ExperimentResultAggregator aggregator = createAggregatorWithSuccesses(1000, 1000);

            ProportionEstimate ci = estimator.estimate(
                    aggregator.getSuccesses(), aggregator.getSamplesExecuted(), 0.95);

            // Rule of 3: 1 - 3/1000 = 0.997
            assertThat(ci.lowerBound()).isLessThan(1.0);
            assertThat(ci.lowerBound()).isCloseTo(0.997, within(0.002));
        }

        @Test
        @DisplayName("mixed results (90/100) produce reasonable interval")
        void mixedResultsProduceReasonableInterval() {
            ExperimentResultAggregator aggregator = createAggregatorWithMixedResults(100, 90);

            ProportionEstimate ci = estimator.estimate(
                    aggregator.getSuccesses(), aggregator.getSamplesExecuted(), 0.95);

            assertThat(ci.lowerBound()).isGreaterThan(0.8).isLessThan(0.9);
            assertThat(ci.upperBound()).isGreaterThan(0.9).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("zero successes (0/100) produces lower bound of 0.0")
        void zeroSuccessesProducesLowerBoundOfZero() {
            ExperimentResultAggregator aggregator = createAggregatorWithMixedResults(100, 0);

            ProportionEstimate ci = estimator.estimate(
                    aggregator.getSuccesses(), aggregator.getSamplesExecuted(), 0.95);

            assertThat(ci.lowerBound()).isEqualTo(0.0);
            assertThat(ci.upperBound()).isLessThan(0.1);
        }
    }

    private ExperimentResultAggregator createAggregatorWithSuccesses(int total, int successes) {
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", total);
        for (int i = 0; i < successes; i++) {
            aggregator.recordSuccess(createPassingOutcome());
        }
        return aggregator;
    }

    private ExperimentResultAggregator createAggregatorWithMixedResults(int total, int successes) {
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", total);
        for (int i = 0; i < successes; i++) {
            aggregator.recordSuccess(createPassingOutcome());
        }
        for (int i = 0; i < total - successes; i++) {
            aggregator.recordException(new AssertionError("fail"));
        }
        return aggregator;
    }

    private static UseCaseOutcome<String> createPassingOutcome() {
        ServiceContract<String, String> contract = ServiceContract
                .<String, String>define()
                .ensure("always passes", s -> Outcome.ok())
                .build();

        return UseCaseOutcome
                .withContract(contract)
                .input("test")
                .execute(s -> "result")
                .build();
    }
}

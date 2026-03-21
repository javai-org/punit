package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExperimentResultAggregator")
class ExperimentResultAggregatorTest {

    @Nested
    @DisplayName("confidence interval")
    class ConfidenceInterval {

        @Test
        @DisplayName("perfect baseline (100% success) produces lower bound < 1.0")
        void perfectBaselineProducesLowerBoundBelowOne() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 200);

            for (int i = 0; i < 200; i++) {
                aggregator.recordSuccess(createPassingOutcome());
            }

            double[] ci = aggregator.getConfidenceInterval95();

            // With 200/200 successes, the Wilson lower bound at 95% is approximately
            // 1 - 3/200 = 0.985 (rule of 3 approximation)
            assertThat(ci[0])
                    .as("Wilson lower bound for 200/200 should be < 1.0")
                    .isLessThan(1.0);
            assertThat(ci[0])
                    .as("Wilson lower bound for 200/200 should be close to rule-of-3 estimate")
                    .isCloseTo(0.985, within(0.005));

            assertThat(ci[1])
                    .as("Upper bound should be 1.0")
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("perfect baseline with 1000 samples produces tighter lower bound")
        void perfectBaselineWith1000SamplesProducesTighterBound() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 1000);

            for (int i = 0; i < 1000; i++) {
                aggregator.recordSuccess(createPassingOutcome());
            }

            double[] ci = aggregator.getConfidenceInterval95();

            // 1000/1000: lower bound ≈ 1 - 3/1000 = 0.997
            assertThat(ci[0]).isLessThan(1.0);
            assertThat(ci[0]).isCloseTo(0.997, within(0.002));
        }

        @Test
        @DisplayName("mixed results produce reasonable interval")
        void mixedResultsProduceReasonableInterval() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 100);

            for (int i = 0; i < 90; i++) {
                aggregator.recordSuccess(createPassingOutcome());
            }
            for (int i = 0; i < 10; i++) {
                aggregator.recordException(new AssertionError("fail"));
            }

            double[] ci = aggregator.getConfidenceInterval95();

            assertThat(ci[0]).isGreaterThan(0.8);
            assertThat(ci[0]).isLessThan(0.9);
            assertThat(ci[1]).isGreaterThan(0.9);
            assertThat(ci[1]).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("zero successes produces lower bound of 0.0")
        void zeroSuccessesProducesLowerBoundOfZero() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 100);

            for (int i = 0; i < 100; i++) {
                aggregator.recordException(new AssertionError("fail"));
            }

            double[] ci = aggregator.getConfidenceInterval95();

            assertThat(ci[0]).isEqualTo(0.0);
            assertThat(ci[1]).isLessThan(0.1);
        }
    }

    @SuppressWarnings("unchecked")
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

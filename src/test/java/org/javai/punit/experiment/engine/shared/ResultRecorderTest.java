package org.javai.punit.experiment.engine.shared;

import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.contract.Outcomes;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.model.UseCaseCriteria;
import org.javai.punit.model.UseCaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResultRecorder")
class ResultRecorderTest {

    private ExperimentResultAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new ExperimentResultAggregator("TestUseCase", 100);
    }

    @Nested
    @DisplayName("with contract-based outcomes")
    class ContractOutcomeTests {

        @Test
        @DisplayName("records success when all postconditions pass")
        void recordsSuccessWhenAllPostconditionsPass() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Contains data", s -> s.contains("data"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "test data",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            OutcomeCaptor captor = new OutcomeCaptor();
            captor.record(outcome);

            ResultRecorder.recordResult(captor, aggregator);

            assertThat(aggregator.getSuccesses()).isEqualTo(1);
            assertThat(aggregator.getFailures()).isZero();
            assertThat(aggregator.hasPostconditionStats()).isTrue();
        }

        @Test
        @DisplayName("records failure when any postcondition fails")
        void recordsFailureWhenAnyPostconditionFails() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Starts with X", s -> s.startsWith("X"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "test data",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            OutcomeCaptor captor = new OutcomeCaptor();
            captor.record(outcome);

            ResultRecorder.recordResult(captor, aggregator);

            assertThat(aggregator.getSuccesses()).isZero();
            assertThat(aggregator.getFailures()).isEqualTo(1);
            assertThat(aggregator.getFailureDistribution()).containsKey("Starts with X");
        }

        @Test
        @DisplayName("records postcondition stats for aggregation")
        void recordsPostconditionStatsForAggregation() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Check A", s -> true)
                    .ensure("Check B", s -> true)
                    .build();

            for (int i = 0; i < 5; i++) {
                UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                        "test",
                        Duration.ofMillis(100),
                        Instant.now(),
                        Map.of(),
                        contract
                );

                OutcomeCaptor captor = new OutcomeCaptor();
                captor.record(outcome);
                ResultRecorder.recordResult(captor, aggregator);
            }

            assertThat(aggregator.hasPostconditionStats()).isTrue();
            var passRates = aggregator.getPostconditionPassRates();
            assertThat(passRates.get("Check A")).isEqualTo(1.0);
            assertThat(passRates.get("Check B")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("handles derivation failures correctly")
        void handlesDerivationFailuresCorrectly() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .deriving("Parse number", s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a number");
                        }
                    })
                    .ensure("Positive", n -> n > 0)
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "not-a-number",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            OutcomeCaptor captor = new OutcomeCaptor();
            captor.record(outcome);

            ResultRecorder.recordResult(captor, aggregator);

            assertThat(aggregator.getFailures()).isEqualTo(1);
            assertThat(aggregator.getFailureDistribution()).containsKey("Parse number");
        }
    }

    @Nested
    @DisplayName("with legacy criteria")
    @SuppressWarnings("deprecation")
    class LegacyCriteriaTests {

        @Test
        @DisplayName("records success when all criteria pass")
        void recordsSuccessWhenAllCriteriaPass() {
            UseCaseResult result = UseCaseResult.builder()
                    .value("data", "test")
                    .build();

            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("Check A", () -> true)
                    .criterion("Check B", () -> true)
                    .build();

            OutcomeCaptor captor = new OutcomeCaptor();
            captor.record(result);
            captor.recordCriteria(criteria);

            ResultRecorder.recordResult(captor, aggregator);

            assertThat(aggregator.getSuccesses()).isEqualTo(1);
            assertThat(aggregator.getFailures()).isZero();
        }

        @Test
        @DisplayName("records failure when any criterion fails")
        void recordsFailureWhenAnyCriterionFails() {
            UseCaseResult result = UseCaseResult.builder()
                    .value("data", "test")
                    .build();

            UseCaseCriteria criteria = UseCaseCriteria.ordered()
                    .criterion("Check A", () -> true)
                    .criterion("Check B", () -> false)
                    .build();

            OutcomeCaptor captor = new OutcomeCaptor();
            captor.record(result);
            captor.recordCriteria(criteria);

            ResultRecorder.recordResult(captor, aggregator);

            assertThat(aggregator.getSuccesses()).isZero();
            assertThat(aggregator.getFailures()).isEqualTo(1);
            assertThat(aggregator.getFailureDistribution()).containsKey("Check B");
        }
    }

    @Nested
    @DisplayName("with legacy heuristics")
    @SuppressWarnings("deprecation")
    class LegacyHeuristicsTests {

        @Test
        @DisplayName("uses success key from result when no criteria")
        void usesSuccessKeyFromResult() {
            UseCaseResult result = UseCaseResult.builder()
                    .value("success", false)
                    .build();

            OutcomeCaptor captor = new OutcomeCaptor();
            captor.record(result);

            ResultRecorder.recordResult(captor, aggregator);

            assertThat(aggregator.getFailures()).isEqualTo(1);
        }

        @Test
        @DisplayName("defaults to success when no indicators present")
        void defaultsToSuccessWhenNoIndicators() {
            UseCaseResult result = UseCaseResult.builder()
                    .value("someData", "value")
                    .build();

            OutcomeCaptor captor = new OutcomeCaptor();
            captor.record(result);

            ResultRecorder.recordResult(captor, aggregator);

            assertThat(aggregator.getSuccesses()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("exception handling")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("records exception when captor has exception")
        void recordsExceptionWhenCaptorHasException() {
            OutcomeCaptor captor = new OutcomeCaptor();
            captor.recordException(new RuntimeException("Test error"));

            ResultRecorder.recordResult(captor, aggregator);

            assertThat(aggregator.getFailures()).isEqualTo(1);
            assertThat(aggregator.getFailureDistribution()).containsKey("RuntimeException");
        }

        @Test
        @DisplayName("handles null captor gracefully")
        void handlesNullCaptorGracefully() {
            ResultRecorder.recordResult(null, aggregator);

            // Should record a default success
            assertThat(aggregator.getSuccesses()).isEqualTo(1);
        }

        @Test
        @DisplayName("handles captor with no result")
        void handlesCaptorWithNoResult() {
            OutcomeCaptor captor = new OutcomeCaptor();
            // Don't record anything

            ResultRecorder.recordResult(captor, aggregator);

            // Should record a default success
            assertThat(aggregator.getSuccesses()).isEqualTo(1);
        }
    }
}

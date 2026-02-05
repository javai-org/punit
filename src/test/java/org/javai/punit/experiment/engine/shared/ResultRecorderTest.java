package org.javai.punit.experiment.engine.shared;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.outcome.Outcome;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
                    .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check","empty") : Outcome.ok())
                    .ensure("Contains data", s -> s.contains("data") ? Outcome.ok() : Outcome.fail("check","missing data"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "test data",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract,
                    null,
                    null,
                    null
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
                    .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check","empty") : Outcome.ok())
                    .ensure("Starts with X", s -> s.startsWith("X") ? Outcome.ok() : Outcome.fail("check","wrong start"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "test data",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract,
                    null,
                    null,
                    null
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
                    .ensure("Check A", s -> Outcome.ok())
                    .ensure("Check B", s -> Outcome.ok())
                    .build();

            for (int i = 0; i < 5; i++) {
                UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                        "test",
                        Duration.ofMillis(100),
                        Instant.now(),
                        Map.of(),
                        contract,
                        null,
                        null,
                        null
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
                    .derive("Parse number", s -> {
                        try {
                            return Outcome.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcome.fail("check","Not a number");
                        }
                    })
                    .ensure("Positive", n -> n > 0 ? Outcome.ok() : Outcome.fail("check","not positive"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "not-a-number",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract,
                    null,
                    null,
                    null
            );

            OutcomeCaptor captor = new OutcomeCaptor();
            captor.record(outcome);

            ResultRecorder.recordResult(captor, aggregator);

            assertThat(aggregator.getFailures()).isEqualTo(1);
            assertThat(aggregator.getFailureDistribution()).containsKey("Parse number");
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
        @DisplayName("does not record anything when captor is null")
        void doesNotRecordAnythingWhenCaptorIsNull() {
            ResultRecorder.recordResult(null, aggregator);

            // Should not record anything
            assertThat(aggregator.getSamplesExecuted()).isZero();
        }

        @Test
        @DisplayName("does not record anything when captor has no result")
        void doesNotRecordAnythingWhenCaptorHasNoResult() {
            OutcomeCaptor captor = new OutcomeCaptor();
            // Don't record anything

            ResultRecorder.recordResult(captor, aggregator);

            // Should not record anything
            assertThat(aggregator.getSamplesExecuted()).isZero();
        }
    }
}

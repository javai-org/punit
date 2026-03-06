package org.javai.punit.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.javai.outcome.Outcome;
import org.javai.punit.api.OutcomeCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OutcomeCaptor contract integration")
class OutcomeCaptorContractIntegrationTest {

    private OutcomeCaptor captor;

    @BeforeEach
    void setUp() {
        captor = new OutcomeCaptor();
    }

    @Nested
    @DisplayName("record()")
    class RecordTests {

        @Test
        @DisplayName("records contract outcome with passing postconditions")
        void recordsContractOutcomeWithPassingPostconditions() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check","empty") : Outcome.ok())
                    .ensure("Contains hello", s -> s.contains("hello") ? Outcome.ok() : Outcome.fail("check","missing hello"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "hello world",
                    Duration.ofMillis(150),
                    Instant.now(),
                    Map.of("tokensUsed", 42),
                    contract,
                    null,
                    null,
                    null
            );

            captor.record(outcome);

            assertThat(captor.hasResult()).isTrue();
            assertThat(captor.getContractOutcome()).isNotNull();
            assertThat(captor.getContractOutcome().allPostconditionsSatisfied()).isTrue();
        }

        @Test
        @DisplayName("records contract outcome with failing postconditions")
        void recordsContractOutcomeWithFailingPostconditions() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check","empty") : Outcome.ok())
                    .ensure("Starts with X", s -> s.startsWith("X") ? Outcome.ok() : Outcome.fail("check","wrong start"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "hello",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract,
                    null,
                    null,
                    null
            );

            captor.record(outcome);

            assertThat(captor.hasResult()).isTrue();
            assertThat(captor.getContractOutcome().allPostconditionsSatisfied()).isFalse();

            List<PostconditionResult> results = captor.getContractOutcome().evaluatePostconditions();
            assertThat(results).hasSize(2);
            assertThat(results.get(0).passed()).isTrue();
            assertThat(results.get(1).passed()).isFalse();
        }

        @Test
        @DisplayName("preserves execution time in outcome")
        void preservesExecutionTimeInOutcome() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .build();

            Duration executionTime = Duration.ofMillis(250);
            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "result",
                    executionTime,
                    Instant.now(),
                    Map.of(),
                    contract,
                    null,
                    null,
                    null
            );

            captor.record(outcome);

            assertThat(captor.getContractOutcome().executionTime()).isEqualTo(executionTime);
        }

        @Test
        @DisplayName("preserves metadata in outcome")
        void preservesMetadataInOutcome() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "result",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of("tokensUsed", 150, "model", "gpt-4"),
                    contract,
                    null,
                    null,
                    null
            );

            captor.record(outcome);

            assertThat(captor.getContractOutcome().metadata()).containsEntry("tokensUsed", 150);
            assertThat(captor.getContractOutcome().metadata()).containsEntry("model", "gpt-4");
        }

        @Test
        @DisplayName("provides direct access to typed result")
        void providesDirectAccessToTypedResult() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "the actual result",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract,
                    null,
                    null,
                    null
            );

            captor.record(outcome);

            @SuppressWarnings("unchecked")
            UseCaseOutcome<String> captured = (UseCaseOutcome<String>) captor.getContractOutcome();
            assertThat(captured.result()).isEqualTo("the actual result");
        }

        @Test
        @DisplayName("handles null result value")
        void handlesNullResultValue() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Is null", s -> s == null ? Outcome.ok() : Outcome.fail("check","not null"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    null,
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract,
                    null,
                    null,
                    null
            );

            captor.record(outcome);

            assertThat(captor.hasResult()).isTrue();
            @SuppressWarnings("unchecked")
            UseCaseOutcome<String> captured = (UseCaseOutcome<String>) captor.getContractOutcome();
            assertThat(captured.result()).isNull();
            assertThat(captured.allPostconditionsSatisfied()).isTrue();
        }

        @Test
        @DisplayName("only records first outcome")
        void onlyRecordsFirstOutcome() {
            ServiceContract<Void, String> contract1 = ServiceContract
                    .<Void, String>define()
                    .ensure("Always pass", s -> Outcome.ok())
                    .build();

            ServiceContract<Void, String> contract2 = ServiceContract
                    .<Void, String>define()
                    .ensure("Always fail", s -> Outcome.fail("check","always fails"))
                    .build();

            UseCaseOutcome<String> outcome1 = new UseCaseOutcome<>(
                    "first",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract1,
                    null,
                    null,
                    null
            );

            UseCaseOutcome<String> outcome2 = new UseCaseOutcome<>(
                    "second",
                    Duration.ofMillis(200),
                    Instant.now(),
                    Map.of(),
                    contract2,
                    null,
                    null,
                    null
            );

            captor.record(outcome1);
            captor.record(outcome2);

            // Should still have first outcome (which passes)
            assertThat(captor.getContractOutcome().allPostconditionsSatisfied()).isTrue();
            @SuppressWarnings("unchecked")
            UseCaseOutcome<String> captured = (UseCaseOutcome<String>) captor.getContractOutcome();
            assertThat(captured.result()).isEqualTo("first");
        }

        @Test
        @DisplayName("returns outcome for fluent chaining")
        void returnsOutcomeForFluentChaining() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "result",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract,
                    null,
                    null,
                    null
            );

            UseCaseOutcome<String> returned = captor.record(outcome);

            assertThat(returned).isSameAs(outcome);
        }
    }

    @Nested
    @DisplayName("integration with derivations")
    class DerivationIntegrationTests {

        @Test
        @DisplayName("records outcomes from contract with derivations")
        void recordsOutcomesFromContractWithDerivations() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check","empty") : Outcome.ok())
                    .derive("Parse number", s -> {
                        try {
                            return Outcome.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcome.fail("check","Not a valid number");
                        }
                    })
                    .ensure("Positive", n -> n > 0 ? Outcome.ok() : Outcome.fail("check","not positive"))
                    .ensure("Less than 100", n -> n < 100 ? Outcome.ok() : Outcome.fail("check","too large"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "42",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract,
                    null,
                    null,
                    null
            );

            captor.record(outcome);

            assertThat(captor.getContractOutcome().allPostconditionsSatisfied()).isTrue();

            List<PostconditionResult> results = captor.getContractOutcome().evaluatePostconditions();
            assertThat(results).hasSize(4); // Not empty + Parse number + Positive + Less than 100
            assertThat(results).allMatch(PostconditionResult::passed);
        }

        @Test
        @DisplayName("handles failed derivation with skipped postconditions")
        void handlesFailedDerivationWithSkippedPostconditions() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .derive("Parse number", s -> {
                        try {
                            return Outcome.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcome.fail("check","Not a valid number");
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

            captor.record(outcome);

            assertThat(captor.getContractOutcome().allPostconditionsSatisfied()).isFalse();

            List<PostconditionResult> results = captor.getContractOutcome().evaluatePostconditions();
            assertThat(results).hasSize(2);
            // First is the derivation failure
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(0).description()).isEqualTo("Parse number");
            // Second is skipped because derivation failed
            assertThat(results.get(1).description()).isEqualTo("Positive");
        }
    }
}

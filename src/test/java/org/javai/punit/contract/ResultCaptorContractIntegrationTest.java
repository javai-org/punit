package org.javai.punit.contract;

import org.javai.punit.api.ResultCaptor;
import org.javai.punit.model.CriterionOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResultCaptor contract integration")
@SuppressWarnings("deprecation")
class ResultCaptorContractIntegrationTest {

    private ResultCaptor captor;

    @BeforeEach
    void setUp() {
        captor = new ResultCaptor();
    }

    @Nested
    @DisplayName("recordContract()")
    class RecordContractTests {

        @Test
        @DisplayName("records contract outcome with passing postconditions")
        void recordsContractOutcomeWithPassingPostconditions() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Contains hello", s -> s.contains("hello"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "hello world",
                    Duration.ofMillis(150),
                    Instant.now(),
                    Map.of("tokensUsed", 42),
                    contract
            );

            captor.recordContract(outcome);

            assertThat(captor.hasResult()).isTrue();
            assertThat(captor.hasCriteria()).isTrue();
            assertThat(captor.getCriteria().allPassed()).isTrue();
        }

        @Test
        @DisplayName("records contract outcome with failing postconditions")
        void recordsContractOutcomeWithFailingPostconditions() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Starts with X", s -> s.startsWith("X"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "hello",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            captor.recordContract(outcome);

            assertThat(captor.hasResult()).isTrue();
            assertThat(captor.hasCriteria()).isTrue();
            assertThat(captor.getCriteria().allPassed()).isFalse();

            List<CriterionOutcome> outcomes = captor.getCriteria().evaluate();
            assertThat(outcomes).hasSize(2);
            assertThat(outcomes.get(0).passed()).isTrue();
            assertThat(outcomes.get(1).passed()).isFalse();
        }

        @Test
        @DisplayName("preserves execution time in result")
        void preservesExecutionTimeInResult() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .build();

            Duration executionTime = Duration.ofMillis(250);
            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "result",
                    executionTime,
                    Instant.now(),
                    Map.of(),
                    contract
            );

            captor.recordContract(outcome);

            assertThat(captor.getResult().executionTime()).isEqualTo(executionTime);
        }

        @Test
        @DisplayName("preserves metadata in result")
        void preservesMetadataInResult() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "result",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of("tokensUsed", 150, "model", "gpt-4"),
                    contract
            );

            captor.recordContract(outcome);

            assertThat(captor.getResult().metadata()).containsEntry("tokensUsed", 150);
            assertThat(captor.getResult().metadata()).containsEntry("model", "gpt-4");
        }

        @Test
        @DisplayName("stores result value in values map")
        void storesResultValueInValuesMap() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "the actual result",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            captor.recordContract(outcome);

            assertThat(captor.getResult().getValue("result", String.class))
                    .isPresent()
                    .hasValue("the actual result");
        }

        @Test
        @DisplayName("handles null result value")
        void handlesNullResultValue() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Is null", s -> s == null)
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    null,
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            captor.recordContract(outcome);

            assertThat(captor.hasResult()).isTrue();
            assertThat(captor.getResult().hasValue("result")).isFalse();
            assertThat(captor.getCriteria().allPassed()).isTrue();
        }

        @Test
        @DisplayName("only records first contract outcome")
        void onlyRecordsFirstContractOutcome() {
            ServiceContract<Void, String> contract1 = ServiceContract
                    .<Void, String>define()
                    .ensure("Always pass", s -> true)
                    .build();

            ServiceContract<Void, String> contract2 = ServiceContract
                    .<Void, String>define()
                    .ensure("Always fail", s -> false)
                    .build();

            UseCaseOutcome<String> outcome1 = new UseCaseOutcome<>(
                    "first",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract1
            );

            UseCaseOutcome<String> outcome2 = new UseCaseOutcome<>(
                    "second",
                    Duration.ofMillis(200),
                    Instant.now(),
                    Map.of(),
                    contract2
            );

            captor.recordContract(outcome1);
            captor.recordContract(outcome2);

            // Should still have first outcome's criteria (which passes)
            assertThat(captor.getCriteria().allPassed()).isTrue();
            assertThat(captor.getResult().getValue("result", String.class))
                    .hasValue("first");
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
                    contract
            );

            UseCaseOutcome<String> returned = captor.recordContract(outcome);

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
                    .ensure("Not empty", s -> !s.isEmpty())
                    .deriving("Parse number", s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a valid number");
                        }
                    })
                    .ensure("Positive", n -> n > 0)
                    .ensure("Less than 100", n -> n < 100)
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "42",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            captor.recordContract(outcome);

            assertThat(captor.getCriteria().allPassed()).isTrue();

            List<CriterionOutcome> outcomes = captor.getCriteria().evaluate();
            assertThat(outcomes).hasSize(4); // Not empty + Parse number + Positive + Less than 100
            assertThat(outcomes).allMatch(CriterionOutcome::passed);
        }

        @Test
        @DisplayName("handles failed derivation with skipped postconditions")
        void handlesFailedDerivationWithSkippedPostconditions() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .deriving("Parse number", s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a valid number");
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

            captor.recordContract(outcome);

            assertThat(captor.getCriteria().allPassed()).isFalse();

            List<CriterionOutcome> outcomes = captor.getCriteria().evaluate();
            assertThat(outcomes).hasSize(2);
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Failed.class);
            assertThat(outcomes.get(0).description()).isEqualTo("Parse number");
            assertThat(outcomes.get(1)).isInstanceOf(CriterionOutcome.NotEvaluated.class);
            assertThat(outcomes.get(1).description()).isEqualTo("Positive");
        }
    }
}

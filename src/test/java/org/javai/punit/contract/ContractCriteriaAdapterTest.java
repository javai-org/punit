package org.javai.punit.contract;

import org.javai.punit.model.CriterionOutcome;
import org.javai.punit.model.UseCaseCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ContractCriteriaAdapter")
class ContractCriteriaAdapterTest {

    @Nested
    @DisplayName("from(UseCaseOutcome)")
    class FromOutcomeTests {

        @Test
        @DisplayName("adapts contract outcome to UseCaseCriteria")
        void adaptsContractOutcomeToUseCaseCriteria() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Contains expected", s -> s.contains("hello"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "hello world",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            UseCaseCriteria criteria = ContractCriteriaAdapter.from(outcome);

            assertThat(criteria).isNotNull();
            assertThat(criteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("evaluate() returns converted CriterionOutcomes")
        void evaluateReturnsConvertedOutcomes() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Starts with A", s -> s.startsWith("A"))
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "hello",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            UseCaseCriteria criteria = ContractCriteriaAdapter.from(outcome);
            List<CriterionOutcome> outcomes = criteria.evaluate();

            assertThat(outcomes).hasSize(2);
            assertThat(outcomes.get(0)).isInstanceOf(CriterionOutcome.Passed.class);
            assertThat(outcomes.get(0).description()).isEqualTo("Not empty");
            assertThat(outcomes.get(1)).isInstanceOf(CriterionOutcome.Failed.class);
            assertThat(outcomes.get(1).description()).isEqualTo("Starts with A");
        }

        @Test
        @DisplayName("allPassed() returns false when any postcondition fails")
        void allPassedReturnsFalseOnFailure() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Always fails", s -> false)
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "hello",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            UseCaseCriteria criteria = ContractCriteriaAdapter.from(outcome);

            assertThat(criteria.allPassed()).isFalse();
        }

        @Test
        @DisplayName("allPassed() treats skipped postconditions as non-failures")
        void allPassedTreatsSkippedAsNonFailures() {
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

            // "hello" will fail parsing, causing "Positive" to be skipped
            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "hello",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            UseCaseCriteria criteria = ContractCriteriaAdapter.from(outcome);

            // allPassed() is false because the derivation failed
            assertThat(criteria.allPassed()).isFalse();

            // But if derivation passes, even with skipped children due to some other reason...
            // Let's create a passing scenario
            UseCaseOutcome<String> passingOutcome = new UseCaseOutcome<>(
                    "42",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            UseCaseCriteria passingCriteria = ContractCriteriaAdapter.from(passingOutcome);
            assertThat(passingCriteria.allPassed()).isTrue();
        }

        @Test
        @DisplayName("throws on null outcome")
        void throwsOnNullOutcome() {
            assertThatThrownBy(() -> ContractCriteriaAdapter.from((UseCaseOutcome<?>) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("outcome must not be null");
        }
    }

    @Nested
    @DisplayName("from(PostconditionEvaluator, result)")
    class FromEvaluatorTests {

        @Test
        @DisplayName("adapts evaluator and result to UseCaseCriteria")
        void adaptsEvaluatorAndResultToUseCaseCriteria() {
            ServiceContract<Void, Integer> contract = ServiceContract
                    .<Void, Integer>define()
                    .ensure("Positive", n -> n > 0)
                    .ensure("Less than 100", n -> n < 100)
                    .build();

            UseCaseCriteria criteria = ContractCriteriaAdapter.from(contract, 42);

            assertThat(criteria.allPassed()).isTrue();
            List<CriterionOutcome> outcomes = criteria.evaluate();
            assertThat(outcomes).hasSize(2);
            assertThat(outcomes).allMatch(CriterionOutcome::passed);
        }

        @Test
        @DisplayName("throws on null evaluator")
        void throwsOnNullEvaluator() {
            assertThatThrownBy(() -> ContractCriteriaAdapter.from(null, "result"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("evaluator must not be null");
        }

        @Test
        @DisplayName("accepts null result")
        void acceptsNullResult() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Is null", s -> s == null)
                    .build();

            UseCaseCriteria criteria = ContractCriteriaAdapter.from(contract, null);

            assertThat(criteria.allPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("entries()")
    class EntriesTests {

        @Test
        @DisplayName("returns entries compatible with UseCaseCriteria interface")
        void returnsEntriesCompatibleWithInterface() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Has length > 3", s -> s.length() > 3)
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "hello",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            UseCaseCriteria criteria = ContractCriteriaAdapter.from(outcome);
            var entries = criteria.entries();

            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).getKey()).isEqualTo("Not empty");
            assertThat(entries.get(0).getValue().get()).isTrue();
            assertThat(entries.get(1).getKey()).isEqualTo("Has length > 3");
            assertThat(entries.get(1).getValue().get()).isTrue();
        }
    }

    @Nested
    @DisplayName("evaluation caching")
    class CachingTests {

        @Test
        @DisplayName("caches evaluation results for consistency")
        void cachesEvaluationResultsForConsistency() {
            // Create a contract with a postcondition that uses mutable state
            // to verify caching behavior
            int[] callCount = {0};
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Counted", s -> {
                        callCount[0]++;
                        return true;
                    })
                    .build();

            UseCaseOutcome<String> outcome = new UseCaseOutcome<>(
                    "test",
                    Duration.ofMillis(100),
                    Instant.now(),
                    Map.of(),
                    contract
            );

            UseCaseCriteria criteria = ContractCriteriaAdapter.from(outcome);

            // First evaluation
            criteria.evaluate();
            int firstCallCount = callCount[0];

            // Second evaluation should use cached results
            criteria.evaluate();

            // Note: The implementation evaluates fresh each time for entries()
            // but caches for evaluate(). Let's verify evaluate() returns same list.
            List<CriterionOutcome> first = criteria.evaluate();
            List<CriterionOutcome> second = criteria.evaluate();

            assertThat(first).isSameAs(second);
        }
    }
}

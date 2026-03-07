package org.javai.punit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.javai.outcome.Outcome;
import org.javai.punit.contract.match.StringMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UseCaseOutcome dimension-scoped assertions")
class UseCaseOutcomeDimensionAssertionTest {

    private static final ServiceContract<String, String> CONTRACT_WITH_DURATION = ServiceContract
            .<String, String>define()
            .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check", "was empty") : Outcome.ok())
            .ensureDurationBelow(Duration.ofSeconds(10))
            .build();

    private static final ServiceContract<String, String> CONTRACT_ONLY = ServiceContract
            .<String, String>define()
            .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check", "was empty") : Outcome.ok())
            .build();

    private static final ServiceContract<String, String> DURATION_ONLY = ServiceContract
            .<String, String>define()
            .ensureDurationBelow(Duration.ofSeconds(10))
            .build();

    private static final ServiceContract<String, String> FAILING_CONTRACT = ServiceContract
            .<String, String>define()
            .ensure("Always fails", s -> Outcome.fail("check", "intentional"))
            .build();

    @Nested
    @DisplayName("assertContract()")
    class AssertContract {

        @Test
        @DisplayName("passes when postconditions are satisfied")
        void passesWhenPostconditionsSatisfied() {
            UseCaseOutcome<String> outcome = buildOutcome(CONTRACT_ONLY, "hello");
            assertThatCode(outcome::assertContract).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws AssertionError when postconditions fail")
        void throwsWhenPostconditionsFail() {
            UseCaseOutcome<String> outcome = buildOutcome(FAILING_CONTRACT, "hello");
            assertThatThrownBy(outcome::assertContract)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Contract violations")
                    .hasMessageContaining("Always fails");
        }

        @Test
        @DisplayName("throws with context message when provided")
        void throwsWithContextMessage() {
            UseCaseOutcome<String> outcome = buildOutcome(FAILING_CONTRACT, "hello");
            assertThatThrownBy(() -> outcome.assertContract("sample 42"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("sample 42 - Contract violations");
        }

        @Test
        @DisplayName("throws IllegalStateException when no contract criteria configured")
        void throwsWhenNoContractCriteria() {
            UseCaseOutcome<String> outcome = buildOutcome(DURATION_ONLY, "hello");
            assertThatThrownBy(outcome::assertContract)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no service contract is configured");
        }

        @Test
        @DisplayName("ignores duration constraint")
        void ignoresDurationConstraint() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .ensureDurationBelow(Duration.ofMillis(1))
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> {
                        try { Thread.sleep(50); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return s.toUpperCase();
                    })
                    .build();

            // Duration would fail, but assertContract ignores it
            assertThatCode(outcome::assertContract).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("includes expected value mismatch in failures")
        void includesExpectedValueMismatch() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> "actual")
                    .expecting("expected", StringMatcher.exact())
                    .build();

            assertThatThrownBy(outcome::assertContract)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected value mismatch");
        }

        @Test
        @DisplayName("passes with expected value when it matches")
        void passesWhenExpectedValueMatches() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> "hello")
                    .expecting("hello", StringMatcher.exact())
                    .build();

            assertThatCode(outcome::assertContract).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("assertLatency()")
    class AssertLatency {

        @Test
        @DisplayName("passes when within duration limit")
        void passesWhenWithinLimit() {
            UseCaseOutcome<String> outcome = buildOutcome(CONTRACT_WITH_DURATION, "hello");
            assertThatCode(outcome::assertLatency).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws AssertionError when duration constraint fails")
        void throwsWhenDurationFails() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .ensureDurationBelow(Duration.ofMillis(1))
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> {
                        try { Thread.sleep(50); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return s.toUpperCase();
                    })
                    .build();

            assertThatThrownBy(outcome::assertLatency)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Latency violation")
                    .hasMessageContaining("exceeded limit");
        }

        @Test
        @DisplayName("throws with context message when provided")
        void throwsWithContextMessage() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensureDurationBelow(Duration.ofMillis(1))
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> {
                        try { Thread.sleep(50); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return s.toUpperCase();
                    })
                    .build();

            assertThatThrownBy(() -> outcome.assertLatency("sample 7"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("sample 7 - Latency violation");
        }

        @Test
        @DisplayName("throws IllegalStateException when no duration constraint configured")
        void throwsWhenNoDurationConstraint() {
            UseCaseOutcome<String> outcome = buildOutcome(CONTRACT_ONLY, "hello");
            assertThatThrownBy(outcome::assertLatency)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no duration constraint is configured");
        }

        @Test
        @DisplayName("ignores postcondition failures")
        void ignoresPostconditionFailures() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Always fails", s -> Outcome.fail("check", "intentional"))
                    .ensureDurationBelow(Duration.ofSeconds(10))
                    .build();

            UseCaseOutcome<String> outcome = buildOutcome(contract, "hello");

            // Postconditions fail, but assertLatency ignores them
            assertThatCode(outcome::assertLatency).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("adaptive assertAll()")
    class AdaptiveAssertAll {

        @Test
        @DisplayName("asserts both dimensions when both configured")
        void assertsBothWhenBothConfigured() {
            UseCaseOutcome<String> outcome = buildOutcome(CONTRACT_WITH_DURATION, "hello");
            assertThatCode(outcome::assertAll).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("asserts contract only when no duration constraint")
        void assertsContractOnlyWhenNoDuration() {
            UseCaseOutcome<String> outcome = buildOutcome(CONTRACT_ONLY, "hello");
            assertThatCode(outcome::assertAll).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("asserts latency only when no postconditions")
        void assertsLatencyOnlyWhenNoPostconditions() {
            UseCaseOutcome<String> outcome = buildOutcome(DURATION_ONLY, "hello");
            assertThatCode(outcome::assertAll).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws IllegalStateException when neither is configured")
        void throwsWhenNeitherConfigured() {
            ServiceContract<String, String> emptyContract = ServiceContract
                    .<String, String>define()
                    .build();

            UseCaseOutcome<String> outcome = buildOutcome(emptyContract, "hello");
            assertThatThrownBy(outcome::assertAll)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no criteria are configured");
        }

        @Test
        @DisplayName("includes both postcondition and duration failures")
        void includesBothFailures() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Always fails", s -> Outcome.fail("check", "intentional"))
                    .ensureDurationBelow(Duration.ofMillis(1))
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> {
                        try { Thread.sleep(50); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return s.toUpperCase();
                    })
                    .build();

            assertThatThrownBy(outcome::assertAll)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Always fails")
                    .hasMessageContaining("exceeded limit");
        }

        @Test
        @DisplayName("includes context message when provided")
        void includesContextMessage() {
            UseCaseOutcome<String> outcome = buildOutcome(FAILING_CONTRACT, "hello");
            assertThatThrownBy(() -> outcome.assertAll("context info"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("context info - Contract violations");
        }
    }

    @Nested
    @DisplayName("AssertionScope signalling")
    class AssertionScopeSignalling {

        @Test
        @DisplayName("assertContract signals functional dimension")
        void assertContractSignalsFunctional() {
            AssertionScope.begin();
            try {
                UseCaseOutcome<String> outcome = buildOutcome(CONTRACT_ONLY, "hello");
                outcome.assertContract();

                AssertionScope scope = AssertionScope.current();
                assertThat(scope.isFunctionalAsserted()).isTrue();
                assertThat(scope.isFunctionalPassed()).isTrue();
                assertThat(scope.isLatencyAsserted()).isFalse();
            } finally {
                AssertionScope.end();
            }
        }

        @Test
        @DisplayName("assertContract signals functional failure")
        void assertContractSignalsFunctionalFailure() {
            AssertionScope.begin();
            try {
                UseCaseOutcome<String> outcome = buildOutcome(FAILING_CONTRACT, "hello");
                try {
                    outcome.assertContract();
                } catch (AssertionError ignored) {
                }

                AssertionScope scope = AssertionScope.current();
                assertThat(scope.isFunctionalAsserted()).isTrue();
                assertThat(scope.isFunctionalPassed()).isFalse();
            } finally {
                AssertionScope.end();
            }
        }

        @Test
        @DisplayName("assertLatency signals latency dimension")
        void assertLatencySignalsLatency() {
            AssertionScope.begin();
            try {
                UseCaseOutcome<String> outcome = buildOutcome(CONTRACT_WITH_DURATION, "hello");
                outcome.assertLatency();

                AssertionScope scope = AssertionScope.current();
                assertThat(scope.isLatencyAsserted()).isTrue();
                assertThat(scope.isLatencyPassed()).isTrue();
                assertThat(scope.isFunctionalAsserted()).isFalse();
            } finally {
                AssertionScope.end();
            }
        }

        @Test
        @DisplayName("assertAll signals both dimensions when both configured")
        void assertAllSignalsBothDimensions() {
            AssertionScope.begin();
            try {
                UseCaseOutcome<String> outcome = buildOutcome(CONTRACT_WITH_DURATION, "hello");
                outcome.assertAll();

                AssertionScope scope = AssertionScope.current();
                assertThat(scope.isFunctionalAsserted()).isTrue();
                assertThat(scope.isFunctionalPassed()).isTrue();
                assertThat(scope.isLatencyAsserted()).isTrue();
                assertThat(scope.isLatencyPassed()).isTrue();
            } finally {
                AssertionScope.end();
            }
        }

        @Test
        @DisplayName("assertAll signals only contract when no duration configured")
        void assertAllSignalsContractOnly() {
            AssertionScope.begin();
            try {
                UseCaseOutcome<String> outcome = buildOutcome(CONTRACT_ONLY, "hello");
                outcome.assertAll();

                AssertionScope scope = AssertionScope.current();
                assertThat(scope.isFunctionalAsserted()).isTrue();
                assertThat(scope.isLatencyAsserted()).isFalse();
            } finally {
                AssertionScope.end();
            }
        }

        @Test
        @DisplayName("assertAll signals only latency when no postconditions configured")
        void assertAllSignalsLatencyOnly() {
            AssertionScope.begin();
            try {
                UseCaseOutcome<String> outcome = buildOutcome(DURATION_ONLY, "hello");
                outcome.assertAll();

                AssertionScope scope = AssertionScope.current();
                assertThat(scope.isFunctionalAsserted()).isFalse();
                assertThat(scope.isLatencyAsserted()).isTrue();
            } finally {
                AssertionScope.end();
            }
        }

        @Test
        @DisplayName("no signalling when no scope is active")
        void noSignallingWithoutScope() {
            UseCaseOutcome<String> outcome = buildOutcome(CONTRACT_ONLY, "hello");
            // Should not throw even without an active scope
            assertThatCode(outcome::assertContract).doesNotThrowAnyException();
        }
    }

    private static UseCaseOutcome<String> buildOutcome(ServiceContract<String, String> contract, String input) {
        return UseCaseOutcome
                .withContract(contract)
                .input(input)
                .execute(s -> s.toUpperCase())
                .build();
    }
}

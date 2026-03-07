package org.javai.punit.contract;

import org.javai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UseCaseOutcome duration constraint")
class UseCaseOutcomeDurationTest {

    @Nested
    @DisplayName("without duration constraint")
    class WithoutDurationConstraint {

        @Test
        @DisplayName("withinDurationLimit returns true when no constraint")
        void withinDurationLimitTrueWhenNoConstraint() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> s.toUpperCase())
                    .build();

            assertThat(outcome.withinDurationLimit()).isTrue();
            assertThat(outcome.hasDurationConstraint()).isFalse();
            assertThat(outcome.getDurationResult()).isEmpty();
        }

        @Test
        @DisplayName("fullySatisfied ignores duration when no constraint")
        void fullySatisfiedIgnoresDurationWhenNoConstraint() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> s.toUpperCase())
                    .build();

            assertThat(outcome.fullySatisfied()).isTrue();
        }
    }

    @Nested
    @DisplayName("with duration constraint")
    class WithDurationConstraint {

        @Test
        @DisplayName("passes when execution is fast")
        void passesWhenExecutionIsFast() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .ensureDurationBelow(Duration.ofSeconds(10))
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> s.toUpperCase())  // Very fast operation
                    .build();

            assertThat(outcome.withinDurationLimit()).isTrue();
            assertThat(outcome.hasDurationConstraint()).isTrue();
            assertThat(outcome.getDurationResult()).isPresent();
            assertThat(outcome.getDurationResult().get().passed()).isTrue();
        }

        @Test
        @DisplayName("fails when execution is slow")
        void failsWhenExecutionIsSlow() throws InterruptedException {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .ensureDurationBelow(Duration.ofMillis(10))
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> {
                        try {
                            Thread.sleep(50);  // Slow operation
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return s.toUpperCase();
                    })
                    .build();

            assertThat(outcome.withinDurationLimit()).isFalse();
            assertThat(outcome.getDurationResult().get().failed()).isTrue();
        }

        @Test
        @DisplayName("fullySatisfied requires both postconditions and duration")
        void fullySatisfiedRequiresBoth() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .ensureDurationBelow(Duration.ofMillis(10))
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> {
                        try {
                            Thread.sleep(50);  // Slow
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return s.toUpperCase();
                    })
                    .build();

            // Postconditions pass but duration fails
            assertThat(outcome.allPostconditionsSatisfied()).isTrue();
            assertThat(outcome.withinDurationLimit()).isFalse();
            assertThat(outcome.fullySatisfied()).isFalse();
        }
    }

    @Nested
    @DisplayName("assertAll")
    class AssertAll {

        @Test
        @DisplayName("throws when duration constraint fails")
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
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return s.toUpperCase();
                    })
                    .build();

            assertThatThrownBy(outcome::assertAll)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Contract violations")
                    .hasMessageContaining("exceeded limit");
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
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
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
        @DisplayName("does not throw when all pass")
        void doesNotThrowWhenAllPass() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .ensureDurationBelow(Duration.ofSeconds(10))
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> s.toUpperCase())
                    .build();

            outcome.assertAll();  // Should not throw
        }
    }

    @Nested
    @DisplayName("independent evaluation")
    class IndependentEvaluation {

        @Test
        @DisplayName("reports both dimensions when postconditions fail but duration passes")
        void reportsWhenPostconditionsFailDurationPasses() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Always fails", s -> Outcome.fail("check", "intentional"))
                    .ensureDurationBelow(Duration.ofSeconds(10))
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> s.toUpperCase())
                    .build();

            assertThat(outcome.allPostconditionsSatisfied()).isFalse();
            assertThat(outcome.withinDurationLimit()).isTrue();
            assertThat(outcome.fullySatisfied()).isFalse();
        }

        @Test
        @DisplayName("reports both dimensions when duration fails but postconditions pass")
        void reportsWhenDurationFailsPostconditionsPass() {
            ServiceContract<String, String> contract = ServiceContract
                    .<String, String>define()
                    .ensure("Always passes", s -> Outcome.ok())
                    .ensureDurationBelow(Duration.ofMillis(1))
                    .build();

            UseCaseOutcome<String> outcome = UseCaseOutcome
                    .withContract(contract)
                    .input("test")
                    .execute(s -> {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return s.toUpperCase();
                    })
                    .build();

            assertThat(outcome.allPostconditionsSatisfied()).isTrue();
            assertThat(outcome.withinDurationLimit()).isFalse();
            assertThat(outcome.fullySatisfied()).isFalse();
        }
    }
}

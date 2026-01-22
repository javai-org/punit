package org.javai.punit.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ServiceContract")
class ServiceContractTest {

    record TestInput(String value, int number) {}

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("builds empty contract")
        void buildsEmptyContract() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .build();

            assertThat(contract.preconditions()).isEmpty();
            assertThat(contract.postconditions()).isEmpty();
            assertThat(contract.derivations()).isEmpty();
            assertThat(contract.postconditionCount()).isZero();
        }

        @Test
        @DisplayName("builds contract with preconditions only")
        void buildsContractWithPreconditionsOnly() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .require("Value not null", in -> in.value() != null)
                    .require("Number positive", in -> in.number() > 0)
                    .build();

            assertThat(contract.preconditions()).hasSize(2);
            assertThat(contract.postconditions()).isEmpty();
            assertThat(contract.derivations()).isEmpty();
            assertThat(contract.postconditionCount()).isZero();
        }

        @Test
        @DisplayName("builds contract with direct postconditions only")
        void buildsContractWithDirectPostconditionsOnly() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Reasonable length", s -> s.length() < 1000)
                    .build();

            assertThat(contract.preconditions()).isEmpty();
            assertThat(contract.postconditions()).hasSize(2);
            assertThat(contract.derivations()).isEmpty();
            assertThat(contract.postconditionCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("builds contract with direct postconditions and derivations")
        void buildsContractWithDirectPostconditionsAndDerivations() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .deriving("Uppercase", Outcomes.lift(String::toUpperCase))
                        .ensure("All caps", s -> s.equals(s.toUpperCase()))
                    .build();

            assertThat(contract.postconditions()).hasSize(1);
            assertThat(contract.derivations()).hasSize(1);
            assertThat(contract.postconditionCount()).isEqualTo(3); // 1 direct + 1 derivation + 1 ensure
        }

        @Test
        @DisplayName("builds contract with derivation and ensures")
        void buildsContractWithDerivation() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .require("Value not null", in -> in.value() != null)
                    .deriving("Valid number", s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a number");
                        }
                    })
                        .ensure("Positive", n -> n > 0)
                        .ensure("Less than 100", n -> n < 100)
                    .build();

            assertThat(contract.preconditions()).hasSize(1);
            assertThat(contract.derivations()).hasSize(1);
            assertThat(contract.postconditionCount()).isEqualTo(3); // derivation + 2 ensures
        }

        @Test
        @DisplayName("builds contract with multiple derivations")
        void buildsContractWithMultipleDerivations() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving("Valid number", s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a number");
                        }
                    })
                        .ensure("Positive", n -> n > 0)
                    .deriving("Uppercase", Outcomes.lift(String::toUpperCase))
                        .ensure("Not empty", s -> !s.isEmpty())
                    .build();

            assertThat(contract.derivations()).hasSize(2);
            assertThat(contract.postconditionCount()).isEqualTo(4); // 2 derivations + 2 ensures
        }

        @Test
        @DisplayName("throws when require description is null")
        void throwsWhenRequireDescriptionIsNull() {
            assertThatThrownBy(() -> ServiceContract
                    .<TestInput, String>define()
                    .require(null, in -> true))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("throws when deriving description is blank")
        void throwsWhenDerivingDescriptionIsBlank() {
            assertThatThrownBy(() -> ServiceContract
                    .<TestInput, String>define()
                    .deriving("   ", s -> Outcomes.ok(s)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws when deriving function is null")
        void throwsWhenDerivingFunctionIsNull() {
            assertThatThrownBy(() -> ServiceContract
                    .<TestInput, String>define()
                    .deriving("Test", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("checkPreconditions()")
    class CheckPreconditionsTests {

        @Test
        @DisplayName("passes when all preconditions satisfied")
        void passesWhenAllSatisfied() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .require("Value not null", in -> in.value() != null)
                    .require("Number positive", in -> in.number() > 0)
                    .build();

            assertThatCode(() -> contract.checkPreconditions(new TestInput("hello", 42)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws on first failed precondition")
        void throwsOnFirstFailed() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .require("Value not null", in -> in.value() != null)
                    .require("Number positive", in -> in.number() > 0)
                    .build();

            assertThatThrownBy(() -> contract.checkPreconditions(new TestInput(null, 42)))
                    .isInstanceOf(PreconditionException.class)
                    .satisfies(e -> {
                        PreconditionException ex = (PreconditionException) e;
                        assertThat(ex.getPreconditionDescription()).isEqualTo("Value not null");
                    });
        }

        @Test
        @DisplayName("checks preconditions in order")
        void checksPreconditionsInOrder() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .require("Value not null", in -> in.value() != null)
                    .require("Number positive", in -> in.number() > 0)
                    .build();

            // Both fail, but first one throws
            assertThatThrownBy(() -> contract.checkPreconditions(new TestInput(null, -1)))
                    .isInstanceOf(PreconditionException.class)
                    .satisfies(e -> {
                        PreconditionException ex = (PreconditionException) e;
                        assertThat(ex.getPreconditionDescription()).isEqualTo("Value not null");
                    });
        }
    }

    @Nested
    @DisplayName("evaluate()")
    class EvaluatePostconditionsTests {

        @Test
        @DisplayName("evaluates direct postconditions")
        void evaluatesDirectPostconditions() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Starts with H", s -> s.startsWith("H"))
                    .build();

            List<PostconditionResult> results = contract.evaluate("Hello");

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(PostconditionResult::passed);
        }

        @Test
        @DisplayName("evaluates direct postconditions before derivations")
        void evaluatesDirectPostconditionsBeforeDerivations() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .deriving("Uppercase", Outcomes.lift(String::toUpperCase))
                        .ensure("All caps", s -> s.equals(s.toUpperCase()))
                    .build();

            List<PostconditionResult> results = contract.evaluate("hello");

            assertThat(results).hasSize(3);
            // Direct postcondition first
            assertThat(results.get(0).description()).isEqualTo("Not empty");
            assertThat(results.get(0).passed()).isTrue();
            // Then derivation
            assertThat(results.get(1).description()).isEqualTo("Uppercase");
            assertThat(results.get(1).passed()).isTrue();
            // Then derived postcondition
            assertThat(results.get(2).description()).isEqualTo("All caps");
            assertThat(results.get(2).passed()).isTrue();
        }

        @Test
        @DisplayName("returns all passed when all postconditions satisfied")
        void returnsAllPassedWhenAllSatisfied() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving("Valid number", s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a number");
                        }
                    })
                        .ensure("Positive", n -> n > 0)
                        .ensure("Less than 100", n -> n < 100)
                    .build();

            List<PostconditionResult> results = contract.evaluate("42");

            assertThat(results).hasSize(3);
            assertThat(results).allMatch(PostconditionResult::passed);
        }

        @Test
        @DisplayName("returns failed derivation and skipped ensures when derivation fails")
        void returnsFailedDerivationAndSkippedEnsures() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving("Valid number", s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a number");
                        }
                    })
                        .ensure("Positive", n -> n > 0)
                        .ensure("Less than 100", n -> n < 100)
                    .build();

            List<PostconditionResult> results = contract.evaluate("not-a-number");

            assertThat(results).hasSize(3);
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(1).skipped()).isTrue();
            assertThat(results.get(2).skipped()).isTrue();
        }

        @Test
        @DisplayName("evaluates multiple derivations independently")
        void evaluatesMultipleDerivationsIndependently() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving("Valid number", s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a number");
                        }
                    })
                        .ensure("Positive", n -> n > 0)
                    .deriving("Uppercase", Outcomes.lift(String::toUpperCase))
                        .ensure("Not empty", s -> !s.isEmpty())
                    .build();

            // First derivation fails, second succeeds
            List<PostconditionResult> results = contract.evaluate("hello");

            assertThat(results).hasSize(4);
            // First derivation: Failed + Skipped
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(1).skipped()).isTrue();
            assertThat(results.get(1).description()).isEqualTo("Positive");
            // Second derivation: Passed + Passed
            assertThat(results.get(2).passed()).isTrue();
            assertThat(results.get(2).description()).isEqualTo("Uppercase");
            assertThat(results.get(3).passed()).isTrue();
            assertThat(results.get(3).description()).isEqualTo("Not empty");
        }
    }

    @Nested
    @DisplayName("postconditionCount()")
    class PostconditionCountTests {

        @Test
        @DisplayName("counts direct postconditions")
        void countsDirectPostconditions() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .ensure("Not empty", s -> !s.isEmpty())
                    .ensure("Reasonable length", s -> s.length() < 1000)
                    .build();

            assertThat(contract.postconditionCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("counts derivation as postcondition")
        void countsDerivationAsPostcondition() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving("Valid JSON", s -> Outcomes.ok(s))
                        .ensure("Has field", s -> true)
                    .build();

            assertThat(contract.postconditionCount()).isEqualTo(2); // derivation + ensure
        }

        @Test
        @DisplayName("counts derivation without ensures")
        void countsDerivationWithoutEnsures() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving("Uppercase", Outcomes.lift(String::toUpperCase))
                    .build();

            assertThat(contract.postconditionCount()).isEqualTo(1); // just the derivation
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("returns descriptive string")
        void returnsDescriptiveString() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .require("Value not null", in -> in.value() != null)
                    .deriving("Valid number", s -> Outcomes.ok(42))
                        .ensure("Positive", n -> n > 0)
                    .build();

            assertThat(contract.toString())
                    .isEqualTo("ServiceContract[preconditions=1, derivations=1, postconditions=2]");
        }
    }
}

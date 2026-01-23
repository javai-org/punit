package org.javai.punit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

            assertThat(contract.postconditions()).isEmpty();
            assertThat(contract.derivations()).isEmpty();
            assertThat(contract.postconditionCount()).isZero();
        }

        @Test
        @DisplayName("builds contract with direct postconditions only")
        void buildsContractWithDirectPostconditionsOnly() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .ensure("Not empty", s -> s.isEmpty() ? Outcomes.fail("empty") : Outcomes.okVoid())
                    .ensure("Reasonable length", s -> s.length() < 1000 ? Outcomes.okVoid() : Outcomes.fail("too long"))
                    .build();

            assertThat(contract.postconditions()).hasSize(2);
            assertThat(contract.derivations()).isEmpty();
            assertThat(contract.postconditionCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("builds contract with direct postconditions and derivations")
        void buildsContractWithDirectPostconditionsAndDerivations() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .ensure("Not empty", s -> s.isEmpty() ? Outcomes.fail("empty") : Outcomes.okVoid())
                    .deriving("Uppercase", s -> Outcomes.ok(s.toUpperCase()))
                        .ensure("All caps", s -> s.equals(s.toUpperCase()) ? Outcomes.okVoid() : Outcomes.fail("not caps"))
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
                    .deriving("Valid number", s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a number");
                        }
                    })
                        .ensure("Positive", n -> n > 0 ? Outcomes.okVoid() : Outcomes.fail("not positive"))
                        .ensure("Less than 100", n -> n < 100 ? Outcomes.okVoid() : Outcomes.fail("too large"))
                    .build();

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
                        .ensure("Positive", n -> n > 0 ? Outcomes.okVoid() : Outcomes.fail("not positive"))
                    .derive("Uppercase", s -> Outcomes.ok(s.toUpperCase()))
                        .ensure("Not empty", s -> s.isEmpty() ? Outcomes.fail("empty") : Outcomes.okVoid())
                    .build();

            assertThat(contract.derivations()).hasSize(2);
            assertThat(contract.postconditionCount()).isEqualTo(4); // 2 derivations + 2 ensures
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
    @DisplayName("evaluate()")
    class EvaluatePostconditionsTests {

        @Test
        @DisplayName("evaluates direct postconditions")
        void evaluatesDirectPostconditions() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .ensure("Not empty", s -> s.isEmpty() ? Outcomes.fail("empty") : Outcomes.okVoid())
                    .ensure("Starts with H", s -> s.startsWith("H") ? Outcomes.okVoid() : Outcomes.fail("wrong start"))
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
                    .ensure("Not empty", s -> s.isEmpty() ? Outcomes.fail("empty") : Outcomes.okVoid())
                    .deriving("Uppercase", s -> Outcomes.ok(s.toUpperCase()))
                        .ensure("All caps", s -> s.equals(s.toUpperCase()) ? Outcomes.okVoid() : Outcomes.fail("not caps"))
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
                        .ensure("Positive", n -> n > 0 ? Outcomes.okVoid() : Outcomes.fail("not positive"))
                        .ensure("Less than 100", n -> n < 100 ? Outcomes.okVoid() : Outcomes.fail("too large"))
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
                        .ensure("Positive", n -> n > 0 ? Outcomes.okVoid() : Outcomes.fail("not positive"))
                        .ensure("Less than 100", n -> n < 100 ? Outcomes.okVoid() : Outcomes.fail("too large"))
                    .build();

            List<PostconditionResult> results = contract.evaluate("not-a-number");

            assertThat(results).hasSize(3);
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            // Skipped postconditions are represented as failures with "Skipped:" prefix
            assertThat(results.get(1).failed()).isTrue();
            assertThat(results.get(1).failureReason()).hasValueSatisfying(r -> assertThat(r).startsWith("Skipped:"));
            assertThat(results.get(2).failed()).isTrue();
            assertThat(results.get(2).failureReason()).hasValueSatisfying(r -> assertThat(r).startsWith("Skipped:"));
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
                        .ensure("Positive", n -> n > 0 ? Outcomes.okVoid() : Outcomes.fail("not positive"))
                    .derive("Uppercase", Outcomes.lift(String::toUpperCase))
                        .ensure("Not empty", s -> s.isEmpty() ? Outcomes.fail("empty") : Outcomes.okVoid())
                    .build();

            // First derivation fails, second succeeds
            List<PostconditionResult> results = contract.evaluate("hello");

            assertThat(results).hasSize(4);
            // First derivation: Failed + Skipped (skipped = failed with "Skipped:" prefix)
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(1).failed()).isTrue();
            assertThat(results.get(1).description()).isEqualTo("Positive");
            assertThat(results.get(1).failureReason()).hasValueSatisfying(r -> assertThat(r).startsWith("Skipped:"));
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
                    .ensure("Not empty", s -> s.isEmpty() ? Outcomes.fail("empty") : Outcomes.okVoid())
                    .ensure("Reasonable length", s -> s.length() < 1000 ? Outcomes.okVoid() : Outcomes.fail("too long"))
                    .build();

            assertThat(contract.postconditionCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("counts derivation as postcondition")
        void countsDerivationAsPostcondition() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving("Valid JSON", s -> Outcomes.ok(s))
                        .ensure("Has field", s -> Outcomes.okVoid())
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
                    .deriving("Valid number", s -> Outcomes.ok(42))
                        .ensure("Positive", n -> n > 0 ? Outcomes.okVoid() : Outcomes.fail("not positive"))
                    .build();

            assertThat(contract.toString())
                    .isEqualTo("ServiceContract[derivations=1, postconditions=2]");
        }
    }
}

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
            assertThat(contract.derivations()).isEmpty();
            assertThat(contract.postconditionCount()).isZero();
        }

        @Test
        @DisplayName("builds contract with fallible derivation and ensures")
        void buildsContractWithFallibleDerivation() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .require("Value not null", in -> in.value() != null)
                    .deriving("Valid number", s -> {
                        try {
                            return Outcome.success(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcome.failure("Not a number");
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
        @DisplayName("builds contract with infallible derivation")
        void buildsContractWithInfallibleDerivation() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving(Outcome.lift(String::toUpperCase))
                        .ensure("Not empty", s -> !s.isEmpty())
                    .build();

            assertThat(contract.derivations()).hasSize(1);
            assertThat(contract.postconditionCount()).isEqualTo(1); // only the ensure
        }

        @Test
        @DisplayName("builds contract with multiple derivations")
        void buildsContractWithMultipleDerivations() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving("Valid number", s -> {
                        try {
                            return Outcome.success(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcome.failure("Not a number");
                        }
                    })
                        .ensure("Positive", n -> n > 0)
                    .deriving(Outcome.lift(String::toUpperCase))
                        .ensure("Not empty", s -> !s.isEmpty())
                    .build();

            assertThat(contract.derivations()).hasSize(2);
            assertThat(contract.postconditionCount()).isEqualTo(3); // 1 fallible derivation + 2 ensures
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
                    .deriving("   ", s -> Outcome.success(s)))
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
                    .isInstanceOf(UseCasePreconditionException.class)
                    .satisfies(e -> {
                        UseCasePreconditionException ex = (UseCasePreconditionException) e;
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
                    .isInstanceOf(UseCasePreconditionException.class)
                    .satisfies(e -> {
                        UseCasePreconditionException ex = (UseCasePreconditionException) e;
                        assertThat(ex.getPreconditionDescription()).isEqualTo("Value not null");
                    });
        }
    }

    @Nested
    @DisplayName("evaluatePostconditions()")
    class EvaluatePostconditionsTests {

        @Test
        @DisplayName("returns all passed when all postconditions satisfied")
        void returnsAllPassedWhenAllSatisfied() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving("Valid number", s -> {
                        try {
                            return Outcome.success(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcome.failure("Not a number");
                        }
                    })
                        .ensure("Positive", n -> n > 0)
                        .ensure("Less than 100", n -> n < 100)
                    .build();

            List<PostconditionResult> results = contract.evaluatePostconditions("42");

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
                            return Outcome.success(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcome.failure("Not a number");
                        }
                    })
                        .ensure("Positive", n -> n > 0)
                        .ensure("Less than 100", n -> n < 100)
                    .build();

            List<PostconditionResult> results = contract.evaluatePostconditions("not-a-number");

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
                            return Outcome.success(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcome.failure("Not a number");
                        }
                    })
                        .ensure("Positive", n -> n > 0)
                    .deriving(Outcome.lift(String::toUpperCase))
                        .ensure("Not empty", s -> !s.isEmpty())
                    .build();

            // First derivation fails, second succeeds
            List<PostconditionResult> results = contract.evaluatePostconditions("hello");

            assertThat(results).hasSize(3);
            // First derivation (fallible): Failed + Skipped
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(1).skipped()).isTrue();
            assertThat(results.get(1).description()).isEqualTo("Positive");
            // Second derivation (infallible): only ensure is evaluated
            assertThat(results.get(2).passed()).isTrue();
            assertThat(results.get(2).description()).isEqualTo("Not empty");
        }
    }

    @Nested
    @DisplayName("postconditionCount()")
    class PostconditionCountTests {

        @Test
        @DisplayName("counts fallible derivation as postcondition")
        void countsFallibleDerivationAsPostcondition() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving("Valid JSON", s -> Outcome.success(s))
                        .ensure("Has field", s -> true)
                    .build();

            assertThat(contract.postconditionCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("does not count infallible derivation as postcondition")
        void doesNotCountInfallibleDerivationAsPostcondition() {
            ServiceContract<TestInput, String> contract = ServiceContract
                    .<TestInput, String>define()
                    .deriving(Outcome.lift(String::toUpperCase))
                        .ensure("Not empty", s -> !s.isEmpty())
                    .build();

            assertThat(contract.postconditionCount()).isEqualTo(1);
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
                    .deriving("Valid number", s -> Outcome.success(42))
                        .ensure("Positive", n -> n > 0)
                    .build();

            assertThat(contract.toString())
                    .isEqualTo("ServiceContract[preconditions=1, derivations=1, postconditions=2]");
        }
    }
}

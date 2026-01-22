package org.javai.punit.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Derivation")
class DerivationTest {

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates fallible derivation with description")
        void createsFallibleDerivation() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> Outcome.success(Integer.parseInt(s)),
                    List.of(new Postcondition<>("Positive", n -> n > 0)));

            assertThat(derivation.description()).isEqualTo("Valid number");
            assertThat(derivation.isFallible()).isTrue();
            assertThat(derivation.postconditions()).hasSize(1);
        }

        @Test
        @DisplayName("creates infallible derivation without description")
        void createsInfallibleDerivation() {
            Derivation<String, String> derivation = new Derivation<>(
                    null,
                    s -> Outcome.success(s.toUpperCase()),
                    List.of(new Postcondition<>("Not empty", s -> !s.isEmpty())));

            assertThat(derivation.description()).isNull();
            assertThat(derivation.isFallible()).isFalse();
        }

        @Test
        @DisplayName("throws when description is blank")
        void throwsWhenDescriptionIsBlank() {
            assertThatThrownBy(() -> new Derivation<>(
                    "   ",
                    s -> Outcome.success(s),
                    List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("description must not be blank");
        }

        @Test
        @DisplayName("throws when function is null")
        void throwsWhenFunctionIsNull() {
            assertThatThrownBy(() -> new Derivation<String, String>(
                    "Test",
                    null,
                    List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("function must not be null");
        }

        @Test
        @DisplayName("throws when postconditions is null")
        void throwsWhenPostconditionsIsNull() {
            assertThatThrownBy(() -> new Derivation<>(
                    "Test",
                    s -> Outcome.success(s),
                    null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("postconditions must not be null");
        }
    }

    @Nested
    @DisplayName("evaluate() - fallible derivation")
    class FallibleEvaluateTests {

        @Test
        @DisplayName("returns passed derivation and evaluated postconditions when derivation succeeds")
        void returnsPassedWhenDerivationSucceeds() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> Outcome.success(Integer.parseInt(s)),
                    List.of(
                            new Postcondition<>("Positive", n -> n > 0),
                            new Postcondition<>("Less than 100", n -> n < 100)));

            List<PostconditionResult> results = derivation.evaluate("42");

            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isInstanceOf(PostconditionResult.Passed.class);
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(1)).isInstanceOf(PostconditionResult.Passed.class);
            assertThat(results.get(1).description()).isEqualTo("Positive");
            assertThat(results.get(2)).isInstanceOf(PostconditionResult.Passed.class);
            assertThat(results.get(2).description()).isEqualTo("Less than 100");
        }

        @Test
        @DisplayName("returns failed derivation and skipped postconditions when derivation fails")
        void returnsFailedWhenDerivationFails() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> {
                        try {
                            return Outcome.success(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcome.failure("Not a number");
                        }
                    },
                    List.of(
                            new Postcondition<>("Positive", n -> n > 0),
                            new Postcondition<>("Less than 100", n -> n < 100)));

            List<PostconditionResult> results = derivation.evaluate("not-a-number");

            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isInstanceOf(PostconditionResult.Failed.class);
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(((PostconditionResult.Failed) results.get(0)).reason()).isEqualTo("Not a number");
            assertThat(results.get(1)).isInstanceOf(PostconditionResult.Skipped.class);
            assertThat(results.get(1).description()).isEqualTo("Positive");
            assertThat(results.get(2)).isInstanceOf(PostconditionResult.Skipped.class);
            assertThat(results.get(2).description()).isEqualTo("Less than 100");
        }

        @Test
        @DisplayName("returns failed derivation when function throws exception")
        void returnsFailedWhenFunctionThrows() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> Outcome.success(Integer.parseInt(s)),
                    List.of(new Postcondition<>("Positive", n -> n > 0)));

            List<PostconditionResult> results = derivation.evaluate("not-a-number");

            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isInstanceOf(PostconditionResult.Failed.class);
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(1)).isInstanceOf(PostconditionResult.Skipped.class);
        }
    }

    @Nested
    @DisplayName("evaluate() - infallible derivation")
    class InfallibleEvaluateTests {

        @Test
        @DisplayName("evaluates postconditions without derivation result")
        void evaluatesPostconditionsWithoutDerivationResult() {
            Derivation<String, String> derivation = new Derivation<>(
                    null,
                    s -> Outcome.success(s.toUpperCase()),
                    List.of(
                            new Postcondition<>("Not empty", s -> !s.isEmpty()),
                            new Postcondition<>("Contains HELLO", s -> s.contains("HELLO"))));

            List<PostconditionResult> results = derivation.evaluate("hello");

            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isInstanceOf(PostconditionResult.Passed.class);
            assertThat(results.get(0).description()).isEqualTo("Not empty");
            assertThat(results.get(1)).isInstanceOf(PostconditionResult.Passed.class);
            assertThat(results.get(1).description()).isEqualTo("Contains HELLO");
        }

        @Test
        @DisplayName("returns empty list when function throws and no description")
        void returnsEmptyWhenFunctionThrowsAndInfallible() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    null,
                    s -> Outcome.success(s.length() / 0), // will throw
                    List.of(new Postcondition<>("Positive", n -> n > 0)));

            List<PostconditionResult> results = derivation.evaluate("test");

            // Infallible derivation has no description, so no result is added
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("returns descriptive string for fallible derivation")
        void returnsDescriptiveStringForFallible() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> Outcome.success(Integer.parseInt(s)),
                    List.of(new Postcondition<>("Positive", n -> n > 0)));

            assertThat(derivation.toString()).isEqualTo("Derivation[Valid number, postconditions=1]");
        }

        @Test
        @DisplayName("returns descriptive string for infallible derivation")
        void returnsDescriptiveStringForInfallible() {
            Derivation<String, String> derivation = new Derivation<>(
                    null,
                    s -> Outcome.success(s.toUpperCase()),
                    List.of());

            assertThat(derivation.toString()).isEqualTo("Derivation[(infallible), postconditions=0]");
        }
    }
}

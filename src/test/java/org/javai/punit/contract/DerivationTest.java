package org.javai.punit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Derivation")
class DerivationTest {

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates derivation with description")
        void createsDerivation() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> Outcomes.ok(Integer.parseInt(s)),
                    List.of(Postcondition.simple("Positive", n -> n > 0)));

            assertThat(derivation.description()).isEqualTo("Valid number");
            assertThat(derivation.postconditions()).hasSize(1);
        }

        @Test
        @DisplayName("throws when description is null")
        void throwsWhenDescriptionIsNull() {
            assertThatThrownBy(() -> new Derivation<>(
                    null,
                    s -> Outcomes.ok(s),
                    List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description must not be null");
        }

        @Test
        @DisplayName("throws when description is blank")
        void throwsWhenDescriptionIsBlank() {
            assertThatThrownBy(() -> new Derivation<>(
                    "   ",
                    s -> Outcomes.ok(s),
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
                    s -> Outcomes.ok(s),
                    null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("postconditions must not be null");
        }
    }

    @Nested
    @DisplayName("evaluate()")
    class EvaluateTests {

        @Test
        @DisplayName("returns passed derivation and evaluated postconditions when derivation succeeds")
        void returnsPassedWhenDerivationSucceeds() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> Outcomes.ok(Integer.parseInt(s)),
                    List.of(
                            Postcondition.simple("Positive", n -> n > 0),
                            Postcondition.simple("Less than 100", n -> n < 100)));

            List<PostconditionResult> results = derivation.evaluate("42");

            assertThat(results).hasSize(3);
            assertThat(results.get(0).passed()).isTrue();
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(1).passed()).isTrue();
            assertThat(results.get(1).description()).isEqualTo("Positive");
            assertThat(results.get(2).passed()).isTrue();
            assertThat(results.get(2).description()).isEqualTo("Less than 100");
        }

        @Test
        @DisplayName("returns failed derivation and skipped postconditions when derivation fails")
        void returnsFailedWhenDerivationFails() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> {
                        try {
                            return Outcomes.ok(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Outcomes.fail("Not a number");
                        }
                    },
                    List.of(
                            Postcondition.simple("Positive", n -> n > 0),
                            Postcondition.simple("Less than 100", n -> n < 100)));

            List<PostconditionResult> results = derivation.evaluate("not-a-number");

            assertThat(results).hasSize(3);
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(0).failureReason()).hasValue("Not a number");
            // Skipped postconditions are represented as failures with "Skipped:" prefix
            assertThat(results.get(1).failed()).isTrue();
            assertThat(results.get(1).description()).isEqualTo("Positive");
            assertThat(results.get(1).failureReason()).hasValueSatisfying(r -> assertThat(r).startsWith("Skipped:"));
            assertThat(results.get(2).failed()).isTrue();
            assertThat(results.get(2).description()).isEqualTo("Less than 100");
            assertThat(results.get(2).failureReason()).hasValueSatisfying(r -> assertThat(r).startsWith("Skipped:"));
        }

        @Test
        @DisplayName("returns failed derivation when function throws exception")
        void returnsFailedWhenFunctionThrows() {
            Derivation<String, Integer> derivation = new Derivation<>(
                    "Valid number",
                    s -> Outcomes.ok(Integer.parseInt(s)),
                    List.of(Postcondition.simple("Positive", n -> n > 0)));

            List<PostconditionResult> results = derivation.evaluate("not-a-number");

            assertThat(results).hasSize(2);
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(0).description()).isEqualTo("Valid number");
            assertThat(results.get(1).failed()).isTrue();
            assertThat(results.get(1).failureReason()).hasValueSatisfying(r -> assertThat(r).startsWith("Skipped:"));
        }

        @Test
        @DisplayName("evaluates with no nested postconditions")
        void evaluatesWithNoNestedPostconditions() {
            Derivation<String, String> derivation = new Derivation<>(
                    "Uppercase",
                    s -> Outcomes.ok(s.toUpperCase()),
                    List.of());

            List<PostconditionResult> results = derivation.evaluate("hello");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).passed()).isTrue();
            assertThat(results.get(0).description()).isEqualTo("Uppercase");
        }
    }
}

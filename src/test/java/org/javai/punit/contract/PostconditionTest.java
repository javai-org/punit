package org.javai.punit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Postcondition")
class PostconditionTest {

    @Nested
    @DisplayName("constructor (rich form)")
    class ConstructorTests {

        @Test
        @DisplayName("creates postcondition with description and check function")
        void createsPostcondition() {
            Postcondition<String> postcondition = new Postcondition<>(
                    "Is not empty", s -> s.isEmpty() ? Outcomes.fail("Was empty") : Outcomes.okVoid());

            assertThat(postcondition.description()).isEqualTo("Is not empty");
            assertThat(postcondition.check()).isNotNull();
        }

        @Test
        @DisplayName("throws when description is null")
        void throwsWhenDescriptionIsNull() {
            assertThatThrownBy(() -> new Postcondition<String>(null, s -> Outcomes.okVoid()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description must not be null");
        }

        @Test
        @DisplayName("throws when description is blank")
        void throwsWhenDescriptionIsBlank() {
            assertThatThrownBy(() -> new Postcondition<String>("   ", s -> Outcomes.okVoid()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("description must not be blank");
        }

        @Test
        @DisplayName("throws when check is null")
        void throwsWhenCheckIsNull() {
            assertThatThrownBy(() -> new Postcondition<String>("Valid", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("check must not be null");
        }
    }

    @Nested
    @DisplayName("simple() factory method")
    class SimpleFactoryTests {

        @Test
        @DisplayName("creates postcondition from predicate")
        void createsFromPredicate() {
            Postcondition<String> postcondition = Postcondition.simple(
                    "Is not empty", s -> !s.isEmpty());

            assertThat(postcondition.description()).isEqualTo("Is not empty");
            assertThat(postcondition.check()).isNotNull();
        }

        @Test
        @DisplayName("throws when predicate is null")
        void throwsWhenPredicateIsNull() {
            assertThatThrownBy(() -> Postcondition.simple("Valid", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("predicate must not be null");
        }
    }

    @Nested
    @DisplayName("evaluate() with simple predicate")
    class EvaluateSimpleTests {

        @Test
        @DisplayName("returns passed when predicate returns true")
        void returnsPassed() {
            Postcondition<String> postcondition = Postcondition.simple(
                    "Is not empty", s -> !s.isEmpty());

            PostconditionResult result = postcondition.evaluate("hello");

            assertThat(result.passed()).isTrue();
            assertThat(result.description()).isEqualTo("Is not empty");
            assertThat(result.failureReason()).isEmpty();
        }

        @Test
        @DisplayName("returns failed when predicate returns false")
        void returnsFailed() {
            Postcondition<String> postcondition = Postcondition.simple(
                    "Is not empty", s -> !s.isEmpty());

            PostconditionResult result = postcondition.evaluate("");

            assertThat(result.failed()).isTrue();
            assertThat(result.description()).isEqualTo("Is not empty");
            assertThat(result.failureReason()).hasValue("Postcondition not satisfied");
        }

        @Test
        @DisplayName("returns failed with message when predicate throws exception")
        void returnsFailedWhenPredicateThrows() {
            Postcondition<String> postcondition = Postcondition.simple(
                    "Has length", s -> s.length() > 0);

            PostconditionResult result = postcondition.evaluate(null);

            assertThat(result.failed()).isTrue();
            assertThat(result.description()).isEqualTo("Has length");
            assertThat(result.failureReason()).isPresent();
            // Modern Java NPE provides descriptive message about the null access
            assertThat(result.failureReason()).hasValueSatisfying(r -> assertThat(r).containsIgnoringCase("null"));
        }

        @Test
        @DisplayName("evaluates predicate lazily")
        void evaluatesLazily() {
            int[] callCount = {0};
            Postcondition<String> postcondition = Postcondition.simple(
                    "Tracks calls", s -> {
                        callCount[0]++;
                        return true;
                    });

            assertThat(callCount[0]).isZero();

            postcondition.evaluate("test");

            assertThat(callCount[0]).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("evaluate() with rich check function")
    class EvaluateRichTests {

        @Test
        @DisplayName("returns passed when check returns ok")
        void returnsPassed() {
            Postcondition<String> postcondition = new Postcondition<>(
                    "Is not empty", s -> s.isEmpty() ? Outcomes.fail("Was empty") : Outcomes.okVoid());

            PostconditionResult result = postcondition.evaluate("hello");

            assertThat(result.passed()).isTrue();
            assertThat(result.description()).isEqualTo("Is not empty");
            assertThat(result.failureReason()).isEmpty();
        }

        @Test
        @DisplayName("returns failed with custom reason when check returns failure")
        void returnsFailedWithCustomReason() {
            Postcondition<String> postcondition = new Postcondition<>(
                    "Is not empty", s -> s.isEmpty() ? Outcomes.fail("Was empty") : Outcomes.okVoid());

            PostconditionResult result = postcondition.evaluate("");

            assertThat(result.failed()).isTrue();
            assertThat(result.description()).isEqualTo("Is not empty");
            assertThat(result.failureReason()).hasValue("Was empty");
        }

        @Test
        @DisplayName("returns failed with detailed reason for complex validation")
        void returnsDetailedFailureReason() {
            Postcondition<Integer> postcondition = new Postcondition<>(
                    "Is in valid range", value -> {
                        if (value < 0) {
                            return Outcomes.fail("Value " + value + " is negative");
                        }
                        if (value > 100) {
                            return Outcomes.fail("Value " + value + " exceeds maximum of 100");
                        }
                        return Outcomes.okVoid();
                    });

            PostconditionResult resultNegative = postcondition.evaluate(-5);
            assertThat(resultNegative.failed()).isTrue();
            assertThat(resultNegative.failureReason()).hasValue("Value -5 is negative");

            PostconditionResult resultTooLarge = postcondition.evaluate(150);
            assertThat(resultTooLarge.failed()).isTrue();
            assertThat(resultTooLarge.failureReason()).hasValue("Value 150 exceeds maximum of 100");

            PostconditionResult resultValid = postcondition.evaluate(50);
            assertThat(resultValid.passed()).isTrue();
        }

        @Test
        @DisplayName("returns failed with message when check throws exception")
        void returnsFailedWhenCheckThrows() {
            Postcondition<String> postcondition = new Postcondition<>(
                    "Has length", s -> s.length() > 0 ? Outcomes.okVoid() : Outcomes.fail("Empty"));

            PostconditionResult result = postcondition.evaluate(null);

            assertThat(result.failed()).isTrue();
            assertThat(result.description()).isEqualTo("Has length");
            assertThat(result.failureReason()).isPresent();
            assertThat(result.failureReason()).hasValueSatisfying(r -> assertThat(r).containsIgnoringCase("null"));
        }
    }

    @Nested
    @DisplayName("skip()")
    class SkipTests {

        @Test
        @DisplayName("creates failed result with skip reason")
        void createsSkippedResult() {
            Postcondition<String> postcondition = Postcondition.simple(
                    "Has operations", s -> true);

            PostconditionResult result = postcondition.skip("Derivation 'Valid JSON' failed");

            assertThat(result.failed()).isTrue();
            assertThat(result.description()).isEqualTo("Has operations");
            assertThat(result.failureReason()).hasValue("Skipped: Derivation 'Valid JSON' failed");
        }
    }

}

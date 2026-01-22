package org.javai.punit.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Outcome")
class OutcomeTest {

    @Nested
    @DisplayName("success()")
    class SuccessTests {

        @Test
        @DisplayName("creates a successful outcome with the given value")
        void createsSuccessfulOutcome() {
            Outcome<String> outcome = Outcome.success("hello");

            assertThat(outcome.isSuccess()).isTrue();
            assertThat(outcome.isFailure()).isFalse();
            assertThat(outcome.value()).isEqualTo("hello");
        }

        @Test
        @DisplayName("throws when value is null")
        void throwsWhenValueIsNull() {
            assertThatThrownBy(() -> Outcome.success(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("value must not be null");
        }

        @Test
        @DisplayName("throws when accessing failureReason on success")
        void throwsWhenAccessingFailureReasonOnSuccess() {
            Outcome<String> outcome = Outcome.success("hello");

            assertThatThrownBy(outcome::failureReason)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot get failure reason from a successful outcome");
        }
    }

    @Nested
    @DisplayName("failure()")
    class FailureTests {

        @Test
        @DisplayName("creates a failed outcome with the given reason")
        void createsFailedOutcome() {
            Outcome<String> outcome = Outcome.failure("Parse error");

            assertThat(outcome.isSuccess()).isFalse();
            assertThat(outcome.isFailure()).isTrue();
            assertThat(outcome.failureReason()).isEqualTo("Parse error");
        }

        @Test
        @DisplayName("throws when reason is null")
        void throwsWhenReasonIsNull() {
            assertThatThrownBy(() -> Outcome.failure(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("reason must not be null");
        }

        @Test
        @DisplayName("throws when reason is blank")
        void throwsWhenReasonIsBlank() {
            assertThatThrownBy(() -> Outcome.failure("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason must not be blank");
        }

        @Test
        @DisplayName("throws when accessing value on failure")
        void throwsWhenAccessingValueOnFailure() {
            Outcome<String> outcome = Outcome.failure("Parse error");

            assertThatThrownBy(outcome::value)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot get value from a failed outcome");
        }
    }

    @Nested
    @DisplayName("lift()")
    class LiftTests {

        @Test
        @DisplayName("wraps pure function result in success")
        void wrapsPureFunctionInSuccess() {
            Function<String, Outcome<String>> lifted = Outcome.lift(String::toUpperCase);

            Outcome<String> result = lifted.apply("hello");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("works with complex transformations")
        void worksWithComplexTransformations() {
            Function<Integer, Outcome<String>> lifted = Outcome.lift(n -> "Number: " + n);

            Outcome<String> result = lifted.apply(42);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo("Number: 42");
        }

        @Test
        @DisplayName("throws when function is null")
        void throwsWhenFunctionIsNull() {
            assertThatThrownBy(() -> Outcome.lift(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("fn must not be null");
        }
    }

    @Nested
    @DisplayName("pattern matching")
    class PatternMatchingTests {

        @Test
        @DisplayName("can pattern match on Success")
        void canPatternMatchOnSuccess() {
            Outcome<String> outcome = Outcome.success("hello");

            String result = switch (outcome) {
                case Outcome.Success<String> s -> "Got: " + s.value();
                case Outcome.Failure<String> f -> "Error: " + f.reason();
            };

            assertThat(result).isEqualTo("Got: hello");
        }

        @Test
        @DisplayName("can pattern match on Failure")
        void canPatternMatchOnFailure() {
            Outcome<String> outcome = Outcome.failure("Parse error");

            String result = switch (outcome) {
                case Outcome.Success<String> s -> "Got: " + s.value();
                case Outcome.Failure<String> f -> "Error: " + f.reason();
            };

            assertThat(result).isEqualTo("Error: Parse error");
        }
    }
}

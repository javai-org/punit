package org.javai.punit.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Postcondition")
class PostconditionTest {

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates postcondition with description and predicate")
        void createsPostcondition() {
            Postcondition<String> postcondition = new Postcondition<>(
                    "Is not empty", s -> !s.isEmpty());

            assertThat(postcondition.description()).isEqualTo("Is not empty");
            assertThat(postcondition.predicate()).isNotNull();
        }

        @Test
        @DisplayName("throws when description is null")
        void throwsWhenDescriptionIsNull() {
            assertThatThrownBy(() -> new Postcondition<String>(null, s -> true))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description must not be null");
        }

        @Test
        @DisplayName("throws when description is blank")
        void throwsWhenDescriptionIsBlank() {
            assertThatThrownBy(() -> new Postcondition<String>("   ", s -> true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("description must not be blank");
        }

        @Test
        @DisplayName("throws when predicate is null")
        void throwsWhenPredicateIsNull() {
            assertThatThrownBy(() -> new Postcondition<String>("Valid", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("predicate must not be null");
        }
    }

    @Nested
    @DisplayName("evaluate()")
    class EvaluateTests {

        @Test
        @DisplayName("returns Passed when predicate returns true")
        void returnsPassed() {
            Postcondition<String> postcondition = new Postcondition<>(
                    "Is not empty", s -> !s.isEmpty());

            PostconditionResult result = postcondition.evaluate("hello");

            assertThat(result).isInstanceOf(PostconditionResult.Passed.class);
            assertThat(result.description()).isEqualTo("Is not empty");
            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("returns Failed when predicate returns false")
        void returnsFailed() {
            Postcondition<String> postcondition = new Postcondition<>(
                    "Is not empty", s -> !s.isEmpty());

            PostconditionResult result = postcondition.evaluate("");

            assertThat(result).isInstanceOf(PostconditionResult.Failed.class);
            assertThat(result.description()).isEqualTo("Is not empty");
            assertThat(result.failed()).isTrue();
            assertThat(((PostconditionResult.Failed) result).reason()).isNull();
        }

        @Test
        @DisplayName("returns Failed with message when predicate throws exception")
        void returnsFailedWhenPredicateThrows() {
            Postcondition<String> postcondition = new Postcondition<>(
                    "Has length", s -> s.length() > 0);

            PostconditionResult result = postcondition.evaluate(null);

            assertThat(result).isInstanceOf(PostconditionResult.Failed.class);
            assertThat(result.description()).isEqualTo("Has length");
            assertThat(result.failed()).isTrue();
            assertThat(((PostconditionResult.Failed) result).reason()).isNotNull();
        }

        @Test
        @DisplayName("evaluates predicate lazily")
        void evaluatesLazily() {
            int[] callCount = {0};
            Postcondition<String> postcondition = new Postcondition<>(
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
    @DisplayName("skip()")
    class SkipTests {

        @Test
        @DisplayName("creates Skipped result with description and reason")
        void createsSkippedResult() {
            Postcondition<String> postcondition = new Postcondition<>(
                    "Has operations", s -> true);

            PostconditionResult result = postcondition.skip("Derivation 'Valid JSON' failed");

            assertThat(result).isInstanceOf(PostconditionResult.Skipped.class);
            assertThat(result.description()).isEqualTo("Has operations");
            assertThat(result.skipped()).isTrue();
            assertThat(((PostconditionResult.Skipped) result).reason())
                    .isEqualTo("Derivation 'Valid JSON' failed");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("returns descriptive string")
        void returnsDescriptiveString() {
            Postcondition<String> postcondition = new Postcondition<>(
                    "Has operations", s -> true);

            assertThat(postcondition.toString()).isEqualTo("Postcondition[Has operations]");
        }
    }
}

package org.javai.punit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PostconditionResult")
class PostconditionResultTest {

    @Nested
    @DisplayName("passed()")
    class PassedTests {

        @Test
        @DisplayName("creates passed result with description")
        void createsPassedResult() {
            PostconditionResult result = PostconditionResult.passed("Valid JSON");

            assertThat(result.description()).isEqualTo("Valid JSON");
            assertThat(result.passed()).isTrue();
            assertThat(result.failed()).isFalse();
            assertThat(result.failureReason()).isEmpty();
        }

        @Test
        @DisplayName("creates passed result with value")
        void createsPassedResultWithValue() {
            PostconditionResult result = PostconditionResult.passed("Parse number", 42);

            assertThat(result.description()).isEqualTo("Parse number");
            assertThat(result.passed()).isTrue();
            assertThat(result.failed()).isFalse();
            assertThat(result.outcome().getOrThrow()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("failed()")
    class FailedTests {

        @Test
        @DisplayName("creates failed result with description and reason")
        void createsFailedResultWithReason() {
            PostconditionResult result = PostconditionResult.failed("Has operations", "Array was empty");

            assertThat(result.description()).isEqualTo("Has operations");
            assertThat(result.passed()).isFalse();
            assertThat(result.failed()).isTrue();
            assertThat(result.failureReason()).hasValue("Array was empty");
        }

        @Test
        @DisplayName("creates failed result with default reason")
        void createsFailedResultWithDefaultReason() {
            PostconditionResult result = PostconditionResult.failed("Has operations");

            assertThat(result.description()).isEqualTo("Has operations");
            assertThat(result.failed()).isTrue();
            assertThat(result.failureReason()).hasValue("Postcondition not satisfied");
        }

        @Test
        @DisplayName("throws when reason is null")
        void throwsWhenReasonIsNull() {
            assertThatThrownBy(() -> PostconditionResult.failed("desc", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("reason must not be null");
        }
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("throws when description is null")
        void throwsWhenDescriptionIsNull() {
            assertThatThrownBy(() -> new PostconditionResult(null, Outcomes.okVoid()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description must not be null");
        }

        @Test
        @DisplayName("throws when outcome is null")
        void throwsWhenOutcomeIsNull() {
            assertThatThrownBy(() -> new PostconditionResult("desc", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("outcome must not be null");
        }
    }

    @Nested
    @DisplayName("outcome access")
    class OutcomeAccessTests {

        @Test
        @DisplayName("can access underlying outcome for passed result")
        void canAccessOutcomeForPassed() {
            PostconditionResult result = PostconditionResult.passed("test", "value");

            assertThat(result.outcome().isOk()).isTrue();
            assertThat(result.outcome().getOrThrow()).isEqualTo("value");
        }

        @Test
        @DisplayName("can access underlying outcome for failed result")
        void canAccessOutcomeForFailed() {
            PostconditionResult result = PostconditionResult.failed("test", "error message");

            assertThat(result.outcome().isOk()).isFalse();
            assertThat(Outcomes.failureMessage(result.outcome())).isEqualTo("error message");
        }
    }
}

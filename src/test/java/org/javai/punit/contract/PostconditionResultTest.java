package org.javai.punit.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PostconditionResult")
class PostconditionResultTest {

    @Nested
    @DisplayName("Passed")
    class PassedTests {

        @Test
        @DisplayName("creates passed result with description")
        void createsPassedResult() {
            PostconditionResult result = new PostconditionResult.Passed("Valid JSON");

            assertThat(result.description()).isEqualTo("Valid JSON");
            assertThat(result.passed()).isTrue();
            assertThat(result.failed()).isFalse();
            assertThat(result.skipped()).isFalse();
            assertThat(result.wasEvaluated()).isTrue();
        }

        @Test
        @DisplayName("throws when description is null")
        void throwsWhenDescriptionIsNull() {
            assertThatThrownBy(() -> new PostconditionResult.Passed(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description must not be null");
        }
    }

    @Nested
    @DisplayName("Failed")
    class FailedTests {

        @Test
        @DisplayName("creates failed result with description and reason")
        void createsFailedResultWithReason() {
            PostconditionResult result = new PostconditionResult.Failed("Has operations", "Array was empty");

            assertThat(result.description()).isEqualTo("Has operations");
            assertThat(result.passed()).isFalse();
            assertThat(result.failed()).isTrue();
            assertThat(result.skipped()).isFalse();
            assertThat(result.wasEvaluated()).isTrue();
            assertThat(((PostconditionResult.Failed) result).reason()).isEqualTo("Array was empty");
        }

        @Test
        @DisplayName("creates failed result with no reason")
        void createsFailedResultWithoutReason() {
            PostconditionResult result = new PostconditionResult.Failed("Has operations");

            assertThat(result.description()).isEqualTo("Has operations");
            assertThat(result.failed()).isTrue();
            assertThat(((PostconditionResult.Failed) result).reason()).isNull();
        }

        @Test
        @DisplayName("throws when description is null")
        void throwsWhenDescriptionIsNull() {
            assertThatThrownBy(() -> new PostconditionResult.Failed(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description must not be null");
        }
    }

    @Nested
    @DisplayName("Skipped")
    class SkippedTests {

        @Test
        @DisplayName("creates skipped result with description and reason")
        void createsSkippedResult() {
            PostconditionResult result = new PostconditionResult.Skipped(
                    "Has operations", "Derivation 'Valid JSON' failed");

            assertThat(result.description()).isEqualTo("Has operations");
            assertThat(result.passed()).isFalse();
            assertThat(result.failed()).isFalse();
            assertThat(result.skipped()).isTrue();
            assertThat(result.wasEvaluated()).isFalse();
            assertThat(((PostconditionResult.Skipped) result).reason())
                    .isEqualTo("Derivation 'Valid JSON' failed");
        }

        @Test
        @DisplayName("throws when description is null")
        void throwsWhenDescriptionIsNull() {
            assertThatThrownBy(() -> new PostconditionResult.Skipped(null, "reason"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description must not be null");
        }

        @Test
        @DisplayName("throws when reason is null")
        void throwsWhenReasonIsNull() {
            assertThatThrownBy(() -> new PostconditionResult.Skipped("description", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("reason must not be null");
        }
    }

    @Nested
    @DisplayName("pattern matching")
    class PatternMatchingTests {

        @Test
        @DisplayName("can pattern match on all result types")
        void canPatternMatchOnAllTypes() {
            assertThat(describe(new PostconditionResult.Passed("A"))).isEqualTo("PASSED: A");
            assertThat(describe(new PostconditionResult.Failed("B", "reason"))).isEqualTo("FAILED: B");
            assertThat(describe(new PostconditionResult.Skipped("C", "reason"))).isEqualTo("SKIPPED: C");
        }

        private String describe(PostconditionResult result) {
            return switch (result) {
                case PostconditionResult.Passed p -> "PASSED: " + p.description();
                case PostconditionResult.Failed f -> "FAILED: " + f.description();
                case PostconditionResult.Skipped s -> "SKIPPED: " + s.description();
            };
        }
    }
}

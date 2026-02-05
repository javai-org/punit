package org.javai.punit.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DurationConstraint")
class DurationConstraintTest {

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("creates constraint with default description")
        void createsWithDefaultDescription() {
            DurationConstraint constraint = DurationConstraint.of(Duration.ofMillis(500));

            assertThat(constraint.description()).isEqualTo("Duration below 500ms");
            assertThat(constraint.maxDuration()).isEqualTo(Duration.ofMillis(500));
        }

        @Test
        @DisplayName("creates constraint with custom description")
        void createsWithCustomDescription() {
            DurationConstraint constraint = DurationConstraint.of("API response time", Duration.ofMillis(200));

            assertThat(constraint.description()).isEqualTo("API response time");
            assertThat(constraint.maxDuration()).isEqualTo(Duration.ofMillis(200));
        }

        @Test
        @DisplayName("formats seconds in description")
        void formatsSecondsInDescription() {
            DurationConstraint constraint = DurationConstraint.of(Duration.ofSeconds(2));

            assertThat(constraint.description()).isEqualTo("Duration below 2.0s");
        }

        @Test
        @DisplayName("formats minutes in description")
        void formatsMinutesInDescription() {
            DurationConstraint constraint = DurationConstraint.of(Duration.ofMinutes(2));

            assertThat(constraint.description()).isEqualTo("Duration below 2.0m");
        }

        @Test
        @DisplayName("rejects null maxDuration")
        void rejectsNullMaxDuration() {
            assertThatThrownBy(() -> DurationConstraint.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("maxDuration");
        }

        @Test
        @DisplayName("rejects zero maxDuration")
        void rejectsZeroMaxDuration() {
            assertThatThrownBy(() -> DurationConstraint.of(Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("rejects negative maxDuration")
        void rejectsNegativeMaxDuration() {
            assertThatThrownBy(() -> DurationConstraint.of(Duration.ofMillis(-100)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }
    }

    @Nested
    @DisplayName("evaluation")
    class Evaluation {

        @Test
        @DisplayName("passes when actual is below limit")
        void passesWhenBelowLimit() {
            DurationConstraint constraint = DurationConstraint.of(Duration.ofMillis(500));

            DurationResult result = constraint.evaluate(Duration.ofMillis(200));

            assertThat(result.passed()).isTrue();
            assertThat(result.failed()).isFalse();
        }

        @Test
        @DisplayName("passes when actual equals limit")
        void passesWhenEqualsLimit() {
            DurationConstraint constraint = DurationConstraint.of(Duration.ofMillis(500));

            DurationResult result = constraint.evaluate(Duration.ofMillis(500));

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("fails when actual exceeds limit")
        void failsWhenExceedsLimit() {
            DurationConstraint constraint = DurationConstraint.of(Duration.ofMillis(500));

            DurationResult result = constraint.evaluate(Duration.ofMillis(600));

            assertThat(result.passed()).isFalse();
            assertThat(result.failed()).isTrue();
        }

        @Test
        @DisplayName("rejects null actual duration")
        void rejectsNullActual() {
            DurationConstraint constraint = DurationConstraint.of(Duration.ofMillis(500));

            assertThatThrownBy(() -> constraint.evaluate(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("actual");
        }
    }
}

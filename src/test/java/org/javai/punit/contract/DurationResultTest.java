package org.javai.punit.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DurationResult")
class DurationResultTest {

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("creates passed result")
        void createsPassedResult() {
            DurationResult result = new DurationResult(
                    "API response time",
                    Duration.ofMillis(500),
                    Duration.ofMillis(200),
                    true
            );

            assertThat(result.description()).isEqualTo("API response time");
            assertThat(result.limit()).isEqualTo(Duration.ofMillis(500));
            assertThat(result.actual()).isEqualTo(Duration.ofMillis(200));
            assertThat(result.passed()).isTrue();
            assertThat(result.failed()).isFalse();
        }

        @Test
        @DisplayName("creates failed result")
        void createsFailedResult() {
            DurationResult result = new DurationResult(
                    "API response time",
                    Duration.ofMillis(500),
                    Duration.ofMillis(800),
                    false
            );

            assertThat(result.passed()).isFalse();
            assertThat(result.failed()).isTrue();
        }

        @Test
        @DisplayName("rejects null description")
        void rejectsNullDescription() {
            assertThatThrownBy(() -> new DurationResult(null, Duration.ofMillis(500), Duration.ofMillis(200), true))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description");
        }

        @Test
        @DisplayName("rejects null limit")
        void rejectsNullLimit() {
            assertThatThrownBy(() -> new DurationResult("test", null, Duration.ofMillis(200), true))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("limit");
        }

        @Test
        @DisplayName("rejects null actual")
        void rejectsNullActual() {
            assertThatThrownBy(() -> new DurationResult("test", Duration.ofMillis(500), null, true))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("actual");
        }
    }

    @Nested
    @DisplayName("message formatting")
    class MessageFormatting {

        @Test
        @DisplayName("formats passed message with actual and limit")
        void formatsPassedMessage() {
            DurationResult result = new DurationResult(
                    "API response time",
                    Duration.ofMillis(500),
                    Duration.ofMillis(200),
                    true
            );

            assertThat(result.message()).isEqualTo("API response time: 200ms (limit: 500ms)");
        }

        @Test
        @DisplayName("formats failed message with exceeded notice")
        void formatsFailedMessage() {
            DurationResult result = new DurationResult(
                    "API response time",
                    Duration.ofMillis(500),
                    Duration.ofMillis(800),
                    false
            );

            assertThat(result.message()).isEqualTo("API response time: 800ms exceeded limit of 500ms");
        }

        @Test
        @DisplayName("failureMessage is same as message")
        void failureMessageSameAsMessage() {
            DurationResult result = new DurationResult(
                    "test",
                    Duration.ofMillis(500),
                    Duration.ofMillis(800),
                    false
            );

            assertThat(result.failureMessage()).isEqualTo(result.message());
        }

        @Test
        @DisplayName("formats seconds correctly")
        void formatsSecondsCorrectly() {
            DurationResult result = new DurationResult(
                    "test",
                    Duration.ofSeconds(5),
                    Duration.ofMillis(2500),
                    true
            );

            assertThat(result.message()).isEqualTo("test: 2.5s (limit: 5.0s)");
        }

        @Test
        @DisplayName("formats minutes correctly")
        void formatsMinutesCorrectly() {
            DurationResult result = new DurationResult(
                    "test",
                    Duration.ofMinutes(2),
                    Duration.ofSeconds(90),
                    true
            );

            assertThat(result.message()).isEqualTo("test: 1.5m (limit: 2.0m)");
        }
    }
}

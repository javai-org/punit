package org.javai.punit.controls.pacing;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PacingConfiguration}.
 */
class PacingConfigurationTest {

    @Nested
    @DisplayName("hasPacing()")
    class HasPacingTests {

        @Test
        @DisplayName("noPacing returns false")
        void noPacing_returnsFalse() {
            PacingConfiguration config = PacingConfiguration.noPacing();
            assertThat(config.hasPacing()).isFalse();
        }

        @Test
        @DisplayName("Returns true when maxRequestsPerSecond is set")
        void maxRps_returnsTrue() {
            PacingConfiguration config = new PacingConfiguration(
                    2.0, 0, 0, 0, 0, 500, 1, 50000, 2.0);
            assertThat(config.hasPacing()).isTrue();
        }

        @Test
        @DisplayName("Returns true when maxRequestsPerMinute is set")
        void maxRpm_returnsTrue() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 100000, 1.0);
            assertThat(config.hasPacing()).isTrue();
        }

        @Test
        @DisplayName("Returns true when maxRequestsPerHour is set")
        void maxRph_returnsTrue() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 0, 3600, 0, 0, 1000, 1, 100000, 1.0);
            assertThat(config.hasPacing()).isTrue();
        }

        @Test
        @DisplayName("Returns true when maxConcurrentRequests > 1")
        void maxConcurrent_returnsTrue() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 0, 0, 3, 0, 0, 3, 0, Double.MAX_VALUE);
            assertThat(config.hasPacing()).isTrue();
        }

        @Test
        @DisplayName("Returns true when minMsPerSample is set")
        void minMs_returnsTrue() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 0, 0, 0, 500, 500, 1, 50000, 2.0);
            assertThat(config.hasPacing()).isTrue();
        }
    }

    @Nested
    @DisplayName("isConcurrent()")
    class IsConcurrentTests {

        @Test
        @DisplayName("Returns false when effectiveConcurrency is 1")
        void sequential_returnsFalse() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 100000, 1.0);
            assertThat(config.isConcurrent()).isFalse();
        }

        @Test
        @DisplayName("Returns true when effectiveConcurrency > 1")
        void concurrent_returnsTrue() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 60, 0, 3, 0, 1000, 3, 100000, 1.0);
            assertThat(config.isConcurrent()).isTrue();
        }
    }

    @Nested
    @DisplayName("PacingReporter.formattedDuration()")
    class FormattedDurationTests {

        @Test
        @DisplayName("Zero duration returns '< 1s'")
        void zeroDuration() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 0, 0, 0, 0, 0, 1, 0, Double.MAX_VALUE);
            assertThat(PacingReporter.formattedDuration(config)).isEqualTo("< 1s");
        }

        @Test
        @DisplayName("Formats seconds only")
        void secondsOnly() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 0, 0, 0, 0, 0, 1, 45000, 1.0);
            assertThat(PacingReporter.formattedDuration(config)).isEqualTo("45s");
        }

        @Test
        @DisplayName("Formats minutes and seconds")
        void minutesAndSeconds() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 0, 0, 0, 0, 0, 1, 200000, 1.0);
            assertThat(PacingReporter.formattedDuration(config)).isEqualTo("3m 20s");
        }

        @Test
        @DisplayName("Formats hours, minutes and seconds")
        void hoursMinutesSeconds() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 0, 0, 0, 0, 0, 1, 3725000, 1.0);
            assertThat(PacingReporter.formattedDuration(config)).isEqualTo("1h 2m 5s");
        }
    }

    @Nested
    @DisplayName("PacingReporter.formattedThroughput()")
    class FormattedThroughputTests {

        @Test
        @DisplayName("Unlimited RPS formats as 'unlimited'")
        void unlimited() {
            PacingConfiguration config = PacingConfiguration.noPacing();
            assertThat(PacingReporter.formattedThroughput(config)).isEqualTo("unlimited");
        }

        @Test
        @DisplayName("1 RPS formats as '60 samples/min'")
        void oneRps() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 100000, 1.0);
            assertThat(PacingReporter.formattedThroughput(config)).isEqualTo("60 samples/min");
        }

        @Test
        @DisplayName("0.5 RPS formats with decimals")
        void halfRps() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 30, 0, 0, 0, 2000, 1, 200000, 0.5);
            assertThat(PacingReporter.formattedThroughput(config)).isEqualTo("30 samples/min");
        }
    }

    @Nested
    @DisplayName("estimatedCompletionTime()")
    class EstimatedCompletionTimeTests {

        @Test
        @DisplayName("Computes completion time from start time and duration")
        void computesCompletionTime() {
            Instant startTime = Instant.parse("2024-01-15T14:00:00Z");
            PacingConfiguration config = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            Instant completionTime = config.estimatedCompletionTime(startTime);

            assertThat(completionTime).isEqualTo(Instant.parse("2024-01-15T14:03:20Z"));
        }
    }

    @Nested
    @DisplayName("PacingReporter.formatTime()")
    class FormatTimeTests {

        @Test
        @DisplayName("Formats instant as HH:mm:ss")
        void formatsTime() {
            Instant time = Instant.parse("2024-01-15T14:23:45Z");

            String formatted = PacingReporter.formatTime(time);

            // The exact output depends on system timezone, but it should be in HH:mm:ss format
            assertThat(formatted).matches("\\d{2}:\\d{2}:\\d{2}");
        }
    }

    @Nested
    @DisplayName("PacingReporter.delaySource()")
    class DelaySourceTests {

        @Test
        @DisplayName("No delay returns 'none'")
        void noDelay_returnsNone() {
            PacingConfiguration config = PacingConfiguration.noPacing();
            assertThat(PacingReporter.delaySource(config)).isEqualTo("none");
        }

        @Test
        @DisplayName("Explicit minMsPerSample is identified")
        void explicitMinMs() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 0, 0, 0, 500, 500, 1, 50000, 2.0);
            assertThat(PacingReporter.delaySource(config)).isEqualTo("explicit minMsPerSample");
        }

        @Test
        @DisplayName("Derived from RPM is identified")
        void derivedFromRpm() {
            PacingConfiguration config = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 100000, 1.0);
            assertThat(PacingReporter.delaySource(config)).contains("RPM");
        }

        @Test
        @DisplayName("Derived from RPS is identified")
        void derivedFromRps() {
            PacingConfiguration config = new PacingConfiguration(
                    2.0, 0, 0, 0, 0, 500, 1, 50000, 2.0);
            assertThat(PacingReporter.delaySource(config)).contains("RPS");
        }
    }
}

package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TimePeriodExtractor}.
 */
@DisplayName("TimePeriodExtractor")
class TimePeriodExtractorTest {

    private final TimePeriodExtractor extractor = new TimePeriodExtractor();

    @Nested
    @DisplayName("extract")
    class ExtractTests {

        @Test
        @DisplayName("should return empty list for empty input")
        void shouldReturnEmptyListForEmptyInput() {
            var result = extractor.extract(new String[0]);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should parse valid hour-only time periods")
        void shouldParseValidHourOnlyTimePeriods() {
            var result = extractor.extract(new String[]{"08:00/2h", "16:00/3h"});

            assertThat(result).hasSize(2);

            var first = result.get(0);
            assertThat(first.start()).isEqualTo(LocalTime.of(8, 0));
            assertThat(first.durationMinutes()).isEqualTo(120);
            assertThat(first.label()).isEqualTo("08:00/2h");

            var second = result.get(1);
            assertThat(second.start()).isEqualTo(LocalTime.of(16, 0));
            assertThat(second.durationMinutes()).isEqualTo(180);
            assertThat(second.label()).isEqualTo("16:00/3h");
        }

        @Test
        @DisplayName("should parse minute-only duration")
        void shouldParseMinuteOnlyDuration() {
            var result = extractor.extract(new String[]{"08:00/30m"});

            assertThat(result).hasSize(1);
            assertThat(result.get(0).start()).isEqualTo(LocalTime.of(8, 0));
            assertThat(result.get(0).durationMinutes()).isEqualTo(30);
            assertThat(result.get(0).label()).isEqualTo("08:00/30m");
        }

        @Test
        @DisplayName("should parse mixed hours-and-minutes duration")
        void shouldParseMixedDuration() {
            var result = extractor.extract(new String[]{"08:00/2h30m"});

            assertThat(result).hasSize(1);
            assertThat(result.get(0).start()).isEqualTo(LocalTime.of(8, 0));
            assertThat(result.get(0).durationMinutes()).isEqualTo(150);
            assertThat(result.get(0).label()).isEqualTo("08:00/2h30m");
        }

        @Test
        @DisplayName("should accept non-overlapping adjacent periods")
        void shouldAcceptNonOverlappingAdjacentPeriods() {
            var result = extractor.extract(new String[]{"08:00/2h", "10:00/2h"});

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should accept single period covering full day")
        void shouldAcceptSinglePeriod() {
            var result = extractor.extract(new String[]{"00:00/24h"});

            assertThat(result).hasSize(1);
            assertThat(result.get(0).start()).isEqualTo(LocalTime.of(0, 0));
            assertThat(result.get(0).durationMinutes()).isEqualTo(1440);
        }

        @Test
        @DisplayName("should accept non-overlapping sub-hour periods")
        void shouldAcceptNonOverlappingSubHourPeriods() {
            var result = extractor.extract(new String[]{"08:00/30m", "08:30/30m"});

            assertThat(result).hasSize(2);
            assertThat(result.get(0).end()).isEqualTo(LocalTime.of(8, 30));
            assertThat(result.get(1).start()).isEqualTo(LocalTime.of(8, 30));
        }
    }

    @Nested
    @DisplayName("format validation")
    class FormatValidationTests {

        @Test
        @DisplayName("should reject invalid format")
        void shouldRejectInvalidFormat() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"invalid"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("Invalid time period format");
        }

        @Test
        @DisplayName("should reject missing duration unit")
        void shouldRejectMissingDurationUnit() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"08:00/2"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("Invalid time period format");
        }

        @Test
        @DisplayName("should provide helpful message for invalid format")
        void shouldProvideHelpfulMessageForInvalidFormat() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"08:00/2"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("08:00/2")
                .hasMessageContaining("HH:mm/Nh")
                .hasMessageContaining("HH:mm/Nm")
                .hasMessageContaining("HH:mm/NhMm")
                .hasMessageContaining("e.g.");
        }

        @Test
        @DisplayName("should reject invalid hour")
        void shouldRejectInvalidHour() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"25:00/2h"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("Invalid hour");
        }

        @Test
        @DisplayName("should reject invalid minute")
        void shouldRejectInvalidMinute() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"08:61/2h"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("Invalid minute");
        }

        @Test
        @DisplayName("should reject zero minutes duration")
        void shouldRejectZeroMinutesDuration() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"08:00/0m"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("Duration must be positive");
        }

        @Test
        @DisplayName("should reject combined duration with minutes exceeding 59")
        void shouldRejectCombinedDurationWithExcessiveMinutes() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"08:00/2h60m"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("Minutes component must be 0-59");
        }
    }

    @Nested
    @DisplayName("midnight crossing")
    class MidnightCrossingTests {

        @Test
        @DisplayName("should reject period crossing midnight")
        void shouldRejectPeriodCrossingMidnight() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"23:00/2h"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("crosses midnight");
        }

        @Test
        @DisplayName("should reject sub-hour period crossing midnight")
        void shouldRejectSubHourPeriodCrossingMidnight() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"23:30/45m"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("crosses midnight");
        }

        @Test
        @DisplayName("should accept period ending at midnight")
        void shouldAcceptPeriodEndingAtMidnight() {
            var result = extractor.extract(new String[]{"22:00/2h"});

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should accept sub-hour period ending at midnight")
        void shouldAcceptSubHourPeriodEndingAtMidnight() {
            var result = extractor.extract(new String[]{"23:30/30m"});

            assertThat(result).hasSize(1);
            assertThat(result.get(0).end()).isEqualTo(java.time.LocalTime.MIDNIGHT);
        }
    }

    @Nested
    @DisplayName("overlap detection")
    class OverlapDetectionTests {

        @Test
        @DisplayName("should reject overlapping time periods")
        void shouldRejectOverlappingTimePeriods() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"08:00/4h", "10:00/2h"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("overlap");
        }

        @Test
        @DisplayName("should reject overlapping periods regardless of input order")
        void shouldRejectOverlappingPeriodsRegardlessOfOrder() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"10:00/2h", "08:00/4h"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("overlap");
        }

        @Test
        @DisplayName("should reject overlapping sub-hour periods")
        void shouldRejectOverlappingSubHourPeriods() {
            assertThatThrownBy(() -> extractor.extract(new String[]{"08:00/45m", "08:30/30m"}))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("overlap");
        }
    }
}

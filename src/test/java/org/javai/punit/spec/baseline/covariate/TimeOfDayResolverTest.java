package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.javai.punit.model.CovariateValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TimeOfDayResolver}.
 */
@DisplayName("TimeOfDayResolver")
class TimeOfDayResolverTest {

    private final TimeOfDayResolver resolver = new TimeOfDayResolver();

    @Nested
    @DisplayName("with experiment context")
    class WithExperimentContext {

        @Test
        @DisplayName("should return time window from experiment timing")
        void shouldReturnTimeWindowFromExperimentTiming() {
            var start = Instant.parse("2026-01-10T14:30:00Z");
            var end = Instant.parse("2026-01-10T14:45:00Z");
            
            var context = DefaultCovariateResolutionContext.builder()
                .experimentTiming(start, end)
                .systemTimezone(ZoneId.of("UTC"))
                .build();
            
            var result = resolver.resolve(context);
            
            assertThat(result).isInstanceOf(CovariateValue.TimeWindowValue.class);
            var window = (CovariateValue.TimeWindowValue) result;
            assertThat(window.start()).isEqualTo(LocalTime.of(14, 30));
            assertThat(window.end()).isEqualTo(LocalTime.of(14, 45));
            assertThat(window.timezone()).isEqualTo(ZoneId.of("UTC"));
        }

        @Test
        @DisplayName("should apply timezone to window times")
        void shouldApplyTimezoneToWindowTimes() {
            // 14:30 UTC = 15:30 in Europe/Paris (winter time, UTC+1)
            var start = Instant.parse("2026-01-10T14:30:00Z");
            var end = Instant.parse("2026-01-10T14:45:00Z");
            
            var context = DefaultCovariateResolutionContext.builder()
                .experimentTiming(start, end)
                .systemTimezone(ZoneId.of("Europe/Paris"))
                .build();
            
            var result = resolver.resolve(context);
            var window = (CovariateValue.TimeWindowValue) result;
            
            assertThat(window.start()).isEqualTo(LocalTime.of(15, 30));
            assertThat(window.end()).isEqualTo(LocalTime.of(15, 45));
            assertThat(window.timezone()).isEqualTo(ZoneId.of("Europe/Paris"));
        }
    }

    @Nested
    @DisplayName("without experiment context")
    class WithoutExperimentContext {

        @Test
        @DisplayName("should return current time as point-in-time window")
        void shouldReturnCurrentTimeAsPointInTimeWindow() {
            var now = Instant.parse("2026-01-10T09:15:00Z");
            
            var context = DefaultCovariateResolutionContext.builder()
                .now(now)
                .systemTimezone(ZoneId.of("UTC"))
                .build();
            
            var result = resolver.resolve(context);
            
            assertThat(result).isInstanceOf(CovariateValue.TimeWindowValue.class);
            var window = (CovariateValue.TimeWindowValue) result;
            assertThat(window.start()).isEqualTo(LocalTime.of(9, 15));
            assertThat(window.end()).isEqualTo(LocalTime.of(9, 15)); // Same as start
        }
    }

    @Nested
    @DisplayName("canonical string format")
    class CanonicalStringFormat {

        @Test
        @DisplayName("should format as 'HH:mm-HH:mm Timezone'")
        void shouldFormatCorrectly() {
            var start = Instant.parse("2026-01-10T14:30:00Z");
            var end = Instant.parse("2026-01-10T14:45:00Z");
            
            var context = DefaultCovariateResolutionContext.builder()
                .experimentTiming(start, end)
                .systemTimezone(ZoneId.of("Europe/London"))
                .build();
            
            var result = resolver.resolve(context);
            
            assertThat(result.toCanonicalString()).isEqualTo("14:30-14:45 Europe/London");
        }
    }

    @Nested
    @DisplayName("time precision truncation")
    class TimePrecisionTruncation {

        @Test
        @DisplayName("should truncate seconds and milliseconds to minute precision")
        void shouldTruncateToMinutePrecision() {
            // Times with seconds and milliseconds that should be truncated
            var start = Instant.parse("2026-01-10T07:54:22.988Z");
            var end = Instant.parse("2026-01-10T07:54:23.379Z");
            
            var context = DefaultCovariateResolutionContext.builder()
                .experimentTiming(start, end)
                .systemTimezone(ZoneId.of("UTC"))
                .build();
            
            var result = resolver.resolve(context);
            var window = (CovariateValue.TimeWindowValue) result;
            
            // Both should be truncated to 07:54
            assertThat(window.start()).isEqualTo(LocalTime.of(7, 54));
            assertThat(window.end()).isEqualTo(LocalTime.of(7, 54));
            assertThat(result.toCanonicalString()).isEqualTo("07:54-07:54 UTC");
        }

        @Test
        @DisplayName("different sub-minute times in same minute should produce identical values")
        void differentSubMinuteTimesShouldProduceIdenticalValues() {
            var zone = ZoneId.of("Europe/Zurich");
            
            // Simulate multiple resolutions within the same minute
            var context1 = DefaultCovariateResolutionContext.builder()
                .experimentTiming(
                    Instant.parse("2026-01-10T07:54:22.988Z"),
                    Instant.parse("2026-01-10T07:54:23.379Z"))
                .systemTimezone(zone)
                .build();
            
            var context2 = DefaultCovariateResolutionContext.builder()
                .experimentTiming(
                    Instant.parse("2026-01-10T07:54:01.123Z"),
                    Instant.parse("2026-01-10T07:54:59.999Z"))
                .systemTimezone(zone)
                .build();
            
            var result1 = resolver.resolve(context1);
            var result2 = resolver.resolve(context2);
            
            // Both should produce identical canonical strings (stable for hashing)
            assertThat(result1.toCanonicalString())
                .as("Sub-minute differences should produce identical values for stable hashing")
                .isEqualTo(result2.toCanonicalString());
        }

        @Test
        @DisplayName("current time should also be truncated to minute precision")
        void currentTimeShouldBeTruncatedToMinutePrecision() {
            // Time with seconds/nanos
            var now = Instant.parse("2026-01-10T09:15:47.123456789Z");
            
            var context = DefaultCovariateResolutionContext.builder()
                .now(now)
                .systemTimezone(ZoneId.of("UTC"))
                .build();
            
            var result = resolver.resolve(context);
            var window = (CovariateValue.TimeWindowValue) result;
            
            assertThat(window.start()).isEqualTo(LocalTime.of(9, 15));
            assertThat(window.end()).isEqualTo(LocalTime.of(9, 15));
        }
    }
}


package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.javai.punit.model.TimePeriodDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TimeOfDayResolver}.
 */
@DisplayName("TimeOfDayResolver")
class TimeOfDayResolverTest {

    @Nested
    @DisplayName("with matching period")
    class WithMatchingPeriod {

        @Test
        @DisplayName("should return period label when time falls within period")
        void shouldReturnPeriodLabelWhenTimeMatchesPeriod() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(8, 0), 2, "morning"),
                new TimePeriodDefinition(LocalTime.of(16, 0), 3, "evening")
            );
            var resolver = new TimeOfDayResolver(periods);

            // 9:00 UTC falls within 08:00-10:00
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T09:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("morning");
        }

        @Test
        @DisplayName("should return correct label for second period")
        void shouldReturnCorrectLabelForSecondPeriod() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(8, 0), 2, "morning"),
                new TimePeriodDefinition(LocalTime.of(16, 0), 3, "evening")
            );
            var resolver = new TimeOfDayResolver(periods);

            // 17:30 UTC falls within 16:00-19:00
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T17:30:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("evening");
        }

        @Test
        @DisplayName("should match at period start boundary (inclusive)")
        void shouldMatchAtPeriodStartBoundary() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(14, 0), 2, "afternoon")
            );
            var resolver = new TimeOfDayResolver(periods);

            // Exactly 14:00
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T14:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("afternoon");
        }
    }

    @Nested
    @DisplayName("with no matching period")
    class WithNoMatchingPeriod {

        @Test
        @DisplayName("should return OTHER when time is outside all periods")
        void shouldReturnOtherWhenTimeIsOutsideAllPeriods() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(8, 0), 2, "morning"),
                new TimePeriodDefinition(LocalTime.of(16, 0), 3, "evening")
            );
            var resolver = new TimeOfDayResolver(periods);

            // 12:00 UTC is outside both periods
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T12:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("OTHER");
        }

        @Test
        @DisplayName("should return OTHER at period end boundary (exclusive)")
        void shouldReturnOtherAtPeriodEndBoundary() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(14, 0), 2, "afternoon")
            );
            var resolver = new TimeOfDayResolver(periods);

            // Exactly 16:00 is the end boundary (exclusive)
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T16:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("OTHER");
        }

        @Test
        @DisplayName("should return OTHER when no periods defined")
        void shouldReturnOtherWhenNoPeriodsAreDefined() {
            var resolver = new TimeOfDayResolver(List.of());

            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T09:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("OTHER");
        }
    }

    @Nested
    @DisplayName("timezone handling")
    class TimezoneHandling {

        @Test
        @DisplayName("should apply timezone when determining current time")
        void shouldApplyTimezoneWhenDeterminingCurrentTime() {
            // 14:30 UTC = 15:30 in Europe/Paris (winter time, UTC+1)
            // Period covers 15:00-17:00 local time
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(15, 0), 2, "paris_afternoon")
            );
            var resolver = new TimeOfDayResolver(periods);

            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T14:30:00Z"))
                .systemTimezone(ZoneId.of("Europe/Paris"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("paris_afternoon");
        }

        @Test
        @DisplayName("should not match when timezone shifts time outside period")
        void shouldNotMatchWhenTimezoneShiftsTimeOutsidePeriod() {
            // Period covers 08:00-10:00
            // 09:00 UTC = 04:00 in America/New_York (EST, UTC-5) -- outside period
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(8, 0), 2, "morning")
            );
            var resolver = new TimeOfDayResolver(periods);

            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T09:00:00Z"))
                .systemTimezone(ZoneId.of("America/New_York"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("OTHER");
        }
    }

    @Nested
    @DisplayName("StringValue result")
    class StringValueResult {

        @Test
        @DisplayName("should return StringValue for matched period")
        void shouldReturnStringValueForMatchedPeriod() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(8, 0), 2, "08:00/2h")
            );
            var resolver = new TimeOfDayResolver(periods);

            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T09:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result).isInstanceOf(org.javai.punit.model.CovariateValue.StringValue.class);
            assertThat(result.toCanonicalString()).isEqualTo("08:00/2h");
        }

        @Test
        @DisplayName("should return StringValue for OTHER")
        void shouldReturnStringValueForOther() {
            var resolver = new TimeOfDayResolver(List.of());

            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T09:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result).isInstanceOf(org.javai.punit.model.CovariateValue.StringValue.class);
            assertThat(result.toCanonicalString()).isEqualTo("OTHER");
        }
    }
}

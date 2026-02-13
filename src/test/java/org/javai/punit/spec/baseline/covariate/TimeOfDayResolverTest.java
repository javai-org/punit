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
        @DisplayName("should return remainder label combining all gaps")
        void shouldReturnRemainderLabelCombiningAllGaps() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(8, 0), 2, "morning"),
                new TimePeriodDefinition(LocalTime.of(16, 0), 3, "evening")
            );
            var resolver = new TimeOfDayResolver(periods);

            // 12:00 UTC is outside both periods — remainder covers all three gaps
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T12:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("00:00/8h, 10:00/6h, 19:00/5h");
        }

        @Test
        @DisplayName("should return same remainder label regardless of which gap time falls in")
        void shouldReturnSameRemainderLabelRegardlessOfGap() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(8, 0), 2, "morning"),
                new TimePeriodDefinition(LocalTime.of(16, 0), 3, "evening")
            );
            var resolver = new TimeOfDayResolver(periods);
            var expected = "00:00/8h, 10:00/6h, 19:00/5h";

            // First gap: 03:00
            var first = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T03:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();
            assertThat(resolver.resolve(first).toCanonicalString()).isEqualTo(expected);

            // Middle gap: 12:00
            var middle = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T12:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();
            assertThat(resolver.resolve(middle).toCanonicalString()).isEqualTo(expected);

            // Last gap: 21:00
            var last = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T21:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();
            assertThat(resolver.resolve(last).toCanonicalString()).isEqualTo(expected);
        }

        @Test
        @DisplayName("should return remainder label at period end boundary (exclusive)")
        void shouldReturnRemainderLabelAtPeriodEndBoundary() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(14, 0), 2, "afternoon")
            );
            var resolver = new TimeOfDayResolver(periods);

            // Exactly 16:00 is the end boundary (exclusive) — remainder is the complement
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T16:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("00:00/14h, 16:00/8h");
        }

        @Test
        @DisplayName("should return full day gap when no periods defined")
        void shouldReturnFullDayGapWhenNoPeriodsAreDefined() {
            var resolver = new TimeOfDayResolver(List.of());

            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T09:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("00:00/24h");
        }

        @Test
        @DisplayName("should return remainder with two gaps for single period")
        void shouldReturnRemainderWithTwoGapsForSinglePeriod() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(10, 0), 4, "midday")
            );
            var resolver = new TimeOfDayResolver(periods);

            // Before period: 05:00 — remainder is the same regardless of which gap
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T05:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            assertThat(resolver.resolve(context).toCanonicalString()).isEqualTo("00:00/10h, 14:00/10h");

            // After period: 20:00 — same remainder label
            var afterContext = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T20:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            assertThat(resolver.resolve(afterContext).toCanonicalString()).isEqualTo("00:00/10h, 14:00/10h");
        }

        @Test
        @DisplayName("should produce no gaps when periods cover full day")
        void shouldProduceNoGapsWhenFullDayCovered() {
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(0, 0), 8, "00:00/8h"),
                new TimePeriodDefinition(LocalTime.of(8, 0), 8, "08:00/8h"),
                new TimePeriodDefinition(LocalTime.of(16, 0), 8, "16:00/8h")
            );
            var resolver = new TimeOfDayResolver(periods);

            // Every time matches a declared period — no gaps
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T04:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            assertThat(resolver.resolve(context).toCanonicalString()).isEqualTo("00:00/8h");
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
            // Remainder is the complement: 00:00/8h, 10:00/14h
            var periods = List.of(
                new TimePeriodDefinition(LocalTime.of(8, 0), 2, "morning")
            );
            var resolver = new TimeOfDayResolver(periods);

            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T09:00:00Z"))
                .systemTimezone(ZoneId.of("America/New_York"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result.toCanonicalString()).isEqualTo("00:00/8h, 10:00/14h");
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
        @DisplayName("should return StringValue for gap label")
        void shouldReturnStringValueForGapLabel() {
            var resolver = new TimeOfDayResolver(List.of());

            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-10T09:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var result = resolver.resolve(context);

            assertThat(result).isInstanceOf(org.javai.punit.model.CovariateValue.StringValue.class);
            assertThat(result.toCanonicalString()).isEqualTo("00:00/24h");
        }
    }
}

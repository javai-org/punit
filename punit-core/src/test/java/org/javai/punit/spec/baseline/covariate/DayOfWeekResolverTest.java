package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.DayGroupDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DayOfWeekResolver")
class DayOfWeekResolverTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Nested
    @DisplayName("Group matching")
    class GroupMatchingTests {

        @Test
        @DisplayName("resolves Saturday to WEEKEND group")
        void resolvesSaturdayToWeekendGroup() {
            var groups = List.of(
                    new DayGroupDefinition(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND")
            );
            var resolver = new DayOfWeekResolver(groups);

            // Saturday 2026-01-03 (a Saturday)
            var saturday = findNextDay(DayOfWeek.SATURDAY);
            var context = contextForDate(saturday);

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue("WEEKEND"));
        }

        @Test
        @DisplayName("resolves Sunday to WEEKEND group")
        void resolvesSundayToWeekendGroup() {
            var groups = List.of(
                    new DayGroupDefinition(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND")
            );
            var resolver = new DayOfWeekResolver(groups);

            var sunday = findNextDay(DayOfWeek.SUNDAY);
            var context = contextForDate(sunday);

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue("WEEKEND"));
        }

        @Test
        @DisplayName("resolves Monday to matching group")
        void resolvesWeekdayToMatchingGroup() {
            var groups = List.of(
                    new DayGroupDefinition(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND"),
                    new DayGroupDefinition(Set.of(DayOfWeek.MONDAY), "MONDAY")
            );
            var resolver = new DayOfWeekResolver(groups);

            var monday = findNextDay(DayOfWeek.MONDAY);
            var context = contextForDate(monday);

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue("MONDAY"));
        }
    }

    @Nested
    @DisplayName("Remainder")
    class RemainderTests {

        @Test
        @DisplayName("resolves unmatched day to WEEKDAY when only WEEKEND declared")
        void resolvesUnmatchedDayToWeekday() {
            var groups = List.of(
                    new DayGroupDefinition(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND")
            );
            var resolver = new DayOfWeekResolver(groups);

            var wednesday = findNextDay(DayOfWeek.WEDNESDAY);
            var context = contextForDate(wednesday);

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue("WEEKDAY"));
        }

        @Test
        @DisplayName("resolves unmatched day to joined name when non-standard grouping")
        void resolvesUnmatchedDayToJoinedName() {
            var groups = List.of(
                    new DayGroupDefinition(Set.of(DayOfWeek.MONDAY), "MONDAY")
            );
            var resolver = new DayOfWeekResolver(groups);

            var tuesday = findNextDay(DayOfWeek.TUESDAY);
            var context = contextForDate(tuesday);

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue(
                    "TUESDAY_WEDNESDAY_THURSDAY_FRIDAY_SATURDAY_SUNDAY"));
        }

        @Test
        @DisplayName("resolves to WEEKEND when only weekdays declared")
        void resolvesRemainderToWeekend() {
            var groups = List.of(
                    new DayGroupDefinition(
                            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                            "WEEKDAY")
            );
            var resolver = new DayOfWeekResolver(groups);

            var saturday = findNextDay(DayOfWeek.SATURDAY);
            var context = contextForDate(saturday);

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue("WEEKEND"));
        }

        @Test
        @DisplayName("fully covered days has no remainder path reachable")
        void fullyCoveredDaysHasNoRemainderReachable() {
            var groups = List.of(
                    new DayGroupDefinition(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND"),
                    new DayGroupDefinition(
                            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                            "WEEKDAY")
            );
            var resolver = new DayOfWeekResolver(groups);

            // Every day maps to a declared group — remainder is never reached
            for (DayOfWeek day : DayOfWeek.values()) {
                var date = findNextDay(day);
                var context = contextForDate(date);
                var result = resolver.resolve(context);
                assertThat(result.toCanonicalString()).isIn("WEEKEND", "WEEKDAY");
            }
        }
    }

    @Nested
    @DisplayName("Timezone sensitivity")
    class TimezoneSensitivityTests {

        @Test
        @DisplayName("respects timezone when determining day")
        void respectsTimezoneWhenDeterminingDay() {
            var groups = List.of(
                    new DayGroupDefinition(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND")
            );

            // Find a Saturday in UTC
            var saturday = findNextDay(DayOfWeek.SATURDAY);
            // Create an instant just after midnight Saturday UTC
            var instant = saturday.atStartOfDay(ZoneId.of("UTC")).plusMinutes(30).toInstant();

            // In UTC+14 (Line Islands), this is actually Saturday evening already,
            // but in UTC-12 it's still Friday
            var pacificZone = ZoneId.of("Pacific/Pago_Pago"); // UTC-11
            var resolver = new DayOfWeekResolver(groups);

            // Check what day it is in this timezone
            var dayInPacific = instant.atZone(pacificZone).getDayOfWeek();
            var context = contextForInstant(instant, pacificZone);
            var result = resolver.resolve(context);

            if (dayInPacific == DayOfWeek.SATURDAY || dayInPacific == DayOfWeek.SUNDAY) {
                assertThat(result).isEqualTo(new CovariateValue.StringValue("WEEKEND"));
            } else {
                assertThat(result).isEqualTo(new CovariateValue.StringValue("WEEKDAY"));
            }
        }
    }

    private static LocalDate findNextDay(DayOfWeek target) {
        var date = LocalDate.of(2026, 1, 1);
        while (date.getDayOfWeek() != target) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static CovariateResolutionContext contextForDate(LocalDate date) {
        var instant = date.atTime(12, 0).atZone(LONDON).toInstant();
        return contextForInstant(instant, LONDON);
    }

    private static CovariateResolutionContext contextForInstant(Instant instant, ZoneId zone) {
        return DefaultCovariateResolutionContext.builder()
                .now(instant)
                .systemTimezone(zone)
                .build();
    }
}

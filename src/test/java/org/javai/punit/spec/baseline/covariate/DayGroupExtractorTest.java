package org.javai.punit.spec.baseline.covariate;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.util.Set;
import org.javai.punit.api.DayGroup;
import org.javai.punit.api.UseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DayGroupExtractor}.
 */
@DisplayName("DayGroupExtractor")
class DayGroupExtractorTest {

    private final DayGroupExtractor extractor = new DayGroupExtractor();

    @Nested
    @DisplayName("extract")
    class ExtractTests {

        @Test
        @DisplayName("should return empty list for empty input")
        void shouldReturnEmptyListForEmptyInput() {
            var result = extractor.extract(dayGroupsFrom(EmptyDayGroups.class));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should extract weekday/weekend day groups")
        void shouldExtractWeekdayWeekendDayGroups() {
            var result = extractor.extract(dayGroupsFrom(WeekdayWeekend.class));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should auto-derive WEEKEND label for Saturday+Sunday")
        void shouldAutoDeriveWeekendLabel() {
            var result = extractor.extract(dayGroupsFrom(WeekdayWeekend.class));

            assertThat(result)
                .anyMatch(g -> g.label().equals("WEEKEND")
                    && g.days().containsAll(Set.of(SATURDAY, SUNDAY)));
        }

        @Test
        @DisplayName("should auto-derive WEEKDAY label for Monday-Friday")
        void shouldAutoDeriveWeekdayLabel() {
            var result = extractor.extract(dayGroupsFrom(WeekdayWeekend.class));

            assertThat(result)
                .anyMatch(g -> g.label().equals("WEEKDAY")
                    && g.days().containsAll(Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)));
        }

        @Test
        @DisplayName("should join day names for non-standard grouping")
        void shouldJoinDayNamesForNonStandardGrouping() {
            var result = extractor.extract(dayGroupsFrom(CustomGrouping.class));

            assertThat(result)
                .anyMatch(g -> g.label().equals("MONDAY_TUESDAY"));
        }

        @Test
        @DisplayName("should use custom label when provided")
        void shouldUseCustomLabelWhenProvided() {
            var result = extractor.extract(dayGroupsFrom(CustomLabel.class));

            assertThat(result)
                .anyMatch(g -> g.label().equals("Restdays"));
        }

        @Test
        @DisplayName("should reject overlapping day groups")
        void shouldRejectOverlappingDayGroups() {
            assertThatThrownBy(() -> extractor.extract(dayGroupsFrom(OverlappingDays.class)))
                .isInstanceOf(CovariateValidationException.class)
                .hasMessageContaining("more than one @DayGroup");
        }
    }

    @Nested
    @DisplayName("deriveDayGroupLabel")
    class DeriveLabelTests {

        @Test
        @DisplayName("should return WEEKEND for Saturday+Sunday")
        void shouldReturnWeekendForSaturdaySunday() {
            assertThat(DayGroupExtractor.deriveDayGroupLabel(Set.of(SATURDAY, SUNDAY)))
                .isEqualTo("WEEKEND");
        }

        @Test
        @DisplayName("should return WEEKDAY for Monday-Friday")
        void shouldReturnWeekdayForMondayToFriday() {
            assertThat(DayGroupExtractor.deriveDayGroupLabel(
                    Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)))
                .isEqualTo("WEEKDAY");
        }

        @Test
        @DisplayName("should join sorted day names for arbitrary grouping")
        void shouldJoinSortedDayNames() {
            assertThat(DayGroupExtractor.deriveDayGroupLabel(Set.of(WEDNESDAY, MONDAY)))
                .isEqualTo("MONDAY_WEDNESDAY");
        }
    }

    // -- Helpers --

    private static DayGroup[] dayGroupsFrom(Class<?> clazz) {
        return clazz.getAnnotation(UseCase.class).covariateDayOfWeek();
    }

    // -- Fixtures --

    @UseCase("test.empty")
    private static class EmptyDayGroups {}

    @UseCase(
        value = "test.weekday.weekend",
        covariateDayOfWeek = {
            @DayGroup({SATURDAY, SUNDAY}),
            @DayGroup({MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY})
        }
    )
    private static class WeekdayWeekend {}

    @UseCase(
        value = "test.custom.grouping",
        covariateDayOfWeek = {
            @DayGroup({MONDAY, TUESDAY})
        }
    )
    private static class CustomGrouping {}

    @UseCase(
        value = "test.custom.label",
        covariateDayOfWeek = {
            @DayGroup(value = {SATURDAY, SUNDAY}, label = "Restdays")
        }
    )
    private static class CustomLabel {}

    @UseCase(
        value = "test.overlapping",
        covariateDayOfWeek = {
            @DayGroup({SATURDAY, SUNDAY}),
            @DayGroup({SUNDAY, MONDAY})
        }
    )
    private static class OverlappingDays {}
}

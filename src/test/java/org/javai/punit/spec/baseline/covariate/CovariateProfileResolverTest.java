package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.model.DayGroupDefinition;
import org.javai.punit.model.RegionGroupDefinition;
import org.javai.punit.model.TimePeriodDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateProfileResolver}.
 */
@DisplayName("CovariateProfileResolver")
class CovariateProfileResolverTest {

    private final CovariateProfileResolver resolver = new CovariateProfileResolver();

    @Nested
    @DisplayName("resolve()")
    class ResolveTests {

        @Test
        @DisplayName("should return empty profile for empty declaration")
        void shouldReturnEmptyProfileForEmptyDeclaration() {
            var context = DefaultCovariateResolutionContext.forNow();

            var profile = resolver.resolve(CovariateDeclaration.EMPTY, context);

            assertThat(profile.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should resolve day-of-week covariate using day groups")
        void shouldResolveDayOfWeekCovariateUsingDayGroups() {
            var dayGroups = List.of(
                new DayGroupDefinition(
                    Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND"),
                new DayGroupDefinition(
                    Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), "WEEKDAY")
            );
            var declaration = new CovariateDeclaration(
                dayGroups, List.of(), List.of(), false, Map.of()
            );
            // 2026-01-13 is a Tuesday
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-13T10:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var profile = resolver.resolve(declaration, context);

            assertThat(profile.size()).isEqualTo(1);
            assertThat(profile.get("day_of_week").toCanonicalString()).isEqualTo("WEEKDAY");
        }

        @Test
        @DisplayName("should resolve timezone covariate")
        void shouldResolveTimezoneCovariate() {
            var declaration = new CovariateDeclaration(
                List.of(), List.of(), List.of(), true, Map.of()
            );
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-13T10:00:00Z"))
                .systemTimezone(ZoneId.of("Europe/London"))
                .build();

            var profile = resolver.resolve(declaration, context);

            assertThat(profile.size()).isEqualTo(1);
            assertThat(profile.get("timezone").toCanonicalString()).isEqualTo("Europe/London");
        }

        @Test
        @DisplayName("should resolve all declared covariates")
        void shouldResolveAllDeclaredCovariates() {
            var dayGroups = List.of(
                new DayGroupDefinition(
                    Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND"),
                new DayGroupDefinition(
                    Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), "WEEKDAY")
            );
            var declaration = new CovariateDeclaration(
                dayGroups, List.of(), List.of(), true, Map.of()
            );
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-13T10:00:00Z")) // Tuesday
                .systemTimezone(ZoneId.of("Europe/London"))
                .build();

            var profile = resolver.resolve(declaration, context);

            assertThat(profile.size()).isEqualTo(2);
            assertThat(profile.get("day_of_week").toCanonicalString()).isEqualTo("WEEKDAY");
            assertThat(profile.get("timezone").toCanonicalString()).isEqualTo("Europe/London");
        }

        @Test
        @DisplayName("should preserve declaration order")
        void shouldPreserveDeclarationOrder() {
            var declaration = new CovariateDeclaration(
                List.of(),
                List.of(),
                List.of(new RegionGroupDefinition(Set.of("US"), "US")),
                true,
                Map.of("custom1", CovariateCategory.OPERATIONAL)
            );
            var context = DefaultCovariateResolutionContext.forNow();

            var profile = resolver.resolve(declaration, context);

            assertThat(profile.orderedKeys())
                .containsExactly("region", "timezone", "custom1");
        }

        @Test
        @DisplayName("should resolve time-of-day covariate using time periods")
        void shouldResolveTimeOfDayCovariateUsingTimePeriods() {
            var timePeriods = List.of(
                new TimePeriodDefinition(LocalTime.of(8, 0), 2, "08:00/2h"),
                new TimePeriodDefinition(LocalTime.of(16, 0), 3, "16:00/3h")
            );
            var declaration = new CovariateDeclaration(
                List.of(), timePeriods, List.of(), false, Map.of()
            );
            // 9:00 UTC falls within 08:00/2h period
            var context = DefaultCovariateResolutionContext.builder()
                .now(Instant.parse("2026-01-13T09:00:00Z"))
                .systemTimezone(ZoneId.of("UTC"))
                .build();

            var profile = resolver.resolve(declaration, context);

            assertThat(profile.size()).isEqualTo(1);
            assertThat(profile.get("time_of_day").toCanonicalString()).isEqualTo("08:00/2h");
        }
    }
}

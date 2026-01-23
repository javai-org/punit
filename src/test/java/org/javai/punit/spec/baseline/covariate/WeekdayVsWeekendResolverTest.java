package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.javai.punit.model.CovariateValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link WeekdayVsWeekendResolver}.
 */
@DisplayName("WeekdayVsWeekendResolver")
class WeekdayVsWeekendResolverTest {

    private final WeekdayVsWeekendResolver resolver = new WeekdayVsWeekendResolver();

    @ParameterizedTest(name = "{0} should resolve to Mo-Fr")
    @ValueSource(strings = {"2026-01-12", "2026-01-13", "2026-01-14", "2026-01-15", "2026-01-16"})
    @DisplayName("weekdays should resolve to Mo-Fr")
    void weekdaysShouldResolveToMoFr(String date) {
        var localDate = LocalDate.parse(date);
        var instant = localDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        
        var context = DefaultCovariateResolutionContext.builder()
            .now(instant)
            .systemTimezone(ZoneId.of("UTC"))
            .build();
        
        var result = resolver.resolve(context);
        
        assertThat(result).isInstanceOf(CovariateValue.StringValue.class);
        assertThat(result.toCanonicalString()).isEqualTo("Mo-Fr");
    }

    @ParameterizedTest(name = "{0} should resolve to Sa-So")
    @ValueSource(strings = {"2026-01-10", "2026-01-11", "2026-01-17", "2026-01-18"})
    @DisplayName("weekends should resolve to Sa-So")
    void weekendsShouldResolveToSaSo(String date) {
        var localDate = LocalDate.parse(date);
        var instant = localDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        
        var context = DefaultCovariateResolutionContext.builder()
            .now(instant)
            .systemTimezone(ZoneId.of("UTC"))
            .build();
        
        var result = resolver.resolve(context);
        
        assertThat(result).isInstanceOf(CovariateValue.StringValue.class);
        assertThat(result.toCanonicalString()).isEqualTo("Sa-So");
    }

    @Test
    @DisplayName("should respect timezone for day calculation")
    void shouldRespectTimezoneForDayCalculation() {
        // 2026-01-11 23:00 UTC is 2026-01-12 08:00 in Asia/Tokyo
        // Jan 11 is Sunday, Jan 12 is Monday
        var instant = ZonedDateTime.of(2026, 1, 11, 23, 0, 0, 0, ZoneId.of("UTC")).toInstant();
        
        var contextUtc = DefaultCovariateResolutionContext.builder()
            .now(instant)
            .systemTimezone(ZoneId.of("UTC"))
            .build();
        
        var contextTokyo = DefaultCovariateResolutionContext.builder()
            .now(instant)
            .systemTimezone(ZoneId.of("Asia/Tokyo"))
            .build();
        
        assertThat(resolver.resolve(contextUtc).toCanonicalString()).isEqualTo("Sa-So");
        assertThat(resolver.resolve(contextTokyo).toCanonicalString()).isEqualTo("Mo-Fr");
    }
}


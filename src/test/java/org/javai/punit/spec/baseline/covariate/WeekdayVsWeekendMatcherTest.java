package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.spec.baseline.covariate.CovariateMatcher.MatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WeekdayVsWeekendMatcher}.
 */
@DisplayName("WeekdayVsWeekendMatcher")
class WeekdayVsWeekendMatcherTest {

    private final WeekdayVsWeekendMatcher matcher = new WeekdayVsWeekendMatcher();

    @Test
    @DisplayName("Mo-Fr should match Mo-Fr")
    void weekdayShouldMatchWeekday() {
        var baseline = new CovariateValue.StringValue("Mo-Fr");
        var test = new CovariateValue.StringValue("Mo-Fr");
        
        assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
    }

    @Test
    @DisplayName("Sa-So should match Sa-So")
    void weekendShouldMatchWeekend() {
        var baseline = new CovariateValue.StringValue("Sa-So");
        var test = new CovariateValue.StringValue("Sa-So");
        
        assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
    }

    @Test
    @DisplayName("Mo-Fr should not match Sa-So")
    void weekdayShouldNotMatchWeekend() {
        var baseline = new CovariateValue.StringValue("Mo-Fr");
        var test = new CovariateValue.StringValue("Sa-So");
        
        assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
    }

    @Test
    @DisplayName("Sa-So should not match Mo-Fr")
    void weekendShouldNotMatchWeekday() {
        var baseline = new CovariateValue.StringValue("Sa-So");
        var test = new CovariateValue.StringValue("Mo-Fr");
        
        assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
    }

    @Test
    @DisplayName("should return DOES_NOT_CONFORM for non-string values")
    void shouldNotConformForNonStringValues() {
        var baseline = new CovariateValue.StringValue("Mo-Fr");
        var test = new CovariateValue.TimeWindowValue(
            java.time.LocalTime.of(9, 0),
            java.time.LocalTime.of(17, 0),
            java.time.ZoneId.of("UTC")
        );
        
        assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
    }
}


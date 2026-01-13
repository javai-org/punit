package org.javai.punit.engine.covariate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.time.ZoneId;

import org.javai.punit.engine.covariate.CovariateMatcher.MatchResult;
import org.javai.punit.model.CovariateValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TimeOfDayMatcher}.
 */
@DisplayName("TimeOfDayMatcher")
class TimeOfDayMatcherTest {

    private final TimeOfDayMatcher matcher = new TimeOfDayMatcher();

    @Nested
    @DisplayName("within window")
    class WithinWindowTests {

        @Test
        @DisplayName("time within window should conform")
        void timeWithinWindowShouldConform() {
            var baseline = new CovariateValue.TimeWindowValue(
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                ZoneId.of("UTC")
            );
            var test = new CovariateValue.TimeWindowValue(
                LocalTime.of(12, 0),
                LocalTime.of(12, 0), // Point-in-time
                ZoneId.of("UTC")
            );
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
        }

        @Test
        @DisplayName("time at window start should conform")
        void timeAtWindowStartShouldConform() {
            var baseline = new CovariateValue.TimeWindowValue(
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                ZoneId.of("UTC")
            );
            var test = new CovariateValue.TimeWindowValue(
                LocalTime.of(9, 0),
                LocalTime.of(9, 0),
                ZoneId.of("UTC")
            );
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
        }

        @Test
        @DisplayName("time at window end should conform")
        void timeAtWindowEndShouldConform() {
            var baseline = new CovariateValue.TimeWindowValue(
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                ZoneId.of("UTC")
            );
            var test = new CovariateValue.TimeWindowValue(
                LocalTime.of(17, 0),
                LocalTime.of(17, 0),
                ZoneId.of("UTC")
            );
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
        }
    }

    @Nested
    @DisplayName("outside window")
    class OutsideWindowTests {

        @Test
        @DisplayName("time before window should not conform")
        void timeBeforeWindowShouldNotConform() {
            var baseline = new CovariateValue.TimeWindowValue(
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                ZoneId.of("UTC")
            );
            var test = new CovariateValue.TimeWindowValue(
                LocalTime.of(8, 59),
                LocalTime.of(8, 59),
                ZoneId.of("UTC")
            );
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }

        @Test
        @DisplayName("time after window should not conform")
        void timeAfterWindowShouldNotConform() {
            var baseline = new CovariateValue.TimeWindowValue(
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                ZoneId.of("UTC")
            );
            var test = new CovariateValue.TimeWindowValue(
                LocalTime.of(17, 1),
                LocalTime.of(17, 1),
                ZoneId.of("UTC")
            );
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }
    }

    @Nested
    @DisplayName("overnight window")
    class OvernightWindowTests {

        @Test
        @DisplayName("time after start in overnight window should conform")
        void timeAfterStartInOvernightWindowShouldConform() {
            var baseline = new CovariateValue.TimeWindowValue(
                LocalTime.of(22, 0),
                LocalTime.of(6, 0), // Overnight window
                ZoneId.of("UTC")
            );
            var test = new CovariateValue.TimeWindowValue(
                LocalTime.of(23, 0),
                LocalTime.of(23, 0),
                ZoneId.of("UTC")
            );
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
        }

        @Test
        @DisplayName("time before end in overnight window should conform")
        void timeBeforeEndInOvernightWindowShouldConform() {
            var baseline = new CovariateValue.TimeWindowValue(
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                ZoneId.of("UTC")
            );
            var test = new CovariateValue.TimeWindowValue(
                LocalTime.of(3, 0),
                LocalTime.of(3, 0),
                ZoneId.of("UTC")
            );
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.CONFORMS);
        }

        @Test
        @DisplayName("time in middle of day should not conform to overnight window")
        void timeInMiddleOfDayShouldNotConformToOvernightWindow() {
            var baseline = new CovariateValue.TimeWindowValue(
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                ZoneId.of("UTC")
            );
            var test = new CovariateValue.TimeWindowValue(
                LocalTime.of(12, 0),
                LocalTime.of(12, 0),
                ZoneId.of("UTC")
            );
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }
    }

    @Nested
    @DisplayName("type handling")
    class TypeHandlingTests {

        @Test
        @DisplayName("should return DOES_NOT_CONFORM for non-TimeWindow baseline")
        void shouldNotConformForNonTimeWindowBaseline() {
            var baseline = new CovariateValue.StringValue("09:00-17:00 UTC");
            var test = new CovariateValue.TimeWindowValue(
                LocalTime.of(12, 0),
                LocalTime.of(12, 0),
                ZoneId.of("UTC")
            );
            
            assertThat(matcher.match(baseline, test)).isEqualTo(MatchResult.DOES_NOT_CONFORM);
        }
    }
}


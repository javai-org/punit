package org.javai.punit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateValue}.
 */
@DisplayName("CovariateValue")
class CovariateValueTest {

    @Nested
    @DisplayName("StringValue")
    class StringValueTests {

        @Test
        @DisplayName("toCanonicalString() should return the value")
        void toCanonicalStringShouldReturnValue() {
            var value = new CovariateValue.StringValue("Mo-Fr");
            
            assertThat(value.toCanonicalString()).isEqualTo("Mo-Fr");
        }

        @Test
        @DisplayName("should reject null value")
        void shouldRejectNullValue() {
            assertThatThrownBy(() -> new CovariateValue.StringValue(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("equality should work correctly")
        void equalityShouldWorkCorrectly() {
            var value1 = new CovariateValue.StringValue("EU");
            var value2 = new CovariateValue.StringValue("EU");
            var value3 = new CovariateValue.StringValue("US");
            
            assertThat(value1).isEqualTo(value2);
            assertThat(value1).isNotEqualTo(value3);
            assertThat(value1.hashCode()).isEqualTo(value2.hashCode());
        }
    }

    @Nested
    @DisplayName("TimeWindowValue")
    class TimeWindowValueTests {

        @Test
        @DisplayName("toCanonicalString() should format correctly")
        void toCanonicalStringShouldFormatCorrectly() {
            var value = new CovariateValue.TimeWindowValue(
                LocalTime.of(14, 30),
                LocalTime.of(14, 45),
                ZoneId.of("Europe/London")
            );
            
            assertThat(value.toCanonicalString()).isEqualTo("14:30-14:45 Europe/London");
        }

        @Test
        @DisplayName("should reject null start time")
        void shouldRejectNullStartTime() {
            assertThatThrownBy(() -> new CovariateValue.TimeWindowValue(
                null,
                LocalTime.of(14, 45),
                ZoneId.of("Europe/London")
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null end time")
        void shouldRejectNullEndTime() {
            assertThatThrownBy(() -> new CovariateValue.TimeWindowValue(
                LocalTime.of(14, 30),
                null,
                ZoneId.of("Europe/London")
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null timezone")
        void shouldRejectNullTimezone() {
            assertThatThrownBy(() -> new CovariateValue.TimeWindowValue(
                LocalTime.of(14, 30),
                LocalTime.of(14, 45),
                null
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("equality should work correctly")
        void equalityShouldWorkCorrectly() {
            var value1 = new CovariateValue.TimeWindowValue(
                LocalTime.of(9, 0), LocalTime.of(10, 0), ZoneId.of("UTC")
            );
            var value2 = new CovariateValue.TimeWindowValue(
                LocalTime.of(9, 0), LocalTime.of(10, 0), ZoneId.of("UTC")
            );
            var value3 = new CovariateValue.TimeWindowValue(
                LocalTime.of(9, 0), LocalTime.of(11, 0), ZoneId.of("UTC")
            );
            
            assertThat(value1).isEqualTo(value2);
            assertThat(value1).isNotEqualTo(value3);
            assertThat(value1.hashCode()).isEqualTo(value2.hashCode());
        }

        @Test
        @DisplayName("parse() should handle standard format")
        void parseShouldHandleStandardFormat() {
            var parsed = CovariateValue.TimeWindowValue.parse("14:30-14:45 Europe/London");
            
            assertThat(parsed.start()).isEqualTo(LocalTime.of(14, 30));
            assertThat(parsed.end()).isEqualTo(LocalTime.of(14, 45));
            assertThat(parsed.timezone()).isEqualTo(ZoneId.of("Europe/London"));
        }

        @Test
        @DisplayName("parse() should handle UTC timezone")
        void parseShouldHandleUtcTimezone() {
            var parsed = CovariateValue.TimeWindowValue.parse("09:00-17:00 UTC");
            
            assertThat(parsed.start()).isEqualTo(LocalTime.of(9, 0));
            assertThat(parsed.end()).isEqualTo(LocalTime.of(17, 0));
            assertThat(parsed.timezone()).isEqualTo(ZoneId.of("UTC"));
        }

        @Test
        @DisplayName("parse() should reject invalid format without timezone")
        void parseShouldRejectInvalidFormatWithoutTimezone() {
            assertThatThrownBy(() -> CovariateValue.TimeWindowValue.parse("14:30-14:45"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("parse() should reject null")
        void parseShouldRejectNull() {
            assertThatThrownBy(() -> CovariateValue.TimeWindowValue.parse(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("round-trip through canonical string should preserve value")
        void roundTripShouldPreserveValue() {
            var original = new CovariateValue.TimeWindowValue(
                LocalTime.of(8, 15, 30),
                LocalTime.of(16, 45, 0),
                ZoneId.of("America/New_York")
            );
            
            var canonical = original.toCanonicalString();
            var parsed = CovariateValue.TimeWindowValue.parse(canonical);
            
            assertThat(parsed).isEqualTo(original);
        }
    }
}


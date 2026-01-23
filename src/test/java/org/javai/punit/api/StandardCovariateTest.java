package org.javai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StandardCovariate}.
 */
@DisplayName("StandardCovariate")
class StandardCovariateTest {

    @Nested
    @DisplayName("key()")
    class KeyTests {

        @Test
        @DisplayName("WEEKDAY_VERSUS_WEEKEND should return 'weekday_vs_weekend'")
        void weekdayVsWeekendKey() {
            assertThat(StandardCovariate.WEEKDAY_VERSUS_WEEKEND.key())
                .isEqualTo("weekday_vs_weekend");
        }

        @Test
        @DisplayName("TIME_OF_DAY should return 'time_of_day'")
        void timeOfDayKey() {
            assertThat(StandardCovariate.TIME_OF_DAY.key())
                .isEqualTo("time_of_day");
        }

        @Test
        @DisplayName("TIMEZONE should return 'timezone'")
        void timezoneKey() {
            assertThat(StandardCovariate.TIMEZONE.key())
                .isEqualTo("timezone");
        }

        @Test
        @DisplayName("REGION should return 'region'")
        void regionKey() {
            assertThat(StandardCovariate.REGION.key())
                .isEqualTo("region");
        }
    }

    @Nested
    @DisplayName("Stability")
    class StabilityTests {

        @Test
        @DisplayName("enum values should be stable for serialization")
        void enumValuesShouldBeStable() {
            // These ordinal values should never change (serialization compatibility)
            assertThat(StandardCovariate.WEEKDAY_VERSUS_WEEKEND.ordinal()).isEqualTo(0);
            assertThat(StandardCovariate.TIME_OF_DAY.ordinal()).isEqualTo(1);
            assertThat(StandardCovariate.TIMEZONE.ordinal()).isEqualTo(2);
            assertThat(StandardCovariate.REGION.ordinal()).isEqualTo(3);
        }

        @Test
        @DisplayName("should have exactly 4 standard covariates")
        void shouldHaveExactlyFourCovariates() {
            assertThat(StandardCovariate.values()).hasSize(4);
        }
    }
}


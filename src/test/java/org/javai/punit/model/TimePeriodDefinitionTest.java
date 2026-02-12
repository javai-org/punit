package org.javai.punit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TimePeriodDefinition")
class TimePeriodDefinitionTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("creates with valid parameters")
        void createsWithValidParameters() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 2, "08:00/2h");

            assertThat(period.start()).isEqualTo(LocalTime.of(8, 0));
            assertThat(period.durationHours()).isEqualTo(2);
            assertThat(period.label()).isEqualTo("08:00/2h");
        }

        @Test
        @DisplayName("rejects null start")
        void rejectsNullStart() {
            assertThatThrownBy(() -> new TimePeriodDefinition(null, 2, "label"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects zero duration")
        void rejectsZeroDuration() {
            assertThatThrownBy(() -> new TimePeriodDefinition(LocalTime.of(8, 0), 0, "label"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("rejects negative duration")
        void rejectsNegativeDuration() {
            assertThatThrownBy(() -> new TimePeriodDefinition(LocalTime.of(8, 0), -1, "label"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("rejects midnight crossing")
        void rejectsMidnightCrossing() {
            assertThatThrownBy(() -> new TimePeriodDefinition(LocalTime.of(23, 0), 2, "label"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("midnight");
        }

        @Test
        @DisplayName("accepts period ending exactly at midnight")
        void acceptsPeriodEndingAtMidnight() {
            var period = new TimePeriodDefinition(LocalTime.of(23, 0), 1, "23:00/1h");

            assertThat(period.end()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("rejects blank label")
        void rejectsBlankLabel() {
            assertThatThrownBy(() -> new TimePeriodDefinition(LocalTime.of(8, 0), 2, " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }
    }

    @Nested
    @DisplayName("end()")
    class EndTests {

        @Test
        @DisplayName("computes end time from start + duration")
        void computesEndTime() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 2, "08:00/2h");

            assertThat(period.end()).isEqualTo(LocalTime.of(10, 0));
        }

        @Test
        @DisplayName("handles non-zero minutes")
        void handlesNonZeroMinutes() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 30), 3, "08:30/3h");

            assertThat(period.end()).isEqualTo(LocalTime.of(11, 30));
        }
    }

    @Nested
    @DisplayName("contains()")
    class ContainsTests {

        @Test
        @DisplayName("start is inclusive")
        void startIsInclusive() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 2, "08:00/2h");

            assertThat(period.contains(LocalTime.of(8, 0))).isTrue();
        }

        @Test
        @DisplayName("end is exclusive")
        void endIsExclusive() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 2, "08:00/2h");

            assertThat(period.contains(LocalTime.of(10, 0))).isFalse();
        }

        @Test
        @DisplayName("time within period matches")
        void timeWithinPeriodMatches() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 2, "08:00/2h");

            assertThat(period.contains(LocalTime.of(9, 30))).isTrue();
        }

        @Test
        @DisplayName("time before period does not match")
        void timeBeforePeriodDoesNotMatch() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 2, "08:00/2h");

            assertThat(period.contains(LocalTime.of(7, 59))).isFalse();
        }

        @Test
        @DisplayName("time after period does not match")
        void timeAfterPeriodDoesNotMatch() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 2, "08:00/2h");

            assertThat(period.contains(LocalTime.of(10, 1))).isFalse();
        }
    }
}

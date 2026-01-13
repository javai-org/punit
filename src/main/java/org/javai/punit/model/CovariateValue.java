package org.javai.punit.model;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * A covariate value, which may be atomic or structured.
 *
 * <p>Covariate values are captured during experiments and compared during
 * probabilistic tests to determine baseline conformance.
 */
public sealed interface CovariateValue permits CovariateValue.StringValue, CovariateValue.TimeWindowValue {

    /**
     * Returns the canonical string representation for storage and hashing.
     *
     * <p>This format is used in:
     * <ul>
     *   <li>YAML spec files</li>
     *   <li>Covariate profile hash computation</li>
     *   <li>Reporting output</li>
     * </ul>
     *
     * @return the canonical string representation
     */
    String toCanonicalString();

    /**
     * Simple string value (e.g., "EU", "Mo-Fr", "Europe/London").
     *
     * <p>Used for covariates with discrete, non-range values.
     */
    record StringValue(String value) implements CovariateValue {

        public StringValue {
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public String toCanonicalString() {
            return value;
        }
    }

    /**
     * Time window value with timezone.
     *
     * <p>Used for the {@code TIME_OF_DAY} covariate to represent the time range
     * during which an experiment was conducted. For statistical robustness,
     * test samples should fall within a comparable window.
     *
     * @param start the start time of the window
     * @param end the end time of the window
     * @param timezone the timezone in which the times are expressed
     */
    record TimeWindowValue(
            LocalTime start,
            LocalTime end,
            ZoneId timezone
    ) implements CovariateValue {

        public TimeWindowValue {
            Objects.requireNonNull(start, "start must not be null");
            Objects.requireNonNull(end, "end must not be null");
            Objects.requireNonNull(timezone, "timezone must not be null");
        }

        @Override
        public String toCanonicalString() {
            return String.format("%s-%s %s", start, end, timezone.getId());
        }

        /**
         * Parses a time window value from its canonical string representation.
         *
         * @param canonical the canonical string (e.g., "14:30-14:45 Europe/London")
         * @return the parsed time window value
         * @throws IllegalArgumentException if the format is invalid
         */
        public static TimeWindowValue parse(String canonical) {
            Objects.requireNonNull(canonical, "canonical must not be null");
            
            int lastSpace = canonical.lastIndexOf(' ');
            if (lastSpace < 0) {
                throw new IllegalArgumentException("Invalid time window format: " + canonical);
            }
            
            String timePart = canonical.substring(0, lastSpace);
            String timezonePart = canonical.substring(lastSpace + 1);
            
            int hyphen = timePart.indexOf('-');
            if (hyphen < 0) {
                throw new IllegalArgumentException("Invalid time window format: " + canonical);
            }
            
            LocalTime start = LocalTime.parse(timePart.substring(0, hyphen));
            LocalTime end = LocalTime.parse(timePart.substring(hyphen + 1));
            ZoneId timezone = ZoneId.of(timezonePart);
            
            return new TimeWindowValue(start, end, timezone);
        }
    }
}


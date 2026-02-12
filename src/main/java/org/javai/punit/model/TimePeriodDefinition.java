package org.javai.punit.model;

import java.time.LocalTime;
import java.util.Objects;

/**
 * A named time-of-day period forming a single partition.
 *
 * <p>Periods use half-open intervals {@code [start, start+durationHours)}.
 * The period must not cross midnight: {@code start + durationHours <= 24:00}.
 *
 * @param start the start time (inclusive)
 * @param durationHours the duration in hours (positive, no midnight crossing)
 * @param label the partition label (non-blank)
 */
public record TimePeriodDefinition(LocalTime start, int durationHours, String label) {

    public TimePeriodDefinition {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(label, "label must not be null");
        if (durationHours <= 0) {
            throw new IllegalArgumentException("durationHours must be positive, got: " + durationHours);
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        int startMinutes = start.getHour() * 60 + start.getMinute();
        if (startMinutes + durationHours * 60 > 24 * 60) {
            throw new IllegalArgumentException(
                    "Period must not cross midnight: " + start + " + " + durationHours + "h exceeds 24:00");
        }
    }

    /**
     * Returns the end time (exclusive) of this period.
     *
     * @return the end time
     */
    public LocalTime end() {
        return start.plusHours(durationHours);
    }

    /**
     * Returns true if the given time falls within this period (half-open interval).
     *
     * @param time the time to check
     * @return true if {@code start <= time < end}
     */
    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end());
    }
}

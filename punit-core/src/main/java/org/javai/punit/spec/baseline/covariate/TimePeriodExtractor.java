package org.javai.punit.spec.baseline.covariate;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javai.punit.model.TimePeriodDefinition;

/**
 * Extracts time-of-day period strings into {@link TimePeriodDefinition} instances.
 *
 * <p>Validates format ({@code HH:mm/Nh}, {@code HH:mm/Nm}, or {@code HH:mm/NhMm}),
 * hour/minute bounds, midnight crossing, and overlap between periods.
 */
final class TimePeriodExtractor {

    static final Pattern TIME_PERIOD_PATTERN = Pattern.compile("^(\\d{2}):(\\d{2})/(?:(\\d+)h)?(?:(\\d+)m)?$");

    /**
     * Extracts time period definitions from the given period strings.
     *
     * @param periods the period strings (e.g. "08:00/2h", "08:00/30m", "08:00/2h30m")
     * @return the extracted definitions (empty list if input is empty)
     * @throws CovariateValidationException if format is invalid, bounds exceeded, or periods overlap
     */
    List<TimePeriodDefinition> extract(String[] periods) {
        if (periods.length == 0) {
            return List.of();
        }

        var result = new ArrayList<TimePeriodDefinition>();

        for (String period : periods) {
            Matcher m = TIME_PERIOD_PATTERN.matcher(period);
            if (!m.matches() || (m.group(3) == null && m.group(4) == null)) {
                throw new CovariateValidationException(
                        "Invalid time period format: '" + period
                                + "'. Expected format: 'HH:mm/Nh', 'HH:mm/Nm', or 'HH:mm/NhMm'"
                                + " (e.g., '08:00/2h', '08:00/30m', '08:00/2h30m')");
            }

            int hours = Integer.parseInt(m.group(1));
            int minutes = Integer.parseInt(m.group(2));

            if (hours > 23) {
                throw new CovariateValidationException(
                        "Invalid hour in time period '" + period + "': " + hours + " (must be 00-23)");
            }
            if (minutes > 59) {
                throw new CovariateValidationException(
                        "Invalid minute in time period '" + period + "': " + minutes + " (must be 00-59)");
            }

            int durationHours = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
            int durationMins = m.group(4) != null ? Integer.parseInt(m.group(4)) : 0;

            if (m.group(3) != null && m.group(4) != null && durationMins > 59) {
                throw new CovariateValidationException(
                        "Minutes component must be 0-59 when combined with hours in time period '"
                                + period + "': " + durationMins + "m (e.g., use '"
                                + hours + ":" + String.format("%02d", minutes) + "/"
                                + (durationHours + durationMins / 60) + "h" + (durationMins % 60 > 0 ? durationMins % 60 + "m" : "")
                                + "' instead)");
            }

            int totalDurationMinutes = durationHours * 60 + durationMins;
            if (totalDurationMinutes <= 0) {
                throw new CovariateValidationException(
                        "Duration must be positive in time period '" + period + "'");
            }

            LocalTime start = LocalTime.of(hours, minutes);
            int startMinutes = hours * 60 + minutes;
            if (startMinutes + totalDurationMinutes > 24 * 60) {
                throw new CovariateValidationException(
                        "Time period '" + period + "' crosses midnight (start + duration exceeds 24:00)");
            }

            result.add(new TimePeriodDefinition(start, totalDurationMinutes));
        }

        // Validate no overlap: sort by start, check adjacent pairs
        var sorted = new ArrayList<>(result);
        sorted.sort((a, b) -> a.start().compareTo(b.start()));
        for (int i = 0; i < sorted.size() - 1; i++) {
            var current = sorted.get(i);
            var next = sorted.get(i + 1);
            if (current.end().isAfter(next.start())) {
                throw new CovariateValidationException(
                        "Time periods overlap: '" + current.label() + "' [" + current.start()
                                + ", " + current.end() + ") and '" + next.label()
                                + "' [" + next.start() + ", " + next.end() + ")");
            }
        }

        return result;
    }
}

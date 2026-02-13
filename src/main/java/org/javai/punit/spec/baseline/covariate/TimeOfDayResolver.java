package org.javai.punit.spec.baseline.covariate;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.TimePeriodDefinition;

/**
 * Resolves the time-of-day covariate to a partition label.
 *
 * <p>Gets the current time from the context, finds the matching declared period,
 * and returns that period's label. Times not in any declared period resolve to a
 * descriptive gap label derived from the uncovered intervals between declared periods.
 */
public final class TimeOfDayResolver implements CovariateResolver {

    private final List<TimePeriodDefinition> periods;
    private final String remainderLabel;

    public TimeOfDayResolver(List<TimePeriodDefinition> periods) {
        this.periods = Objects.requireNonNull(periods, "periods must not be null");
        this.remainderLabel = computeRemainderLabel(periods);
    }

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        LocalTime currentTime = context.now()
                .atZone(context.systemTimezone())
                .toLocalTime();

        for (TimePeriodDefinition period : periods) {
            if (period.contains(currentTime)) {
                return new CovariateValue.StringValue(period.label());
            }
        }

        return new CovariateValue.StringValue(remainderLabel);
    }

    private static String computeRemainderLabel(List<TimePeriodDefinition> periods) {
        if (periods.isEmpty()) {
            return "00:00/24h";
        }

        var sorted = periods.stream()
                .sorted(Comparator.comparing(TimePeriodDefinition::start))
                .toList();

        var gapLabels = new ArrayList<String>();
        var cursor = LocalTime.MIDNIGHT;

        for (TimePeriodDefinition period : sorted) {
            if (cursor.isBefore(period.start())) {
                gapLabels.add(formatGapLabel(cursor, period.start()));
            }
            cursor = period.end();
        }

        if (!cursor.equals(LocalTime.MIDNIGHT)) {
            gapLabels.add(formatGapLabel(cursor, LocalTime.MIDNIGHT));
        }

        return String.join(", ", gapLabels);
    }

    private static String formatGapLabel(LocalTime start, LocalTime end) {
        long minutes;
        if (end.equals(LocalTime.MIDNIGHT)) {
            minutes = Duration.between(start, LocalTime.of(23, 59, 59)).toMinutes() + 1;
        } else {
            minutes = Duration.between(start, end).toMinutes();
        }

        if (minutes > 0 && minutes % 60 == 0) {
            return String.format("%s/%dh", format(start), minutes / 60);
        }
        return String.format("%s-%s", format(start), format(end));
    }

    private static String format(LocalTime time) {
        if (time.equals(LocalTime.MIDNIGHT)) {
            return "00:00";
        }
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}

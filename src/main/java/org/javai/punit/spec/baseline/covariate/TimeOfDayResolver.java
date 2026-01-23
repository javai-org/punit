package org.javai.punit.spec.baseline.covariate;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.javai.punit.model.CovariateValue;

/**
 * Resolver for {@link org.javai.punit.api.StandardCovariate#TIME_OF_DAY}.
 *
 * <p>Resolves to a {@link CovariateValue.TimeWindowValue} representing the
 * experiment execution window (start to end time in the system timezone).
 *
 * <p>Times are truncated to minute precision to ensure stable covariate values
 * across an experiment run. Millisecond/nanosecond differences should not
 * produce different baselines.
 *
 * <p>This resolver requires experiment timing context to be available.
 * During probabilistic tests (not experiments), it uses the current time
 * as a point-in-time for matching against baseline windows.
 */
public final class TimeOfDayResolver implements CovariateResolver {

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        var zone = context.systemTimezone();

        // For experiments: use the full experiment window
        if (context.experimentStartTime().isPresent() && context.experimentEndTime().isPresent()) {
            var start = context.experimentStartTime().get();
            var end = context.experimentEndTime().get();

            var startTime = truncateToMinute(start.atZone(zone).toLocalTime());
            var endTime = truncateToMinute(end.atZone(zone).toLocalTime());

            return new CovariateValue.TimeWindowValue(startTime, endTime, zone);
        }

        // For tests without experiment context: use current time as both start and end
        // This creates a point-in-time that will be matched against baseline windows
        var now = context.now();
        var currentTime = truncateToMinute(now.atZone(zone).toLocalTime());

        return new CovariateValue.TimeWindowValue(currentTime, currentTime, zone);
    }

    /**
     * Truncates a LocalTime to minute precision.
     *
     * <p>This ensures that times like 07:54:22.988 and 07:54:23.379 both become 07:54,
     * producing stable covariate values for hashing and filename generation.
     */
    private static LocalTime truncateToMinute(LocalTime time) {
        return time.truncatedTo(ChronoUnit.MINUTES);
    }
}


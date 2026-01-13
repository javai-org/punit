package org.javai.punit.engine.covariate;

import org.javai.punit.model.CovariateValue;

/**
 * Resolver for {@link org.javai.punit.api.StandardCovariate#TIME_OF_DAY}.
 *
 * <p>Resolves to a {@link CovariateValue.TimeWindowValue} representing the
 * experiment execution window (start to end time in the system timezone).
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

            var startTime = start.atZone(zone).toLocalTime();
            var endTime = end.atZone(zone).toLocalTime();

            return new CovariateValue.TimeWindowValue(startTime, endTime, zone);
        }

        // For tests without experiment context: use current time as both start and end
        // This creates a point-in-time that will be matched against baseline windows
        var now = context.now();
        var currentTime = now.atZone(zone).toLocalTime();

        return new CovariateValue.TimeWindowValue(currentTime, currentTime, zone);
    }
}


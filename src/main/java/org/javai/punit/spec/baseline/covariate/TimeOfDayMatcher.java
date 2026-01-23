package org.javai.punit.spec.baseline.covariate;

import java.time.LocalTime;
import org.javai.punit.model.CovariateValue;

/**
 * Matcher for {@link org.javai.punit.api.StandardCovariate#TIME_OF_DAY}.
 *
 * <p>Checks if the test time falls within the baseline's time window.
 * The test value is typically a point-in-time (start == end) representing
 * when the test sample was taken.
 */
public final class TimeOfDayMatcher implements CovariateMatcher {

    // Allow a 30-minute buffer for the match
    static final int LENIENCY_MINUTES = 30;

    @Override
    public MatchResult match(CovariateValue baselineValue, CovariateValue testValue) {
        if (!(baselineValue instanceof CovariateValue.TimeWindowValue baseline)) {
            return MatchResult.DOES_NOT_CONFORM;
        }

        LocalTime testTime = extractTestTime(testValue);
        if (testTime == null) {
            return MatchResult.DOES_NOT_CONFORM;
        }

        // Check if test time falls within baseline window +/- 30 minutes
        if (isWithinWindow(testTime, baseline.start().minusMinutes(LENIENCY_MINUTES), baseline.end().plusMinutes(LENIENCY_MINUTES))) {
            return MatchResult.CONFORMS;
        }

        return MatchResult.DOES_NOT_CONFORM;
    }

    private LocalTime extractTestTime(CovariateValue testValue) {
        if (testValue instanceof CovariateValue.TimeWindowValue tw) {
            // Use start time as the point-in-time for matching
            return tw.start();
        }
        if (testValue instanceof CovariateValue.StringValue(String value)) {
            // Try to parse as time window format
            try {
                var parsed = CovariateValue.TimeWindowValue.parse(value);
                return parsed.start();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private boolean isWithinWindow(LocalTime time, LocalTime start, LocalTime end) {
        // Handle normal case (e.g., 09:00-17:00)
        if (!end.isBefore(start)) {
            return !time.isBefore(start) && !time.isAfter(end);
        }
        
        // Handle overnight window (e.g., 22:00-06:00)
        // Time is within if it's after start OR before end
        return !time.isBefore(start) || !time.isAfter(end);
    }
}


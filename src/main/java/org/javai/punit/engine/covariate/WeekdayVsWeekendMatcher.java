package org.javai.punit.engine.covariate;

import org.javai.punit.model.CovariateValue;

/**
 * Matcher for {@link org.javai.punit.api.StandardCovariate#WEEKDAY_VERSUS_WEEKEND}.
 *
 * <p>Requires exact string match: "Mo-Fr" matches "Mo-Fr", "Sa-So" matches "Sa-So".
 */
public final class WeekdayVsWeekendMatcher implements CovariateMatcher {

    @Override
    public MatchResult match(CovariateValue baselineValue, CovariateValue testValue) {
        if (!(baselineValue instanceof CovariateValue.StringValue baseline) ||
            !(testValue instanceof CovariateValue.StringValue test)) {
            return MatchResult.DOES_NOT_CONFORM;
        }

        return baseline.value().equals(test.value())
            ? MatchResult.CONFORMS
            : MatchResult.DOES_NOT_CONFORM;
    }
}


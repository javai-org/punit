package org.javai.punit.spec.baseline.covariate;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.TimePeriodDefinition;

/**
 * Resolves the time-of-day covariate to a partition label.
 *
 * <p>Gets the current time from the context, finds the matching declared period,
 * and returns that period's label. Times not in any declared period resolve to "OTHER".
 */
public final class TimeOfDayResolver implements CovariateResolver {

    static final String REMAINDER_LABEL = "OTHER";

    private final List<TimePeriodDefinition> periods;

    public TimeOfDayResolver(List<TimePeriodDefinition> periods) {
        this.periods = Objects.requireNonNull(periods, "periods must not be null");
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

        return new CovariateValue.StringValue(REMAINDER_LABEL);
    }
}

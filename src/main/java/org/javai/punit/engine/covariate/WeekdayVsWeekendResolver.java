package org.javai.punit.engine.covariate;

import org.javai.punit.model.CovariateValue;

/**
 * Resolver for {@link org.javai.punit.api.StandardCovariate#WEEKDAY_VERSUS_WEEKEND}.
 *
 * <p>Resolves to "Mo-Fr" for weekdays (Monday-Friday) or "Sa-So" for weekends
 * (Saturday-Sunday) based on the current date in the system timezone.
 */
public final class WeekdayVsWeekendResolver implements CovariateResolver {

    /** Value for weekdays (Monday through Friday). */
    public static final String WEEKDAY = "Mo-Fr";

    /** Value for weekends (Saturday and Sunday). */
    public static final String WEEKEND = "Sa-So";

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        var dayOfWeek = context.now()
            .atZone(context.systemTimezone())
            .getDayOfWeek();

        String value = switch (dayOfWeek) {
            case SATURDAY, SUNDAY -> WEEKEND;
            default -> WEEKDAY;
        };

        return new CovariateValue.StringValue(value);
    }
}


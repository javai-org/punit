package org.javai.punit.spec.baseline.covariate;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Objects;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.DayGroupDefinition;

/**
 * Resolves the day-of-week covariate to a partition label.
 *
 * <p>Gets the current day from the context, finds the matching declared group,
 * and returns that group's label. Days not in any declared group resolve to "OTHER".
 */
public final class DayOfWeekResolver implements CovariateResolver {

    static final String REMAINDER_LABEL = "OTHER";

    private final List<DayGroupDefinition> groups;

    public DayOfWeekResolver(List<DayGroupDefinition> groups) {
        this.groups = Objects.requireNonNull(groups, "groups must not be null");
    }

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        DayOfWeek currentDay = context.now()
                .atZone(context.systemTimezone())
                .getDayOfWeek();

        for (DayGroupDefinition group : groups) {
            if (group.contains(currentDay)) {
                return new CovariateValue.StringValue(group.label());
            }
        }

        return new CovariateValue.StringValue(REMAINDER_LABEL);
    }
}

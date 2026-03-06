package org.javai.punit.spec.baseline.covariate;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.DayGroupDefinition;

/**
 * Resolves the day-of-week covariate to a partition label.
 *
 * <p>Gets the current day from the context, finds the matching declared group,
 * and returns that group's label. Days not in any declared group resolve to a
 * descriptive remainder label derived from the complement of declared days.
 */
public final class DayOfWeekResolver implements CovariateResolver {

    private final List<DayGroupDefinition> groups;
    private final String remainderLabel;

    public DayOfWeekResolver(List<DayGroupDefinition> groups) {
        this.groups = Objects.requireNonNull(groups, "groups must not be null");
        this.remainderLabel = computeRemainderLabel(groups);
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

        return new CovariateValue.StringValue(remainderLabel);
    }

    private static String computeRemainderLabel(List<DayGroupDefinition> groups) {
        var declaredDays = EnumSet.noneOf(DayOfWeek.class);
        for (DayGroupDefinition group : groups) {
            declaredDays.addAll(group.days());
        }

        var complement = EnumSet.complementOf(declaredDays);
        if (complement.isEmpty()) {
            return "";
        }

        return DayGroupExtractor.deriveDayGroupLabel(complement);
    }
}

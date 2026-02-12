package org.javai.punit.spec.baseline.covariate;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.javai.punit.api.DayGroup;
import org.javai.punit.model.DayGroupDefinition;

/**
 * Extracts {@link DayGroup} annotations into {@link DayGroupDefinition} instances.
 *
 * <p>Validates mutual exclusivity (no day appears in more than one group) and
 * auto-derives labels for well-known groupings (WEEKEND, WEEKDAY).
 */
final class DayGroupExtractor {

    static final Set<DayOfWeek> WEEKEND_DAYS = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    static final Set<DayOfWeek> WEEKDAY_DAYS = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    /**
     * Extracts day group definitions from the given annotations.
     *
     * @param dayGroups the day group annotations
     * @return the extracted definitions (empty list if input is empty)
     * @throws CovariateValidationException if a day appears in more than one group
     */
    List<DayGroupDefinition> extract(DayGroup[] dayGroups) {
        if (dayGroups.length == 0) {
            return List.of();
        }

        var seenDays = EnumSet.noneOf(DayOfWeek.class);
        var result = new ArrayList<DayGroupDefinition>();

        for (DayGroup group : dayGroups) {
            var days = EnumSet.copyOf(Arrays.asList(group.value()));
            if (days.isEmpty()) {
                throw new CovariateValidationException("Day group must contain at least one day");
            }

            for (DayOfWeek day : days) {
                if (!seenDays.add(day)) {
                    throw new CovariateValidationException(
                            "Day " + day + " appears in more than one @DayGroup (mutual exclusivity violated)");
                }
            }

            String label = group.label().isEmpty() ? deriveDayGroupLabel(days) : group.label();
            result.add(new DayGroupDefinition(days, label));
        }

        return result;
    }

    static String deriveDayGroupLabel(Set<DayOfWeek> days) {
        if (days.equals(WEEKEND_DAYS)) {
            return "WEEKEND";
        }
        if (days.equals(WEEKDAY_DAYS)) {
            return "WEEKDAY";
        }
        return days.stream()
                .sorted()
                .map(DayOfWeek::name)
                .collect(Collectors.joining("_"));
    }
}

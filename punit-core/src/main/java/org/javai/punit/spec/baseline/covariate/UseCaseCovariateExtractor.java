package org.javai.punit.spec.baseline.covariate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.api.Covariate;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.UseCase;
import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.model.DayGroupDefinition;
import org.javai.punit.model.RegionGroupDefinition;
import org.javai.punit.model.TimePeriodDefinition;

/**
 * Extracts covariate declarations from use case classes.
 *
 * <p>Parses the five covariate attributes on {@link UseCase}:
 * <ul>
 *   <li>{@code covariateDayOfWeek} &rarr; day-of-week partition groups</li>
 *   <li>{@code covariateTimeOfDay} &rarr; time-of-day periods</li>
 *   <li>{@code covariateRegion} &rarr; region partition groups</li>
 *   <li>{@code covariateTimezone} &rarr; timezone identity flag</li>
 *   <li>{@code covariates} &rarr; custom covariates with categories</li>
 * </ul>
 *
 * <p>All validation errors throw {@link CovariateValidationException}.
 */
public final class UseCaseCovariateExtractor {

    private final DayGroupExtractor dayGroupExtractor = new DayGroupExtractor();
    private final TimePeriodExtractor timePeriodExtractor = new TimePeriodExtractor();
    private final RegionGroupExtractor regionGroupExtractor = new RegionGroupExtractor();

    /**
     * Extracts the covariate declaration from a use case class.
     *
     * @param useCaseClass the use case class
     * @return the covariate declaration (empty if no covariates declared)
     * @throws CovariateValidationException if declarations are invalid
     */
    public CovariateDeclaration extractDeclaration(Class<?> useCaseClass) {
        Objects.requireNonNull(useCaseClass, "useCaseClass must not be null");

        UseCase annotation = useCaseClass.getAnnotation(UseCase.class);
        if (annotation == null) {
            return CovariateDeclaration.EMPTY;
        }

        List<DayGroupDefinition> dayGroups = dayGroupExtractor.extract(annotation.covariateDayOfWeek());
        List<TimePeriodDefinition> timePeriods = timePeriodExtractor.extract(annotation.covariateTimeOfDay());
        List<RegionGroupDefinition> regionGroups = regionGroupExtractor.extract(annotation.covariateRegion());
        boolean timezoneEnabled = annotation.covariateTimezone();
        Map<String, CovariateCategory> customCovariates = extractCustomCovariates(annotation.covariates());

        if (dayGroups.isEmpty() && timePeriods.isEmpty() && regionGroups.isEmpty()
                && !timezoneEnabled && customCovariates.isEmpty()) {
            return CovariateDeclaration.EMPTY;
        }

        return new CovariateDeclaration(dayGroups, timePeriods, regionGroups, timezoneEnabled, customCovariates);
    }

    private Map<String, CovariateCategory> extractCustomCovariates(Covariate[] covariates) {
        if (covariates.length == 0) {
            return Map.of();
        }

        Map<String, CovariateCategory> result = new LinkedHashMap<>();
        for (Covariate cov : covariates) {
            result.put(cov.key(), cov.category());
        }
        return result;
    }
}

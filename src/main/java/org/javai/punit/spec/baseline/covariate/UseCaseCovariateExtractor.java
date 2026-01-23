package org.javai.punit.spec.baseline.covariate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.api.Covariate;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.api.UseCase;
import org.javai.punit.model.CovariateDeclaration;

/**
 * Extracts covariate declarations from use case classes.
 */
public final class UseCaseCovariateExtractor {

    /**
     * Extracts the covariate declaration from a use case class.
     *
     * <p>Legacy {@code customCovariates} (String[]) are automatically assigned
     * the {@link CovariateCategory#OPERATIONAL} category. All covariates in the
     * returned declaration have an explicit category.
     *
     * @param useCaseClass the use case class
     * @return the covariate declaration (empty if no covariates declared)
     */
    public CovariateDeclaration extractDeclaration(Class<?> useCaseClass) {
        Objects.requireNonNull(useCaseClass, "useCaseClass must not be null");

        UseCase annotation = useCaseClass.getAnnotation(UseCase.class);
        if (annotation == null) {
            return CovariateDeclaration.EMPTY;
        }

        StandardCovariate[] standard = annotation.covariates();
        String[] legacyCustom = annotation.customCovariates();
        Covariate[] categorized = annotation.categorizedCovariates();

        if (standard.length == 0 && legacyCustom.length == 0 && categorized.length == 0) {
            return CovariateDeclaration.EMPTY;
        }

        // Convert all custom covariates to categorized map
        // Legacy customCovariates get OPERATIONAL as their category
        Map<String, CovariateCategory> categorizedMap = new HashMap<>();
        
        for (String legacyKey : legacyCustom) {
            categorizedMap.put(legacyKey, CovariateCategory.OPERATIONAL);
        }
        
        for (Covariate cov : categorized) {
            categorizedMap.put(cov.key(), cov.category());
        }

        return CovariateDeclaration.of(standard, categorizedMap);
    }
}


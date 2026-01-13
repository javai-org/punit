package org.javai.punit.engine.covariate;

import java.util.List;
import java.util.Objects;

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
        String[] custom = annotation.customCovariates();

        if (standard.length == 0 && custom.length == 0) {
            return CovariateDeclaration.EMPTY;
        }

        return new CovariateDeclaration(List.of(standard), List.of(custom));
    }
}


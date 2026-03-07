package org.javai.punit.spec.baseline.covariate;

import org.javai.punit.model.CovariateValue;

/**
 * Strategy for resolving a covariate's value from the environment.
 *
 * <p>Each standard covariate has a dedicated resolver implementation.
 * Custom covariates use {@link CustomCovariateResolver}.
 */
@FunctionalInterface
public interface CovariateResolver {

    /**
     * Resolves the covariate value for the current execution context.
     *
     * @param context the resolution context (provides environment access, timestamps)
     * @return the resolved value
     * @throws IllegalStateException if required context is missing
     */
    CovariateValue resolve(CovariateResolutionContext context);
}


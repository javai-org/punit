package org.javai.punit.spec.baseline.covariate;

import org.javai.punit.model.CovariateValue;

/**
 * Resolver for the timezone identity covariate.
 *
 * <p>Resolves to the system default timezone ID (e.g., "Europe/London", "America/New_York").
 */
public final class TimezoneResolver implements CovariateResolver {

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        return new CovariateValue.StringValue(context.systemTimezone().getId());
    }
}


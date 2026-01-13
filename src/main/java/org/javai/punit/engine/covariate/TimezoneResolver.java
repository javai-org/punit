package org.javai.punit.engine.covariate;

import org.javai.punit.model.CovariateValue;

/**
 * Resolver for {@link org.javai.punit.api.StandardCovariate#TIMEZONE}.
 *
 * <p>Resolves to the system default timezone ID (e.g., "Europe/London", "America/New_York").
 */
public final class TimezoneResolver implements CovariateResolver {

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        return new CovariateValue.StringValue(context.systemTimezone().getId());
    }
}


package org.javai.punit.engine.covariate;

import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;

/**
 * Resolver for {@link org.javai.punit.api.StandardCovariate#REGION}.
 *
 * <p>Resolves from (in order):
 * <ol>
 *   <li>System property: {@code punit.region}</li>
 *   <li>Environment variable: {@code PUNIT_REGION}</li>
 *   <li>PUnit environment map: {@code region}</li>
 * </ol>
 *
 * <p>If not found, returns {@link CovariateProfile#NOT_SET}.
 */
public final class RegionResolver implements CovariateResolver {

    /** System property key for region. */
    public static final String SYSTEM_PROPERTY_KEY = "punit.region";

    /** Environment variable key for region. */
    public static final String ENV_VAR_KEY = "PUNIT_REGION";

    /** PUnit environment key for region. */
    public static final String PUNIT_ENV_KEY = "region";

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        var value = context.getSystemProperty(SYSTEM_PROPERTY_KEY)
            .or(() -> context.getEnvironmentVariable(ENV_VAR_KEY))
            .or(() -> context.getPunitEnvironment(PUNIT_ENV_KEY))
            .orElse(CovariateProfile.NOT_SET);

        return new CovariateValue.StringValue(value);
    }
}


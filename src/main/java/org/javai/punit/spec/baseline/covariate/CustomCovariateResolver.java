package org.javai.punit.spec.baseline.covariate;

import java.util.Objects;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;

/**
 * Resolver for custom (user-defined) covariates.
 *
 * <p>Resolves from (in order):
 * <ol>
 *   <li>System property: {@code -D{key}=value}</li>
 *   <li>Environment variable: {@code KEY=value} (uppercased, underscores for dots)</li>
 *   <li>PUnit environment map: {@code key}</li>
 * </ol>
 *
 * <p>If not found, returns {@link CovariateProfile#UNDEFINED}.
 */
public final class CustomCovariateResolver implements CovariateResolver {

    private final String key;

    /**
     * Creates a resolver for the given custom covariate key.
     *
     * @param key the covariate key
     */
    public CustomCovariateResolver(String key) {
        this.key = Objects.requireNonNull(key, "key must not be null");
    }

    /**
     * Returns the covariate key this resolver handles.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        // Try sources in order: system property, env var, punit env
        var value = context.getSystemProperty(key)
            .or(() -> context.getEnvironmentVariable(toEnvVarName(key)))
            .or(() -> context.getPunitEnvironment(key))
            .orElse(CovariateProfile.UNDEFINED);

        return new CovariateValue.StringValue(value);
    }

    /**
     * Converts a covariate key to environment variable format.
     *
     * <p>Uppercases and replaces dots/hyphens with underscores.
     *
     * @param key the covariate key
     * @return the environment variable name
     */
    static String toEnvVarName(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }
}


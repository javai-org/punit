package org.javai.punit.spec.baseline.covariate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.api.StandardCovariate;

/**
 * Registry mapping covariate keys to their resolvers.
 *
 * <p>Standard covariates are registered automatically. Custom covariates
 * are handled by {@link CustomCovariateResolver}.
 */
public final class CovariateResolverRegistry {

    private final Map<String, CovariateResolver> resolvers;

    private CovariateResolverRegistry(Map<String, CovariateResolver> resolvers) {
        this.resolvers = Map.copyOf(resolvers);
    }

    /**
     * Creates a registry with standard covariate resolvers.
     *
     * @return a new registry with standard resolvers
     */
    public static CovariateResolverRegistry withStandardResolvers() {
        return builder()
            .register(StandardCovariate.WEEKDAY_VERSUS_WEEKEND, new WeekdayVsWeekendResolver())
            .register(StandardCovariate.TIME_OF_DAY, new TimeOfDayResolver())
            .register(StandardCovariate.TIMEZONE, new TimezoneResolver())
            .register(StandardCovariate.REGION, new RegionResolver())
            .build();
    }

    /**
     * Returns the resolver for the given covariate key.
     *
     * <p>If no specific resolver is registered for the key, returns a
     * {@link CustomCovariateResolver} for that key.
     *
     * @param key the covariate key
     * @return the resolver (never null)
     */
    public CovariateResolver getResolver(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return resolvers.getOrDefault(key, new CustomCovariateResolver(key));
    }

    /**
     * Returns true if a specific resolver is registered for the key.
     *
     * @param key the covariate key
     * @return true if a resolver is registered
     */
    public boolean hasResolver(String key) {
        return resolvers.containsKey(key);
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CovariateResolverRegistry}.
     */
    public static final class Builder {
        private final Map<String, CovariateResolver> resolvers = new HashMap<>();

        private Builder() {
        }

        /**
         * Registers a resolver for a standard covariate.
         *
         * @param covariate the standard covariate
         * @param resolver the resolver
         * @return this builder
         */
        public Builder register(StandardCovariate covariate, CovariateResolver resolver) {
            Objects.requireNonNull(covariate, "covariate must not be null");
            Objects.requireNonNull(resolver, "resolver must not be null");
            resolvers.put(covariate.key(), resolver);
            return this;
        }

        /**
         * Registers a resolver for a custom covariate key.
         *
         * @param key the covariate key
         * @param resolver the resolver
         * @return this builder
         */
        public Builder register(String key, CovariateResolver resolver) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(resolver, "resolver must not be null");
            resolvers.put(key, resolver);
            return this;
        }

        /**
         * Builds the registry.
         *
         * @return the registry
         */
        public CovariateResolverRegistry build() {
            return new CovariateResolverRegistry(resolvers);
        }
    }
}


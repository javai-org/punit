package org.javai.punit.spec.baseline.covariate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.javai.punit.api.StandardCovariate;

/**
 * Registry mapping covariate keys to their matchers.
 *
 * <p>Standard covariates have specialized matchers. Custom covariates
 * default to {@link ExactStringMatcher}.
 */
public final class CovariateMatcherRegistry {

    private final Map<String, CovariateMatcher> matchers;
    private final CovariateMatcher defaultMatcher;

    private CovariateMatcherRegistry(Map<String, CovariateMatcher> matchers, CovariateMatcher defaultMatcher) {
        this.matchers = Map.copyOf(matchers);
        this.defaultMatcher = defaultMatcher;
    }

    /**
     * Creates a registry with standard covariate matchers.
     *
     * @return a new registry with standard matchers
     */
    public static CovariateMatcherRegistry withStandardMatchers() {
        return builder()
            .register(StandardCovariate.WEEKDAY_VERSUS_WEEKEND, new WeekdayVsWeekendMatcher())
            .register(StandardCovariate.TIME_OF_DAY, new TimeOfDayMatcher())
            .register(StandardCovariate.TIMEZONE, new ExactStringMatcher())
            .register(StandardCovariate.REGION, new ExactStringMatcher(false)) // Case-insensitive
            .build();
    }

    /**
     * Returns the matcher for the given covariate key.
     *
     * <p>If no specific matcher is registered, returns an {@link ExactStringMatcher}.
     *
     * @param key the covariate key
     * @return the matcher (never null)
     */
    public CovariateMatcher getMatcher(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return matchers.getOrDefault(key, defaultMatcher);
    }

    /**
     * Returns true if a specific matcher is registered for the key.
     *
     * @param key the covariate key
     * @return true if a matcher is registered
     */
    public boolean hasMatcher(String key) {
        return matchers.containsKey(key);
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
     * Builder for {@link CovariateMatcherRegistry}.
     */
    public static final class Builder {
        private final Map<String, CovariateMatcher> matchers = new HashMap<>();
        private CovariateMatcher defaultMatcher = new ExactStringMatcher();

        private Builder() {
        }

        /**
         * Registers a matcher for a standard covariate.
         *
         * @param covariate the standard covariate
         * @param matcher the matcher
         * @return this builder
         */
        public Builder register(StandardCovariate covariate, CovariateMatcher matcher) {
            Objects.requireNonNull(covariate, "covariate must not be null");
            Objects.requireNonNull(matcher, "matcher must not be null");
            matchers.put(covariate.key(), matcher);
            return this;
        }

        /**
         * Registers a matcher for a custom covariate key.
         *
         * @param key the covariate key
         * @param matcher the matcher
         * @return this builder
         */
        public Builder register(String key, CovariateMatcher matcher) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(matcher, "matcher must not be null");
            matchers.put(key, matcher);
            return this;
        }

        /**
         * Sets the default matcher for unregistered keys.
         *
         * @param defaultMatcher the default matcher
         * @return this builder
         */
        public Builder defaultMatcher(CovariateMatcher defaultMatcher) {
            this.defaultMatcher = Objects.requireNonNull(defaultMatcher, "defaultMatcher must not be null");
            return this;
        }

        /**
         * Builds the registry.
         *
         * @return the registry
         */
        public CovariateMatcherRegistry build() {
            return new CovariateMatcherRegistry(matchers, defaultMatcher);
        }
    }
}


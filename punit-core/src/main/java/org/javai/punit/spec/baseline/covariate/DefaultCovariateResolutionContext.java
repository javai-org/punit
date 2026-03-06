package org.javai.punit.spec.baseline.covariate;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation of {@link CovariateResolutionContext} with real system access.
 *
 * <p>Supports injected experiment timing and PUnit environment map.
 */
public final class DefaultCovariateResolutionContext implements CovariateResolutionContext {

    private final Instant now;
    private final Instant experimentStartTime;
    private final Instant experimentEndTime;
    private final ZoneId systemTimezone;
    private final Map<String, String> punitEnvironment;
    private final Object useCaseInstance;

    private DefaultCovariateResolutionContext(Builder builder) {
        this.now = builder.now != null ? builder.now : Instant.now();
        this.experimentStartTime = builder.experimentStartTime;
        this.experimentEndTime = builder.experimentEndTime;
        this.systemTimezone = builder.systemTimezone != null ? builder.systemTimezone : ZoneId.systemDefault();
        this.punitEnvironment = builder.punitEnvironment != null ? Map.copyOf(builder.punitEnvironment) : Map.of();
        this.useCaseInstance = builder.useCaseInstance;
    }

    /**
     * Creates a context for the current instant with default settings.
     *
     * @return a new context
     */
    public static DefaultCovariateResolutionContext forNow() {
        return builder().build();
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public Optional<Instant> experimentStartTime() {
        return Optional.ofNullable(experimentStartTime);
    }

    @Override
    public Optional<Instant> experimentEndTime() {
        return Optional.ofNullable(experimentEndTime);
    }

    @Override
    public ZoneId systemTimezone() {
        return systemTimezone;
    }

    @Override
    public Optional<String> getSystemProperty(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(System.getProperty(key));
    }

    @Override
    public Optional<String> getEnvironmentVariable(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(System.getenv(key));
    }

    @Override
    public Optional<String> getPunitEnvironment(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(punitEnvironment.get(key));
    }

    @Override
    public Optional<Object> getUseCaseInstance() {
        return Optional.ofNullable(useCaseInstance);
    }

    /**
     * Builder for {@link DefaultCovariateResolutionContext}.
     */
    public static final class Builder {
        private Instant now;
        private Instant experimentStartTime;
        private Instant experimentEndTime;
        private ZoneId systemTimezone;
        private Map<String, String> punitEnvironment;
        private Object useCaseInstance;

        private Builder() {
        }

        /**
         * Sets the current instant (defaults to Instant.now()).
         *
         * @param now the instant to use as "now"
         * @return this builder
         */
        public Builder now(Instant now) {
            this.now = now;
            return this;
        }

        /**
         * Sets the experiment start time.
         *
         * @param experimentStartTime the experiment start time
         * @return this builder
         */
        public Builder experimentStartTime(Instant experimentStartTime) {
            this.experimentStartTime = experimentStartTime;
            return this;
        }

        /**
         * Sets the experiment end time.
         *
         * @param experimentEndTime the experiment end time
         * @return this builder
         */
        public Builder experimentEndTime(Instant experimentEndTime) {
            this.experimentEndTime = experimentEndTime;
            return this;
        }

        /**
         * Sets the experiment timing (both start and end).
         *
         * @param start the experiment start time
         * @param end the experiment end time
         * @return this builder
         */
        public Builder experimentTiming(Instant start, Instant end) {
            this.experimentStartTime = start;
            this.experimentEndTime = end;
            return this;
        }

        /**
         * Sets the system timezone (defaults to ZoneId.systemDefault()).
         *
         * @param systemTimezone the timezone to use
         * @return this builder
         */
        public Builder systemTimezone(ZoneId systemTimezone) {
            this.systemTimezone = systemTimezone;
            return this;
        }

        /**
         * Sets the PUnit environment map.
         *
         * @param punitEnvironment the environment map
         * @return this builder
         */
        public Builder punitEnvironment(Map<String, String> punitEnvironment) {
            this.punitEnvironment = punitEnvironment;
            return this;
        }

        /**
         * Sets the use case instance for @CovariateSource method invocation.
         *
         * @param useCaseInstance the use case instance
         * @return this builder
         */
        public Builder useCaseInstance(Object useCaseInstance) {
            this.useCaseInstance = useCaseInstance;
            return this;
        }

        /**
         * Builds the context.
         *
         * @return the context
         */
        public DefaultCovariateResolutionContext build() {
            return new DefaultCovariateResolutionContext(this);
        }
    }
}


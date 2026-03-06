package org.javai.punit.spec.baseline.covariate;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Context for covariate resolution, providing access to environment and timing.
 *
 * <p>This interface abstracts the environment from which covariate values
 * are resolved, enabling testability and flexibility.
 */
public interface CovariateResolutionContext {

    /**
     * Returns the current instant (for time-based covariates).
     *
     * @return the current instant
     */
    Instant now();

    /**
     * Returns the experiment start time (for TIME_OF_DAY resolution).
     *
     * <p>During experiments, this is when sampling began.
     * During tests, this may be empty or set to test start time.
     *
     * @return the experiment start time, or empty if not in experiment context
     */
    Optional<Instant> experimentStartTime();

    /**
     * Returns the experiment end time (for TIME_OF_DAY resolution).
     *
     * <p>During experiments, this is when sampling completed.
     * During tests, this may be empty or set to current time.
     *
     * @return the experiment end time, or empty if not in experiment context
     */
    Optional<Instant> experimentEndTime();

    /**
     * Returns the system timezone.
     *
     * @return the system default timezone
     */
    ZoneId systemTimezone();

    /**
     * Returns a system property value.
     *
     * @param key the property key
     * @return the property value, or empty if not set
     */
    Optional<String> getSystemProperty(String key);

    /**
     * Returns an environment variable value.
     *
     * @param key the variable name
     * @return the variable value, or empty if not set
     */
    Optional<String> getEnvironmentVariable(String key);

    /**
     * Returns a value from the PUnit environment map.
     *
     * <p>The PUnit environment is a programmatic map for setting
     * covariate values that aren't available via system properties
     * or environment variables.
     *
     * @param key the environment key
     * @return the value, or empty if not set
     */
    Optional<String> getPunitEnvironment(String key);

    /**
     * Returns the use case instance for @CovariateSource method invocation.
     *
     * <p>If a use case instance is available, @CovariateSource methods
     * on it can be invoked to resolve covariate values.
     *
     * @return the use case instance, or empty if not available
     */
    default Optional<Object> getUseCaseInstance() {
        return Optional.empty();
    }
}


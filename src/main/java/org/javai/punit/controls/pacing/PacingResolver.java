package org.javai.punit.controls.pacing;

import java.lang.reflect.Method;
import org.javai.punit.api.Pacing;

/**
 * Resolves pacing configuration from multiple sources with defined precedence.
 *
 * <h2>Precedence (highest to lowest)</h2>
 * <ol>
 *   <li>System property (e.g., {@code -Dpunit.pacing.maxRpm=60})</li>
 *   <li>Environment variable (e.g., {@code PUNIT_PACING_MAX_RPM=60})</li>
 *   <li>Annotation value (e.g., {@code @Pacing(maxRequestsPerMinute = 60)})</li>
 *   <li>Framework default (no pacing)</li>
 * </ol>
 *
 * <p>This allows different pacing configurations per environment without code changes.
 *
 * @see Pacing
 * @see PacingConfiguration
 * @see PacingCalculator
 */
public class PacingResolver {

    // System property names
    public static final String PROP_MAX_RPS = "punit.pacing.maxRps";
    public static final String PROP_MAX_RPM = "punit.pacing.maxRpm";
    public static final String PROP_MAX_RPH = "punit.pacing.maxRph";
    public static final String PROP_MAX_CONCURRENT = "punit.pacing.maxConcurrent";
    public static final String PROP_MIN_MS_PER_SAMPLE = "punit.pacing.minMsPerSample";

    // Environment variable names
    public static final String ENV_MAX_RPS = "PUNIT_PACING_MAX_RPS";
    public static final String ENV_MAX_RPM = "PUNIT_PACING_MAX_RPM";
    public static final String ENV_MAX_RPH = "PUNIT_PACING_MAX_RPH";
    public static final String ENV_MAX_CONCURRENT = "PUNIT_PACING_MAX_CONCURRENT";
    public static final String ENV_MIN_MS_PER_SAMPLE = "PUNIT_PACING_MIN_MS_PER_SAMPLE";

    private final PacingCalculator calculator;

    /**
     * Creates a new resolver with a default calculator.
     */
    public PacingResolver() {
        this(new PacingCalculator());
    }

    /**
     * Creates a new resolver with the specified calculator.
     *
     * @param calculator the calculator to use for computing execution plans
     */
    public PacingResolver(PacingCalculator calculator) {
        this.calculator = calculator;
    }

    /**
     * Resolves pacing configuration for a test method.
     *
     * @param testMethod the test method (may have @Pacing annotation)
     * @param samples the number of samples to execute
     * @return the resolved pacing configuration
     */
    public PacingConfiguration resolve(Method testMethod, int samples) {
        return resolve(testMethod, samples, 0);
    }

    /**
     * Resolves pacing configuration for a test method with estimated latency.
     *
     * @param testMethod the test method (may have @Pacing annotation)
     * @param samples the number of samples to execute
     * @param estimatedLatencyMs estimated average latency per sample
     * @return the resolved pacing configuration
     */
    public PacingConfiguration resolve(Method testMethod, int samples, long estimatedLatencyMs) {
        Pacing pacing = testMethod.getAnnotation(Pacing.class);
        return resolve(pacing, samples, estimatedLatencyMs);
    }

    /**
     * Resolves pacing configuration from an annotation.
     *
     * @param pacing the pacing annotation (may be null)
     * @param samples the number of samples to execute
     * @param estimatedLatencyMs estimated average latency per sample
     * @return the resolved pacing configuration
     */
    public PacingConfiguration resolve(Pacing pacing, int samples, long estimatedLatencyMs) {
        // Resolve each constraint with precedence
        double maxRps = resolveDouble(PROP_MAX_RPS, ENV_MAX_RPS,
                pacing != null ? pacing.maxRequestsPerSecond() : 0, 0);
        double maxRpm = resolveDouble(PROP_MAX_RPM, ENV_MAX_RPM,
                pacing != null ? pacing.maxRequestsPerMinute() : 0, 0);
        double maxRph = resolveDouble(PROP_MAX_RPH, ENV_MAX_RPH,
                pacing != null ? pacing.maxRequestsPerHour() : 0, 0);
        int maxConcurrent = resolveInt(PROP_MAX_CONCURRENT, ENV_MAX_CONCURRENT,
                pacing != null ? pacing.maxConcurrentRequests() : 0, 0);
        long minMsPerSample = resolveLong(PROP_MIN_MS_PER_SAMPLE, ENV_MIN_MS_PER_SAMPLE,
                pacing != null ? pacing.minMsPerSample() : 0, 0);

        // Validate constraints
        validateConstraints(maxRps, maxRpm, maxRph, maxConcurrent, minMsPerSample);

        // Compute execution plan
        return calculator.compute(
                samples,
                maxRps,
                maxRpm,
                maxRph,
                maxConcurrent,
                minMsPerSample,
                estimatedLatencyMs
        );
    }

    /**
     * Validates pacing constraints.
     *
     * @throws IllegalArgumentException if any constraint is invalid
     */
    private void validateConstraints(double maxRps, double maxRpm, double maxRph,
                                     int maxConcurrent, long minMsPerSample) {
        if (maxRps < 0) {
            throw new IllegalArgumentException("maxRequestsPerSecond must be >= 0, but was: " + maxRps);
        }
        if (maxRpm < 0) {
            throw new IllegalArgumentException("maxRequestsPerMinute must be >= 0, but was: " + maxRpm);
        }
        if (maxRph < 0) {
            throw new IllegalArgumentException("maxRequestsPerHour must be >= 0, but was: " + maxRph);
        }
        if (maxConcurrent < 0) {
            throw new IllegalArgumentException("maxConcurrentRequests must be >= 0, but was: " + maxConcurrent);
        }
        if (minMsPerSample < 0) {
            throw new IllegalArgumentException("minMsPerSample must be >= 0, but was: " + minMsPerSample);
        }
    }

    /**
     * Resolves an integer value with precedence: system prop > env var > annotation.
     */
    private int resolveInt(String sysProp, String envVar, int annotationValue, int defaultValue) {
        String sysPropValue = System.getProperty(sysProp);
        if (sysPropValue != null && !sysPropValue.isEmpty()) {
            try {
                return Integer.parseInt(sysPropValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for system property " + sysProp + ": " + sysPropValue);
            }
        }

        String envValue = getEnvironmentVariable(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for environment variable " + envVar + ": " + envValue);
            }
        }

        return annotationValue;
    }

    /**
     * Resolves a long value with precedence: system prop > env var > annotation.
     */
    private long resolveLong(String sysProp, String envVar, long annotationValue, long defaultValue) {
        String sysPropValue = System.getProperty(sysProp);
        if (sysPropValue != null && !sysPropValue.isEmpty()) {
            try {
                return Long.parseLong(sysPropValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for system property " + sysProp + ": " + sysPropValue);
            }
        }

        String envValue = getEnvironmentVariable(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Long.parseLong(envValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for environment variable " + envVar + ": " + envValue);
            }
        }

        return annotationValue;
    }

    /**
     * Resolves a double value with precedence: system prop > env var > annotation.
     */
    private double resolveDouble(String sysProp, String envVar, double annotationValue, double defaultValue) {
        String sysPropValue = System.getProperty(sysProp);
        if (sysPropValue != null && !sysPropValue.isEmpty()) {
            try {
                return Double.parseDouble(sysPropValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for system property " + sysProp + ": " + sysPropValue);
            }
        }

        String envValue = getEnvironmentVariable(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Double.parseDouble(envValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for environment variable " + envVar + ": " + envValue);
            }
        }

        return annotationValue;
    }

    /**
     * Gets an environment variable. Extracted for testability.
     */
    protected String getEnvironmentVariable(String name) {
        return System.getenv(name);
    }
}


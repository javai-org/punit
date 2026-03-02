package org.javai.punit.ptest.engine;

import org.javai.punit.api.Latency;

/**
 * Resolved latency assertion configuration from a {@link Latency} annotation.
 *
 * <p>Encapsulates which percentiles are asserted and their threshold values.
 * A threshold of {@code -1} means that percentile is not asserted.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
record LatencyAssertionConfig(
        long p50Ms,
        long p90Ms,
        long p95Ms,
        long p99Ms,
        boolean disabled
) {

    /** System property to enforce latency assertions (default: false = advisory). */
    static final String PROP_LATENCY_ENFORCE = "punit.latency.enforce";

    /** Environment variable to enforce latency assertions (default: false = advisory). */
    static final String ENV_LATENCY_ENFORCE = "PUNIT_LATENCY_ENFORCE";

    /**
     * Creates a config from a {@link Latency} annotation.
     *
     * @param latency the annotation
     * @return the resolved config
     */
    static LatencyAssertionConfig fromAnnotation(Latency latency) {
        return new LatencyAssertionConfig(
                latency.p50Ms(),
                latency.p90Ms(),
                latency.p95Ms(),
                latency.p99Ms(),
                latency.disabled()
        );
    }

    /**
     * Checks whether latency assertions are enforced (breaches fail the test).
     *
     * <p>By default, latency assertions are advisory — breaches produce warnings but
     * do not fail the test. Set {@code -Dpunit.latency.enforce=true} or
     * {@code PUNIT_LATENCY_ENFORCE=true} to make breaches fail the test.
     *
     * @return true if latency breaches should fail the test
     */
    static boolean isEnforced() {
        String sysProp = System.getProperty(PROP_LATENCY_ENFORCE);
        if (sysProp != null) {
            return Boolean.parseBoolean(sysProp.trim());
        }
        String envVar = System.getenv(ENV_LATENCY_ENFORCE);
        if (envVar != null) {
            return Boolean.parseBoolean(envVar.trim());
        }
        return false;
    }

    /**
     * Returns true if any explicit latency assertion is requested
     * (i.e. explicit thresholds are set and latency is not disabled).
     */
    boolean isLatencyRequested() {
        return !disabled && hasExplicitThresholds();
    }

    /**
     * Returns true if at least one explicit percentile threshold is set.
     */
    boolean hasExplicitThresholds() {
        return p50Ms >= 0 || p90Ms >= 0 || p95Ms >= 0 || p99Ms >= 0;
    }

    /**
     * Returns true if the given percentile has an explicit threshold.
     */
    boolean hasP50() {
        return p50Ms >= 0;
    }

    boolean hasP90() {
        return p90Ms >= 0;
    }

    boolean hasP95() {
        return p95Ms >= 0;
    }

    boolean hasP99() {
        return p99Ms >= 0;
    }
}

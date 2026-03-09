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
     * Determines whether latency assertions are effectively enforced, considering
     * both the threshold origin and the global enforcement flag.
     *
     * <p>The enforcement model is context-aware:
     * <ul>
     *   <li>Explicit thresholds (developer declared {@code @Latency} with at least one
     *       percentile value ≥ 0) are always enforced. The global flag has no effect.</li>
     *   <li>Baseline-derived thresholds (no explicit {@code @Latency} values) are advisory
     *       by default. Set {@code -Dpunit.latency.enforce=true} or
     *       {@code PUNIT_LATENCY_ENFORCE=true} to enforce them.</li>
     * </ul>
     *
     * @param hasExplicitThresholds true if the {@code @Latency} annotation declares
     *                              at least one percentile value ≥ 0
     * @return true if latency breaches should fail the test
     */
    static boolean isEffectivelyEnforced(boolean hasExplicitThresholds) {
        if (hasExplicitThresholds) {
            return true;
        }
        return isGlobalFlagSet();
    }

    /**
     * Checks whether the global latency enforcement flag is set.
     *
     * <p>This flag promotes baseline-derived thresholds from advisory to enforced.
     * It has no effect on explicit thresholds, which are always enforced.
     *
     * @return true if the global enforcement flag is set
     */
    static boolean isGlobalFlagSet() {
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

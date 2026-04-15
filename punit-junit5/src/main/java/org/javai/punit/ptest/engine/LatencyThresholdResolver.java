package org.javai.punit.ptest.engine;

import org.javai.punit.spec.model.LatencyBaseline;
import org.javai.punit.statistics.LatencyThresholdDeriver;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Orchestrates latency threshold resolution from annotation and/or baseline data.
 *
 * <h2>Decision matrix</h2>
 * <table>
 *   <tr><th>Baseline w/latency</th><th>@Latency explicit</th><th>@Latency(disabled)</th><th>Result</th></tr>
 *   <tr><td>No</td><td>No</td><td>No</td><td>No latency assertion</td></tr>
 *   <tr><td>No</td><td>Yes</td><td>No</td><td>Explicit thresholds</td></tr>
 *   <tr><td>No</td><td>—</td><td>Yes</td><td>No latency assertion</td></tr>
 *   <tr><td>Yes</td><td>No</td><td>No</td><td>Derive from baseline (automatic)</td></tr>
 *   <tr><td>Yes</td><td>Yes</td><td>No</td><td>Explicit thresholds (override baseline)</td></tr>
 *   <tr><td>Yes</td><td>No</td><td>Yes</td><td>No latency assertion (opted out)</td></tr>
 *   <tr><td>Yes</td><td>Yes</td><td>Yes</td><td>Misconfiguration error</td></tr>
 * </table>
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
class LatencyThresholdResolver {

    /**
     * Resolved thresholds for a single percentile.
     *
     * @param thresholdMs the resolved threshold value (-1 if not asserted)
     * @param source description of where the threshold came from
     */
    record ResolvedPercentile(long thresholdMs, String source) {
        static final ResolvedPercentile NOT_ASSERTED = new ResolvedPercentile(-1, "");
    }

    /**
     * Complete resolved threshold set.
     */
    record ResolvedThresholds(
            ResolvedPercentile p50,
            ResolvedPercentile p90,
            ResolvedPercentile p95,
            ResolvedPercentile p99
    ) {
        /**
         * Converts to a LatencyAssertionConfig with resolved thresholds.
         */
        LatencyAssertionConfig toConfig() {
            return new LatencyAssertionConfig(
                    p50.thresholdMs(), p90.thresholdMs(),
                    p95.thresholdMs(), p99.thresholdMs(),
                    false // baseline already resolved
            );
        }

        /**
         * Returns the source for a given percentile label.
         */
        String sourceFor(String label) {
            return switch (label) {
                case "p50" -> p50.source();
                case "p90" -> p90.source();
                case "p95" -> p95.source();
                case "p99" -> p99.source();
                default -> "explicit";
            };
        }
    }

    private static final ResolvedThresholds ALL_NOT_ASSERTED = new ResolvedThresholds(
            ResolvedPercentile.NOT_ASSERTED,
            ResolvedPercentile.NOT_ASSERTED,
            ResolvedPercentile.NOT_ASSERTED,
            ResolvedPercentile.NOT_ASSERTED
    );

    /**
     * Resolves latency thresholds according to the decision matrix.
     *
     * @param annotationConfig the raw config from the @Latency annotation
     * @param baseline the latency baseline from the spec (null if not available)
     * @param confidence the confidence level for CI computation
     * @return the resolved thresholds
     * @throws ExtensionConfigurationException on misconfiguration
     */
    ResolvedThresholds resolve(LatencyAssertionConfig annotationConfig,
                               LatencyBaseline baseline,
                               double confidence) {

        boolean hasExplicit = annotationConfig.hasExplicitThresholds();
        boolean hasBaseline = baseline != null;

        // disabled + explicit is always a misconfiguration
        if (annotationConfig.disabled() && hasExplicit) {
            throw new ExtensionConfigurationException(
                    "@Latency(disabled = true) cannot be combined with explicit latency thresholds. " +
                    "Either remove the explicit thresholds or remove disabled = true.");
        }

        // disabled → no latency assertions
        if (annotationConfig.disabled()) {
            return ALL_NOT_ASSERTED;
        }

        // explicit thresholds take precedence — whether or not a baseline exists
        if (hasExplicit) {
            return resolveExplicitOnly(annotationConfig);
        }

        // baseline present, no explicit → derive automatically
        if (hasBaseline) {
            return resolveFromBaseline(baseline, confidence);
        }

        // neither explicit nor baseline → no latency assertions
        return ALL_NOT_ASSERTED;
    }

    private ResolvedThresholds resolveExplicitOnly(LatencyAssertionConfig config) {
        return new ResolvedThresholds(
                explicitPercentile(config.p50Ms()),
                explicitPercentile(config.p90Ms()),
                explicitPercentile(config.p95Ms()),
                explicitPercentile(config.p99Ms())
        );
    }

    private ResolvedThresholds resolveFromBaseline(LatencyBaseline baseline, double confidence) {
        double[] baselineVector = baseline.sortedLatenciesAsDoubles();
        return new ResolvedThresholds(
                deriveFromBaseline(baselineVector, 0.50, confidence),
                deriveFromBaseline(baselineVector, 0.90, confidence),
                deriveFromBaseline(baselineVector, 0.95, confidence),
                deriveFromBaseline(baselineVector, 0.99, confidence)
        );
    }

    private ResolvedPercentile explicitPercentile(long thresholdMs) {
        if (thresholdMs < 0) {
            return ResolvedPercentile.NOT_ASSERTED;
        }
        return new ResolvedPercentile(thresholdMs, "explicit");
    }

    private ResolvedPercentile deriveFromBaseline(double[] baselineLatencies, double p, double confidence) {
        LatencyThresholdDeriver.Threshold derived =
                LatencyThresholdDeriver.derive(baselineLatencies, p, confidence);
        long thresholdMs = (long) Math.ceil(derived.threshold());
        return new ResolvedPercentile(thresholdMs, "from baseline");
    }
}

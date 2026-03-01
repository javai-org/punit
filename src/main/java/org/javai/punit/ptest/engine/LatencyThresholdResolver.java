package org.javai.punit.ptest.engine;

import org.javai.punit.spec.model.LatencyBaseline;
import org.javai.punit.statistics.LatencyThresholdDeriver;

/**
 * Orchestrates latency threshold resolution from annotation and/or baseline data.
 *
 * <h2>Resolution modes</h2>
 * <ul>
 *   <li><strong>Explicit only:</strong> {@code @Latency(p95Ms=500)} without baseline.
 *       Explicit thresholds used as-is.</li>
 *   <li><strong>Baseline only:</strong> {@code latencyBaseline=true} without explicit thresholds.
 *       All thresholds derived from baseline via CI upper bound. Error if no baseline latency data.</li>
 *   <li><strong>Mixed:</strong> {@code @Latency(p99Ms=2000)} with baseline present.
 *       Baseline-derived thresholds fill gaps; explicit values act as ceilings
 *       (explicit wins only if stricter).</li>
 * </ul>
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

    /**
     * Resolves latency thresholds.
     *
     * @param annotationConfig the raw config from the @Latency annotation
     * @param baseline the latency baseline from the spec (null if not available)
     * @param confidence the confidence level for CI computation
     * @return the resolved thresholds
     * @throws org.junit.jupiter.api.extension.ExtensionConfigurationException if
     *         latencyBaseline=true but no baseline latency data is available
     */
    ResolvedThresholds resolve(LatencyAssertionConfig annotationConfig,
                               LatencyBaseline baseline,
                               double confidence) {

        if (!annotationConfig.isLatencyRequested()) {
            return new ResolvedThresholds(
                    ResolvedPercentile.NOT_ASSERTED,
                    ResolvedPercentile.NOT_ASSERTED,
                    ResolvedPercentile.NOT_ASSERTED,
                    ResolvedPercentile.NOT_ASSERTED
            );
        }

        // latencyBaseline=true but no baseline → configuration error
        if (annotationConfig.baselineRequested() && baseline == null) {
            throw new org.junit.jupiter.api.extension.ExtensionConfigurationException(
                    "latencyBaseline = true but no latency data found in baseline spec. " +
                    "Run a MEASURE experiment to collect latency data.");
        }

        boolean hasBaseline = baseline != null;

        if (!hasBaseline) {
            // Explicit only — no baseline available
            return resolveExplicitOnly(annotationConfig);
        }

        if (annotationConfig.baselineRequested() && !annotationConfig.hasExplicitThresholds()) {
            // Pure baseline derivation
            return resolveFromBaseline(baseline, confidence);
        }

        // Mixed mode: baseline fills gaps, explicit acts as ceiling
        return resolveMixed(annotationConfig, baseline, confidence);
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
        return new ResolvedThresholds(
                deriveFromBaseline("p50", baseline.p50Ms(), baseline.standardDeviationMs(),
                        baseline.sampleCount(), confidence),
                deriveFromBaseline("p90", baseline.p90Ms(), baseline.standardDeviationMs(),
                        baseline.sampleCount(), confidence),
                deriveFromBaseline("p95", baseline.p95Ms(), baseline.standardDeviationMs(),
                        baseline.sampleCount(), confidence),
                deriveFromBaseline("p99", baseline.p99Ms(), baseline.standardDeviationMs(),
                        baseline.sampleCount(), confidence)
        );
    }

    private ResolvedThresholds resolveMixed(LatencyAssertionConfig config,
                                            LatencyBaseline baseline,
                                            double confidence) {
        return new ResolvedThresholds(
                resolveSingleMixed(config.p50Ms(), baseline.p50Ms(),
                        baseline.standardDeviationMs(), baseline.sampleCount(), confidence),
                resolveSingleMixed(config.p90Ms(), baseline.p90Ms(),
                        baseline.standardDeviationMs(), baseline.sampleCount(), confidence),
                resolveSingleMixed(config.p95Ms(), baseline.p95Ms(),
                        baseline.standardDeviationMs(), baseline.sampleCount(), confidence),
                resolveSingleMixed(config.p99Ms(), baseline.p99Ms(),
                        baseline.standardDeviationMs(), baseline.sampleCount(), confidence)
        );
    }

    /**
     * Resolves a single percentile in mixed mode.
     * Baseline fills gaps; explicit acts as ceiling (explicit wins only if stricter).
     */
    private ResolvedPercentile resolveSingleMixed(long explicitMs, long baselinePercentileMs,
                                                  long baselineStdDevMs, int baselineSampleCount,
                                                  double confidence) {
        long derived = LatencyThresholdDeriver.deriveUpperBound(
                baselinePercentileMs, baselineStdDevMs, baselineSampleCount, confidence);

        if (explicitMs < 0) {
            // No explicit threshold — use baseline-derived
            return new ResolvedPercentile(derived, "from baseline");
        }

        // Both explicit and baseline available — use the stricter (lower) value
        if (explicitMs <= derived) {
            return new ResolvedPercentile(explicitMs, "explicit (stricter than baseline)");
        } else {
            return new ResolvedPercentile(derived, "from baseline (stricter than explicit)");
        }
    }

    private ResolvedPercentile explicitPercentile(long thresholdMs) {
        if (thresholdMs < 0) {
            return ResolvedPercentile.NOT_ASSERTED;
        }
        return new ResolvedPercentile(thresholdMs, "explicit");
    }

    private ResolvedPercentile deriveFromBaseline(String label, long baselinePercentileMs,
                                                  long baselineStdDevMs, int baselineSampleCount,
                                                  double confidence) {
        long derived = LatencyThresholdDeriver.deriveUpperBound(
                baselinePercentileMs, baselineStdDevMs, baselineSampleCount, confidence);
        return new ResolvedPercentile(derived, "from baseline");
    }
}

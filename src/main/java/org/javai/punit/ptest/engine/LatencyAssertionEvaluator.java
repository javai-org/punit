package org.javai.punit.ptest.engine;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.statistics.LatencyDistribution;

/**
 * Evaluates latency assertions by comparing observed percentiles against thresholds.
 *
 * <p>This evaluator takes a {@link LatencyDistribution} (computed from successful
 * sample durations) and a {@link LatencyAssertionConfig} (explicit thresholds from
 * the {@code @Latency} annotation), and produces a {@link LatencyAssertionResult}.
 *
 * <h2>Evaluation rules</h2>
 * <ul>
 *   <li>Zero successful samples → skip (pass with caveat)</li>
 *   <li>For each asserted percentile: observed ≤ threshold → pass</li>
 *   <li>Overall: pass if and only if all asserted percentiles pass</li>
 *   <li>Undersized samples: still evaluate, but mark results as indicative</li>
 * </ul>
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
class LatencyAssertionEvaluator {

    /**
     * Minimum recommended samples for reliable percentile computation.
     * Below this, results are marked as indicative.
     */
    private static final int MIN_RECOMMENDED_SAMPLES_P50 = 5;
    private static final int MIN_RECOMMENDED_SAMPLES_P90 = 10;
    private static final int MIN_RECOMMENDED_SAMPLES_P95 = 20;
    private static final int MIN_RECOMMENDED_SAMPLES_P99 = 100;

    /**
     * Evaluates latency assertions.
     *
     * @param config the latency assertion config (thresholds)
     * @param distribution the observed latency distribution (null if no successful samples)
     * @param successfulSampleCount number of successful samples
     * @return the evaluation result
     */
    LatencyAssertionResult evaluate(LatencyAssertionConfig config,
                                    LatencyDistribution distribution,
                                    int successfulSampleCount) {

        if (!config.isLatencyRequested()) {
            return LatencyAssertionResult.notRequested();
        }

        if (!config.hasExplicitThresholds()) {
            // Only baseline requested, no explicit thresholds — Phase 3 handles this
            return LatencyAssertionResult.notRequested();
        }

        if (successfulSampleCount == 0 || distribution == null) {
            return LatencyAssertionResult.skipped(
                    "No successful samples — latency evaluation skipped.");
        }

        List<LatencyAssertionResult.PercentileResult> results = new ArrayList<>();
        List<String> caveats = new ArrayList<>();
        boolean allPassed = true;

        if (config.hasP50()) {
            var result = evaluatePercentile("p50", distribution.p50Ms(), config.p50Ms(),
                    successfulSampleCount, MIN_RECOMMENDED_SAMPLES_P50);
            results.add(result);
            if (!result.passed()) allPassed = false;
        }

        if (config.hasP90()) {
            var result = evaluatePercentile("p90", distribution.p90Ms(), config.p90Ms(),
                    successfulSampleCount, MIN_RECOMMENDED_SAMPLES_P90);
            results.add(result);
            if (!result.passed()) allPassed = false;
        }

        if (config.hasP95()) {
            var result = evaluatePercentile("p95", distribution.p95Ms(), config.p95Ms(),
                    successfulSampleCount, MIN_RECOMMENDED_SAMPLES_P95);
            results.add(result);
            if (!result.passed()) allPassed = false;
        }

        if (config.hasP99()) {
            var result = evaluatePercentile("p99", distribution.p99Ms(), config.p99Ms(),
                    successfulSampleCount, MIN_RECOMMENDED_SAMPLES_P99);
            results.add(result);
            if (!result.passed()) allPassed = false;
        }

        // Add undersized caveat if any result is indicative
        boolean anyIndicative = results.stream().anyMatch(LatencyAssertionResult.PercentileResult::indicative);
        if (anyIndicative) {
            caveats.add("Sample size is small for some percentiles — results are indicative, not evidential.");
        }

        return new LatencyAssertionResult(allPassed, results, successfulSampleCount, false, caveats);
    }

    private LatencyAssertionResult.PercentileResult evaluatePercentile(
            String label, long observedMs, long thresholdMs,
            int sampleCount, int minRecommended) {

        boolean passed = observedMs <= thresholdMs;
        boolean indicative = sampleCount < minRecommended;

        return new LatencyAssertionResult.PercentileResult(
                label, observedMs, thresholdMs, passed, indicative);
    }
}

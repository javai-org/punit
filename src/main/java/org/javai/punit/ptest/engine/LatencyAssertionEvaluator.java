package org.javai.punit.ptest.engine;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.statistics.LatencyDistribution;

/**
 * Evaluates latency assertions by comparing observed percentiles against thresholds.
 *
 * <p>This evaluator takes a {@link LatencyDistribution} (computed from successful
 * sample durations) and resolved thresholds (from annotation and/or baseline),
 * and produces a {@link LatencyAssertionResult}.
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
     * Evaluates latency assertions using resolved thresholds.
     *
     * @param resolvedThresholds the resolved thresholds (from annotation, baseline, or mixed)
     * @param distribution the observed latency distribution (null if no successful samples)
     * @param successfulSampleCount number of successful samples
     * @return the evaluation result
     */
    LatencyAssertionResult evaluate(LatencyThresholdResolver.ResolvedThresholds resolvedThresholds,
                                    LatencyDistribution distribution,
                                    int successfulSampleCount) {

        LatencyAssertionConfig resolvedConfig = resolvedThresholds.toConfig();

        if (!resolvedConfig.hasExplicitThresholds()) {
            return LatencyAssertionResult.notRequested();
        }

        if (successfulSampleCount == 0 || distribution == null) {
            return LatencyAssertionResult.skipped(
                    "No successful samples — latency evaluation skipped.");
        }

        List<LatencyAssertionResult.PercentileResult> results = new ArrayList<>();
        List<String> caveats = new ArrayList<>();
        boolean allPassed = true;

        if (resolvedConfig.hasP50()) {
            var result = evaluatePercentile("p50", distribution.p50Ms(), resolvedConfig.p50Ms(),
                    successfulSampleCount, MIN_RECOMMENDED_SAMPLES_P50,
                    resolvedThresholds.p50().source());
            results.add(result);
            if (!result.passed()) allPassed = false;
        }

        if (resolvedConfig.hasP90()) {
            var result = evaluatePercentile("p90", distribution.p90Ms(), resolvedConfig.p90Ms(),
                    successfulSampleCount, MIN_RECOMMENDED_SAMPLES_P90,
                    resolvedThresholds.p90().source());
            results.add(result);
            if (!result.passed()) allPassed = false;
        }

        if (resolvedConfig.hasP95()) {
            var result = evaluatePercentile("p95", distribution.p95Ms(), resolvedConfig.p95Ms(),
                    successfulSampleCount, MIN_RECOMMENDED_SAMPLES_P95,
                    resolvedThresholds.p95().source());
            results.add(result);
            if (!result.passed()) allPassed = false;
        }

        if (resolvedConfig.hasP99()) {
            var result = evaluatePercentile("p99", distribution.p99Ms(), resolvedConfig.p99Ms(),
                    successfulSampleCount, MIN_RECOMMENDED_SAMPLES_P99,
                    resolvedThresholds.p99().source());
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

    /**
     * Evaluates latency assertions using raw config (backward-compatible for explicit-only mode).
     *
     * @param config the latency assertion config (thresholds)
     * @param distribution the observed latency distribution (null if no successful samples)
     * @param successfulSampleCount number of successful samples
     * @return the evaluation result
     */
    LatencyAssertionResult evaluate(LatencyAssertionConfig config,
                                    LatencyDistribution distribution,
                                    int successfulSampleCount) {

        if (!config.isLatencyRequested() || !config.hasExplicitThresholds()) {
            return LatencyAssertionResult.notRequested();
        }

        // Convert to resolved thresholds with "explicit" source
        LatencyThresholdResolver resolver = new LatencyThresholdResolver();
        LatencyThresholdResolver.ResolvedThresholds resolved = resolver.resolve(config, null, 0.95);
        return evaluate(resolved, distribution, successfulSampleCount);
    }

    private LatencyAssertionResult.PercentileResult evaluatePercentile(
            String label, long observedMs, long thresholdMs,
            int sampleCount, int minRecommended, String source) {

        boolean passed = observedMs <= thresholdMs;
        boolean indicative = sampleCount < minRecommended;

        return new LatencyAssertionResult.PercentileResult(
                label, observedMs, thresholdMs, passed, indicative, source);
    }
}

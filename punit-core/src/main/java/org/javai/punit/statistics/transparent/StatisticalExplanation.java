package org.javai.punit.statistics.transparent;

import java.time.Instant;
import java.util.List;

/**
 * Immutable data class holding all components of a statistical explanation.
 *
 * <p>This record captures the complete statistical reasoning for a probabilistic
 * test verdict, enabling transparent reporting for auditors, stakeholders, and
 * educational purposes.
 *
 * @param testName The name of the test being explained
 * @param hypothesis The hypothesis statement (H₀ and H₁)
 * @param observed The observed data from test execution
 * @param baseline Reference to the baseline/spec data
 * @param inference Statistical inference calculations
 * @param verdict The final verdict with interpretation
 * @param provenance Optional provenance information (may be null)
 */
public record StatisticalExplanation(
        String testName,
        HypothesisStatement hypothesis,
        ObservedData observed,
        BaselineReference baseline,
        StatisticalInference inference,
        VerdictInterpretation verdict,
        Provenance provenance,
        LatencyAnalysis latencyAnalysis
) {

    /**
     * Backward-compatible constructor without latency analysis.
     */
    public StatisticalExplanation(
            String testName,
            HypothesisStatement hypothesis,
            ObservedData observed,
            BaselineReference baseline,
            StatisticalInference inference,
            VerdictInterpretation verdict,
            Provenance provenance) {
        this(testName, hypothesis, observed, baseline, inference, verdict, provenance, null);
    }

    /**
     * Returns true if latency analysis data is available.
     */
    public boolean hasLatencyAnalysis() {
        return latencyAnalysis != null;
    }

    /**
     * Provenance information documenting the origin of the threshold.
     * 
     * <p>This record uses String values to avoid dependencies on the API package,
     * keeping the statistics module isolated.
     *
     * @param thresholdOriginName The name of the threshold origin (e.g., "SLA", "SLO", "UNSPECIFIED")
     * @param contractRef Human-readable reference to the source document
     */
    public record Provenance(
            String thresholdOriginName,
            String contractRef
    ) {
        /**
         * Returns true if any provenance information is specified.
         */
        public boolean hasProvenance() {
            return hasThresholdOrigin() || hasContractRef();
        }

        /**
         * Returns true if thresholdOrigin is specified (not UNSPECIFIED or empty).
         */
        public boolean hasThresholdOrigin() {
            return thresholdOriginName != null 
                    && !thresholdOriginName.isEmpty() 
                    && !"UNSPECIFIED".equals(thresholdOriginName);
        }

        /**
         * Returns true if contractRef is specified (not null or empty).
         */
        public boolean hasContractRef() {
            return contractRef != null && !contractRef.isEmpty();
        }

        /**
         * Creates an empty provenance (no information specified).
         */
        public static Provenance empty() {
            return new Provenance("UNSPECIFIED", "");
        }
    }

    /**
     * The hypothesis being tested.
     *
     * @param nullHypothesis Description of H₀ (what we're trying to disprove)
     * @param alternativeHypothesis Description of H₁ (what we want to show)
     * @param testType The type of statistical test (e.g., "One-sided binomial proportion test")
     */
    public record HypothesisStatement(
            String nullHypothesis,
            String alternativeHypothesis,
            String testType
    ) {
    }

    /**
     * The observed data from test execution.
     *
     * @param sampleSize Number of samples executed (n)
     * @param successes Number of successful samples (k)
     * @param observedRate Observed success rate (p̂ = k/n)
     */
    public record ObservedData(
            int sampleSize,
            int successes,
            double observedRate
    ) {
        /**
         * Creates observed data from sample counts.
         */
        public static ObservedData of(int sampleSize, int successes) {
            double rate = sampleSize > 0 ? (double) successes / sampleSize : 0.0;
            return new ObservedData(sampleSize, successes, rate);
        }
    }

    /**
     * Reference to the baseline specification.
     *
     * @param sourceFile The spec file path/name
     * @param generatedAt When the spec was generated
     * @param baselineSamples Number of samples in the baseline experiment
     * @param baselineSuccesses Number of successes in the baseline experiment
     * @param baselineRate Observed rate from baseline (p̂_baseline)
     * @param thresholdDerivation Description of how the threshold was derived
     * @param threshold The actual threshold value used
     */
    public record BaselineReference(
            String sourceFile,
            Instant generatedAt,
            int baselineSamples,
            int baselineSuccesses,
            double baselineRate,
            String thresholdDerivation,
            double threshold
    ) {
        /**
         * Returns true if baseline reference data is available.
         */
        public boolean hasBaselineData() {
            return baselineSamples > 0;
        }
    }

    /**
     * Statistical inference calculations.
     *
     * @param standardError Standard error of the observed proportion
     * @param ciLower Lower bound of confidence interval
     * @param ciUpper Upper bound of confidence interval
     * @param confidenceLevel Confidence level used (e.g., 0.95 for 95%)
     * @param testStatistic Z-score or other test statistic (nullable)
     * @param pValue P-value of the test (nullable)
     */
    public record StatisticalInference(
            double standardError,
            double ciLower,
            double ciUpper,
            double confidenceLevel,
            Double testStatistic,
            Double pValue
    ) {
        /**
         * Returns the confidence level as a percentage.
         */
        public double confidencePercent() {
            return confidenceLevel * 100;
        }

        /**
         * Returns true if a test statistic is available.
         */
        public boolean hasTestStatistic() {
            return testStatistic != null;
        }

        /**
         * Returns true if a p-value is available.
         */
        public boolean hasPValue() {
            return pValue != null;
        }
    }

    /**
     * The final verdict with interpretation.
     *
     * @param passed Whether the test passed
     * @param technicalResult Brief technical result (e.g., "PASS" or "FAIL")
     * @param plainEnglish Plain English interpretation of the result
     * @param caveats List of caveats and limitations
     */
    public record VerdictInterpretation(
            boolean passed,
            String technicalResult,
            String plainEnglish,
            List<String> caveats
    ) {
        public VerdictInterpretation {
            caveats = caveats != null ? List.copyOf(caveats) : List.of();
        }
    }

    /**
     * Latency analysis for transparent stats output.
     *
     * <p>Uses only primitives and strings to avoid dependencies on the API package,
     * keeping the statistics module isolated (ArchUnit constraint).
     *
     * @param successfulSamples number of successful samples used for latency computation
     * @param totalSamples total samples executed (including failures)
     * @param skipped true if latency evaluation was skipped (e.g., zero successes)
     * @param skipReason reason for skipping (null if not skipped)
     * @param p50Ms observed p50 latency (-1 if not available)
     * @param p90Ms observed p90 latency (-1 if not available)
     * @param p95Ms observed p95 latency (-1 if not available)
     * @param p99Ms observed p99 latency (-1 if not available)
     * @param maxMs observed max latency (-1 if not available)
     * @param percentileAssertions per-percentile assertion results (only asserted percentiles)
     * @param caveats advisory messages
     * @param thresholdSource overall source description (e.g., "explicit", "from baseline")
     * @param baselineFilename the baseline filename if thresholds derived from baseline (nullable)
     */
    public record LatencyAnalysis(
            int successfulSamples,
            int totalSamples,
            boolean skipped,
            String skipReason,
            long p50Ms,
            long p90Ms,
            long p95Ms,
            long p99Ms,
            long maxMs,
            List<PercentileAssertion> percentileAssertions,
            List<String> caveats,
            String thresholdSource,
            String baselineFilename
    ) {
        public LatencyAnalysis {
            percentileAssertions = percentileAssertions != null ? List.copyOf(percentileAssertions) : List.of();
            caveats = caveats != null ? List.copyOf(caveats) : List.of();
        }

        /**
         * Returns true if any percentile assertion failed.
         */
        public boolean hasBreaches() {
            return percentileAssertions.stream().anyMatch(a -> !a.passed);
        }

        /**
         * Returns true if any result is indicative (undersized sample).
         */
        public boolean hasIndicativeResults() {
            return percentileAssertions.stream().anyMatch(PercentileAssertion::indicative);
        }

        /**
         * Returns true if thresholds were derived from a baseline.
         */
        public boolean isBaselineDerived() {
            return thresholdSource != null && thresholdSource.contains("baseline");
        }
    }

    /**
     * Result for a single percentile assertion in the latency analysis.
     *
     * @param label the percentile label (e.g., "p95")
     * @param observedMs the observed value in milliseconds
     * @param thresholdMs the threshold value in milliseconds
     * @param passed true if observed <= threshold
     * @param indicative true if sample size is too small for reliable percentile
     * @param source the source of the threshold (e.g., "explicit", "from baseline")
     */
    public record PercentileAssertion(
            String label,
            long observedMs,
            long thresholdMs,
            boolean passed,
            boolean indicative,
            String source
    ) {}
}


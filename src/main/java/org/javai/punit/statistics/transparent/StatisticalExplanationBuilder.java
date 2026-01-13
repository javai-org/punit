package org.javai.punit.statistics.transparent;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.statistics.BinomialProportionEstimator;
import org.javai.punit.statistics.ProportionEstimate;

/**
 * Builds statistical explanations from test execution context.
 *
 * <p>This builder constructs comprehensive {@link StatisticalExplanation} objects
 * that document the complete statistical reasoning behind a test verdict.
 */
public class StatisticalExplanationBuilder {

    private final BinomialProportionEstimator estimator;

    /**
     * Creates a builder with the default binomial proportion estimator.
     */
    public StatisticalExplanationBuilder() {
        this(new BinomialProportionEstimator());
    }

    /**
     * Creates a builder with a custom estimator (for testing).
     *
     * @param estimator the proportion estimator to use
     */
    public StatisticalExplanationBuilder(BinomialProportionEstimator estimator) {
        this.estimator = estimator;
    }

    /**
     * Builds a complete statistical explanation.
     *
     * @param testName The name of the test
     * @param samples Number of samples executed
     * @param successes Number of successful samples
     * @param baseline The baseline data (may be null or empty for legacy tests)
     * @param threshold The pass/fail threshold
     * @param passed Whether the test passed
     * @param confidenceLevel The confidence level used (e.g., 0.95)
     * @return A complete statistical explanation
     */
    public StatisticalExplanation build(
            String testName,
            int samples,
            int successes,
            BaselineData baseline,
            double threshold,
            boolean passed,
            double confidenceLevel) {
        return build(testName, samples, successes, baseline, threshold, passed, confidenceLevel,
                "UNSPECIFIED", "");
    }

    /**
     * Builds a complete statistical explanation with provenance information.
     *
     * @param testName The name of the test
     * @param samples Number of samples executed
     * @param successes Number of successful samples
     * @param baseline The baseline data (may be null or empty for legacy tests)
     * @param threshold The pass/fail threshold
     * @param passed Whether the test passed
     * @param confidenceLevel The confidence level used (e.g., 0.95)
     * @param thresholdOriginName The name of the threshold origin (e.g., "SLA", "SLO")
     * @param contractRef Human-readable reference to the source document
     * @return A complete statistical explanation
     */
    public StatisticalExplanation build(
            String testName,
            int samples,
            int successes,
            BaselineData baseline,
            double threshold,
            boolean passed,
            double confidenceLevel,
            String thresholdOriginName,
            String contractRef) {

        BaselineData effectiveBaseline = baseline != null ? baseline : BaselineData.empty();
        StatisticalExplanation.Provenance provenance = new StatisticalExplanation.Provenance(
                thresholdOriginName != null ? thresholdOriginName : "UNSPECIFIED",
                contractRef != null ? contractRef : ""
        );
        
        return new StatisticalExplanation(
                testName,
                buildHypothesis(threshold, thresholdOriginName),
                buildObservedData(samples, successes),
                buildBaselineReference(effectiveBaseline, threshold, confidenceLevel),
                buildInference(samples, successes, confidenceLevel),
                buildVerdict(passed, samples, successes, threshold, effectiveBaseline, confidenceLevel, thresholdOriginName),
                provenance
        );
    }

    /**
     * Builds a statistical explanation for inline threshold mode (no baseline spec).
     *
     * <p>Use this when the test specifies an explicit {@code minPassRate} rather than
     * deriving the threshold from baseline experiment data.
     */
    public StatisticalExplanation buildWithInlineThreshold(
            String testName,
            int samples,
            int successes,
            double threshold,
            boolean passed) {
        return buildWithInlineThreshold(testName, samples, successes, threshold, passed,
                "UNSPECIFIED", "");
    }

    /**
     * Builds a statistical explanation for inline threshold mode with provenance.
     *
     * <p>Use this when the test specifies an explicit {@code minPassRate} rather than
     * deriving the threshold from baseline experiment data.
     *
     * @param testName The name of the test
     * @param samples Number of samples executed
     * @param successes Number of successful samples
     * @param threshold The pass/fail threshold
     * @param passed Whether the test passed
     * @param thresholdOriginName The name of the threshold origin (e.g., "SLA", "SLO")
     * @param contractRef Human-readable reference to the source document
     * @return A complete statistical explanation
     */
    public StatisticalExplanation buildWithInlineThreshold(
            String testName,
            int samples,
            int successes,
            double threshold,
            boolean passed,
            String thresholdOriginName,
            String contractRef) {

        double confidenceLevel = 0.95; // Default confidence for legacy mode
        StatisticalExplanation.Provenance provenance = new StatisticalExplanation.Provenance(
                thresholdOriginName != null ? thresholdOriginName : "UNSPECIFIED",
                contractRef != null ? contractRef : ""
        );
        
        return new StatisticalExplanation(
                testName,
                buildHypothesis(threshold, thresholdOriginName),
                buildObservedData(samples, successes),
                buildInlineThresholdBaselineReference(threshold),
                buildInference(samples, successes, confidenceLevel),
                buildInlineThresholdVerdict(passed, samples, successes, threshold, thresholdOriginName),
                provenance
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION BUILDERS
    // ═══════════════════════════════════════════════════════════════════════════

    private StatisticalExplanation.HypothesisStatement buildHypothesis(double threshold, String thresholdOriginName) {
        // Adapt hypothesis framing based on threshold origin (per STATISTICAL-COMPANION Section 7.4)
        HypothesisFraming framing = getHypothesisFraming(thresholdOriginName);
        
        String nullHypothesis = String.format(
                "True success rate %s %s %.2f (%s)",
                StatisticalVocabulary.PI, StatisticalVocabulary.GEQ, threshold, framing.h0Text);
        
        String alternativeHypothesis = String.format(
                "True success rate %s < %.2f (%s)",
                StatisticalVocabulary.PI, threshold, framing.h1Text);
        
        return new StatisticalExplanation.HypothesisStatement(
                nullHypothesis,
                alternativeHypothesis,
                "One-sided binomial proportion test"
        );
    }

    /**
     * Returns hypothesis framing text based on the threshold origin.
     * 
     * <p>This implements the framing described in STATISTICAL-COMPANION Section 7.4:
     * <ul>
     *   <li>SLA: "System meets SLA requirement" / "System violates SLA"</li>
     *   <li>SLO: "System meets SLO target" / "System falls short of SLO"</li>
     *   <li>POLICY: "System meets policy requirement" / "System violates policy"</li>
     *   <li>EMPIRICAL: "No degradation from baseline" / "Degradation from baseline"</li>
     *   <li>UNSPECIFIED: "Success rate meets threshold" / "Success rate below threshold"</li>
     * </ul>
     */
    private HypothesisFraming getHypothesisFraming(String thresholdOriginName) {
        if (thresholdOriginName == null) {
            thresholdOriginName = "UNSPECIFIED";
        }
        
        return switch (thresholdOriginName.toUpperCase()) {
            case "SLA" -> new HypothesisFraming(
                    "system meets SLA requirement",
                    "system violates SLA");
            case "SLO" -> new HypothesisFraming(
                    "system meets SLO target",
                    "system falls short of SLO");
            case "POLICY" -> new HypothesisFraming(
                    "system meets policy requirement",
                    "system violates policy");
            case "EMPIRICAL" -> new HypothesisFraming(
                    "no degradation from baseline",
                    "degradation from baseline");
            default -> new HypothesisFraming(
                    "success rate meets threshold",
                    "success rate below threshold");
        };
    }

    private record HypothesisFraming(String h0Text, String h1Text) {}

    private StatisticalExplanation.ObservedData buildObservedData(int samples, int successes) {
        return StatisticalExplanation.ObservedData.of(samples, successes);
    }

    private StatisticalExplanation.BaselineReference buildBaselineReference(
            BaselineData baseline,
            double threshold,
            double confidenceLevel) {

        if (!baseline.hasData()) {
            return buildInlineThresholdBaselineReference(threshold);
        }

        String derivation = buildThresholdDerivation(baseline.baselineSamples(), baseline.baselineSuccesses(), 
                baseline.baselineRate(), threshold, confidenceLevel);

        return new StatisticalExplanation.BaselineReference(
                baseline.sourceFile(),
                baseline.generatedAt(),
                baseline.baselineSamples(),
                baseline.baselineSuccesses(),
                baseline.baselineRate(),
                derivation,
                threshold
        );
    }

    private StatisticalExplanation.BaselineReference buildInlineThresholdBaselineReference(double threshold) {
        return new StatisticalExplanation.BaselineReference(
                "(inline threshold)",
                null,
                0,
                0,
                0.0,
                "Threshold specified directly in @ProbabilisticTest annotation",
                threshold
        );
    }

    private String buildThresholdDerivation(
            int baselineSamples,
            int baselineSuccesses,
            double baselineRate,
            double threshold,
            double confidenceLevel) {

        double lowerBound = estimator.lowerBound(baselineSuccesses, baselineSamples, confidenceLevel);
        int confidencePercent = (int) (confidenceLevel * 100);
        
        return String.format(
                "Lower bound of %d%% CI = %.1f%%, min pass rate = %.0f%%",
                confidencePercent, lowerBound * 100, threshold * 100);
    }

    private StatisticalExplanation.StatisticalInference buildInference(
            int samples,
            int successes,
            double confidenceLevel) {

        double observedRate = samples > 0 ? (double) successes / samples : 0.0;
        
        // Standard error: SE = √(p̂(1-p̂)/n)
        double standardError = samples > 0 
                ? Math.sqrt(observedRate * (1 - observedRate) / samples)
                : 0.0;

        // Wilson score confidence interval
        ProportionEstimate estimate = samples > 0
                ? estimator.estimate(successes, samples, confidenceLevel)
                : new ProportionEstimate(0, 0, 0, 0, confidenceLevel);

        // Test statistic and p-value (for completeness)
        Double testStatistic = null;
        Double pValue = null;

        return new StatisticalExplanation.StatisticalInference(
                standardError,
                estimate.lowerBound(),
                estimate.upperBound(),
                confidenceLevel,
                testStatistic,
                pValue
        );
    }

    private StatisticalExplanation.VerdictInterpretation buildVerdict(
            boolean passed,
            int samples,
            int successes,
            double threshold,
            BaselineData baseline,
            double confidenceLevel,
            String thresholdOriginName) {

        double observedRate = samples > 0 ? (double) successes / samples : 0.0;
        String technicalResult = passed ? "PASS" : "FAIL";
        VerdictFraming framing = getVerdictFraming(thresholdOriginName);

        String plainEnglish;
        if (passed) {
            if (baseline != null && baseline.hasData()) {
                plainEnglish = String.format(
                        "The observed success rate of %.1f%% is consistent with the baseline expectation of %.1f%%. " +
                        "%s",
                        observedRate * 100,
                        baseline.baselineRate() * 100,
                        framing.passText);
            } else {
                plainEnglish = String.format(
                        "The observed success rate of %.1f%% meets the required threshold of %.1f%%. %s",
                        observedRate * 100,
                        threshold * 100,
                        framing.passText);
            }
        } else {
            plainEnglish = String.format(
                    "The observed success rate of %.1f%% falls below the required threshold of %.1f%%. %s",
                    observedRate * 100,
                    threshold * 100,
                    framing.failText);
        }

        List<String> caveats = buildCaveats(samples, observedRate, threshold);

        return new StatisticalExplanation.VerdictInterpretation(
                passed,
                technicalResult,
                plainEnglish,
                caveats
        );
    }

    private StatisticalExplanation.VerdictInterpretation buildInlineThresholdVerdict(
            boolean passed,
            int samples,
            int successes,
            double threshold,
            String thresholdOriginName) {

        double observedRate = samples > 0 ? (double) successes / samples : 0.0;
        String technicalResult = passed ? "PASS" : "FAIL";
        VerdictFraming framing = getVerdictFraming(thresholdOriginName);

        String plainEnglish;
        if (passed) {
            plainEnglish = String.format(
                    "The observed success rate of %.1f%% meets the required threshold of %.1f%%. %s",
                    observedRate * 100,
                    threshold * 100,
                    framing.passText);
        } else {
            plainEnglish = String.format(
                    "The observed success rate of %.1f%% falls below the required threshold of %.1f%%. %s",
                    observedRate * 100,
                    threshold * 100,
                    framing.failText);
        }

        List<String> caveats = buildCaveats(samples, observedRate, threshold);
        caveats = new ArrayList<>(caveats);
        
        // Add caveat about inline threshold if not using SLA/SLO/POLICY source
        if (thresholdOriginName == null || thresholdOriginName.equalsIgnoreCase("UNSPECIFIED") 
                || thresholdOriginName.equalsIgnoreCase("EMPIRICAL")) {
            caveats.add("Using inline threshold (no baseline spec). For statistically-derived " +
                    "thresholds with confidence intervals, run a MEASURE experiment first.");
        }

        return new StatisticalExplanation.VerdictInterpretation(
                passed,
                technicalResult,
                plainEnglish,
                caveats
        );
    }

    /**
     * Returns verdict framing text based on the threshold origin.
     */
    private VerdictFraming getVerdictFraming(String thresholdOriginName) {
        if (thresholdOriginName == null) {
            thresholdOriginName = "UNSPECIFIED";
        }
        
        return switch (thresholdOriginName.toUpperCase()) {
            case "SLA" -> new VerdictFraming(
                    "The system meets its SLA requirement.",
                    "This indicates the system is not meeting its SLA obligation.");
            case "SLO" -> new VerdictFraming(
                    "The system meets its SLO target.",
                    "This indicates the system is falling short of its SLO target.");
            case "POLICY" -> new VerdictFraming(
                    "The system meets the policy requirement.",
                    "This indicates a policy violation.");
            case "EMPIRICAL" -> new VerdictFraming(
                    "No degradation from baseline detected.",
                    "This suggests potential degradation from the established baseline.");
            default -> new VerdictFraming(
                    "The test passes.",
                    "This suggests the system is not meeting its expected performance level.");
        };
    }

    private record VerdictFraming(String passText, String failText) {}

    private List<String> buildCaveats(int samples, double observedRate, double threshold) {
        List<String> caveats = new ArrayList<>();

        // Sample size caveat
        if (samples < 30) {
            caveats.add(String.format(
                    "Small sample size (n=%d). Statistical conclusions should be interpreted with caution. " +
                    "Consider increasing sample size for more reliable results.",
                    samples));
        } else if (samples < 100) {
            caveats.add(String.format(
                    "With n=%d samples, subtle performance changes may not be detectable. " +
                    "For higher sensitivity, consider increasing sample size.",
                    samples));
        } else {
            // Power analysis caveat
            double margin = observedRate - threshold;
            if (margin > 0 && margin < 0.05) {
                caveats.add(String.format(
                        "The observed rate (%.1f%%) is close to the min pass rate (%.1f%%). " +
                        "Small fluctuations in future runs may cause different verdicts.",
                        observedRate * 100, threshold * 100));
            }
        }

        // Edge case caveats
        if (observedRate == 1.0) {
            caveats.add("Perfect success rate observed. This may indicate insufficient test coverage " +
                    "or a test that doesn't adequately challenge the system.");
        } else if (observedRate == 0.0) {
            caveats.add("Zero success rate observed. This indicates a fundamental failure " +
                    "that may warrant investigation before further testing.");
        }

        return caveats;
    }
}

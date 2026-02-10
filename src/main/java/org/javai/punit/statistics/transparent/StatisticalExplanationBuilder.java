package org.javai.punit.statistics.transparent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.javai.punit.reporting.RateFormat;
import org.javai.punit.statistics.BinomialProportionEstimator;
import org.javai.punit.statistics.ComplianceEvidenceEvaluator;
import org.javai.punit.statistics.ProportionEstimate;
import org.javai.punit.statistics.StatisticalDefaults;
import org.javai.punit.statistics.VerificationFeasibilityEvaluator;

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
     * Represents a covariate misalignment between baseline and test conditions.
     *
     * @param covariateKey the name of the misaligned covariate
     * @param baselineValue the covariate value in the baseline
     * @param testValue the covariate value at test time
     */
    public record CovariateMisalignment(
            String covariateKey,
            String baselineValue,
            String testValue
    ) {}

    /**
     * Builds a complete statistical explanation.
     *
     * @param testName The name of the test
     * @param samples Number of samples executed
     * @param successes Number of successful samples
     * @param baseline The baseline data (may be null or empty for inline threshold tests)
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
     * @param baseline The baseline data (may be null or empty for inline threshold tests)
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
        return build(testName, samples, successes, baseline, threshold, passed, confidenceLevel,
                thresholdOriginName, contractRef, Collections.emptyList());
    }

    /**
     * Builds a complete statistical explanation with provenance and covariate misalignment info.
     *
     * @param testName The name of the test
     * @param samples Number of samples executed
     * @param successes Number of successful samples
     * @param baseline The baseline data (may be null or empty for inline threshold tests)
     * @param threshold The pass/fail threshold
     * @param passed Whether the test passed
     * @param confidenceLevel The confidence level used (e.g., 0.95)
     * @param thresholdOriginName The name of the threshold origin (e.g., "SLA", "SLO")
     * @param contractRef Human-readable reference to the source document
     * @param misalignments List of covariate misalignments (may be empty)
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
            String contractRef,
            List<CovariateMisalignment> misalignments) {
        return build(testName, samples, successes, baseline, threshold, passed, confidenceLevel,
                thresholdOriginName, contractRef, misalignments, false);
    }

    /**
     * Builds a complete statistical explanation with provenance, covariate misalignment, and intent.
     *
     * @param testName The name of the test
     * @param samples Number of samples executed
     * @param successes Number of successful samples
     * @param baseline The baseline data (may be null or empty for inline threshold tests)
     * @param threshold The pass/fail threshold
     * @param passed Whether the test passed
     * @param confidenceLevel The confidence level used (e.g., 0.95)
     * @param thresholdOriginName The name of the threshold origin (e.g., "SLA", "SLO")
     * @param contractRef Human-readable reference to the source document
     * @param misalignments List of covariate misalignments (may be empty)
     * @param isSmoke true if the test intent is SMOKE (softened language, no compliance assertions)
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
            String contractRef,
            List<CovariateMisalignment> misalignments,
            boolean isSmoke) {

        BaselineData effectiveBaseline = baseline != null ? baseline : BaselineData.empty();
        StatisticalExplanation.Provenance provenance = new StatisticalExplanation.Provenance(
                thresholdOriginName != null ? thresholdOriginName : "UNSPECIFIED",
                contractRef != null ? contractRef : ""
        );
        List<CovariateMisalignment> effectiveMisalignments = misalignments != null ? misalignments : Collections.emptyList();

        String effectiveContractRef = contractRef != null ? contractRef : "";

        return new StatisticalExplanation(
                testName,
                buildHypothesis(threshold, thresholdOriginName, isSmoke),
                buildObservedData(samples, successes),
                buildBaselineReference(effectiveBaseline, threshold, confidenceLevel),
                buildInference(samples, successes, confidenceLevel),
                buildVerdict(passed, samples, successes, threshold, effectiveBaseline, confidenceLevel,
                        thresholdOriginName, effectiveContractRef, effectiveMisalignments, isSmoke),
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
        return buildWithInlineThreshold(testName, samples, successes, threshold, passed,
                thresholdOriginName, contractRef, false);
    }

    /**
     * Builds a statistical explanation for inline threshold mode with provenance and intent.
     *
     * @param testName The name of the test
     * @param samples Number of samples executed
     * @param successes Number of successful samples
     * @param threshold The pass/fail threshold
     * @param passed Whether the test passed
     * @param thresholdOriginName The name of the threshold origin (e.g., "SLA", "SLO")
     * @param contractRef Human-readable reference to the source document
     * @param isSmoke true if the test intent is SMOKE (softened language, no compliance assertions)
     * @return A complete statistical explanation
     */
    public StatisticalExplanation buildWithInlineThreshold(
            String testName,
            int samples,
            int successes,
            double threshold,
            boolean passed,
            String thresholdOriginName,
            String contractRef,
            boolean isSmoke) {

        double confidenceLevel = StatisticalDefaults.DEFAULT_CONFIDENCE;
        String effectiveContractRef = contractRef != null ? contractRef : "";
        StatisticalExplanation.Provenance provenance = new StatisticalExplanation.Provenance(
                thresholdOriginName != null ? thresholdOriginName : "UNSPECIFIED",
                effectiveContractRef
        );

        return new StatisticalExplanation(
                testName,
                buildHypothesis(threshold, thresholdOriginName, isSmoke),
                buildObservedData(samples, successes),
                buildInlineThresholdBaselineReference(threshold),
                buildInference(samples, successes, confidenceLevel),
                buildInlineThresholdVerdict(passed, samples, successes, threshold,
                        thresholdOriginName, effectiveContractRef, isSmoke),
                provenance
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION BUILDERS
    // ═══════════════════════════════════════════════════════════════════════════

    private StatisticalExplanation.HypothesisStatement buildHypothesis(double threshold, String thresholdOriginName) {
        return buildHypothesis(threshold, thresholdOriginName, false);
    }

    private StatisticalExplanation.HypothesisStatement buildHypothesis(
            double threshold, String thresholdOriginName, boolean isSmoke) {
        // Adapt hypothesis framing based on threshold origin and intent
        HypothesisFraming framing = getHypothesisFraming(thresholdOriginName, isSmoke);

        String nullHypothesis = String.format(
                "True success rate %s %s %s (%s)",
                StatisticalVocabulary.PI, StatisticalVocabulary.GEQ, RateFormat.format(threshold), framing.h0Text);

        String alternativeHypothesis = String.format(
                "True success rate %s < %s (%s)",
                StatisticalVocabulary.PI, RateFormat.format(threshold), framing.h1Text);

        return new StatisticalExplanation.HypothesisStatement(
                nullHypothesis,
                alternativeHypothesis,
                "One-sided binomial proportion test"
        );
    }

    /**
     * Returns hypothesis framing text based on the threshold origin and intent.
     *
     * <p>For VERIFICATION intent, this implements the framing described in
     * STATISTICAL-COMPANION Section 7.4. For SMOKE intent, the framing is softened
     * to avoid compliance language (Req 11).
     */
    private HypothesisFraming getHypothesisFraming(String thresholdOriginName, boolean isSmoke) {
        if (thresholdOriginName == null) {
            thresholdOriginName = "UNSPECIFIED";
        }

        // SMOKE intent uses softened language — no compliance assertions
        if (isSmoke) {
            return switch (thresholdOriginName.toUpperCase()) {
                case "SLA", "SLO", "POLICY" -> new HypothesisFraming(
                        "observed rate consistent with target",
                        "observed rate inconsistent with target");
                case "EMPIRICAL" -> new HypothesisFraming(
                        "no degradation from baseline",
                        "degradation from baseline");
                default -> new HypothesisFraming(
                        "observed rate meets threshold",
                        "observed rate below threshold");
            };
        }

        // VERIFICATION intent (or null) — full compliance framing
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

        // No baseline spec at all - use inline threshold mode
        if (!baseline.hasBaselineSpec()) {
            return buildInlineThresholdBaselineReference(threshold);
        }

        // Have baseline spec, may or may not have empirical data
        String derivation;
        if (baseline.hasEmpiricalData()) {
            derivation = buildThresholdDerivation(baseline.baselineSamples(), baseline.baselineSuccesses(), 
                    baseline.baselineRate(), threshold, confidenceLevel);
        } else {
            // Baseline spec exists but without empirical data - threshold is from requirements
            derivation = "Threshold from baseline specification (no empirical basis)";
        }

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
                "Lower bound of %d%% CI = %s, min pass rate = %s",
                confidencePercent, RateFormat.format(lowerBound), RateFormat.format(threshold));
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
            String thresholdOriginName,
            String contractRef,
            List<CovariateMisalignment> misalignments) {
        return buildVerdict(passed, samples, successes, threshold, baseline, confidenceLevel,
                thresholdOriginName, contractRef, misalignments, false);
    }

    private StatisticalExplanation.VerdictInterpretation buildVerdict(
            boolean passed,
            int samples,
            int successes,
            double threshold,
            BaselineData baseline,
            double confidenceLevel,
            String thresholdOriginName,
            String contractRef,
            List<CovariateMisalignment> misalignments,
            boolean isSmoke) {

        double observedRate = samples > 0 ? (double) successes / samples : 0.0;
        String technicalResult = passed ? "PASS" : "FAIL";
        VerdictFraming framing = getVerdictFraming(thresholdOriginName, isSmoke);

        String plainEnglish;
        if (passed) {
            if (baseline != null && baseline.hasEmpiricalData()) {
                plainEnglish = String.format(
                        "The observed success rate of %s is consistent with the baseline expectation of %s. " +
                        "%s",
                        RateFormat.format(observedRate),
                        RateFormat.format(baseline.baselineRate()),
                        framing.passText);
            } else {
                plainEnglish = String.format(
                        "The observed success rate of %s meets the required threshold of %s. %s",
                        RateFormat.format(observedRate),
                        RateFormat.format(threshold),
                        framing.passText);
            }
        } else {
            plainEnglish = String.format(
                    "The observed success rate of %s falls below the required threshold of %s. %s",
                    RateFormat.format(observedRate),
                    RateFormat.format(threshold),
                    framing.failText);
        }

        List<String> caveats = buildCaveats(samples, observedRate, threshold, misalignments);
        appendComplianceEvidenceCaveat(caveats, samples, threshold, thresholdOriginName, contractRef);
        appendSmokeIntentCaveats(caveats, samples, threshold, confidenceLevel, thresholdOriginName, isSmoke);

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
            String thresholdOriginName,
            String contractRef) {
        return buildInlineThresholdVerdict(passed, samples, successes, threshold,
                thresholdOriginName, contractRef, false);
    }

    private StatisticalExplanation.VerdictInterpretation buildInlineThresholdVerdict(
            boolean passed,
            int samples,
            int successes,
            double threshold,
            String thresholdOriginName,
            String contractRef,
            boolean isSmoke) {

        double observedRate = samples > 0 ? (double) successes / samples : 0.0;
        String technicalResult = passed ? "PASS" : "FAIL";
        VerdictFraming framing = getVerdictFraming(thresholdOriginName, isSmoke);

        String plainEnglish;
        if (passed) {
            plainEnglish = String.format(
                    "The observed success rate of %s meets the required threshold of %s. %s",
                    RateFormat.format(observedRate),
                    RateFormat.format(threshold),
                    framing.passText);
        } else {
            plainEnglish = String.format(
                    "The observed success rate of %s falls below the required threshold of %s. %s",
                    RateFormat.format(observedRate),
                    RateFormat.format(threshold),
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

        appendComplianceEvidenceCaveat(caveats, samples, threshold, thresholdOriginName, contractRef);
        appendSmokeIntentCaveats(caveats, samples, threshold, StatisticalDefaults.DEFAULT_CONFIDENCE, thresholdOriginName, isSmoke);

        return new StatisticalExplanation.VerdictInterpretation(
                passed,
                technicalResult,
                plainEnglish,
                caveats
        );
    }

    /**
     * Returns verdict framing text based on the threshold origin and intent.
     *
     * <p>SMOKE intent avoids compliance language (Req 11): "inconsistent with target"
     * instead of "not meeting SLA obligation".
     */
    private VerdictFraming getVerdictFraming(String thresholdOriginName, boolean isSmoke) {
        if (thresholdOriginName == null) {
            thresholdOriginName = "UNSPECIFIED";
        }

        // SMOKE intent uses softened language
        if (isSmoke) {
            return switch (thresholdOriginName.toUpperCase()) {
                case "SLA", "SLO", "POLICY" -> new VerdictFraming(
                        "The observed rate is consistent with the target.",
                        "The observed rate is inconsistent with the target.");
                case "EMPIRICAL" -> new VerdictFraming(
                        "No degradation from baseline detected.",
                        "This suggests potential degradation from the established baseline.");
                default -> new VerdictFraming(
                        "The test passes.",
                        "The observed rate does not meet the threshold.");
            };
        }

        // VERIFICATION intent (or null) — full compliance framing
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
        return buildCaveats(samples, observedRate, threshold, Collections.emptyList());
    }

    private List<String> buildCaveats(int samples, double observedRate, double threshold,
            List<CovariateMisalignment> misalignments) {
        List<String> caveats = new ArrayList<>();

        // Covariate misalignment caveat (listed first as it affects baseline validity)
        if (misalignments != null && !misalignments.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Covariate misalignment detected: the test conditions differ from the baseline. ");
            sb.append("Misaligned covariates: ");
            for (int i = 0; i < misalignments.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                CovariateMisalignment m = misalignments.get(i);
                sb.append(m.covariateKey());
                sb.append(" (baseline=").append(m.baselineValue());
                sb.append(", test=").append(m.testValue()).append(")");
            }
            sb.append(". Statistical comparison may be less reliable.");
            caveats.add(sb.toString());
        }

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
                        "The observed rate (%s) is close to the min pass rate (%s). " +
                        "Small fluctuations in future runs may cause different verdicts.",
                        RateFormat.format(observedRate), RateFormat.format(threshold)));
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

    /**
     * Appends intent-specific caveats for SMOKE tests with normative thresholds.
     *
     * <p>When a SMOKE test has a normative threshold (SLA/SLO/POLICY), PUnit checks
     * whether the sample size would be sufficient for VERIFICATION and adds an
     * appropriate caveat.
     */
    private void appendSmokeIntentCaveats(List<String> caveats, int samples,
            double threshold, double confidenceLevel,
            String thresholdOriginName, boolean isSmoke) {
        if (!isSmoke) {
            return;
        }
        // Only add sizing caveats for normative thresholds
        if (thresholdOriginName == null) {
            return;
        }
        boolean isNormative = thresholdOriginName.equalsIgnoreCase("SLA")
                || thresholdOriginName.equalsIgnoreCase("SLO")
                || thresholdOriginName.equalsIgnoreCase("POLICY");
        if (!isNormative) {
            return;
        }
        // Check feasibility for verification
        if (Double.isNaN(threshold) || threshold <= 0.0 || threshold >= 1.0) {
            return;
        }
        var result = VerificationFeasibilityEvaluator.evaluate(samples, threshold, confidenceLevel);
        if (!result.feasible()) {
            caveats.add(String.format(
                    "Sample not sized for verification (N=%d, need %d). " +
                    "This is a smoke-test-level observation, not a compliance determination.",
                    samples, result.minimumSamples()));
        } else {
            caveats.add("Sample is sized for verification. " +
                    "Consider setting intent = VERIFICATION for stronger statistical guarantees.");
        }
    }

    /**
     * Appends a compliance evidence caveat if the test has a compliance context
     * and the sample size is insufficient for compliance-grade evidence.
     *
     * <p>For high-reliability targets (e.g. 99.99%), a PASS verdict at small N
     * is not reliable evidence of conformance — it is a smoke test. However, a FAIL
     * verdict IS reliable evidence of non-conformance, since observed failures directly
     * demonstrate the system is not meeting the target.
     *
     * @param caveats the mutable caveats list to append to
     * @param samples the number of test samples
     * @param threshold the target pass rate
     * @param thresholdOriginName the threshold origin (e.g. "SLA", "SLO", "POLICY")
     * @param contractRef the contract reference (may be null or empty)
     */
    private void appendComplianceEvidenceCaveat(List<String> caveats, int samples,
            double threshold, String thresholdOriginName, String contractRef) {
        if (!ComplianceEvidenceEvaluator.hasComplianceContext(thresholdOriginName, contractRef)) {
            return;
        }
        if (!ComplianceEvidenceEvaluator.isUndersized(samples, threshold)) {
            return;
        }
        caveats.add(String.format(
                "Warning: %s. With n=%d and target of %s, even zero failures would " +
                "not provide sufficient statistical evidence of compliance (\u03b1=%.3f). " +
                "A PASS at this sample size is a smoke-test-level observation, not a compliance " +
                "determination. Note: a FAIL verdict remains a reliable indication of non-conformance.",
                ComplianceEvidenceEvaluator.SIZING_NOTE, samples, RateFormat.format(threshold),
                ComplianceEvidenceEvaluator.DEFAULT_ALPHA));
    }
}

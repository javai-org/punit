package org.javai.punit.reporting;

import org.javai.punit.statistics.VerificationFeasibilityEvaluator.FeasibilityResult;

/**
 * Renders human-readable infeasibility messages when a VERIFICATION test's
 * sample size is too small for meaningful statistical evidence.
 *
 * <p>Separates presentation from statistical evaluation: the math lives in
 * {@link org.javai.punit.statistics.VerificationFeasibilityEvaluator}, the
 * prose lives here. Both the legacy {@code ProbabilisticTestExtension} and
 * the typed-pipeline {@code engine.criteria.Feasibility} gate consume this
 * single renderer so the user sees the same wording regardless of how the
 * test was authored.
 */
public final class InfeasibilityMessageRenderer {

    private InfeasibilityMessageRenderer() {}

    /**
     * Builds a human-readable infeasibility message.
     *
     * <p>When {@code verbose} is false, produces a concise message suited to
     * non-statisticians. When true, includes the full statistical context
     * (criterion, confidence, alpha, assumptions).
     *
     * @param testName the test identity (method name in legacy use; use case id
     *                 in typed-pipeline use) — appears verbatim in the output
     * @param result   the infeasible evaluation result
     * @param verbose  true for full statistical detail, false for summary
     * @return a formatted message explaining why verification is impossible
     */
    public static String render(String testName, FeasibilityResult result, boolean verbose) {
        return verbose
                ? renderVerbose(testName, result)
                : renderSummary(testName, result);
    }

    private static String renderSummary(String testName, FeasibilityResult result) {
        String targetPercent = formatTargetAsPercentage(result.target());
        StringBuilder sb = new StringBuilder();
        sb.append("\nINFEASIBLE VERIFICATION\n\n");
        sb.append(testName).append("\n\n");
        sb.append(String.format(
                "The configured sample size (%d) is too small to verify a %s\n",
                result.configuredSamples(), targetPercent));
        sb.append(String.format("pass rate. At least %d samples are required.\n\n", result.minimumSamples()));
        sb.append("REMEDIATION\n");
        sb.append("  • Increase samples to at least ").append(result.minimumSamples()).append("\n");
        sb.append("  • Set intent = SMOKE to run as a sentinel test");
        return sb.toString();
    }

    private static String renderVerbose(String testName, FeasibilityResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nINFEASIBLE VERIFICATION\n\n");
        sb.append(testName).append("\n\n");
        sb.append(String.format(
                "The configured sample size (N=%d) is insufficient for verification\n",
                result.configuredSamples()));
        sb.append("at the declared confidence level.\n\n");
        sb.append("CONFIGURATION\n");
        sb.append("  ").append(PUnitReporter.labelValueLn("Target (p₀):", String.format("%.4f", result.target())));
        sb.append("  ").append(PUnitReporter.labelValueLn("Confidence:",
                String.format("%.2f (α = %.2f)", 1.0 - result.configuredAlpha(), result.configuredAlpha())));
        sb.append("  ").append(PUnitReporter.labelValueLn("Samples:", String.valueOf(result.configuredSamples())));
        sb.append("\nFEASIBILITY\n");
        sb.append("  ").append(PUnitReporter.labelValueLn("Criterion:", result.criterion()));
        sb.append("  ").append(PUnitReporter.labelValueLn("Minimum N:", String.valueOf(result.minimumSamples())));
        sb.append("  ").append(PUnitReporter.labelValueLn("Assumption:", FeasibilityResult.ASSUMPTION));
        sb.append("\nREMEDIATION\n");
        sb.append("  • Increase samples to at least ").append(result.minimumSamples()).append("\n");
        sb.append("  • Set intent = SMOKE to run as a sentinel test");
        return sb.toString();
    }

    static String formatTargetAsPercentage(double target) {
        double percent = target * 100.0;
        if (percent == Math.floor(percent)) {
            return String.format("%.0f%%", percent);
        }
        String formatted = String.format("%.4f", percent).replaceAll("0+$", "").replaceAll("\\.$", "");
        return formatted + "%";
    }
}

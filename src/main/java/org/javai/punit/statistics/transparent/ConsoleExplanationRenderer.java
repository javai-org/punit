package org.javai.punit.statistics.transparent;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.javai.punit.reporting.PUnitReporter;

/**
 * Renders statistical explanations for console output with box drawing characters.
 *
 * <p>This renderer produces human-readable output with:
 * <ul>
 *   <li>Clear visual delineation using box drawing characters</li>
 *   <li>Proper mathematical notation (Unicode with ASCII fallback)</li>
 *   <li>Dual-audience content: formulas for statisticians, plain English for everyone else</li>
 * </ul>
 */
public class ConsoleExplanationRenderer implements ExplanationRenderer {

    private static final int LINE_WIDTH = 78;
    private static final DateTimeFormatter DATE_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final StatisticalVocabulary.Symbols symbols;
    private final TransparentStatsConfig.DetailLevel detailLevel;

    /**
     * Creates a renderer with default settings (Unicode symbols, STANDARD detail).
     */
    public ConsoleExplanationRenderer() {
        this(TransparentStatsConfig.supportsUnicode(), TransparentStatsConfig.DetailLevel.STANDARD);
    }

    /**
     * Creates a renderer with specified settings.
     *
     * @param useUnicode whether to use Unicode symbols
     * @param detailLevel the level of detail to include
     */
    public ConsoleExplanationRenderer(boolean useUnicode, TransparentStatsConfig.DetailLevel detailLevel) {
        this.symbols = StatisticalVocabulary.symbols(useUnicode);
        this.detailLevel = detailLevel;
    }

    /**
     * Creates a renderer from configuration.
     *
     * @param config the transparent stats configuration
     */
    public ConsoleExplanationRenderer(TransparentStatsConfig config) {
        this(TransparentStatsConfig.supportsUnicode(), config.detailLevel());
    }

    /**
     * Result of rendering containing title and body for PUnitReporter.
     */
    public record RenderResult(String title, String body) {}

    /**
     * Renders the explanation and returns title and body separately.
     *
     * @param explanation the statistical explanation to render
     * @return a RenderResult containing title and body
     */
    public RenderResult renderForReporter(StatisticalExplanation explanation) {
        String title = "STATISTICAL ANALYSIS: " + explanation.testName();
        StringBuilder sb = new StringBuilder();

        // Sections (no header/footer - PUnitReporter handles those)
        renderHypothesisSection(sb, explanation.hypothesis());
        renderObservedDataSection(sb, explanation.observed());
        renderBaselineReferenceSection(sb, explanation.baseline());
        renderStatisticalInferenceSection(sb, explanation);
        renderVerdictSection(sb, explanation.verdict());
        renderProvenanceSection(sb, explanation.provenance());

        return new RenderResult(title, sb.toString().trim());
    }

    @Override
    public String render(StatisticalExplanation explanation) {
        RenderResult result = renderForReporter(explanation);
        // For backwards compatibility, include a simple header
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(symbols.singleLine(LINE_WIDTH)).append("\n");
        sb.append(result.title()).append("\n");
        sb.append(symbols.singleLine(LINE_WIDTH)).append("\n");
        sb.append("\n");
        sb.append(result.body());
        sb.append("\n");
        sb.append(symbols.singleLine(LINE_WIDTH)).append("\n");
        return sb.toString();
    }

    @Override
    public String render(StatisticalExplanation explanation, TransparentStatsConfig config) {
        // Use the config's detail level if different from constructor
        if (config.detailLevel() != this.detailLevel) {
            return new ConsoleExplanationRenderer(
                    TransparentStatsConfig.supportsUnicode(), 
                    config.detailLevel()
            ).render(explanation);
        }
        return render(explanation);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION RENDERERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderHypothesisSection(StringBuilder sb, StatisticalExplanation.HypothesisStatement hypothesis) {
        sb.append("HYPOTHESIS TEST\n");
        sb.append(String.format("  %s (null):        %s%n", symbols.h0(), hypothesis.nullHypothesis()));
        sb.append(String.format("  %s (alternative): %s%n", symbols.h1(), hypothesis.alternativeHypothesis()));
        sb.append(String.format("  Test type:        %s%n", hypothesis.testType()));
        sb.append("\n");
    }

    private void renderObservedDataSection(StringBuilder sb, StatisticalExplanation.ObservedData observed) {
        sb.append("OBSERVED DATA\n");
        sb.append(statLabel("Sample size (n):", String.valueOf(observed.sampleSize())));
        sb.append(statLabel("Successes (k):", String.valueOf(observed.successes())));
        sb.append(statLabel("Observed rate (" + symbols.pHat() + "):", String.format("%.3f", observed.observedRate())));
        sb.append("\n");
    }

    private void renderBaselineReferenceSection(StringBuilder sb, StatisticalExplanation.BaselineReference baseline) {
        sb.append("BASELINE REFERENCE\n");
        
        if (baseline.hasBaselineData()) {
            String dateStr = baseline.generatedAt() != null 
                    ? DATE_FORMAT.format(baseline.generatedAt())
                    : "unknown";
            sb.append(statLabel("Source:", baseline.sourceFile() + " (generated " + dateStr + ")"));
            sb.append(statLabel("Empirical basis:", 
                    String.format("%d samples, %d successes (%.1f%%)",
                            baseline.baselineSamples(), baseline.baselineSuccesses(), baseline.baselineRate() * 100)));
            sb.append(statLabel("Threshold derivation:", baseline.thresholdDerivation()));
        } else {
            sb.append(statLabel("Source:", baseline.sourceFile()));
            sb.append(statLabel("Threshold:", 
                    String.format("%.1f%% (%s)", baseline.threshold() * 100, baseline.thresholdDerivation())));
        }
        sb.append("\n");
    }

    private void renderStatisticalInferenceSection(StringBuilder sb, StatisticalExplanation explanation) {
        StatisticalExplanation.StatisticalInference inference = explanation.inference();
        StatisticalExplanation.ObservedData observed = explanation.observed();
        StatisticalExplanation.BaselineReference baseline = explanation.baseline();
        
        sb.append("STATISTICAL INFERENCE\n");
        
        // Standard error with formula
        double pHat = observed.observedRate();
        int n = observed.sampleSize();
        sb.append(String.format("  Standard error:      SE = %s(%s(1-%s)/n) = %s(%.2f %s %.2f / %d) = %.4f%n",
                symbols.sqrt(), symbols.pHat(), symbols.pHat(),
                symbols.sqrt(), pHat, symbols.times(), (1 - pHat), n,
                inference.standardError()));

        // Confidence interval
        sb.append(String.format("  %.0f%% Confidence interval: [%.3f, %.3f]%n",
                inference.confidencePercent(),
                inference.ciLower(),
                inference.ciUpper()));

        // Test statistic and p-value (if available and detail level warrants)
        if (detailLevel == TransparentStatsConfig.DetailLevel.VERBOSE 
                || detailLevel == TransparentStatsConfig.DetailLevel.STANDARD) {
            renderZTestCalculation(sb, observed, baseline);
        }

        sb.append("\n");
    }

    private void renderZTestCalculation(StringBuilder sb, 
            StatisticalExplanation.ObservedData observed,
            StatisticalExplanation.BaselineReference baseline) {
        
        if (observed.sampleSize() == 0) {
            return;
        }

        double pHat = observed.observedRate();
        double pi0 = baseline.threshold();
        int n = observed.sampleSize();
        
        // Calculate z-score
        double se = Math.sqrt(pi0 * (1 - pi0) / n);
        double z = se > 0 ? (pHat - pi0) / se : 0;
        
        sb.append("  \n");
        sb.append(String.format("  Test statistic:      z = (%s - %s%s) / %s(%s%s(1-%s%s)/n)%n",
                symbols.pHat(), symbols.pi(), StatisticalVocabulary.SUB_ZERO,
                symbols.sqrt(), symbols.pi(), StatisticalVocabulary.SUB_ZERO,
                symbols.pi(), StatisticalVocabulary.SUB_ZERO));
        sb.append(String.format("                       z = (%.2f - %.2f) / %s(%.2f %s %.2f / %d)%n",
                pHat, pi0, symbols.sqrt(), pi0, symbols.times(), (1 - pi0), n));
        sb.append(String.format("                       z = %.2f%n", z));
        
        // P-value (approximate using standard normal)
        // For one-sided test: P(Z > z)
        double pValue = 1 - normalCDF(z);
        sb.append(String.format("  \n"));
        sb.append(String.format("  p-value:             P(Z > %.2f) = %.3f%n", z, pValue));
    }

    private void renderVerdictSection(StringBuilder sb, StatisticalExplanation.VerdictInterpretation verdict) {
        sb.append("VERDICT\n");
        sb.append(statLabel("Result:", verdict.technicalResult()));
        
        // Wrap plain English interpretation
        sb.append("  Interpretation:      ");
        String[] words = verdict.plainEnglish().split(" ");
        int lineLength = 19; // Length of "  Interpretation:      "
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (lineLength + word.length() + 1 > LINE_WIDTH && line.length() > 0) {
                sb.append(line.toString().trim()).append("\n");
                sb.append("                       ");
                line = new StringBuilder();
                lineLength = 23;
            }
            line.append(word).append(" ");
            lineLength += word.length() + 1;
        }
        if (line.length() > 0) {
            sb.append(line.toString().trim()).append("\n");
        }
        
        // Caveats
        if (!verdict.caveats().isEmpty()) {
            sb.append("                       \n");
            for (String caveat : verdict.caveats()) {
                sb.append("  Caveat:              ");
                String[] caveatWords = caveat.split(" ");
                lineLength = 23;
                line = new StringBuilder();
                for (String word : caveatWords) {
                    if (lineLength + word.length() + 1 > LINE_WIDTH && line.length() > 0) {
                        sb.append(line.toString().trim()).append("\n");
                        sb.append("                       ");
                        line = new StringBuilder();
                        lineLength = 23;
                    }
                    line.append(word).append(" ");
                    lineLength += word.length() + 1;
                }
                if (line.length() > 0) {
                    sb.append(line.toString().trim()).append("\n");
                }
            }
        }
        
        sb.append("\n");
    }

    private void renderProvenanceSection(StringBuilder sb, StatisticalExplanation.Provenance provenance) {
        // Only render if provenance information is present
        if (provenance == null || !provenance.hasProvenance()) {
            return;
        }

        sb.append("THRESHOLD PROVENANCE\n");
        if (provenance.hasThresholdOrigin()) {
            sb.append(statLabel("Threshold origin:", provenance.thresholdOriginName()));
        }
        if (provenance.hasContractRef()) {
            sb.append(String.format("  Contract ref:        %s%n", provenance.contractRef()));
        }
        sb.append("\n");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Approximates the standard normal CDF using the error function approximation.
     */
    private static double normalCDF(double z) {
        // Approximation using error function
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    /**
     * Approximates the error function using Horner's method.
     * Abramowitz and Stegun approximation 7.1.26
     */
    private static double erf(double x) {
        // Constants
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;

        // Save the sign of x
        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x);

        // Approximation
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);

        return sign * y;
    }

    /**
     * Formats a label-value line for statistical analysis sections.
     *
     * <p>Uses {@link PUnitReporter#STATS_LABEL_WIDTH} for consistent alignment
     * across all statistical analysis output.
     *
     * @param label the label (e.g., "Sample size (n):")
     * @param value the value
     * @return formatted line with newline
     */
    private String statLabel(String label, String value) {
        return "  " + PUnitReporter.labelValueLn(label, value, PUnitReporter.STATS_LABEL_WIDTH);
    }
}


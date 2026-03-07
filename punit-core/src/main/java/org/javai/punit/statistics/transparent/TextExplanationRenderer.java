package org.javai.punit.statistics.transparent;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.reporting.RateFormat;

/**
 * Renders statistical explanations as formatted text with box drawing characters.
 *
 * <p>This renderer produces human-readable output with:
 * <ul>
 *   <li>Clear visual delineation using box drawing characters</li>
 *   <li>Proper mathematical notation (Unicode with ASCII fallback)</li>
 *   <li>Dual-audience content: formulas for statisticians, plain English for everyone else</li>
 * </ul>
 *
 * <p>This class is a pure formatter — all statistical values (z-scores, p-values,
 * confidence intervals) are pre-computed by {@link StatisticalExplanationBuilder}
 * and stored in the {@link StatisticalExplanation} model.
 */
public class TextExplanationRenderer implements ExplanationRenderer {

    private static final int LINE_WIDTH = 78;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final StatisticalVocabulary.Symbols symbols;
    private final TransparentStatsConfig.DetailLevel detailLevel;

    /**
     * Creates a renderer with default settings (Unicode symbols, VERBOSE detail).
     */
    public TextExplanationRenderer() {
        this(TransparentStatsConfig.supportsUnicode(), TransparentStatsConfig.DetailLevel.VERBOSE);
    }

    /**
     * Creates a renderer with specified settings.
     *
     * @param useUnicode whether to use Unicode symbols
     * @param detailLevel the level of detail to include
     */
    public TextExplanationRenderer(boolean useUnicode, TransparentStatsConfig.DetailLevel detailLevel) {
        this.symbols = StatisticalVocabulary.symbols(useUnicode);
        this.detailLevel = detailLevel;
    }

    /**
     * Creates a renderer from configuration.
     *
     * @param config the transparent stats configuration
     */
    public TextExplanationRenderer(TransparentStatsConfig config) {
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
        String title = "STATISTICAL ANALYSIS FOR: " + explanation.testName();
        StringBuilder sb = new StringBuilder();

        // Sections (no header/footer - PUnitReporter handles those)
        if (detailLevel == TransparentStatsConfig.DetailLevel.VERBOSE) {
            renderHypothesisSection(sb, explanation.hypothesis());
        }
        renderObservedDataSection(sb, explanation.observed());
        renderBaselineReferenceSection(sb, explanation.baseline());
        if (detailLevel == TransparentStatsConfig.DetailLevel.VERBOSE) {
            renderStatisticalInferenceSection(sb, explanation);
        }
        if (explanation.hasLatencyAnalysis()) {
            renderLatencyAnalysisSection(sb, explanation.latencyAnalysis());
        }
        renderVerdictSection(sb, explanation.verdict());
        renderProvenanceSection(sb, explanation.provenance());

        return new RenderResult(title, sb.toString().trim());
    }

    @Override
    public String render(StatisticalExplanation explanation) {
        RenderResult result = renderForReporter(explanation);
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
        if (config.detailLevel() != this.detailLevel) {
            return new TextExplanationRenderer(
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
        sb.append(statLabel(symbols.h0() + " (null):", hypothesis.nullHypothesis()));
        sb.append(statLabel(symbols.h1() + " (alternative):", hypothesis.alternativeHypothesis()));
        sb.append(statLabel("Test type:", hypothesis.testType()));
        sb.append("\n");
    }

    private void renderObservedDataSection(StringBuilder sb, StatisticalExplanation.ObservedData observed) {
        sb.append("OBSERVED DATA\n");
        sb.append(statLabel("Sample size (n):", String.valueOf(observed.sampleSize())));
        sb.append(statLabel("Successes (k):", String.valueOf(observed.successes())));
        sb.append(statLabel("Observed rate (" + symbols.pHat() + "):", RateFormat.format(observed.observedRate())));
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
                    String.format("%d samples, %d successes (%s)",
                            baseline.baselineSamples(), baseline.baselineSuccesses(), RateFormat.format(baseline.baselineRate()))));
            sb.append(statLabel("Threshold derivation:", baseline.thresholdDerivation()));
        } else {
            sb.append(statLabel("Source:", baseline.sourceFile()));
            sb.append(statLabel("Threshold:",
                    String.format("%s (%s)", RateFormat.format(baseline.threshold()), baseline.thresholdDerivation())));
        }
        sb.append("\n");
    }

    private void renderStatisticalInferenceSection(StringBuilder sb, StatisticalExplanation explanation) {
        StatisticalExplanation.StatisticalInference inference = explanation.inference();
        StatisticalExplanation.ObservedData observed = explanation.observed();

        sb.append("STATISTICAL INFERENCE\n");

        // Standard error with formula
        double pHat = observed.observedRate();
        int n = observed.sampleSize();
        sb.append(statLabel("Standard error:",
                String.format("SE = %s(%s(1-%s)/n) = %s(%.2f %s %.2f / %d) = %.4f",
                        symbols.sqrt(), symbols.pHat(), symbols.pHat(),
                        symbols.sqrt(), pHat, symbols.times(), (1 - pHat), n,
                        inference.standardError())));

        // Confidence interval
        sb.append(statLabel("Confidence interval:",
                String.format("%.0f%% [%.3f, %.3f]",
                        inference.confidencePercent(), inference.ciLower(), inference.ciUpper())));

        renderZTestCalculation(sb, explanation);

        sb.append("\n");
    }

    private void renderZTestCalculation(StringBuilder sb, StatisticalExplanation explanation) {
        StatisticalExplanation.StatisticalInference inference = explanation.inference();
        StatisticalExplanation.ObservedData observed = explanation.observed();
        StatisticalExplanation.BaselineReference baseline = explanation.baseline();

        if (!inference.hasTestStatistic()) {
            return;
        }

        double pHat = observed.observedRate();
        double pi0 = baseline.threshold();
        int n = observed.sampleSize();
        double z = inference.testStatistic();

        String valueIndent = " ".repeat(PUnitReporter.DETAIL_LABEL_WIDTH);
        sb.append("\n");
        sb.append(statLabel("Test statistic:",
                String.format("z = (%s - %s%s) / %s(%s%s(1-%s%s)/n)",
                        symbols.pHat(), symbols.pi(), StatisticalVocabulary.SUB_ZERO,
                        symbols.sqrt(), symbols.pi(), StatisticalVocabulary.SUB_ZERO,
                        symbols.pi(), StatisticalVocabulary.SUB_ZERO)));
        sb.append(String.format("  %sz = (%.2f - %.2f) / %s(%.2f %s %.2f / %d)%n",
                valueIndent, pHat, pi0, symbols.sqrt(), pi0, symbols.times(), (1 - pi0), n));
        sb.append(String.format("  %sz = %.2f%n", valueIndent, z));

        if (inference.hasPValue()) {
            sb.append("\n");
            sb.append(statLabel("p-value:", String.format("P(Z > %.2f) = %.3f", z, inference.pValue())));
        }
    }

    private void renderLatencyAnalysisSection(StringBuilder sb, StatisticalExplanation.LatencyAnalysis latency) {
        sb.append("LATENCY ANALYSIS\n");

        // Population line
        if (latency.skipped()) {
            sb.append(statLabel("Population:",
                    String.format("No successful samples available (%d of %d)",
                            latency.successfulSamples(), latency.totalSamples())));
            // Skipped message
            String wrapIndent = "  " + " ".repeat(PUnitReporter.DETAIL_LABEL_WIDTH);
            int wrapIndentLen = wrapIndent.length();
            appendWrappedLabel(sb, "Latency:",
                    "Not evaluated. Latency assertions require at least one successful sample. " +
                    "The pass rate failure should be investigated first.",
                    wrapIndent, wrapIndentLen);
            sb.append("\n");
            return;
        }

        sb.append(statLabel("Population:",
                String.format("Successful samples only (n=%d of %d)",
                        latency.successfulSamples(), latency.totalSamples())));

        // Observed distribution
        sb.append("  Observed distribution:\n");
        if (latency.p50Ms() >= 0) sb.append(statLabel("p50:", latency.p50Ms() + "ms"));
        if (latency.p90Ms() >= 0) sb.append(statLabel("p90:", latency.p90Ms() + "ms"));
        if (latency.p95Ms() >= 0) sb.append(statLabel("p95:", latency.p95Ms() + "ms"));
        if (latency.p99Ms() >= 0) sb.append(statLabel("p99:", latency.p99Ms() + "ms"));
        if (latency.maxMs() >= 0) sb.append(statLabel("max:", latency.maxMs() + "ms"));
        sb.append("\n");

        // Percentile thresholds
        if (!latency.percentileAssertions().isEmpty()) {
            String thresholdHeader = latency.isBaselineDerived()
                    ? "Percentile thresholds (from baseline):"
                    : "Percentile thresholds:";
            sb.append("  ").append(thresholdHeader).append("\n");

            for (var assertion : latency.percentileAssertions()) {
                String comparison = assertion.passed()
                        ? assertion.observedMs() + "ms <= " + assertion.thresholdMs() + "ms"
                        : assertion.observedMs() + "ms > " + assertion.thresholdMs() + "ms";

                String result = assertion.passed() ? "PASS" : "FAIL";
                if (assertion.indicative() && assertion.passed()) {
                    result = "PASS (indicative)";
                }

                // Format: label: comparison right-aligned result
                String labelAndComparison = String.format("%-" + PUnitReporter.DETAIL_LABEL_WIDTH + "s%s",
                        assertion.label() + ":", comparison);
                int padding = LINE_WIDTH - 2 - labelAndComparison.length() - result.length();
                if (padding < 1) padding = 1;
                sb.append("  ").append(labelAndComparison)
                  .append(" ".repeat(padding)).append(result).append("\n");
            }
            sb.append("\n");
        }

        // Caveats
        if (!latency.caveats().isEmpty()) {
            String wrapIndent = "  " + " ".repeat(PUnitReporter.DETAIL_LABEL_WIDTH);
            int wrapIndentLen = wrapIndent.length();
            for (String caveat : latency.caveats()) {
                appendWrappedLabel(sb, "Caveat:", caveat, wrapIndent, wrapIndentLen);
            }
            sb.append("\n");
        }

        // Baseline reference
        if (latency.isBaselineDerived() && latency.baselineFilename() != null) {
            sb.append(statLabel("Baseline reference:", latency.baselineFilename()));
            sb.append("\n");
        }
    }

    private void renderVerdictSection(StringBuilder sb, StatisticalExplanation.VerdictInterpretation verdict) {
        sb.append("VERDICT\n");
        sb.append(statLabel("Result:", verdict.technicalResult()));

        // Indentation for wrapped content: 2 (section indent) + DETAIL_LABEL_WIDTH
        String wrapIndent = "  " + " ".repeat(PUnitReporter.DETAIL_LABEL_WIDTH);
        int wrapIndentLen = wrapIndent.length();

        // Wrap plain English interpretation
        appendWrappedLabel(sb, "Interpretation:", verdict.plainEnglish(), wrapIndent, wrapIndentLen);

        // Caveats
        if (!verdict.caveats().isEmpty()) {
            sb.append("\n");
            for (String caveat : verdict.caveats()) {
                appendWrappedLabel(sb, "Caveat:", caveat, wrapIndent, wrapIndentLen);
            }
        }

        sb.append("\n");
    }

    private void appendWrappedLabel(StringBuilder sb, String label, String text,
            String wrapIndent, int wrapIndentLen) {
        sb.append("  ").append(String.format("%-" + PUnitReporter.DETAIL_LABEL_WIDTH + "s", label));
        String[] words = text.split(" ");
        int lineLength = wrapIndentLen;
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (lineLength + word.length() + 1 > LINE_WIDTH && !line.isEmpty()) {
                sb.append(line.toString().trim()).append("\n");
                sb.append(wrapIndent);
                line = new StringBuilder();
                lineLength = wrapIndentLen;
            }
            line.append(word).append(" ");
            lineLength += word.length() + 1;
        }
        if (!line.isEmpty()) {
            sb.append(line.toString().trim()).append("\n");
        }
    }

    private void renderProvenanceSection(StringBuilder sb, StatisticalExplanation.Provenance provenance) {
        if (provenance == null || !provenance.hasProvenance()) {
            return;
        }

        sb.append("THRESHOLD PROVENANCE\n");
        if (provenance.hasThresholdOrigin()) {
            sb.append(statLabel("Threshold origin:", provenance.thresholdOriginName()));
        }
        if (provenance.hasContractRef()) {
            sb.append(statLabel("Contract:", provenance.contractRef()));
        }
        sb.append("\n");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Formats a label-value line for statistical analysis sections.
     *
     * <p>Uses {@link PUnitReporter#DETAIL_LABEL_WIDTH} for consistent alignment
     * across all statistical analysis output.
     *
     * @param label the label (e.g., "Sample size (n):")
     * @param value the value
     * @return formatted line with newline
     */
    private String statLabel(String label, String value) {
        return "  " + PUnitReporter.labelValueLn(label, value, PUnitReporter.DETAIL_LABEL_WIDTH);
    }
}

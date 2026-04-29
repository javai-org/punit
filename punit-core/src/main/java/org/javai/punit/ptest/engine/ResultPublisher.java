package org.javai.punit.ptest.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.javai.punit.api.TestIntent;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.model.ExpirationStatus;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.reporting.RateFormat;
import org.javai.punit.spec.expiration.ExpirationEvaluator;
import org.javai.punit.spec.expiration.ExpirationReportPublisher;
import org.javai.punit.spec.expiration.ExpirationWarningRenderer;
import org.javai.punit.spec.expiration.WarningLevel;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.statistics.ComplianceEvidenceEvaluator;
import org.javai.punit.statistics.VerificationFeasibilityEvaluator;
import org.javai.punit.statistics.transparent.TransparentStatsConfig;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdict.CostSummary;
import org.javai.punit.verdict.ProbabilisticTestVerdict.CovariateStatus;
import org.javai.punit.verdict.ProbabilisticTestVerdict.ExecutionSummary;
import org.javai.punit.verdict.ProbabilisticTestVerdict.FunctionalDimension;
import org.javai.punit.verdict.ProbabilisticTestVerdict.LatencyDimension;
import org.javai.punit.verdict.ProbabilisticTestVerdict.Misalignment;
import org.javai.punit.verdict.ProbabilisticTestVerdict.SpecProvenance;
import org.javai.punit.verdict.ProbabilisticTestVerdict.Termination;
import org.javai.punit.verdict.PUnitVerdict;
import org.javai.punit.verdict.VerdictTextRenderer;

/**
 * Publishes test results via TestReporter and prints console summaries.
 *
 * <p>This class consumes {@link ProbabilisticTestVerdict} — the single source of truth
 * for all verdict data — and renders it for console output and TestReporter entries.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
class ResultPublisher {

    private final PUnitReporter reporter;

    ResultPublisher(PUnitReporter reporter) {
        this.reporter = reporter;
    }

    /**
     * Builds report entries for TestReporter from the verdict model.
     *
     * @param verdict the probabilistic test verdict
     * @param spec the execution specification for expiration properties (may be null)
     * @return map of punit.* entries
     */
    Map<String, String> buildReportEntries(ProbabilisticTestVerdict verdict, ExecutionSpecification spec) {
        Map<String, String> entries = new LinkedHashMap<>();
        ExecutionSummary exec = verdict.execution();
        Termination term = verdict.termination();

        entries.put("punit.samples", String.valueOf(exec.plannedSamples()));
        entries.put("punit.samplesExecuted", String.valueOf(exec.samplesExecuted()));
        entries.put("punit.successes", String.valueOf(exec.successes()));
        entries.put("punit.failures", String.valueOf(exec.failures()));
        entries.put("punit.minPassRate", String.format("%.4f", exec.minPassRate()));
        entries.put("punit.observedPassRate", String.format("%.4f", exec.observedPassRate()));
        entries.put("punit.verdict", verdict.junitPassed() ? "PASS" : "FAIL");
        entries.put("punit.terminationReason", term.reason().name());
        entries.put("punit.elapsedMs", String.valueOf(exec.elapsedMs()));

        // Warmup
        if (exec.warmup() > 0) {
            entries.put("punit.warmup", String.valueOf(exec.warmup()));
        }

        // Multiplier
        exec.appliedMultiplier().ifPresent(m ->
                entries.put("punit.samplesMultiplier", String.format("%.2f", m)));

        // Method-level budget info
        CostSummary cost = verdict.cost();
        if (cost.methodTimeBudgetMs() > 0) {
            entries.put("punit.method.timeBudgetMs", String.valueOf(cost.methodTimeBudgetMs()));
        }
        if (cost.methodTokenBudget() > 0) {
            entries.put("punit.method.tokenBudget", String.valueOf(cost.methodTokenBudget()));
        }
        entries.put("punit.method.tokensConsumed", String.valueOf(cost.methodTokensConsumed()));

        if (cost.tokenMode() != CostBudgetMonitor.TokenMode.NONE) {
            entries.put("punit.tokenMode", cost.tokenMode().name());
        }

        // Class-level budget info
        cost.classBudget().ifPresent(snap -> {
            if (snap.timeBudgetMs() > 0) {
                entries.put("punit.class.timeBudgetMs", String.valueOf(snap.timeBudgetMs()));
                entries.put("punit.class.elapsedMs", String.valueOf(snap.elapsedMs()));
            }
            if (snap.tokenBudget() > 0) {
                entries.put("punit.class.tokenBudget", String.valueOf(snap.tokenBudget()));
            }
            entries.put("punit.class.tokensConsumed", String.valueOf(snap.tokensConsumed()));
        });

        // Suite-level budget info
        cost.suiteBudget().ifPresent(snap -> {
            if (snap.timeBudgetMs() > 0) {
                entries.put("punit.suite.timeBudgetMs", String.valueOf(snap.timeBudgetMs()));
                entries.put("punit.suite.elapsedMs", String.valueOf(snap.elapsedMs()));
            }
            if (snap.tokenBudget() > 0) {
                entries.put("punit.suite.tokenBudget", String.valueOf(snap.tokenBudget()));
            }
            entries.put("punit.suite.tokensConsumed", String.valueOf(snap.tokensConsumed()));
        });

        // Per-dimension results
        verdict.functional().ifPresent(func -> {
            entries.put("punit.dimension.functional", "true");
            entries.put("punit.dimension.functional.successes", String.valueOf(func.successes()));
            entries.put("punit.dimension.functional.failures", String.valueOf(func.failures()));
        });
        verdict.latency().ifPresent(lat -> {
            if (!lat.skipped()) {
                entries.put("punit.dimension.latency", "true");
                entries.put("punit.dimension.latency.successes", String.valueOf(lat.dimensionSuccesses()));
                entries.put("punit.dimension.latency.failures", String.valueOf(lat.dimensionFailures()));
            }
        });

        // Expiration status
        if (spec != null) {
            ExpirationStatus expirationStatus = ExpirationEvaluator.evaluate(spec);
            entries.putAll(ExpirationReportPublisher.buildProperties(spec, expirationStatus));
        }

        return entries;
    }

    /**
     * Prints a summary message to the console for visibility.
     *
     * @param verdict the probabilistic test verdict
     * @param transparentStats the transparent stats config (rendering concern, may be null)
     * @param spec the execution specification for expiration warnings (may be null)
     */
    void printConsoleSummary(ProbabilisticTestVerdict verdict,
                             TransparentStatsConfig transparentStats,
                             ExecutionSpecification spec) {
        // If transparent stats mode is enabled, render the full statistical explanation
        if (transparentStats != null && transparentStats.enabled()) {
            printTransparentStatsSummary(verdict, transparentStats, spec);
            return;
        }

        ExecutionSummary exec = verdict.execution();
        Termination term = verdict.termination();

        boolean isBudgetExhausted = term.reason().isBudgetExhaustion();

        String verdictLabel = verdict.punitVerdict().name();
        String title = "VERDICT: " + verdictLabel + " (" + verdict.verdictReason() + ")";

        String testName = verdict.identity().className() + "." + verdict.identity().methodName();
        StringBuilder sb = new StringBuilder();
        sb.append(testName).append("\n\n");

        if (isBudgetExhausted) {
            sb.append(PUnitReporter.labelValueLn("Samples executed:",
                    String.format("%d of %d (budget exhausted)", exec.samplesExecuted(), exec.plannedSamples())));
            sb.append(PUnitReporter.labelValueLn("Pass rate:",
                    String.format("%s (%d/%d), required: %s",
                            RateFormat.format(exec.observedPassRate()),
                            exec.successes(), exec.samplesExecuted(),
                            RateFormat.format(exec.minPassRate()))));
        } else {
            String comparator = exec.observedPassRate() >= exec.minPassRate() ? ">=" : "<";
            sb.append(PUnitReporter.labelValueLn("Observed pass rate:",
                    String.format("%s (%d/%d) %s required: %s",
                            RateFormat.format(exec.observedPassRate()),
                            exec.successes(), exec.samplesExecuted(),
                            comparator,
                            RateFormat.format(exec.minPassRate()))));
        }

        // Append warmup line if warmup was used
        if (exec.warmup() > 0) {
            String useCaseLabel = verdict.identity().useCaseId().orElse("");
            sb.append(PUnitReporter.labelValueLn("Warmup:",
                    String.format("%d invocations discarded%s",
                            exec.warmup(),
                            useCaseLabel.isEmpty() ? "" : " (" + useCaseLabel + ")")));
        }

        // Append per-dimension breakdown if available
        appendDimensionBreakdown(sb, verdict);

        // Append covariate comparison for INCONCLUSIVE verdicts
        appendCovariateComparison(sb, verdict);

        // Append latency result if evaluated
        appendLatencyResult(sb, verdict);

        // Append baseline provenance if available
        appendBaselineProvenance(sb, verdict);

        // Append provenance if configured
        appendProvenance(sb, verdict);

        // Append termination details
        if (term.reason() != TerminationReason.COMPLETED) {
            sb.append(PUnitReporter.labelValueLn("Termination:",
                    VerdictTextRenderer.formatTerminationMessage(term.reason(), exec.samplesExecuted(), exec.plannedSamples())));
            term.details().ifPresent(details -> {
                if (!details.isEmpty()) {
                    sb.append(PUnitReporter.labelValueLn("Details:", details));
                }
            });
            if (term.reason() == TerminationReason.IMPOSSIBILITY) {
                int required = (int) Math.ceil(exec.plannedSamples() * exec.minPassRate());
                int remaining = exec.plannedSamples() - exec.samplesExecuted();
                int maxPossible = exec.successes() + remaining;
                sb.append(PUnitReporter.labelValueLn("Analysis:",
                        String.format("Needed %d successes, maximum possible is %d", required, maxPossible)));
            }
        }

        sb.append(PUnitReporter.labelValue("Elapsed:", exec.elapsedMs() + "ms"));

        // Append notes (with blank line separator)
        StringBuilder notes = new StringBuilder();
        appendComplianceEvidenceNote(notes, verdict);
        appendSmokeIntentNote(notes, verdict);
        if (!notes.isEmpty()) {
            sb.append("\n\n").append(notes);
        }

        reporter.reportInfo(title, sb.toString());

        // Print expiration warning if applicable (summary mode defaults to VERBOSE)
        TransparentStatsConfig.DetailLevel detailLevel = transparentStats != null
                ? transparentStats.detailLevel()
                : TransparentStatsConfig.DetailLevel.VERBOSE;
        printExpirationWarning(spec, detailLevel);
    }

    /**
     * Prints an expiration warning if the baseline is expired or expiring.
     */
    void printExpirationWarning(ExecutionSpecification spec, TransparentStatsConfig.DetailLevel detailLevel) {
        if (spec == null) {
            return;
        }

        ExpirationStatus status = ExpirationEvaluator.evaluate(spec);
        if (!status.requiresWarning()) {
            return;
        }

        WarningLevel level = WarningLevel.forStatus(status);
        if (level == null || !level.shouldShow(detailLevel)) {
            return;
        }

        var warning = ExpirationWarningRenderer.renderWarning(spec, status);
        if (!warning.isEmpty()) {
            reporter.reportWarn(warning.title(), warning.body());
        }
    }

    private static final LatencySummaryRenderer latencyRenderer = new LatencySummaryRenderer();

    /**
     * Appends per-dimension breakdown to the verdict when both dimensions are asserted.
     */
    void appendDimensionBreakdown(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        Optional<FunctionalDimension> func = verdict.functional();
        Optional<LatencyDimension> lat = verdict.latency();

        if (func.isEmpty() || lat.isEmpty() || lat.get().skipped()) {
            return; // Only show breakdown when both dimensions are asserted
        }

        FunctionalDimension f = func.get();
        LatencyDimension l = lat.get();

        sb.append(PUnitReporter.labelValueLn("Contract:",
                String.format("%d/%d passed",
                        f.successes(), f.successes() + f.failures())));
        sb.append(PUnitReporter.labelValueLn("Latency:",
                String.format("%d/%d within limit",
                        l.dimensionSuccesses(), l.dimensionSuccesses() + l.dimensionFailures())));
    }

    /**
     * Appends latency assertion result to the verdict output if evaluated.
     */
    void appendLatencyResult(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        verdict.latency().ifPresent(lat -> {
            if (!lat.skipped()) {
                latencyRenderer.appendTo(sb, lat);
            }
        });
    }

    /**
     * Appends a covariate comparison block when the verdict is INCONCLUSIVE due to misalignment.
     */
    void appendCovariateComparison(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        CovariateStatus cov = verdict.covariates();
        if (cov.aligned() || cov.baselineProfile().isEmpty()) {
            return;
        }

        sb.append("\n");
        sb.append("Covariate misalignment:\n");
        sb.append("  Baseline:  ").append(formatProfile(cov.baselineProfile())).append("\n");
        sb.append("  Observed:  ").append(formatProfile(cov.observedProfile())).append("\n");

        List<Misalignment> diffs = cov.misalignments();
        if (!diffs.isEmpty()) {
            String diffStr = diffs.stream()
                    .map(m -> m.covariateKey() + " (baseline: " + m.baselineValue()
                            + ", observed: " + m.testValue() + ")")
                    .collect(Collectors.joining(", "));
            sb.append("  Differs:   ").append(diffStr).append("\n");
        }
    }

    private String formatProfile(Map<String, String> profile) {
        return profile.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Appends baseline provenance (source file, measurement date, sample count, threshold).
     */
    void appendBaselineProvenance(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        verdict.statistics().baseline().ifPresent(baseline -> {
            sb.append(PUnitReporter.labelValueLn("Baseline:", baseline.sourceFile()));

            String dateStr = baseline.generatedAt()
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                    .toString();
            sb.append(PUnitReporter.labelValueLn("",
                    String.format("(measured %s, %d samples, minPassRate=%s)",
                            dateStr,
                            baseline.baselineSamples(),
                            RateFormat.format(baseline.derivedThreshold()))));
        });
    }

    /**
     * Appends provenance information to the verdict output if configured.
     */
    void appendProvenance(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        verdict.provenance().ifPresent(prov -> {
            String originName = prov.thresholdOriginName();
            if (originName != null && !originName.equals("UNSPECIFIED")) {
                sb.append(PUnitReporter.labelValueLn("Threshold origin:", originName));
            }
            String contractRef = prov.contractRef();
            if (contractRef != null && !contractRef.isEmpty()) {
                sb.append(PUnitReporter.labelValueLn("Contract:", contractRef));
            }
        });
    }

    /**
     * Appends a compliance evidence sizing note if the test has a compliance context
     * and the sample size is insufficient for compliance-grade evidence.
     */
    void appendComplianceEvidenceNote(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        ExecutionSummary exec = verdict.execution();
        boolean isSmoke = exec.intent() == TestIntent.SMOKE;

        String originName = verdict.provenance()
                .map(SpecProvenance::thresholdOriginName)
                .orElse(null);
        String contractRef = verdict.provenance()
                .map(SpecProvenance::contractRef)
                .orElse(null);
        boolean hasThresholdOrigin = originName != null && !originName.equals("UNSPECIFIED");

        // SMOKE tests with a normative origin get sizing feedback from appendSmokeIntentNote
        if (isSmoke && hasThresholdOrigin) {
            return;
        }
        if (!ComplianceEvidenceEvaluator.hasComplianceContext(originName, contractRef)) {
            return;
        }
        if (!ComplianceEvidenceEvaluator.isUndersized(exec.samplesExecuted(), exec.minPassRate())) {
            return;
        }
        sb.append(PUnitReporter.labelValue("Note:", ComplianceEvidenceEvaluator.SIZING_NOTE));
    }

    /**
     * Appends intent-specific sizing notes for SMOKE tests with normative thresholds.
     */
    void appendSmokeIntentNote(StringBuilder sb, ProbabilisticTestVerdict verdict) {
        ExecutionSummary exec = verdict.execution();
        boolean isSmoke = exec.intent() == TestIntent.SMOKE;
        String originName = verdict.provenance()
                .map(SpecProvenance::thresholdOriginName)
                .orElse(null);
        boolean hasThresholdOrigin = originName != null && !originName.equals("UNSPECIFIED");

        if (!isSmoke || !hasThresholdOrigin) {
            return;
        }
        double target = exec.minPassRate();
        if (Double.isNaN(target) || target <= 0.0 || target >= 1.0) {
            return;
        }
        var result = VerificationFeasibilityEvaluator.evaluate(
                exec.samplesExecuted(), target, exec.resolvedConfidence());
        if (!result.feasible()) {
            sb.append(PUnitReporter.labelValue("Note:",
                    String.format("Sample not sized for verification (N=%d, need %d for %s at %.0f%% confidence).",
                            exec.samplesExecuted(), result.minimumSamples(),
                            RateFormat.format(target), exec.resolvedConfidence() * 100)));
        } else {
            sb.append(PUnitReporter.labelValue("Note:",
                    "Sample is sized for verification. Consider setting intent = VERIFICATION for stronger statistical guarantees."));
        }
    }

    /**
     * Prints a comprehensive statistical explanation for transparent stats mode.
     */
    void printTransparentStatsSummary(ProbabilisticTestVerdict verdict,
                                       TransparentStatsConfig transparentStats,
                                       ExecutionSpecification spec) {
        VerdictTextRenderer renderer = new VerdictTextRenderer(transparentStats);
        var rendered = renderer.renderForReporter(verdict);
        reporter.reportInfo(rendered.title(), rendered.body());

        // Print expiration warning respecting the configured detail level
        printExpirationWarning(spec, transparentStats.detailLevel());
    }
}

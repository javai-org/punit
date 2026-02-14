package org.javai.punit.ptest.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.punit.api.TestIntent;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.SharedBudgetMonitor;
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
import org.javai.punit.statistics.transparent.BaselineData;
import org.javai.punit.statistics.transparent.ConsoleExplanationRenderer;
import org.javai.punit.statistics.transparent.StatisticalExplanation;
import org.javai.punit.statistics.transparent.StatisticalExplanationBuilder;
import org.javai.punit.statistics.transparent.StatisticalExplanationBuilder.CovariateMisalignment;
import org.javai.punit.statistics.transparent.TransparentStatsConfig;

/**
 * Publishes test results via TestReporter and prints console summaries.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Building TestReporter entries with punit.* properties</li>
 *   <li>Printing console summaries with verdict information</li>
 *   <li>Rendering transparent stats explanations</li>
 *   <li>Printing expiration warnings</li>
 * </ul>
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
class ResultPublisher {

    private final PUnitReporter reporter;

    ResultPublisher(PUnitReporter reporter) {
        this.reporter = reporter;
    }

    /**
     * Data needed for publishing results.
     */
    record PublishContext(
            String testName,
            int plannedSamples,
            int samplesExecuted,
            int successes,
            int failures,
            double minPassRate,
            double observedPassRate,
            boolean passed,
            Optional<TerminationReason> terminationReason,
            String terminationDetails,
            long elapsedMs,
            boolean hasMultiplier,
            double appliedMultiplier,
            long timeBudgetMs,
            long tokenBudget,
            long methodTokensConsumed,
            CostBudgetMonitor.TokenMode tokenMode,
            SharedBudgetMonitor classBudget,
            SharedBudgetMonitor suiteBudget,
            ExecutionSpecification spec,
            TransparentStatsConfig transparentStats,
            org.javai.punit.api.ThresholdOrigin thresholdOrigin,
            String contractRef,
            Double confidence,
            BaselineData baseline,
            List<CovariateMisalignment> misalignments,
            String baselineFilename,
            TestIntent intent,
            double resolvedConfidence
    ) {
        /**
         * Backward-compatible constructor that defaults to VERIFICATION intent and 0.95 confidence.
         */
        PublishContext(
                String testName, int plannedSamples, int samplesExecuted,
                int successes, int failures, double minPassRate, double observedPassRate,
                boolean passed, Optional<TerminationReason> terminationReason,
                String terminationDetails, long elapsedMs, boolean hasMultiplier,
                double appliedMultiplier, long timeBudgetMs, long tokenBudget,
                long methodTokensConsumed, CostBudgetMonitor.TokenMode tokenMode,
                SharedBudgetMonitor classBudget, SharedBudgetMonitor suiteBudget,
                ExecutionSpecification spec, TransparentStatsConfig transparentStats,
                org.javai.punit.api.ThresholdOrigin thresholdOrigin, String contractRef,
                Double confidence, BaselineData baseline,
                List<CovariateMisalignment> misalignments, String baselineFilename) {
            this(testName, plannedSamples, samplesExecuted, successes, failures,
                    minPassRate, observedPassRate, passed, terminationReason,
                    terminationDetails, elapsedMs, hasMultiplier, appliedMultiplier,
                    timeBudgetMs, tokenBudget, methodTokensConsumed, tokenMode,
                    classBudget, suiteBudget, spec, transparentStats, thresholdOrigin,
                    contractRef, confidence, baseline, misalignments, baselineFilename,
                    TestIntent.VERIFICATION, 0.95);
        }

        boolean hasTimeBudget() {
            return timeBudgetMs > 0;
        }

        boolean hasTokenBudget() {
            return tokenBudget > 0;
        }

        boolean hasTransparentStats() {
            return transparentStats != null && transparentStats.enabled();
        }

        boolean hasThresholdOrigin() {
            return thresholdOrigin != null
                    && thresholdOrigin != org.javai.punit.api.ThresholdOrigin.UNSPECIFIED;
        }

        boolean hasContractRef() {
            return contractRef != null && !contractRef.isEmpty();
        }

        boolean isSmoke() {
            return intent == TestIntent.SMOKE;
        }

        boolean isVerification() {
            return intent == null || intent == TestIntent.VERIFICATION;
        }
    }

    /**
     * Builds report entries for TestReporter.
     *
     * @param ctx the publish context
     * @return map of punit.* entries
     */
    Map<String, String> buildReportEntries(PublishContext ctx) {
        Map<String, String> entries = new LinkedHashMap<>();

        String terminationReasonStr = ctx.terminationReason()
                .map(Enum::name)
                .orElse(TerminationReason.COMPLETED.name());

        entries.put("punit.samples", String.valueOf(ctx.plannedSamples()));
        entries.put("punit.samplesExecuted", String.valueOf(ctx.samplesExecuted()));
        entries.put("punit.successes", String.valueOf(ctx.successes()));
        entries.put("punit.failures", String.valueOf(ctx.failures()));
        entries.put("punit.minPassRate", String.format("%.4f", ctx.minPassRate()));
        entries.put("punit.observedPassRate", String.format("%.4f", ctx.observedPassRate()));
        entries.put("punit.verdict", ctx.passed() ? "PASS" : "FAIL");
        entries.put("punit.terminationReason", terminationReasonStr);
        entries.put("punit.elapsedMs", String.valueOf(ctx.elapsedMs()));

        // Include multiplier info if one was applied
        if (ctx.hasMultiplier()) {
            entries.put("punit.samplesMultiplier", String.format("%.2f", ctx.appliedMultiplier()));
        }

        // Include method-level budget info
        if (ctx.hasTimeBudget()) {
            entries.put("punit.method.timeBudgetMs", String.valueOf(ctx.timeBudgetMs()));
        }
        if (ctx.hasTokenBudget()) {
            entries.put("punit.method.tokenBudget", String.valueOf(ctx.tokenBudget()));
        }
        entries.put("punit.method.tokensConsumed", String.valueOf(ctx.methodTokensConsumed()));

        if (ctx.tokenMode() != CostBudgetMonitor.TokenMode.NONE) {
            entries.put("punit.tokenMode", ctx.tokenMode().name());
        }

        // Include class-level budget info
        if (ctx.classBudget() != null) {
            SharedBudgetMonitor classBudget = ctx.classBudget();
            if (classBudget.hasTimeBudget()) {
                entries.put("punit.class.timeBudgetMs", String.valueOf(classBudget.getTimeBudgetMs()));
                entries.put("punit.class.elapsedMs", String.valueOf(classBudget.getElapsedMs()));
            }
            if (classBudget.hasTokenBudget()) {
                entries.put("punit.class.tokenBudget", String.valueOf(classBudget.getTokenBudget()));
            }
            entries.put("punit.class.tokensConsumed", String.valueOf(classBudget.getTokensConsumed()));
        }

        // Include suite-level budget info
        if (ctx.suiteBudget() != null) {
            SharedBudgetMonitor suiteBudget = ctx.suiteBudget();
            if (suiteBudget.hasTimeBudget()) {
                entries.put("punit.suite.timeBudgetMs", String.valueOf(suiteBudget.getTimeBudgetMs()));
                entries.put("punit.suite.elapsedMs", String.valueOf(suiteBudget.getElapsedMs()));
            }
            if (suiteBudget.hasTokenBudget()) {
                entries.put("punit.suite.tokenBudget", String.valueOf(suiteBudget.getTokenBudget()));
            }
            entries.put("punit.suite.tokensConsumed", String.valueOf(suiteBudget.getTokensConsumed()));
        }

        // Include expiration status
        if (ctx.spec() != null) {
            ExpirationStatus expirationStatus = ExpirationEvaluator.evaluate(ctx.spec());
            entries.putAll(ExpirationReportPublisher.buildProperties(ctx.spec(), expirationStatus));
        }

        return entries;
    }

    /**
     * Prints a summary message to the console for visibility.
     *
     * @param ctx the publish context
     */
    void printConsoleSummary(PublishContext ctx) {
        // If transparent stats mode is enabled, render the full statistical explanation
        if (ctx.hasTransparentStats()) {
            printTransparentStatsSummary(ctx);
            return;
        }

        // Check if termination was due to budget exhaustion
        boolean isBudgetExhausted = ctx.terminationReason()
                .map(TerminationReason::isBudgetExhaustion)
                .orElse(false);

        String intentLabel = ctx.intent() != null ? ctx.intent().name() : "VERIFICATION";
        String title = (ctx.passed() ? "VERDICT: PASS" : "VERDICT: FAIL") + " (" + intentLabel + ")";
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.testName()).append("\n\n");

        if (ctx.passed()) {
            sb.append(PUnitReporter.labelValueLn("Observed pass rate:",
                    String.format("%s (%d/%d) >= required: %s",
                            RateFormat.format(ctx.observedPassRate()),
                            ctx.successes(), ctx.samplesExecuted(),
                            RateFormat.format(ctx.minPassRate()))));
        } else if (isBudgetExhausted) {
            sb.append(PUnitReporter.labelValueLn("Samples executed:",
                    String.format("%d of %d (budget exhausted)", ctx.samplesExecuted(), ctx.plannedSamples())));
            sb.append(PUnitReporter.labelValueLn("Pass rate:",
                    String.format("%s (%d/%d), required: %s",
                            RateFormat.format(ctx.observedPassRate()),
                            ctx.successes(), ctx.samplesExecuted(),
                            RateFormat.format(ctx.minPassRate()))));
        } else {
            sb.append(PUnitReporter.labelValueLn("Observed pass rate:",
                    String.format("%s (%d/%d) < required: %s",
                            RateFormat.format(ctx.observedPassRate()),
                            ctx.successes(), ctx.samplesExecuted(),
                            RateFormat.format(ctx.minPassRate()))));
        }

        // Append provenance if configured
        appendProvenance(sb, ctx);

        // Append termination details
        ctx.terminationReason()
                .filter(r -> r != TerminationReason.COMPLETED)
                .ifPresent(r -> {
                    sb.append(PUnitReporter.labelValueLn("Termination:", r.getDescription()));
                    String details = ctx.terminationDetails();
                    if (details != null && !details.isEmpty()) {
                        sb.append(PUnitReporter.labelValueLn("Details:", details));
                    }
                    if (r == TerminationReason.IMPOSSIBILITY) {
                        int required = (int) Math.ceil(ctx.plannedSamples() * ctx.minPassRate());
                        int remaining = ctx.plannedSamples() - ctx.samplesExecuted();
                        int maxPossible = ctx.successes() + remaining;
                        sb.append(PUnitReporter.labelValueLn("Analysis:",
                                String.format("Needed %d successes, maximum possible is %d", required, maxPossible)));
                    }
                });

        sb.append(PUnitReporter.labelValue("Elapsed:", ctx.elapsedMs() + "ms"));

        // Append notes (with blank line separator)
        StringBuilder notes = new StringBuilder();
        appendComplianceEvidenceNote(notes, ctx);
        appendSmokeIntentNote(notes, ctx);
        if (!notes.isEmpty()) {
            sb.append("\n\n").append(notes);
        }

        reporter.reportInfo(title, sb.toString());

        // Print expiration warning if applicable (summary mode defaults to VERBOSE)
        TransparentStatsConfig.DetailLevel detailLevel = ctx.transparentStats() != null
                ? ctx.transparentStats().detailLevel()
                : TransparentStatsConfig.DetailLevel.VERBOSE;
        printExpirationWarning(ctx.spec(), detailLevel);
    }

    /**
     * Prints an expiration warning if the baseline is expired or expiring.
     *
     * @param spec the execution specification (may be null)
     * @param detailLevel the detail level controlling which warnings are shown
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

    /**
     * Appends provenance information to the verdict output if configured.
     */
    void appendProvenance(StringBuilder sb, PublishContext ctx) {
        if (ctx.hasThresholdOrigin()) {
            sb.append(PUnitReporter.labelValueLn("Threshold origin:", ctx.thresholdOrigin().name()));
        }
        if (ctx.hasContractRef()) {
            sb.append(PUnitReporter.labelValueLn("Contract:", ctx.contractRef()));
        }
    }

    /**
     * Appends a compliance evidence sizing note if the test has a compliance context
     * and the sample size is insufficient for compliance-grade evidence.
     *
     * <p>This note appears in summary (non-transparent-stats) mode. In transparent
     * stats mode, the equivalent information appears as a caveat in the
     * statistical explanation.
     */
    void appendComplianceEvidenceNote(StringBuilder sb, PublishContext ctx) {
        // SMOKE tests with a normative origin get sizing feedback from appendSmokeIntentNote,
        // which includes specific N, N_min, target, and confidence — skip the less detailed note.
        if (ctx.isSmoke() && ctx.hasThresholdOrigin()) {
            return;
        }
        String originName = ctx.thresholdOrigin() != null ? ctx.thresholdOrigin().name() : null;
        if (!ComplianceEvidenceEvaluator.hasComplianceContext(originName, ctx.contractRef())) {
            return;
        }
        if (!ComplianceEvidenceEvaluator.isUndersized(ctx.samplesExecuted(), ctx.minPassRate())) {
            return;
        }
        sb.append(PUnitReporter.labelValue("Note:", ComplianceEvidenceEvaluator.SIZING_NOTE));
    }

    /**
     * Appends intent-specific sizing notes for SMOKE tests with normative thresholds.
     *
     * <p>When a SMOKE test has a normative threshold origin (SLA/SLO/POLICY), PUnit
     * checks whether the sample size would be sufficient for VERIFICATION:
     * <ul>
     *   <li>Undersized → notes that sample is not sized for verification</li>
     *   <li>Sized → hints that the test could use intent = VERIFICATION</li>
     * </ul>
     */
    void appendSmokeIntentNote(StringBuilder sb, PublishContext ctx) {
        if (!ctx.isSmoke() || !ctx.hasThresholdOrigin()) {
            return;
        }
        // Only evaluate feasibility when target is valid for the evaluator
        double target = ctx.minPassRate();
        if (Double.isNaN(target) || target <= 0.0 || target >= 1.0) {
            return;
        }
        var result = VerificationFeasibilityEvaluator.evaluate(
                ctx.samplesExecuted(), target, ctx.resolvedConfidence());
        if (!result.feasible()) {
            sb.append(PUnitReporter.labelValue("Note:",
                    String.format("Sample not sized for verification (N=%d, need %d for %s at %.0f%% confidence).",
                            ctx.samplesExecuted(), result.minimumSamples(),
                            RateFormat.format(target), ctx.resolvedConfidence() * 100)));
        } else {
            sb.append(PUnitReporter.labelValue("Note:",
                    "Sample is sized for verification. Consider setting intent = VERIFICATION for stronger statistical guarantees."));
        }
    }

    /**
     * Prints a comprehensive statistical explanation for transparent stats mode.
     */
    void printTransparentStatsSummary(PublishContext ctx) {
        StatisticalExplanationBuilder builder = new StatisticalExplanationBuilder();

        String thresholdOriginName = ctx.thresholdOrigin() != null 
                ? ctx.thresholdOrigin().name() 
                : "UNSPECIFIED";

        StatisticalExplanation explanation;
        boolean hasSelectedBaseline = ctx.spec() != null;
        BaselineData baseline = hasSelectedBaseline ? ctx.baseline() : BaselineData.empty();

        boolean isSmoke = ctx.isSmoke();

        if (hasSelectedBaseline && baseline != null && baseline.hasEmpiricalData()) {
            // Spec-driven mode: threshold derived from baseline
            explanation = builder.build(
                    ctx.testName(),
                    ctx.samplesExecuted(),
                    ctx.successes(),
                    baseline,
                    ctx.minPassRate(),
                    ctx.passed(),
                    ctx.confidence() != null ? ctx.confidence() : 0.95,
                    thresholdOriginName,
                    ctx.contractRef(),
                    ctx.misalignments(),
                    isSmoke
            );
        } else {
            // Inline threshold mode (no baseline spec)
            explanation = builder.buildWithInlineThreshold(
                    ctx.testName(),
                    ctx.samplesExecuted(),
                    ctx.successes(),
                    ctx.minPassRate(),
                    ctx.passed(),
                    thresholdOriginName,
                    ctx.contractRef(),
                    isSmoke
            );
        }

        // Render and print
        ConsoleExplanationRenderer renderer = new ConsoleExplanationRenderer(ctx.transparentStats());
        var rendered = renderer.renderForReporter(explanation);
        reporter.reportInfo(rendered.title(), rendered.body());

        // Print expiration warning respecting the configured detail level
        printExpirationWarning(ctx.spec(), ctx.transparentStats().detailLevel());
    }
}


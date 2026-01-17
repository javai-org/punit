package org.javai.punit.ptest.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.SharedBudgetMonitor;
import org.javai.punit.model.ExpirationStatus;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.spec.expiration.ExpirationEvaluator;
import org.javai.punit.spec.expiration.ExpirationReportPublisher;
import org.javai.punit.spec.expiration.ExpirationWarningRenderer;
import org.javai.punit.spec.expiration.WarningLevel;
import org.javai.punit.spec.model.ExecutionSpecification;
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
            String baselineFilename
    ) {
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

        String title = ctx.passed() ? "VERDICT: PASS" : "VERDICT: FAIL";
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.testName()).append("\n");

        if (ctx.passed()) {
            sb.append(String.format("Observed pass rate: %.1f%% (%d/%d) >= min pass rate: %.1f%%",
                    ctx.observedPassRate() * 100,
                    ctx.successes(),
                    ctx.samplesExecuted(),
                    ctx.minPassRate() * 100));
        } else if (isBudgetExhausted) {
            sb.append(String.format("Samples executed: %d of %d (budget exhausted before completion)%n",
                    ctx.samplesExecuted(),
                    ctx.plannedSamples()));
            sb.append(String.format("Pass rate at termination: %.1f%% (%d/%d), required: %.1f%%",
                    ctx.observedPassRate() * 100,
                    ctx.successes(),
                    ctx.samplesExecuted(),
                    ctx.minPassRate() * 100));
        } else {
            sb.append(String.format("Observed pass rate: %.1f%% (%d/%d) < min pass rate: %.1f%%",
                    ctx.observedPassRate() * 100,
                    ctx.successes(),
                    ctx.samplesExecuted(),
                    ctx.minPassRate() * 100));
        }

        // Append provenance if configured
        appendProvenance(sb, ctx);

        ctx.terminationReason()
                .filter(r -> r != TerminationReason.COMPLETED)
                .ifPresent(r -> {
                    sb.append(String.format("%nTermination: %s", r.getDescription()));
                    String details = ctx.terminationDetails();
                    if (details != null && !details.isEmpty()) {
                        sb.append(String.format("%nDetails: %s", details));
                    }
                    // For IMPOSSIBILITY, show what was needed
                    if (r == TerminationReason.IMPOSSIBILITY) {
                        int required = (int) Math.ceil(ctx.plannedSamples() * ctx.minPassRate());
                        int remaining = ctx.plannedSamples() - ctx.samplesExecuted();
                        int maxPossible = ctx.successes() + remaining;
                        sb.append(String.format("%nAnalysis: Needed %d successes, maximum possible is %d",
                                required, maxPossible));
                    }
                });

        sb.append(String.format("%nElapsed: %dms", ctx.elapsedMs()));
        reporter.reportInfo(title, sb.toString());

        // Print expiration warning if applicable
        printExpirationWarning(ctx.spec(), ctx.hasTransparentStats());
    }

    /**
     * Prints an expiration warning if the baseline is expired or expiring.
     */
    void printExpirationWarning(ExecutionSpecification spec, boolean verbose) {
        if (spec == null) {
            return;
        }

        ExpirationStatus status = ExpirationEvaluator.evaluate(spec);
        if (!status.requiresWarning()) {
            return;
        }

        WarningLevel level = WarningLevel.forStatus(status);
        if (level == null || !level.shouldShow(verbose)) {
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
            sb.append(String.format("  Threshold origin: %s%n", ctx.thresholdOrigin().name()));
        }
        if (ctx.hasContractRef()) {
            sb.append(String.format("  Contract ref: %s%n", ctx.contractRef()));
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
                    ctx.misalignments()
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
                    ctx.contractRef()
            );
        }

        // Render and print
        ConsoleExplanationRenderer renderer = new ConsoleExplanationRenderer(ctx.transparentStats());
        var rendered = renderer.renderForReporter(explanation);
        reporter.reportInfo(rendered.title(), rendered.body());

        // Print expiration warning (verbose=true for transparent stats mode)
        printExpirationWarning(ctx.spec(), true);
    }
}


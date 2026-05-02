package org.javai.punit.verdict;

import java.util.List;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.covariate.CovariateAlignment;
import org.javai.punit.api.spec.EngineRunSummary;
import org.javai.punit.api.spec.EvaluatedCriterion;
import org.javai.punit.api.spec.ProbabilisticTestResult;
import org.javai.punit.api.spec.Verdict;
import org.javai.punit.controls.budget.CostBudgetMonitor.TokenMode;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.verdict.ProbabilisticTestVerdictBuilder.LatencyInput;
import org.javai.punit.verdict.ProbabilisticTestVerdictBuilder.MisalignmentInput;

/**
 * Adapts a {@link ProbabilisticTestResult} to the XML-bound
 * {@link ProbabilisticTestVerdict} shape so the
 * {@link org.javai.punit.report.VerdictXmlWriter VerdictXmlWriter} (and
 * the HTML report) can serialise runs to verdict XML.
 *
 * <p>The adapter is a thin field-mapping function — it does not perform
 * statistical computation, judgement, or rendering. It reads the
 * result's components and the supplied {@link RunMetadata}, and
 * delegates to {@link ProbabilisticTestVerdictBuilder} for the heavy
 * lifting (Wilson-score CI, baseline-derivation narrative, verdict-reason
 * synthesis).
 *
 * <h2>Field provenance</h2>
 *
 * <ul>
 *   <li><b>Identity</b> — from {@code RunMetadata}.</li>
 *   <li><b>Execution</b> — from {@code result.engineSummary()}; the
 *       observed pass rate is computed from successes/total. The
 *       threshold is lifted from the first {@code PassRate}
 *       criterion's detail map (key {@code "threshold"}); falls back
 *       to 0.0 when no such criterion exists.</li>
 *   <li><b>Functional dimension</b> — from
 *       {@code engineSummary.successes()} / {@code .failures()}.</li>
 *   <li><b>Latency</b> — from {@code engineSummary.latencyResult()},
 *       observed-only (no per-percentile assertions). Skipped when
 *       {@code sampleCount() == 0}.</li>
 *   <li><b>Statistics</b> — derived by the builder from the execution
 *       summary at the configured confidence.</li>
 *   <li><b>Covariates</b> — from {@code result.covariates()}.</li>
 *   <li><b>Cost</b> — only {@code methodTokensConsumed} populated;
 *       budgets and {@link TokenMode} default to "unlimited / NONE"
 *       because per-method budgets are not surfaced on the result
 *       today.</li>
 *   <li><b>Provenance</b> — threshold origin from the first
 *       {@code PassRate} criterion's {@code "origin"} detail
 *       key; contract reference from
 *       {@link ProbabilisticTestResult#contractRef()}; spec filename
 *       from {@code engineSummary.baselineFilename()}.</li>
 *   <li><b>Termination</b> — the API
 *       {@link org.javai.punit.api.spec.TerminationReason} mapped
 *       to the richer core enum; details kept null.</li>
 *   <li><b>Postcondition failures</b> — pass-through from
 *       {@link ProbabilisticTestResult#failuresByPostcondition()}.</li>
 *   <li><b>Verdict</b> — {@link Verdict#PASS} maps to
 *       {@code passedStatistically=true}; {@link Verdict#FAIL} and
 *       {@link Verdict#INCONCLUSIVE} both map to false (the builder's
 *       covariate-alignment logic decides FAIL vs INCONCLUSIVE).</li>
 * </ul>
 *
 * <h2>What the adapter cannot fill</h2>
 *
 * <p>Some {@link ProbabilisticTestVerdict} components have no analogue
 * on the result today: budget snapshots, pacing configuration, spec
 * expiration, JUnit-pass status. The adapter produces a verdict with
 * those left at their builder defaults. Field-level fidelity is
 * captured in the test suite; renderers are tolerant of absent
 * optional fields.
 */
public final class VerdictAdapter {

    private VerdictAdapter() { }

    /**
     * Build a {@link ProbabilisticTestVerdict} from a result and the
     * per-run metadata.
     *
     * @param result the run's result
     * @param meta   the run metadata captured at the JUnit boundary
     * @return a fully populated verdict, ready for XML/HTML
     *         serialisation
     */
    public static ProbabilisticTestVerdict adapt(
            ProbabilisticTestResult result, RunMetadata meta) {

        EngineRunSummary engine = result.engineSummary();
        ProbabilisticTestVerdictBuilder b = new ProbabilisticTestVerdictBuilder();

        // Envelope
        meta.correlationId().ifPresent(b::correlationId);
        b.environmentMetadata(meta.environmentMetadata());

        // Identity
        b.identity(
                meta.className(),
                meta.methodName(),
                meta.useCaseId().orElse(null));

        // Execution
        double threshold = scanDoubleDetail(result.criterionResults(), "threshold").orElse(0.0);
        double observed = engine.samplesExecuted() == 0
                ? 0.0
                : (double) engine.successes() / (double) engine.samplesExecuted();
        b.execution(
                engine.plannedSamples(),
                engine.samplesExecuted(),
                engine.successes(),
                engine.failures(),
                threshold,
                observed,
                engine.elapsedMs());
        b.intent(result.intent(), engine.confidence());

        // Functional + latency dimensions
        b.functionalDimension(engine.successes(), engine.failures());
        LatencyInput latencyInput = toLatencyInput(engine);
        if (latencyInput != null) {
            b.latencyDimension(latencyInput);
        }

        // Covariates
        CovariateAlignment alignment = result.covariates();
        b.covariateProfiles(
                alignment.baseline().values(),
                alignment.observed().values());
        b.misalignments(toMisalignmentInputs(alignment));

        // No per-method budget surface yet; pass tokensConsumed and
        // zero budgets / NONE token mode.
        b.cost(engine.tokensConsumed(), 0L, 0L, TokenMode.NONE);

        // Provenance
        ThresholdOrigin origin = scanThresholdOrigin(result.criterionResults());
        if (origin != null || result.contractRef().isPresent() || engine.baselineFilename().isPresent()) {
            b.provenance(
                    origin,
                    result.contractRef().orElse(null),
                    engine.baselineFilename().orElse(null));
        }

        // Termination
        b.termination(
                mapTerminationReason(engine.terminationReason()),
                null);

        // Postcondition failure histogram
        b.postconditionFailures(result.failuresByPostcondition());

        // Verdict
        b.passedStatistically(result.verdict() == Verdict.PASS);
        // No JUnit-pass concept on the result; default true (the
        // field is only meaningful inside the JUnit context).
        b.junitPassed(true);

        return b.build();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static java.util.Optional<Double> scanDoubleDetail(
            List<EvaluatedCriterion> evaluated, String key) {
        for (EvaluatedCriterion ec : evaluated) {
            Object v = ec.result().detail().get(key);
            if (v instanceof Number n) {
                return java.util.Optional.of(n.doubleValue());
            }
        }
        return java.util.Optional.empty();
    }

    private static ThresholdOrigin scanThresholdOrigin(List<EvaluatedCriterion> evaluated) {
        for (EvaluatedCriterion ec : evaluated) {
            Object v = ec.result().detail().get("origin");
            if (v instanceof String s && !s.isBlank()) {
                try {
                    return ThresholdOrigin.valueOf(s);
                } catch (IllegalArgumentException ignored) {
                    // Detail map carried a non-enum value; treat as
                    // unspecified rather than throwing.
                    return null;
                }
            }
        }
        return null;
    }

    private static LatencyInput toLatencyInput(EngineRunSummary engine) {
        LatencyResult lat = engine.latencyResult();
        if (lat.sampleCount() == 0) {
            return null;
        }
        // Observed-only latency — no per-percentile assertions, no
        // caveats. Successful samples = engine.successes; dimension
        // failures = 0 (latency isn't asserted as a criterion today).
        return new LatencyInput(
                engine.successes(),
                engine.samplesExecuted(),
                false,                                 // skipped
                null,                                  // skipReason
                lat.p50().toMillis(),
                lat.p90().toMillis(),
                lat.p95().toMillis(),
                lat.p99().toMillis(),
                -1L,                                   // maxMs unavailable
                List.of(),                             // assertions
                List.of(),                             // caveats
                engine.successes(),                    // dimensionSuccesses
                0);                                    // dimensionFailures
    }

    private static List<MisalignmentInput> toMisalignmentInputs(CovariateAlignment alignment) {
        if (alignment.aligned() || alignment.mismatches().isEmpty()) {
            return List.of();
        }
        return alignment.mismatches().stream()
                .map(m -> new MisalignmentInput(
                        m.covariateKey(),
                        m.baseline() == null ? "" : m.baseline(),
                        m.observed() == null ? "" : m.observed()))
                .toList();
    }

    private static TerminationReason mapTerminationReason(
            org.javai.punit.api.spec.TerminationReason source) {
        return switch (source) {
            case COMPLETED -> TerminationReason.COMPLETED;
            case TIME_BUDGET -> TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED;
            case TOKEN_BUDGET -> TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED;
        };
    }
}

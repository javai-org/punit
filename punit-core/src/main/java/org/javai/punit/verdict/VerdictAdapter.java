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
 *   <li><b>Verdict</b> — the criterion's three-state
 *       {@link Verdict} flows straight through to the builder via
 *       {@link ProbabilisticTestVerdictBuilder#criterionVerdict}. The
 *       builder then derives the {@link PUnitVerdict} consulting both
 *       the criterion verdict and the run-level overrides
 *       (covariate misalignment and budget exhaustion both force
 *       INCONCLUSIVE regardless of what the criterion concluded).
 *       This preserves a non-covariate INCONCLUSIVE — no baseline,
 *       sample-size violation, identity mismatch — through to the
 *       rendered verdict, per the RP01 invariant that "the verdict
 *       is consistent with the statistical analysis."</li>
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
        b.criterionVerdict(result.verdict());
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
        // LT01: only samples whose contract evaluated to Outcome.ok
        // contribute to the percentiles. Emit the descriptive
        // dimension whenever ≥ 1 sample passed, independent of LT04
        // activation; threshold/verdict sub-fields stay LT04-gated
        // and are populated separately when assertions are configured.
        // Each percentile is set to -1L (the "unavailable" sentinel)
        // when contributingSamples is below the LT01 minimum-samples
        // threshold for that percentile.
        LatencyResult lat = engine.passingLatencyResult();
        if (lat.sampleCount() == 0) {
            return null;
        }
        int contributing = engine.successes();
        return new LatencyInput(
                contributing,                          // contributing (passing) samples
                engine.samplesExecuted(),              // total samples
                false,                                 // skipped (LT04 concept; descriptive emitted regardless)
                null,                                  // skipReason
                msIfEmittable("p50", lat.p50(), contributing),
                msIfEmittable("p90", lat.p90(), contributing),
                msIfEmittable("p95", lat.p95(), contributing),
                msIfEmittable("p99", lat.p99(), contributing),
                -1L,                                   // maxMs unavailable
                List.of(),                             // assertions (LT04-gated; populated separately when active)
                List.of(),                             // caveats
                contributing,                          // dimensionSuccesses
                0);                                    // dimensionFailures
    }

    /**
     * Returns {@code duration.toMillis()} when the contributing-sample
     * count meets the LT01 minimum-samples threshold for the named
     * percentile; otherwise returns {@code -1L} to signal "not
     * computed reliably." Renderers and serialisers treat {@code -1L}
     * as the omit-percentile sentinel.
     */
    private static long msIfEmittable(String label, java.time.Duration duration, int contributingSamples) {
        return org.javai.punit.engine.output.LatencySection
                .isPercentileEmittable(label, contributingSamples)
                ? duration.toMillis()
                : -1L;
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

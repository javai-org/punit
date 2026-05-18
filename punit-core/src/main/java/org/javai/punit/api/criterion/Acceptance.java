package org.javai.punit.api.criterion;

import java.util.List;

import org.javai.punit.api.PercentileKey;
import org.javai.punit.api.ThresholdOrigin;

/**
 * Acceptance-mode factories — the starting point for every
 * {@link CriterionDecl}. Each factory expresses how a criterion
 * resolves to a verdict: a contractual threshold to be met
 * ({@code meeting}), a baseline-derived threshold to be matched
 * ({@code empirical}), or zero tolerance for failed samples
 * ({@code zeroTolerance}).
 *
 * <p>The author then chains
 * {@link CriterionDecl#where(String, java.util.function.Predicate)
 * .where(...)},
 * {@link CriterionDecl#contractRef(String) .contractRef(...)},
 * {@link CriterionDecl#atConfidence(double) .atConfidence(...)} etc.
 * to refine the declaration.
 *
 * <p>Statically imported, the call site reads {@code meeting(0.85, SLA)}
 * — not {@code Acceptance.meeting(...)}. The class is a container for
 * autocomplete navigation; the static import is the canonical
 * idiom:
 *
 * <pre>{@code
 * import static org.javai.punit.api.criterion.Acceptance.*;
 * }</pre>
 *
 * <p>Functional vs latency factories share two names (
 * {@code empirical}, {@code meeting}); the compiler picks the right
 * overload from argument shape:
 *
 * <table>
 *   <caption>Acceptance factory overloads</caption>
 *   <tr><th>Call</th><th>Returns</th><th>Meaning</th></tr>
 *   <tr><td>{@code empirical()}</td><td>{@link CriterionDecl}</td><td>functional empirical</td></tr>
 *   <tr><td>{@code empirical(PercentileKey, PercentileKey...)}</td><td>{@link LatencyDecl}</td><td>empirical latency</td></tr>
 *   <tr><td>{@code meeting(double, ThresholdOrigin)}</td><td>{@link CriterionDecl}</td><td>contractual pass-rate</td></tr>
 *   <tr><td>{@code meeting(ThresholdOrigin)}</td><td>{@link LatencyDecl}</td><td>contractual latency (chain {@code .ceiling(...)})</td></tr>
 * </table>
 */
public final class Acceptance {

    private Acceptance() { /* utility class */ }

    /**
     * Statistical, contractual: PASS iff observed pass-rate ≥
     * {@code rate}. The threshold is declared explicitly with a
     * non-empirical origin (SLA / SLO / POLICY).
     */
    public static <O> CriterionDecl<O> meeting(double rate, ThresholdOrigin origin) {
        return new CriterionDecl<>(CriterionPosture.meeting(rate, origin), List.of());
    }

    /**
     * Statistical, empirical: threshold derived from the resolved
     * baseline at evaluate time via the Wilson-lower-bound procedure.
     */
    public static <O> CriterionDecl<O> empirical() {
        return new CriterionDecl<>(CriterionPosture.empirical(), List.of());
    }

    /**
     * Explicit zero-tolerance: any failed sample fails the criterion.
     * The run classifies SMOKE per methodology. Does not compose with
     * {@code .atConfidence(...)}.
     */
    public static <O> CriterionDecl<O> zeroTolerance(ThresholdOrigin origin) {
        return new CriterionDecl<>(CriterionPosture.zeroTolerance(origin), List.of());
    }

    /**
     * Latency, empirical: per-percentile thresholds are derived from
     * the resolved baseline at evaluate time. Recommended over the
     * contractual shape when environments (CI, staging, prod) have
     * different latency envelopes — per-covariate baselines capture
     * each envelope without an explicit override.
     *
     * <pre>{@code
     * Acceptance.<Receipt>empirical(P95, P99)
     *         .name("latency-has-not-degraded");
     * }</pre>
     */
    public static <O> LatencyDecl<O> empirical(PercentileKey first, PercentileKey... rest) {
        return LatencyDecl.empirical(first, rest);
    }

    /**
     * Latency, contractual: per-percentile ceilings are declared on the
     * decl via {@link LatencyDecl#ceiling}. The threshold is the
     * supplied duration; the origin is the supplied non-empirical
     * origin.
     *
     * <pre>{@code
     * Acceptance.<Receipt>meeting(SLA)
     *         .ceiling(P95, Duration.ofMillis(500))
     *         .name("latency-p95")
     *         .contractRef("Payment Provider SLA v2.3 §4.2");
     * }</pre>
     *
     * <p>Best paired with the test-side override mechanism (see
     * {@code DIR-CRITERIA-OVERRIDE-punit}) so a single fixed-SLA
     * contract can run across environments with different operating
     * envelopes. Until override lands, prefer
     * {@link #empirical(PercentileKey, PercentileKey...) the empirical
     * shape} for environment-dependent services.
     */
    public static <O> LatencyDecl<O> meeting(ThresholdOrigin origin) {
        return LatencyDecl.contractual(origin);
    }
}

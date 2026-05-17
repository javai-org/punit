package org.javai.punit.api.criterion;

import java.util.List;

import org.javai.punit.api.ThresholdOrigin;

/**
 * Posture factories — the starting point for every
 * {@link CriterionDecl}.
 *
 * <p>Each factory returns a {@code CriterionDecl<O>} whose
 * {@link CriterionPosture} is the corresponding verdict-producing
 * strategy. The author then chains
 * {@link CriterionDecl#where(String, java.util.function.Predicate)
 * .where(...)},
 * {@link CriterionDecl#contractRef(String) .contractRef(...)},
 * {@link CriterionDecl#atConfidence(double) .atConfidence(...)} etc.
 * to refine the declaration.
 *
 * <p>Statically imported, the call site reads {@code meeting(0.85, SLA)}
 * — not {@code Posture.meeting(...)}. The class is a container for
 * autocomplete navigation; the static import is the canonical
 * idiom:
 *
 * <pre>{@code
 * import static org.javai.punit.api.criterion.Posture.*;
 * }</pre>
 */
public final class Posture {

    private Posture() { /* utility class */ }

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
}

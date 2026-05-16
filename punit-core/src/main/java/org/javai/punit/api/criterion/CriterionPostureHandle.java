package org.javai.punit.api.criterion;

import java.util.Objects;

import org.javai.punit.api.ThresholdOrigin;

/**
 * Fluent posture-setter returned by
 * {@link CriteriaBuilder#addCriterion(String, java.util.function.Consumer) addCriterion},
 * {@link CriteriaBuilder#addTransformingCriterion(String, java.util.function.Function, java.util.function.Consumer)
 *  addTransformingCriterion}, and {@link CriteriaBuilder#add(Criterion) add}.
 * Mutates the posture of the just-added criterion in the builder.
 *
 * <p>Idiomatic usage:
 *
 * <pre>{@code
 * b.addCriterion("valid-json", pb -> pb.ensure(...))
 *         .meeting(0.85, ThresholdOrigin.SLA);
 *
 * b.addCriterion("pii-redacted", pb -> pb.ensure(...))
 *         .meeting(0.999, ThresholdOrigin.POLICY)
 *         .atConfidence(0.99);
 *
 * b.addCriterion("response-not-empty", pb -> pb.ensure(...))
 *         .zeroTolerance(ThresholdOrigin.POLICY);
 *
 * // Un-postured — no terminal call. The criterion is committed at
 * // addCriterion(...) and keeps the implicit zero-tolerance posture.
 * b.addCriterion("response-time-marker", pb -> pb.ensure(...));
 * }</pre>
 *
 * <p>The criterion is committed to the builder at the {@code add*}
 * call; the handle modifies the already-added criterion's posture.
 * There is no {@code .commit()} ceremony, and no way for a forgotten
 * terminal to turn a criterion into a no-op.
 *
 * <p>Order: posture method ({@code .meeting / .empirical /
 * .zeroTolerance}) first; {@code .atConfidence(...)} after. Calling
 * a posture method a second time replaces the previously declared
 * posture (including any confidence floor); the last call wins.
 */
public final class CriterionPostureHandle<O> {

    private final CriteriaBuilder<O> builder;
    private final int criterionIndex;

    CriterionPostureHandle(CriteriaBuilder<O> builder, int criterionIndex) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.criterionIndex = criterionIndex;
    }

    /** Statistical, contractual: PASS iff observed pass rate ≥ {@code rate}. */
    public CriterionPostureHandle<O> meeting(double rate, ThresholdOrigin origin) {
        builder.setPostureAt(criterionIndex, CriterionPosture.meeting(rate, origin));
        return this;
    }

    /** Statistical, empirical: threshold derived from the resolved baseline at evaluate time. */
    public CriterionPostureHandle<O> empirical() {
        builder.setPostureAt(criterionIndex, CriterionPosture.empirical());
        return this;
    }

    /**
     * Explicit zero-tolerance: any failed sample fails the criterion.
     * Does not compose with {@link #atConfidence(double)} — the
     * statistical math is undefined at the threshold boundary.
     * The run classifies SMOKE per the methodology rule.
     */
    public CriterionPostureHandle<O> zeroTolerance(ThresholdOrigin origin) {
        builder.setPostureAt(criterionIndex, CriterionPosture.zeroTolerance(origin));
        return this;
    }

    /**
     * Set a per-criterion confidence floor that the run cannot
     * loosen. Must be called after a statistical posture method
     * ({@code .meeting} or {@code .empirical}); throws
     * {@link IllegalStateException} on a zero-tolerance posture
     * (explicit or implicit).
     */
    public CriterionPostureHandle<O> atConfidence(double confidence) {
        CriterionPosture current = builder.postureAt(criterionIndex);
        builder.setPostureAt(criterionIndex, current.withConfidenceFloor(confidence));
        return this;
    }
}

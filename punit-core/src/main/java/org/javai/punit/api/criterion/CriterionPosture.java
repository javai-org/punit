package org.javai.punit.api.criterion;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import org.javai.punit.api.ThresholdOrigin;

/**
 * The methodology criterion's run-time *commitment*: what counts as
 * acceptable, optionally how confidently to evaluate it. Authored on
 * the criterion via the {@link CriteriaBuilder} convenience methods
 * (see {@code CriteriaBuilder.addCriterion(...).meeting(...)} and
 * variants); consumed by the framework's evaluation path when it
 * computes a per-criterion verdict from the run's sample counts.
 *
 * <p>Three kinds map to the three posture methods on the criterion-
 * declaration handle, plus an implicit kind for criteria that
 * declared no posture at all:
 *
 * <ul>
 *   <li>{@link Kind#STATISTICAL_CONTRACTUAL} — explicit contractual
 *       threshold via {@code .meeting(rate, origin)}. Carries the
 *       rate and origin; carries the confidence iff
 *       {@code .atConfidence(c)} was called.</li>
 *   <li>{@link Kind#STATISTICAL_EMPIRICAL} — empirical threshold via
 *       {@code .empirical()}. The threshold is derived from the
 *       baseline at evaluate time; the {@code origin} is
 *       {@link ThresholdOrigin#EMPIRICAL}.</li>
 *   <li>{@link Kind#ZERO_TOLERANCE} — explicit zero-tolerance via
 *       {@code .zeroTolerance(origin)}. Threshold is implicitly 1.0;
 *       the run classifies SMOKE per the methodology rule
 *       (zero-tolerance is not statistically verifiable).
 *       Composition with {@code .atConfidence(...)} is rejected at
 *       build time.</li>
 *   <li>{@link Kind#IMPLICIT_ZERO_TOLERANCE} — no posture method
 *       called on the criterion declaration. Equivalent in every
 *       respect to {@code .zeroTolerance(POLICY)} once the implicit
 *       default is active (directive step 3).</li>
 * </ul>
 *
 * <p>An instance is immutable; the static factory methods produce
 * the four kinds.
 */
public final class CriterionPosture {

    /** The four posture kinds. */
    public enum Kind {
        /** {@code .meeting(rate, origin)} — explicit numeric target. */
        STATISTICAL_CONTRACTUAL,
        /** {@code .empirical()} — threshold derived from baseline. */
        STATISTICAL_EMPIRICAL,
        /** {@code .zeroTolerance(origin)} — explicit binary commitment. */
        ZERO_TOLERANCE,
        /** No posture method called — implicit zero-tolerance (step 3 default). */
        IMPLICIT_ZERO_TOLERANCE
    }

    private static final CriterionPosture IMPLICIT =
            new CriterionPosture(Kind.IMPLICIT_ZERO_TOLERANCE,
                    OptionalDouble.empty(),
                    Optional.empty(),
                    OptionalDouble.empty());

    private final Kind kind;
    private final OptionalDouble threshold;
    private final Optional<ThresholdOrigin> origin;
    private final OptionalDouble confidenceFloor;

    private CriterionPosture(
            Kind kind,
            OptionalDouble threshold,
            Optional<ThresholdOrigin> origin,
            OptionalDouble confidenceFloor) {
        this.kind = kind;
        this.threshold = threshold;
        this.origin = origin;
        this.confidenceFloor = confidenceFloor;
    }

    /** The implicit-zero-tolerance posture — the default for any criterion that declared no posture method. */
    public static CriterionPosture implicit() {
        return IMPLICIT;
    }

    /** Statistical, contractual: {@code .meeting(rate, origin)}. */
    public static CriterionPosture meeting(double rate, ThresholdOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        if (Double.isNaN(rate) || rate < 0.0 || rate >= 1.0) {
            throw new IllegalArgumentException(
                    "meeting(rate, origin): rate must be in [0, 1), got " + rate);
        }
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "meeting(rate, EMPIRICAL) is contradictory — call .empirical() instead");
        }
        return new CriterionPosture(Kind.STATISTICAL_CONTRACTUAL,
                OptionalDouble.of(rate),
                Optional.of(origin),
                OptionalDouble.empty());
    }

    /** Statistical, empirical: {@code .empirical()}. */
    public static CriterionPosture empirical() {
        return new CriterionPosture(Kind.STATISTICAL_EMPIRICAL,
                OptionalDouble.empty(),
                Optional.of(ThresholdOrigin.EMPIRICAL),
                OptionalDouble.empty());
    }

    /** Explicit zero-tolerance: {@code .zeroTolerance(origin)}. */
    public static CriterionPosture zeroTolerance(ThresholdOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "zeroTolerance(EMPIRICAL) is contradictory — zero-tolerance is a binary commitment, not an empirical one");
        }
        return new CriterionPosture(Kind.ZERO_TOLERANCE,
                OptionalDouble.of(1.0),
                Optional.of(origin),
                OptionalDouble.empty());
    }

    /**
     * Returns a copy of this posture with the given confidence floor.
     * Rejects composition with zero-tolerance (explicit or implicit)
     * — the statistical math is undefined at the threshold boundary.
     */
    public CriterionPosture withConfidenceFloor(double confidence) {
        if (kind == Kind.ZERO_TOLERANCE || kind == Kind.IMPLICIT_ZERO_TOLERANCE) {
            throw new IllegalStateException(
                    ".atConfidence(...) cannot compose with zero-tolerance — "
                            + "statistical confidence is undefined at the threshold boundary");
        }
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    ".atConfidence(c) requires c in (0, 1), got " + confidence);
        }
        return new CriterionPosture(kind, threshold, origin, OptionalDouble.of(confidence));
    }

    public Kind kind() {
        return kind;
    }

    /** The contractual target rate when present (statistical-contractual + zero-tolerance), empty otherwise. */
    public OptionalDouble threshold() {
        return threshold;
    }

    public Optional<ThresholdOrigin> origin() {
        return origin;
    }

    /** Per-criterion confidence floor when declared via {@code .atConfidence(...)}, empty otherwise. */
    public OptionalDouble confidenceFloor() {
        return confidenceFloor;
    }

    /** Whether this posture asks for a statistical evaluation. */
    public boolean isStatistical() {
        return kind == Kind.STATISTICAL_CONTRACTUAL || kind == Kind.STATISTICAL_EMPIRICAL;
    }

    /** Whether this posture asks for a binary evaluation (zero-tolerance, explicit or implicit). */
    public boolean isZeroTolerance() {
        return kind == Kind.ZERO_TOLERANCE || kind == Kind.IMPLICIT_ZERO_TOLERANCE;
    }
}

package org.javai.punit.api.criterion;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import org.javai.punit.api.LatencySpec;
import org.javai.punit.api.PercentileKey;
import org.javai.punit.api.ThresholdOrigin;

/**
 * The contract's per-percentile latency commitment. 0..1 per
 * contract; surfaced via the {@link org.javai.punit.api.Contract#latency()}
 * sibling method.
 *
 * <p>Two authoring shapes:
 * <ul>
 *   <li><b>Empirical</b> — {@link #empirical(PercentileKey, PercentileKey...)}.
 *       Per-percentile thresholds are derived from the resolved
 *       baseline at evaluate time.</li>
 *   <li><b>Contractual</b> — {@link #meeting(ThresholdOrigin, Ceiling, Ceiling...)}.
 *       Per-percentile ceilings are declared as {@link Ceiling}
 *       pairs on the call; the origin is the supplied non-empirical
 *       origin.</li>
 * </ul>
 *
 * <p>Idiomatic call sites (with the recommended static imports):
 *
 * <pre>{@code
 * import static org.javai.punit.api.PercentileKey.P95;
 * import static org.javai.punit.api.PercentileKey.P99;
 * import static org.javai.punit.api.ThresholdOrigin.SLA;
 * import static org.javai.punit.api.criterion.LatencyCriterion.ceiling;
 * import static java.time.Duration.ofMillis;
 *
 * // empirical
 * LatencyCriterion.empirical(P95, P99);
 *
 * // contractual
 * LatencyCriterion.meeting(SLA,
 *         ceiling(P95, ofMillis(500)),
 *         ceiling(P99, ofMillis(1500)))
 *     .contractRef("Acme Payment SLA v3.2 §4.2");
 * }</pre>
 *
 * <p>The id of the resulting runtime criterion is fixed at
 * {@value #ID} — 0..1 cardinality is structural, so no name is
 * authored at the call site. Authors who want a project-specific
 * label put it in {@link #contractRef(String)}.
 */
public final class LatencyCriterion {

    /** The fixed id under which a present latency criterion appears in {@code effectiveCriteria()}. */
    public static final String ID = "latency";

    private enum Mode { NONE, EMPIRICAL, CONTRACTUAL }

    private static final LatencyCriterion NONE = new LatencyCriterion(
            Mode.NONE, EnumSet.noneOf(PercentileKey.class),
            LatencySpec.disabled(), ThresholdOrigin.EMPIRICAL, Optional.empty(),
            OptionalDouble.empty());

    private final Mode mode;
    private final EnumSet<PercentileKey> assertedPercentiles;
    private final LatencySpec spec;
    private final ThresholdOrigin origin;
    private final Optional<String> contractRef;
    private final OptionalDouble confidenceFloor;

    private LatencyCriterion(
            Mode mode,
            EnumSet<PercentileKey> assertedPercentiles,
            LatencySpec spec,
            ThresholdOrigin origin,
            Optional<String> contractRef,
            OptionalDouble confidenceFloor) {
        this.mode = mode;
        this.assertedPercentiles = EnumSet.copyOf(assertedPercentiles);
        this.spec = spec;
        this.origin = origin;
        this.contractRef = contractRef;
        this.confidenceFloor = confidenceFloor;
    }

    /** The empty sentinel — returned by the default {@code Contract.latency()}. */
    public static LatencyCriterion none() {
        return NONE;
    }

    /**
     * Empirical latency at the asserted percentiles. Thresholds are
     * derived from the resolved baseline at evaluate time.
     */
    public static LatencyCriterion empirical(PercentileKey first, PercentileKey... rest) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");
        EnumSet<PercentileKey> set = EnumSet.of(first);
        for (PercentileKey k : rest) {
            if (k == null) {
                throw new NullPointerException("asserted percentiles must not contain null");
            }
            set.add(k);
        }
        return new LatencyCriterion(Mode.EMPIRICAL, set, LatencySpec.disabled(),
                ThresholdOrigin.EMPIRICAL, Optional.empty(),
                OptionalDouble.empty());
    }

    /**
     * Contractual latency: a fixed per-percentile ceiling for each
     * {@link Ceiling} pair. {@code origin} must be a non-empirical
     * origin (SLA / SLO / POLICY).
     */
    public static LatencyCriterion meeting(
            ThresholdOrigin origin, Ceiling first, Ceiling... rest) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "meeting(EMPIRICAL, ...) is contradictory — "
                            + "call LatencyCriterion.empirical(...) for empirical latency");
        }
        EnumSet<PercentileKey> asserted = EnumSet.of(first.percentile());
        LatencySpec.Builder b = LatencySpec.builder();
        applyCeiling(b, first);
        for (Ceiling c : rest) {
            Objects.requireNonNull(c, "ceiling");
            if (!asserted.add(c.percentile())) {
                throw new IllegalArgumentException(
                        "duplicate percentile in ceilings: " + c.percentile());
            }
            applyCeiling(b, c);
        }
        return new LatencyCriterion(Mode.CONTRACTUAL, asserted, b.build(),
                origin, Optional.empty(), OptionalDouble.empty());
    }

    /**
     * Static convenience factory for {@link Ceiling} — designed for
     * static import so the call site reads
     * {@code ceiling(P95, ofMillis(500))}.
     */
    public static Ceiling ceiling(PercentileKey percentile, Duration duration) {
        return new Ceiling(percentile, duration);
    }

    /** Attach a human-readable contract reference. */
    public LatencyCriterion contractRef(String ref) {
        Objects.requireNonNull(ref, "ref");
        if (ref.isBlank()) {
            throw new IllegalArgumentException(".contractRef requires a non-blank string");
        }
        if (mode == Mode.NONE) {
            throw new IllegalStateException(
                    ".contractRef cannot be set on LatencyCriterion.none()");
        }
        return new LatencyCriterion(mode, assertedPercentiles, spec, origin,
                Optional.of(ref), confidenceFloor);
    }

    /**
     * Set the per-criterion confidence floor for the binomial
     * order-statistic upper bound (Statistical Companion §12.4.2).
     * Composes only with {@link Mode#EMPIRICAL}; rejected on
     * contractual latency (the ceiling is the threshold; nothing to
     * estimate).
     *
     * <p>Defaults to {@code StatisticalDefaults.DEFAULT_CONFIDENCE}
     * (0.95) when not set explicitly.
     *
     * @throws IllegalStateException on contractual or empty decls
     * @throws IllegalArgumentException when {@code confidence} is
     *         outside {@code (0, 1)}
     */
    public LatencyCriterion atConfidence(double confidence) {
        if (mode != Mode.EMPIRICAL) {
            throw new IllegalStateException(
                    ".atConfidence(...) composes only with empirical latency; "
                            + "the contractual ceiling is the threshold");
        }
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    ".atConfidence(c) requires c in (0, 1), got " + confidence);
        }
        return new LatencyCriterion(mode, assertedPercentiles, spec, origin,
                contractRef, OptionalDouble.of(confidence));
    }

    /** True for a meaningful latency criterion; false for {@link #none()}. */
    public boolean isPresent() {
        return mode != Mode.NONE;
    }

    /**
     * Lower this latency declaration to a runtime
     * {@link Criterion}. Framework-internal; called by
     * {@code Contract.effectiveCriteria()} when this criterion is
     * present.
     *
     * @throws IllegalStateException when called on {@link #none()}
     */
    public <O> Criterion<O> toRuntime() {
        if (mode == Mode.NONE) {
            throw new IllegalStateException(
                    "LatencyCriterion.none() cannot be lowered to a runtime criterion");
        }
        CriterionPosture posture = mode == Mode.EMPIRICAL
                ? CriterionPosture.latencyEmpirical(assertedPercentiles)
                : CriterionPosture.latencyContractual(spec, origin);
        if (confidenceFloor.isPresent()) {
            posture = posture.withConfidenceFloor(confidenceFloor.getAsDouble());
        }
        if (contractRef.isPresent()) {
            posture = posture.withContractRef(contractRef.get());
        }
        return new DirectCriterion<>(ID, List.of(), posture);
    }

    private static void applyCeiling(LatencySpec.Builder b, Ceiling c) {
        long millis = c.duration().toMillis();
        switch (c.percentile()) {
            case P50 -> b.p50Millis(millis);
            case P90 -> b.p90Millis(millis);
            case P95 -> b.p95Millis(millis);
            case P99 -> b.p99Millis(millis);
        }
    }
}

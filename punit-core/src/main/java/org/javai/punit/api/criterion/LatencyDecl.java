package org.javai.punit.api.criterion;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.LatencySpec;
import org.javai.punit.api.PercentileKey;
import org.javai.punit.api.ThresholdOrigin;

/**
 * A latency-criterion declaration — the value behind one latency row
 * in a contract's criteria declaration.
 *
 * <p>Two shapes:
 * <ul>
 *   <li>{@link Mode#EMPIRICAL} — authored via
 *       {@code Acceptance.<O>empirical(P95, P99)}. Per-percentile
 *       thresholds are derived from the resolved baseline at evaluate
 *       time. Recommended when CI / staging / prod have different
 *       latency envelopes and per-environment baselines (resolved via
 *       covariates) capture each envelope.</li>
 *   <li>{@link Mode#CONTRACTUAL} — authored via
 *       {@code Acceptance.<O>meeting(origin).ceiling(P95, ofMillis(500))}.
 *       Per-percentile ceilings are declared on the decl; the origin
 *       is the supplied non-empirical origin. Best paired with the
 *       test-side override mechanism so a single fixed-SLA contract
 *       can run across environments with different operating envelopes.</li>
 * </ul>
 *
 * <p>The {@code <O>} type parameter is phantom — latency is captured
 * by the engine from per-sample timings, not derived from the
 * contract's output value. The parameter exists so a {@code LatencyDecl}
 * is uniformly a {@link Decl} that {@link Criteria#of(Decl[])} can
 * bundle alongside functional declarations.
 *
 * <p>Cardinality: at most one latency criterion per contract. The
 * engine captures one duration per sample; multiple latency
 * declarations would be redundant or contradictory. Enforced at
 * runtime in {@link org.javai.punit.api.Contract#effectiveCriteria()}.
 *
 * @param <O> the contract's per-sample output value type (phantom)
 */
public final class LatencyDecl<O> implements Decl<O> {

    /** Latency authoring shapes. */
    public enum Mode {
        /** {@code Acceptance.<O>empirical(P95, P99...)}. */
        EMPIRICAL,
        /** {@code Acceptance.<O>meeting(origin).ceiling(...)}. */
        CONTRACTUAL
    }

    private final Mode mode;
    private final EnumSet<PercentileKey> assertedPercentiles;
    private final LatencySpec spec;
    private final ThresholdOrigin origin;
    private final Optional<String> name;
    private final Optional<String> contractRef;

    LatencyDecl(Mode mode,
            EnumSet<PercentileKey> assertedPercentiles,
            LatencySpec spec,
            ThresholdOrigin origin,
            Optional<String> name,
            Optional<String> contractRef) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.assertedPercentiles = EnumSet.copyOf(
                Objects.requireNonNull(assertedPercentiles, "assertedPercentiles"));
        this.spec = Objects.requireNonNull(spec, "spec");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.name = Objects.requireNonNull(name, "name");
        this.contractRef = Objects.requireNonNull(contractRef, "contractRef");
    }

    /**
     * Empirical-mode factory — {@code Acceptance.<O>empirical(P95, P99...)}.
     * Framework-internal entry point; authors use the {@link Acceptance}
     * overloads.
     */
    static <O> LatencyDecl<O> empirical(PercentileKey first, PercentileKey... rest) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");
        EnumSet<PercentileKey> set = EnumSet.of(first);
        for (PercentileKey k : rest) {
            if (k == null) {
                throw new NullPointerException("asserted percentiles must not contain null");
            }
            set.add(k);
        }
        return new LatencyDecl<>(Mode.EMPIRICAL, set, LatencySpec.disabled(),
                ThresholdOrigin.EMPIRICAL, Optional.empty(), Optional.empty());
    }

    /**
     * Contractual-mode factory — {@code Acceptance.<O>meeting(origin)}.
     * Returns a decl with no ceilings yet; chain {@link #ceiling} to
     * declare them.
     */
    static <O> LatencyDecl<O> contractual(ThresholdOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        if (origin == ThresholdOrigin.EMPIRICAL) {
            throw new IllegalArgumentException(
                    "meeting(EMPIRICAL) is contradictory — "
                            + "call Acceptance.<O>empirical(P95, ...) for empirical latency");
        }
        return new LatencyDecl<>(Mode.CONTRACTUAL,
                EnumSet.noneOf(PercentileKey.class),
                LatencySpec.disabled(), origin,
                Optional.empty(), Optional.empty());
    }

    @Override
    public Optional<String> name() {
        return name;
    }

    /**
     * Set the criterion's name. Required for K&gt;1 contracts; defaults
     * to {@link Criteria#DEFAULT_CRITERION_ID} for K=1.
     *
     * @throws IllegalStateException if {@code .name(...)} has already been called
     */
    public LatencyDecl<O> name(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".name(...) requires a non-blank name");
        }
        if (this.name.isPresent()) {
            throw new IllegalStateException(
                    ".name(...) already supplied as '" + this.name.get()
                            + "'; cannot reassign to '" + name + "'");
        }
        return new LatencyDecl<>(mode, assertedPercentiles, spec, origin,
                Optional.of(name), contractRef);
    }

    /**
     * Declare a per-percentile ceiling. Composes only with the
     * contractual mode; rejected on empirical decls (their thresholds
     * come from the baseline, not the contract).
     */
    public LatencyDecl<O> ceiling(PercentileKey percentile, Duration duration) {
        Objects.requireNonNull(percentile, "percentile");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(
                    ".ceiling(" + percentile + ", duration) requires duration > 0, got " + duration);
        }
        if (mode != Mode.CONTRACTUAL) {
            throw new IllegalStateException(
                    ".ceiling(...) composes only with the contractual latency mode "
                            + "(Acceptance.<O>meeting(origin)); empirical latency thresholds "
                            + "come from the baseline");
        }
        LatencySpec next = withCeiling(spec, percentile, duration.toMillis());
        EnumSet<PercentileKey> nextAsserted = EnumSet.copyOf(assertedPercentiles);
        nextAsserted.add(percentile);
        return new LatencyDecl<>(mode, nextAsserted, next, origin, name, contractRef);
    }

    /**
     * Attach an audit pointer — the document and clause that justify
     * this criterion's commitment.
     */
    public LatencyDecl<O> contractRef(String ref) {
        Objects.requireNonNull(ref, "ref");
        if (ref.isBlank()) {
            throw new IllegalArgumentException(".contractRef requires a non-blank string");
        }
        return new LatencyDecl<>(mode, assertedPercentiles, spec, origin,
                name, Optional.of(ref));
    }

    /** The decl's mode (empirical or contractual). Framework-internal. */
    public Mode mode() {
        return mode;
    }

    /** The decl's asserted percentiles. Framework-internal. */
    public EnumSet<PercentileKey> assertedPercentiles() {
        return EnumSet.copyOf(assertedPercentiles);
    }

    /** The decl's contractual {@link LatencySpec}; disabled for empirical. */
    public LatencySpec spec() {
        return spec;
    }

    /** The decl's origin. {@link ThresholdOrigin#EMPIRICAL} in empirical mode. */
    public ThresholdOrigin origin() {
        return origin;
    }

    @Override
    public List<Criterion<O>> asList() {
        return List.of(toRuntime(name.orElse(Criteria.DEFAULT_CRITERION_ID)));
    }

    @Override
    public Criterion<O> toRuntime(String id) {
        CriterionPosture posture = mode == Mode.EMPIRICAL
                ? CriterionPosture.latencyEmpirical(assertedPercentiles)
                : CriterionPosture.latencyContractual(spec, origin);
        if (contractRef.isPresent()) {
            posture = posture.withContractRef(contractRef.get());
        }
        return new DirectCriterion<>(id, List.of(), posture);
    }

    private static LatencySpec withCeiling(LatencySpec base, PercentileKey p, long millis) {
        LatencySpec.Builder b = LatencySpec.builder();
        copyExisting(base, b);
        switch (p) {
            case P50 -> b.p50Millis(millis);
            case P90 -> b.p90Millis(millis);
            case P95 -> b.p95Millis(millis);
            case P99 -> b.p99Millis(millis);
        }
        return b.build();
    }

    private static void copyExisting(LatencySpec base, LatencySpec.Builder b) {
        base.p50Millis().ifPresent(b::p50Millis);
        base.p90Millis().ifPresent(b::p90Millis);
        base.p95Millis().ifPresent(b::p95Millis);
        base.p99Millis().ifPresent(b::p99Millis);
    }
}

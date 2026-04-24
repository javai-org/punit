package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.LatencySpec;

/**
 * The latency side of a two-dimensional verdict.
 *
 * <p>{@link #verdict()} reports {@link Verdict#PASS} when every
 * asserted percentile fell at or below its ceiling, and
 * {@link Verdict#FAIL} when at least one percentile exceeded it.
 * When the spec declared no latency thresholds the engine does not
 * emit a {@code LatencyVerdict} at all.
 *
 * @param verdict PASS / FAIL for the latency dimension
 * @param breaches detail for each percentile that exceeded its
 *                 threshold; empty on PASS
 */
public record LatencyVerdict(Verdict verdict, List<PercentileBreach> breaches) {

    public LatencyVerdict {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(breaches, "breaches");
        switch (verdict) {
            case PASS -> {
                if (!breaches.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a PASS latency verdict must carry no breaches");
                }
            }
            case FAIL -> {
                if (breaches.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a FAIL latency verdict must carry at least one breach");
                }
            }
            case INCONCLUSIVE -> throw new IllegalArgumentException(
                    "INCONCLUSIVE is not a valid latency-dimension verdict");
        }
        breaches = List.copyOf(breaches);
    }

    /**
     * Evaluate a {@link LatencySpec} against an observed
     * {@link LatencyResult}. Model-agnostic — callable from any spec
     * type that collects latency alongside its functional data.
     *
     * @return {@link Optional#empty()} when the spec is disabled or
     *         the observation carries no samples; otherwise a
     *         {@link #PASS}- or {@link #FAIL}-valued verdict.
     */
    public static Optional<LatencyVerdict> evaluate(LatencySpec spec, LatencyResult observed) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(observed, "observed");
        if (spec.isDisabled() || observed.sampleCount() == 0) {
            return Optional.empty();
        }
        List<PercentileBreach> breaches = new ArrayList<>();
        spec.p50Millis().ifPresent(t -> checkBreach("p50", t, observed.p50(), breaches));
        spec.p90Millis().ifPresent(t -> checkBreach("p90", t, observed.p90(), breaches));
        spec.p95Millis().ifPresent(t -> checkBreach("p95", t, observed.p95(), breaches));
        spec.p99Millis().ifPresent(t -> checkBreach("p99", t, observed.p99(), breaches));
        Verdict v = breaches.isEmpty() ? Verdict.PASS : Verdict.FAIL;
        return Optional.of(new LatencyVerdict(v, breaches));
    }

    private static void checkBreach(String name, long thresholdMillis,
                                    Duration observed, List<PercentileBreach> out) {
        Duration threshold = Duration.ofMillis(thresholdMillis);
        if (observed.compareTo(threshold) > 0) {
            out.add(new PercentileBreach(name, threshold, observed));
        }
    }
}

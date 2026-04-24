package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Objects;

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
}

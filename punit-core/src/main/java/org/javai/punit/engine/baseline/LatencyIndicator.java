package org.javai.punit.engine.baseline;

import java.util.Objects;

import org.javai.punit.api.LatencyResult;

/**
 * Bundles a passing-only {@link LatencyResult} with the LT01
 * population-indicator counts ({@code contributingSamples},
 * {@code totalSamples}). Carried on a {@link BaselineRecord} so the
 * baseline writer can emit the normative {@code latency:} YAML
 * block from one self-contained value.
 *
 * <p>{@link #empty()} represents the "no passing samples" case;
 * the writer omits the block entirely when this is the value.
 */
public record LatencyIndicator(
        LatencyResult passingPercentiles,
        int contributingSamples,
        int totalSamples) {

    public LatencyIndicator {
        Objects.requireNonNull(passingPercentiles, "passingPercentiles");
        if (contributingSamples < 0) {
            throw new IllegalArgumentException(
                    "contributingSamples must be non-negative, got " + contributingSamples);
        }
        if (totalSamples < contributingSamples) {
            throw new IllegalArgumentException(
                    "totalSamples (" + totalSamples + ") must be >= contributingSamples ("
                            + contributingSamples + ")");
        }
    }

    /** The empty indicator — no passing samples, no block to emit. */
    public static LatencyIndicator empty() {
        return new LatencyIndicator(LatencyResult.empty(), 0, 0);
    }

    /** Whether the indicator carries percentile data worth emitting. */
    public boolean hasData() {
        return contributingSamples > 0;
    }
}

package org.javai.punit.api.typed.spec;

import java.util.Objects;

import org.javai.punit.api.typed.LatencyResult;

/**
 * Latency baseline, read by empirical variants of
 * {@code PercentileLatency}.
 *
 * @param percentiles the baseline's observed p50 / p90 / p95 / p99
 * @param sampleCount the baseline's total invocation count
 */
public record LatencyStatistics(
        LatencyResult percentiles,
        @Override int sampleCount) implements BaselineStatistics {

    public LatencyStatistics {
        Objects.requireNonNull(percentiles, "percentiles");
        if (sampleCount < 0) {
            throw new IllegalArgumentException(
                    "sampleCount must be non-negative, got " + sampleCount);
        }
    }
}

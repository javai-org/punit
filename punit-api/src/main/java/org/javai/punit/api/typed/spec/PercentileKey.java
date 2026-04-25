package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.OptionalLong;

import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.LatencySpec;

/**
 * The percentiles the {@link PercentileLatency} criterion can assert
 * against. Each value knows how to project a {@link LatencySpec}'s
 * per-percentile ceiling and a {@link LatencyResult}'s observed
 * percentile.
 */
public enum PercentileKey {

    P50 {
        @Override public OptionalLong ceilingMillis(LatencySpec spec) { return spec.p50Millis(); }
        @Override public Duration observed(LatencyResult result) { return result.p50(); }
        @Override public String detailKey() { return "p50"; }
    },
    P90 {
        @Override public OptionalLong ceilingMillis(LatencySpec spec) { return spec.p90Millis(); }
        @Override public Duration observed(LatencyResult result) { return result.p90(); }
        @Override public String detailKey() { return "p90"; }
    },
    P95 {
        @Override public OptionalLong ceilingMillis(LatencySpec spec) { return spec.p95Millis(); }
        @Override public Duration observed(LatencyResult result) { return result.p95(); }
        @Override public String detailKey() { return "p95"; }
    },
    P99 {
        @Override public OptionalLong ceilingMillis(LatencySpec spec) { return spec.p99Millis(); }
        @Override public Duration observed(LatencyResult result) { return result.p99(); }
        @Override public String detailKey() { return "p99"; }
    };

    /** The asserted ceiling from a LatencySpec, when that percentile is asserted. */
    public abstract OptionalLong ceilingMillis(LatencySpec spec);

    /** The observed duration from a LatencyResult at this percentile. */
    public abstract Duration observed(LatencyResult result);

    /** The stable short-form key used in {@link CriterionResult#detail()} map keys. */
    public abstract String detailKey();
}

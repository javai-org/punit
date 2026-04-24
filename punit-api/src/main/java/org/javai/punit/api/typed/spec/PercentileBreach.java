package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.Objects;

/**
 * One percentile's breach detail: the percentile identifier that was
 * asserted, the threshold that was declared, and the observed value
 * that exceeded it.
 *
 * <p>A latency verdict of {@link Verdict#FAIL} carries one
 * {@code PercentileBreach} for each asserted percentile that exceeded
 * its threshold; a {@link Verdict#PASS} verdict's breach list is
 * empty.
 *
 * @param percentile the percentile identifier, e.g. {@code "p50"}
 * @param threshold the ceiling that was declared
 * @param observed the value the engine measured
 */
public record PercentileBreach(String percentile, Duration threshold, Duration observed) {

    public PercentileBreach {
        Objects.requireNonNull(percentile, "percentile");
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(observed, "observed");
    }
}

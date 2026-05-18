package org.javai.punit.api.criterion;

import java.time.Duration;
import java.util.Objects;

import org.javai.punit.api.PercentileKey;

/**
 * A per-percentile latency ceiling — the contractual pair
 * {@code (percentile, duration)} consumed by
 * {@link LatencyCriterion#meeting}.
 *
 * <p>Authored most idiomatically via the static helper
 * {@link LatencyCriterion#ceiling(PercentileKey, Duration)} so the
 * call site reads {@code ceiling(P95, ofMillis(500))}.
 *
 * @param percentile the percentile this ceiling applies to
 * @param duration   the per-percentile duration ceiling — strictly
 *                   positive
 */
public record Ceiling(PercentileKey percentile, Duration duration) {

    public Ceiling {
        Objects.requireNonNull(percentile, "percentile");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(
                    "ceiling duration must be > 0, got " + duration);
        }
    }
}

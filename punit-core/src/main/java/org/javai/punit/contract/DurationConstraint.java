package org.javai.punit.contract;

import java.time.Duration;
import java.util.Objects;

/**
 * A constraint on execution duration.
 *
 * <p>Duration constraints are evaluated independently from postconditions,
 * providing a parallel dimension of success/failure. This allows both
 * "was it correct?" and "was it fast enough?" to be answered for every
 * execution, regardless of whether one or both fail.
 *
 * <p>Example:
 * <pre>{@code
 * ServiceContract.<Input, Response>define()
 *     .ensure("Response has content", ...)
 *     .ensureDurationBelow(Duration.ofMillis(500))
 *     .build();
 * }</pre>
 *
 * @param description human-readable description of the constraint
 * @param maxDuration the maximum allowed execution duration
 * @see ServiceContract#durationConstraint()
 * @see DurationResult
 */
public record DurationConstraint(String description, Duration maxDuration) {

    /**
     * Creates a duration constraint.
     *
     * @throws NullPointerException if description or maxDuration is null
     * @throws IllegalArgumentException if maxDuration is not positive
     */
    public DurationConstraint {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(maxDuration, "maxDuration must not be null");
        if (maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("maxDuration must be positive");
        }
    }

    /**
     * Creates a duration constraint with a default description.
     *
     * @param maxDuration the maximum allowed duration
     * @return the constraint
     * @throws NullPointerException if maxDuration is null
     * @throws IllegalArgumentException if maxDuration is not positive
     */
    public static DurationConstraint of(Duration maxDuration) {
        Objects.requireNonNull(maxDuration, "maxDuration must not be null");
        return new DurationConstraint(
                "Duration below " + formatDuration(maxDuration),
                maxDuration);
    }

    /**
     * Creates a duration constraint with a custom description.
     *
     * @param description the constraint description
     * @param maxDuration the maximum allowed duration
     * @return the constraint
     */
    public static DurationConstraint of(String description, Duration maxDuration) {
        return new DurationConstraint(description, maxDuration);
    }

    /**
     * Evaluates this constraint against an actual duration.
     *
     * @param actual the actual execution duration
     * @return the result of the evaluation
     */
    public DurationResult evaluate(Duration actual) {
        Objects.requireNonNull(actual, "actual must not be null");
        boolean passed = actual.compareTo(maxDuration) <= 0;
        return new DurationResult(description, maxDuration, actual, passed);
    }

    static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            return String.format("%.1fm", millis / 60000.0);
        }
    }
}

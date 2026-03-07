package org.javai.punit.contract;

import java.time.Duration;
import java.util.Objects;

/**
 * The result of evaluating a duration constraint.
 *
 * <p>A duration result captures both the constraint parameters and the actual
 * execution time, along with whether the constraint was satisfied. This allows
 * diagnostic output to show the full picture regardless of pass/fail status.
 *
 * @param description the constraint description
 * @param limit the maximum allowed duration
 * @param actual the actual execution duration
 * @param passed whether the constraint was satisfied
 * @see DurationConstraint
 */
public record DurationResult(
        String description,
        Duration limit,
        Duration actual,
        boolean passed
) {

    /**
     * Creates a duration result.
     *
     * @throws NullPointerException if description, limit, or actual is null
     */
    public DurationResult {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(limit, "limit must not be null");
        Objects.requireNonNull(actual, "actual must not be null");
    }

    /**
     * Returns whether this constraint failed.
     *
     * @return true if the actual duration exceeded the limit
     */
    public boolean failed() {
        return !passed;
    }

    /**
     * Returns a human-readable message describing the result.
     *
     * <p>For passed constraints: "Description: 230ms (limit: 500ms)"
     * <p>For failed constraints: "Description: 847ms exceeded limit of 500ms"
     *
     * @return the result message
     */
    public String message() {
        String actualFormatted = DurationConstraint.formatDuration(actual);
        String limitFormatted = DurationConstraint.formatDuration(limit);
        if (passed) {
            return description + ": " + actualFormatted + " (limit: " + limitFormatted + ")";
        }
        return description + ": " + actualFormatted + " exceeded limit of " + limitFormatted;
    }

    /**
     * Returns the failure message for this result.
     *
     * <p>This is equivalent to {@link #message()} but named consistently
     * with {@link PostconditionResult#failureMessage()} for use in
     * combined error reporting.
     *
     * @return the failure message
     */
    public String failureMessage() {
        return message();
    }
}

package org.javai.punit.contract;

import java.util.Objects;
import java.util.Optional;
import org.javai.outcome.Outcome;

/**
 * The result of evaluating a postcondition.
 *
 * <p>A postcondition result wraps an {@link Outcome} which provides:
 * <ul>
 *   <li>Success/failure semantics</li>
 *   <li>A failure reason when applicable</li>
 *   <li>An optional value for derivation results</li>
 * </ul>
 *
 * <h2>Factory Methods</h2>
 * <pre>{@code
 * // Simple pass/fail
 * PostconditionResult.passed("Response not empty");
 * PostconditionResult.failed("Valid JSON", "Parse error at line 5");
 *
 * // With derivation value
 * PostconditionResult.passed("Parse JSON", jsonNode);
 * }</pre>
 *
 * @param description the human-readable description of the postcondition
 * @param outcome the evaluation outcome (success or failure with reason)
 * @see Postcondition
 * @see ServiceContract
 */
public record PostconditionResult(String description, Outcome<?> outcome) {

    /**
     * Creates a postcondition result.
     *
     * @throws NullPointerException if description or outcome is null
     */
    public PostconditionResult {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
    }

    /**
     * Returns true if this postcondition passed.
     *
     * @return true if the outcome is successful
     */
    public boolean passed() {
        return outcome.isOk();
    }

    /**
     * Returns true if this postcondition failed.
     *
     * @return true if the outcome is a failure
     */
    public boolean failed() {
        return outcome.isFail();
    }

    /**
     * Returns the failure reason, if present.
     *
     * @return the failure reason, or empty if passed
     */
    public Optional<String> failureReason() {
        return switch (outcome) {
            case Outcome.Fail<?> fail -> Optional.of(fail.failure().message());
            case Outcome.Ok<?> ignored -> Optional.empty();
        };
    }

    /**
     * Returns a human-readable failure message combining description and reason.
     *
     * <p>Format: "{description}: {reason}" if reason present, otherwise just "{description}".
     *
     * @return the failure message, or just the description if passed
     */
    public String failureMessage() {
        return failureReason()
                .map(reason -> description + ": " + reason)
                .orElse(description);
    }

    // ========== Factory Methods ==========

    /**
     * Creates a passed result with no associated value.
     *
     * @param description the postcondition description
     * @return a passed result
     */
    public static PostconditionResult passed(String description) {
        return new PostconditionResult(description, Outcome.ok());
    }

    /**
     * Creates a passed result with an associated value.
     *
     * <p>This is typically used for derivations where the derived value
     * may be useful for debugging or inspection.
     *
     * @param description the postcondition description
     * @param value the derived or computed value
     * @param <T> the value type
     * @return a passed result with the value
     */
    public static <T> PostconditionResult passed(String description, T value) {
        return new PostconditionResult(description, Outcome.ok(value));
    }

    /**
     * Creates a failed result with a reason.
     *
     * @param description the postcondition description
     * @param reason the failure reason
     * @return a failed result
     */
    public static PostconditionResult failed(String description, String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return new PostconditionResult(description, Outcome.fail(description, reason));
    }

    /**
     * Creates a failed result with a default reason.
     *
     * @param description the postcondition description
     * @return a failed result
     */
    public static PostconditionResult failed(String description) {
        return failed(description, "Postcondition not satisfied");
    }
}

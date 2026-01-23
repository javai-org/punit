package org.javai.punit.contract;

import java.util.Objects;
import java.util.function.Predicate;
import org.javai.outcome.Outcome;

/**
 * A single postcondition (ensure clause) in a service contract.
 *
 * <p>A postcondition consists of:
 * <ul>
 *   <li>A description — human-readable name for the condition</li>
 *   <li>A check function — returns {@code Outcome<Void>} indicating success or failure with reason</li>
 * </ul>
 *
 * <h2>Two Forms</h2>
 * <p>Postconditions support two forms:
 * <ul>
 *   <li><b>Simple</b> — a predicate returning boolean; use {@link #simple(String, Predicate)}</li>
 *   <li><b>Rich</b> — a function returning {@code Outcome<Void>} with failure details; use the constructor</li>
 * </ul>
 *
 * <h2>Lazy Evaluation</h2>
 * <p>Postconditions are stored as functions and evaluated lazily when
 * {@link #evaluate(Object)} is called. This supports early termination
 * strategies and parallel evaluation.
 *
 * <h2>Usage</h2>
 * <p>Postconditions are typically created through the {@link ServiceContract} builder:
 * <pre>{@code
 * .deriving("Valid JSON", this::parseJson)
 *     // Simple form - boolean predicate
 *     .ensure("Has operations array", json -> json.has("operations"))
 *     // Rich form - Outcome<Void> with failure details
 *     .ensure("All operations valid", json -> validateOperations(json))
 * }</pre>
 *
 * @param description the human-readable description
 * @param check the function that evaluates the postcondition, returning success or failure with reason
 * @param <T> the type of value this postcondition evaluates
 * @see ServiceContract
 * @see PostconditionResult
 */
public record Postcondition<T>(String description, PostconditionCheck<T> check) {

    /**
     * Creates a new postcondition with a rich check function.
     *
     * @throws NullPointerException if description or check is null
     * @throws IllegalArgumentException if description is blank
     */
    public Postcondition {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(check, "check must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    /**
     * Creates a postcondition from a simple boolean predicate.
     *
     * <p>Use this for straightforward checks where the failure reason is
     * self-evident from the description. For checks that need to communicate
     * specific failure details, use the constructor with a function returning
     * {@code Outcome<Void>}.
     *
     * @param description the human-readable description
     * @param predicate the condition to evaluate
     * @param <T> the type of value this postcondition evaluates
     * @return a postcondition wrapping the predicate
     */
    public static <T> Postcondition<T> simple(String description, Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        PostconditionCheck<T> check = value ->
                predicate.test(value)
                        ? Outcomes.okVoid()
                        : Outcomes.fail("Postcondition not satisfied");
        return new Postcondition<>(description, check);
    }

    /**
     * Evaluates this postcondition against a value.
     *
     * @param value the value to evaluate against
     * @return the result of evaluation (passed or failed with reason)
     */
    public PostconditionResult evaluate(T value) {
        try {
            Outcome<Void> result = check.check(value);
            return result.isOk()
                    ? PostconditionResult.passed(description)
                    : PostconditionResult.failed(description, Outcomes.failureMessage(result));
        } catch (Exception e) {
            String message = e.getMessage();
            String reason = (message != null && !message.isBlank())
                    ? message
                    : e.getClass().getSimpleName();
            return PostconditionResult.failed(description, reason);
        }
    }

    /**
     * Creates a skipped result for this postcondition.
     *
     * <p>Used when a prerequisite derivation fails and this postcondition
     * cannot be meaningfully evaluated. Skipped is represented as a failure
     * with the skip reason.
     *
     * @param reason the reason for skipping
     * @return a failed result with the skip reason
     */
    public PostconditionResult skip(String reason) {
        return PostconditionResult.failed(description, "Skipped: " + reason);
    }
}

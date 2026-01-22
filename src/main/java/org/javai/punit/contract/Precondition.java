package org.javai.punit.contract;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A single precondition (require clause) in a service contract.
 *
 * <p>A precondition consists of:
 * <ul>
 *   <li>A description — human-readable name for the condition</li>
 *   <li>A predicate — the condition to evaluate against the input</li>
 * </ul>
 *
 * <h2>Eager Evaluation</h2>
 * <p>Preconditions are evaluated eagerly when input is provided to the use case.
 * If any precondition fails, a {@link PreconditionException} is thrown.
 *
 * <h2>Usage</h2>
 * <p>Preconditions are typically created through the {@link ServiceContract} builder:
 * <pre>{@code
 * ServiceContract.define()
 *     .require("Prompt not null", input -> input.prompt() != null)
 *     .require("Temperature in range", input -> input.temp() >= 0 && input.temp() <= 1)
 *     // ...
 * }</pre>
 *
 * @param description the human-readable description
 * @param predicate the condition to evaluate
 * @param <I> the type of input this precondition evaluates
 * @see ServiceContract
 * @see PreconditionException
 */
public record Precondition<I>(String description, Predicate<I> predicate) {

    /**
     * Creates a new precondition.
     *
     * @throws NullPointerException if description or predicate is null
     * @throws IllegalArgumentException if description is blank
     */
    public Precondition {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    /**
     * Checks this precondition against an input value.
     *
     * @param input the input to check
     * @throws PreconditionException if the precondition is not satisfied
     */
    public void check(I input) {
        try {
            if (!predicate.test(input)) {
                throw new PreconditionException(description, input);
            }
        } catch (PreconditionException e) {
            throw e;
        } catch (Exception e) {
            throw new PreconditionException(
                    description + " (evaluation failed: " + e.getMessage() + ")", input);
        }
    }
}

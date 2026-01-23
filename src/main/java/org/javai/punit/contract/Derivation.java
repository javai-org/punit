package org.javai.punit.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.javai.outcome.Outcome;

/**
 * A derivation transforms the raw result into a derived perspective for postcondition evaluation.
 *
 * <p>A derivation consists of:
 * <ul>
 *   <li>A description — the derivation itself is an ensure clause</li>
 *   <li>A function — transforms the raw result into a derived value</li>
 *   <li>Postconditions — conditions to evaluate against the derived value</li>
 * </ul>
 *
 * <h2>Derivation as a Gate</h2>
 * <p>The derivation acts as a gate for its nested postconditions:
 * <ul>
 *   <li>If derivation succeeds → the derivation passes, nested postconditions are evaluated</li>
 *   <li>If derivation fails → the derivation fails, nested postconditions are <b>skipped</b></li>
 * </ul>
 *
 * @param description the description (this becomes a postcondition)
 * @param function the derivation function
 * @param postconditions the postconditions to evaluate on the derived value
 * @param <R> the raw result type
 * @param <D> the derived type
 * @see ServiceContract
 */
public record Derivation<R, D>(
        String description,
        Function<R, Outcome<D>> function,
        List<Postcondition<D>> postconditions
) {

    /**
     * Creates a new derivation.
     *
     * @throws NullPointerException if description, function, or postconditions is null
     * @throws IllegalArgumentException if description is blank
     */
    public Derivation {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(function, "function must not be null");
        Objects.requireNonNull(postconditions, "postconditions must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        postconditions = List.copyOf(postconditions);
    }

    /**
     * Evaluates this derivation against a raw result.
     *
     * <p>If the derivation function succeeds:
     * <ul>
     *   <li>Adds a passed result for the derivation description (with derived value)</li>
     *   <li>Evaluates all nested postconditions</li>
     * </ul>
     *
     * <p>If the derivation function fails:
     * <ul>
     *   <li>Adds a failed result for the derivation description</li>
     *   <li>Skips all nested postconditions</li>
     * </ul>
     *
     * @param result the raw result to derive from
     * @return list of postcondition results
     */
    public List<PostconditionResult> evaluate(R result) {
        List<PostconditionResult> results = new ArrayList<>();

        Outcome<D> derivationOutcome;
        try {
            derivationOutcome = function.apply(result);
        } catch (Exception e) {
            results.add(PostconditionResult.failed(description, e.getMessage()));
            for (Postcondition<D> postcondition : postconditions) {
                results.add(postcondition.skip("Derivation '" + description + "' failed"));
            }
            return results;
        }

        if (derivationOutcome.isOk()) {
            D derivedValue = derivationOutcome.getOrThrow();
            results.add(PostconditionResult.passed(description, derivedValue));
            for (Postcondition<D> postcondition : postconditions) {
                results.add(postcondition.evaluate(derivedValue));
            }
        } else {
            results.add(PostconditionResult.failed(description, Outcomes.failureMessage(derivationOutcome)));
            for (Postcondition<D> postcondition : postconditions) {
                results.add(postcondition.skip("Derivation '" + description + "' failed"));
            }
        }

        return results;
    }
}

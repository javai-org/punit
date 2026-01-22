package org.javai.punit.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A derivation transforms the raw result into a derived perspective for postcondition evaluation.
 *
 * <p>A derivation consists of:
 * <ul>
 *   <li>An optional description — if present, the derivation itself is an ensure clause</li>
 *   <li>A function — transforms the raw result into a derived value</li>
 *   <li>Postconditions — conditions to evaluate against the derived value</li>
 * </ul>
 *
 * <h2>Derivation as a Gate</h2>
 * <p>When a derivation has a description:
 * <ul>
 *   <li>If derivation succeeds → the named ensure passes, nested postconditions are evaluated</li>
 *   <li>If derivation fails → the named ensure fails, nested postconditions are <b>skipped</b></li>
 * </ul>
 *
 * <p>When a derivation has no description (infallible):
 * <ul>
 *   <li>The derivation is assumed to always succeed</li>
 *   <li>Nested postconditions are always evaluated</li>
 * </ul>
 *
 * @param <R> the raw result type
 * @param <D> the derived type
 * @see ServiceContract
 */
public final class Derivation<R, D> {

    private final String description;
    private final Function<R, Outcome<D>> function;
    private final List<Postcondition<D>> postconditions;

    /**
     * Creates a new derivation.
     *
     * @param description the description (null for infallible derivations)
     * @param function the derivation function
     * @param postconditions the postconditions to evaluate on the derived value
     * @throws NullPointerException if function or postconditions is null
     * @throws IllegalArgumentException if description is blank (when non-null)
     */
    Derivation(String description, Function<R, Outcome<D>> function, List<Postcondition<D>> postconditions) {
        if (description != null && description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        Objects.requireNonNull(function, "function must not be null");
        Objects.requireNonNull(postconditions, "postconditions must not be null");
        this.description = description;
        this.function = function;
        this.postconditions = Collections.unmodifiableList(new ArrayList<>(postconditions));
    }

    /**
     * Returns the description of this derivation, or null if infallible.
     *
     * @return the description, or null
     */
    public String description() {
        return description;
    }

    /**
     * Returns whether this derivation has a description (is fallible).
     *
     * @return true if this derivation has a description
     */
    public boolean isFallible() {
        return description != null;
    }

    /**
     * Returns the derivation function.
     *
     * @return the function
     */
    public Function<R, Outcome<D>> function() {
        return function;
    }

    /**
     * Returns the postconditions for this derivation.
     *
     * @return unmodifiable list of postconditions
     */
    public List<Postcondition<D>> postconditions() {
        return postconditions;
    }

    /**
     * Evaluates this derivation against a raw result.
     *
     * <p>If the derivation function succeeds:
     * <ul>
     *   <li>If fallible: adds a Passed result for the derivation description</li>
     *   <li>Evaluates all nested postconditions</li>
     * </ul>
     *
     * <p>If the derivation function fails:
     * <ul>
     *   <li>If fallible: adds a Failed result for the derivation description</li>
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
            // Derivation function threw an exception
            if (isFallible()) {
                results.add(new PostconditionResult.Failed(description, e.getMessage()));
                for (Postcondition<D> postcondition : postconditions) {
                    results.add(postcondition.skip("Derivation '" + description + "' failed"));
                }
            }
            return results;
        }

        if (derivationOutcome.isSuccess()) {
            // Derivation succeeded
            if (isFallible()) {
                results.add(new PostconditionResult.Passed(description));
            }
            D derivedValue = derivationOutcome.value();
            for (Postcondition<D> postcondition : postconditions) {
                results.add(postcondition.evaluate(derivedValue));
            }
        } else {
            // Derivation failed
            if (isFallible()) {
                results.add(new PostconditionResult.Failed(description, derivationOutcome.failureReason()));
                for (Postcondition<D> postcondition : postconditions) {
                    results.add(postcondition.skip("Derivation '" + description + "' failed"));
                }
            }
        }

        return results;
    }

    @Override
    public String toString() {
        String name = description != null ? description : "(infallible)";
        return "Derivation[" + name + ", postconditions=" + postconditions.size() + "]";
    }
}

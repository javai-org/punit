package org.javai.punit.contract;

import org.javai.outcome.Outcome;

/**
 * A functional interface for postcondition checks that return rich failure information.
 *
 * <p>This interface exists to disambiguate from {@link java.util.function.Predicate}
 * in overloaded methods. While both are functional interfaces taking a value,
 * {@code PostconditionCheck} returns {@code Outcome<Void>} which allows communicating
 * detailed failure reasons.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PostconditionCheck<JsonNode> check = json -> {
 *     if (!json.has("operations")) {
 *         return Outcome.fail("check", "Missing 'operations' field");
 *     }
 *     return Outcome.ok();
 * };
 * }</pre>
 *
 * @param <T> the type of value to check
 * @see Postcondition
 * @see ServiceContract
 */
@FunctionalInterface
public interface PostconditionCheck<T> {

    /**
     * Checks a value and returns the result.
     *
     * @param value the value to check
     * @return {@code Outcome.ok()} if the check passes,
     *         {@code Outcome.fail(...)} with a reason if it fails
     */
    Outcome<Void> check(T value);
}

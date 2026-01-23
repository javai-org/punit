package org.javai.punit.contract;

import org.javai.outcome.Failure;
import org.javai.outcome.FailureCode;
import org.javai.outcome.FailureKind;
import org.javai.outcome.Outcome;

import java.util.Objects;
import java.util.function.Function;

/**
 * Utility methods for creating {@link Outcome} instances in contract contexts.
 *
 * <p>This class provides simplified factory methods for creating outcomes
 * in service contract derivations, hiding the complexity of the underlying
 * failure model.
 *
 * <h2>Usage in Derivations</h2>
 * <pre>{@code
 * .deriving("Valid JSON", response -> {
 *     try {
 *         return Outcomes.ok(objectMapper.readTree(response));
 *     } catch (JsonProcessingException e) {
 *         return Outcomes.fail("Parse error: " + e.getMessage());
 *     }
 * })
 * }</pre>
 *
 * @see ServiceContract
 * @see Derivation
 */
public final class Outcomes {

    private static final FailureCode CONTRACT_FAILURE_CODE = FailureCode.of("contract", "derivation");

    /**
     * Sentinel value representing a successful outcome with no meaningful value.
     */
    private static final Object VOID_SUCCESS = new Object();

    private Outcomes() {
        // Utility class
    }

    /**
     * Creates a successful outcome with no associated value.
     *
     * <p>Use this for postconditions that don't produce a meaningful value,
     * such as simple predicate checks.
     *
     * @return a successful outcome
     */
    @SuppressWarnings("unchecked")
    public static <T> Outcome<T> okVoid() {
        return (Outcome<T>) Outcome.ok(VOID_SUCCESS);
    }

    /**
     * Creates a successful outcome containing the given value.
     *
     * @param <T> the value type
     * @param value the success value (must not be null)
     * @return a successful outcome
     * @throws NullPointerException if value is null
     */
    public static <T> Outcome<T> ok(T value) {
        Objects.requireNonNull(value, "value must not be null");
        return Outcome.ok(value);
    }

    /**
     * Creates a failed outcome with the given reason.
     *
     * @param <T> the value type (for type inference)
     * @param reason the failure reason (must not be null or blank)
     * @return a failed outcome
     * @throws NullPointerException if reason is null
     * @throws IllegalArgumentException if reason is blank
     */
    public static <T> Outcome<T> fail(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        FailureKind kind = FailureKind.permanentFailure(CONTRACT_FAILURE_CODE, reason, null);
        return Outcome.fail(Failure.of(kind, "derivation"));
    }

    /**
     * Lifts a pure function into one that returns an {@code Outcome}.
     *
     * <p>The returned function always produces a successful outcome. Use this
     * for transformations that cannot fail.
     *
     * <h3>Example</h3>
     * <pre>{@code
     * Function<String, Outcome<String>> lowerCase = Outcomes.lift(String::toLowerCase);
     * Outcome<String> result = lowerCase.apply("HELLO"); // Ok("hello")
     * }</pre>
     *
     * @param <A> the input type
     * @param <B> the output type
     * @param fn the pure function to lift
     * @return a function that wraps the result in a successful Outcome
     * @throws NullPointerException if fn is null
     */
    public static <A, B> Function<A, Outcome<B>> lift(Function<A, B> fn) {
        Objects.requireNonNull(fn, "fn must not be null");
        return a -> ok(fn.apply(a));
    }

    /**
     * Extracts the failure message from an outcome.
     *
     * @param outcome the outcome (must be a failure)
     * @return the failure message
     * @throws IllegalStateException if outcome is not a failure
     */
    public static String failureMessage(Outcome<?> outcome) {
        return switch (outcome) {
            case Outcome.Fail<?> f -> f.failure().message();
            case Outcome.Ok<?> ignored -> throw new IllegalStateException("Cannot get failure message from successful outcome");
        };
    }
}

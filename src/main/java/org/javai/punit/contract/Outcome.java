package org.javai.punit.contract;

import java.util.Objects;
import java.util.function.Function;

/**
 * A result type for operations that may fail.
 *
 * <p>{@code Outcome} represents the result of a fallible operation, which is either
 * a success containing a value, or a failure containing a reason.
 *
 * <h2>Usage in Derivations</h2>
 * <p>Derivations in service contracts return {@code Outcome} to elegantly handle
 * transformation failures:
 * <pre>{@code
 * .deriving("Valid JSON", response -> {
 *     try {
 *         return Outcome.success(objectMapper.readTree(response));
 *     } catch (JsonProcessingException e) {
 *         return Outcome.failure("Parse error: " + e.getMessage());
 *     }
 * })
 * }</pre>
 *
 * <h2>Lifting Pure Functions</h2>
 * <p>For transformations that cannot fail, use {@link #lift(Function)} to wrap
 * a pure function:
 * <pre>{@code
 * .deriving(Outcome.lift(String::toLowerCase))
 * }</pre>
 *
 * @param <T> the type of the success value
 * @see ServiceContract
 */
public sealed interface Outcome<T> {

    /**
     * Returns true if this outcome represents success.
     *
     * @return true if success, false if failure
     */
    boolean isSuccess();

    /**
     * Returns true if this outcome represents failure.
     *
     * @return true if failure, false if success
     */
    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Returns the success value.
     *
     * @return the value
     * @throws IllegalStateException if this is a failure
     */
    T value();

    /**
     * Returns the failure reason.
     *
     * @return the failure reason
     * @throws IllegalStateException if this is a success
     */
    String failureReason();

    /**
     * Creates a successful outcome containing the given value.
     *
     * @param <T> the value type
     * @param value the success value (must not be null)
     * @return a successful outcome
     * @throws NullPointerException if value is null
     */
    static <T> Outcome<T> success(T value) {
        return new Success<>(value);
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
    static <T> Outcome<T> failure(String reason) {
        return new Failure<>(reason);
    }

    /**
     * Lifts a pure function into one that returns an {@code Outcome}.
     *
     * <p>The returned function always produces a successful outcome. Use this
     * for transformations that cannot fail.
     *
     * <h3>Example</h3>
     * <pre>{@code
     * Function<String, Outcome<String>> lowerCase = Outcome.lift(String::toLowerCase);
     * Outcome<String> result = lowerCase.apply("HELLO"); // Success("hello")
     * }</pre>
     *
     * @param <A> the input type
     * @param <B> the output type
     * @param fn the pure function to lift
     * @return a function that wraps the result in a successful Outcome
     * @throws NullPointerException if fn is null
     */
    static <A, B> Function<A, Outcome<B>> lift(Function<A, B> fn) {
        Objects.requireNonNull(fn, "fn must not be null");
        return a -> success(fn.apply(a));
    }

    /**
     * A successful outcome containing a value.
     *
     * @param <T> the value type
     * @param value the success value
     */
    record Success<T>(T value) implements Outcome<T> {

        /**
         * Creates a successful outcome.
         *
         * @param value the success value (must not be null)
         */
        public Success {
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public String failureReason() {
            throw new IllegalStateException("Cannot get failure reason from a successful outcome");
        }
    }

    /**
     * A failed outcome containing a reason.
     *
     * @param <T> the value type (for type compatibility)
     * @param reason the failure reason
     */
    record Failure<T>(String reason) implements Outcome<T> {

        /**
         * Creates a failed outcome.
         *
         * @param reason the failure reason (must not be null or blank)
         */
        public Failure {
            Objects.requireNonNull(reason, "reason must not be null");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public T value() {
            throw new IllegalStateException("Cannot get value from a failed outcome: " + reason);
        }

        @Override
        public String failureReason() {
            return reason;
        }
    }
}

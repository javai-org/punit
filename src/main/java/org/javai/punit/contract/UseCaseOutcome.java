package org.javai.punit.contract;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The outcome of a use case execution, containing the result, timing, and postcondition evaluation.
 *
 * <p>A {@code UseCaOutseOutcome} captures:
 * <ul>
 *   <li>The raw result from the service</li>
 *   <li>Execution time (automatically captured)</li>
 *   <li>Arbitrary metadata (e.g., token counts)</li>
 *   <li>Lazy postcondition evaluation</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public UseCaseOutcome<String> translateInstruction(String instruction) {
 *     return UseCaseOutcome
 *         .withContract(CONTRACT)
 *         .input(new ServiceInput(systemPrompt, instruction, temperature))
 *         .execute(in -> llm.chat(in.prompt(), in.instruction(), in.temperature()))
 *         .meta("tokensUsed", llm.getLastTokensUsed())
 *         .build();
 * }
 * }</pre>
 *
 * @param result the raw result from the service
 * @param executionTime the duration of the service execution
 * @param metadata arbitrary key-value metadata (e.g., token counts)
 * @param postconditionEvaluator evaluates postconditions against the result
 * @param <R> the result type
 * @see ServiceContract
 * @see PostconditionEvaluator
 */
public record UseCaseOutcome<R>(
        R result,
        Duration executionTime,
        Instant timestamp,
        Map<String, Object> metadata,
        PostconditionEvaluator<R> postconditionEvaluator
) {

    /**
     * Creates a new use case outcome.
     *
     * @throws NullPointerException if executionTime, timestamp, or postconditionEvaluator is null
     */
    public UseCaseOutcome {
        Objects.requireNonNull(executionTime, "executionTime must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(postconditionEvaluator, "postconditionEvaluator must not be null");
		//noinspection Java9CollectionFactory allows null metadata value
		metadata = Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    /**
     * Evaluates all postconditions against the result.
     *
     * <p>Postconditions are evaluated lazily on each call.
     *
     * @return list of postcondition results
     */
    public List<PostconditionResult> evaluatePostconditions() {
        return postconditionEvaluator.evaluate(result);
    }

    /**
     * Returns whether all postconditions are satisfied.
     *
     * <p>A postcondition is considered satisfied if it passed. Skipped postconditions
     * (due to failed derivations) do not count as failures for this check.
     *
     * @return true if all postconditions passed or were skipped
     */
    public boolean allPostconditionsSatisfied() {
        return evaluatePostconditions().stream().noneMatch(PostconditionResult::failed);
    }

    /**
     * Returns the total number of postconditions.
     *
     * @return the postcondition count
     */
    public int postconditionCount() {
        return postconditionEvaluator.postconditionCount();
    }

    /**
     * Asserts that all postconditions pass.
     *
     * <p>All postconditions are evaluated and any failures are accumulated.
     * If any postconditions fail, an {@link AssertionError} is thrown with
     * messages describing all failed postconditions.
     *
     * @throws AssertionError if any postcondition fails
     */
    public void assertAll() {
        List<String> failures = evaluatePostconditions().stream()
                .filter(PostconditionResult::failed)
                .map(PostconditionResult::failureMessage)
                .toList();

        if (!failures.isEmpty()) {
            throw new AssertionError("Postconditions failed:\n  - " + String.join("\n  - ", failures));
        }
    }

    /**
     * Asserts that all postconditions pass, throwing a custom message on failure.
     *
     * <p>All postconditions are evaluated and any failures are accumulated.
     * If any postconditions fail, an {@link AssertionError} is thrown with
     * the context message and descriptions of all failed postconditions.
     *
     * @param contextMessage additional context for the error message
     * @throws AssertionError if any postcondition fails
     */
    public void assertAll(String contextMessage) {
        List<String> failures = evaluatePostconditions().stream()
                .filter(PostconditionResult::failed)
                .map(PostconditionResult::failureMessage)
                .toList();

        if (!failures.isEmpty()) {
            throw new AssertionError(contextMessage + " - Postconditions failed:\n  - " + String.join("\n  - ", failures));
        }
    }

    // ========== Metadata Accessors ==========

    /**
     * Gets a long value from metadata, trying multiple keys in order.
     *
     * <p>This is useful when different use cases may store the same information
     * under different keys (e.g., "tokensUsed", "tokens", "totalTokens").
     *
     * @param keys the keys to try in order
     * @return the value if found, empty otherwise
     */
    public Optional<Long> getMetadataLong(String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof Number n) {
                return Optional.of(n.longValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Gets a string value from metadata.
     *
     * @param key the metadata key
     * @return the value if present and is a String, empty otherwise
     */
    public Optional<String> getMetadataString(String key) {
        Object value = metadata.get(key);
        if (value instanceof String s) {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    /**
     * Gets a boolean value from metadata.
     *
     * @param key the metadata key
     * @return the value if present and is a Boolean, empty otherwise
     */
    public Optional<Boolean> getMetadataBoolean(String key) {
        Object value = metadata.get(key);
        if (value instanceof Boolean b) {
            return Optional.of(b);
        }
        return Optional.empty();
    }

    /**
     * Starts building a use case outcome with the given contract.
     *
     * @param contract the service contract
     * @param <I> the input type
     * @param <R> the result type
     * @return a builder for providing input
     */
    public static <I, R> InputBuilder<I, R> withContract(ServiceContract<I, R> contract) {
        Objects.requireNonNull(contract, "contract must not be null");
        return new InputBuilder<>(contract);
    }

    /**
     * Builder stage for providing input.
     *
     * @param <I> the input type
     * @param <R> the result type
     */
    public static final class InputBuilder<I, R> {

        private final ServiceContract<I, R> contract;

        private InputBuilder(ServiceContract<I, R> contract) {
            this.contract = contract;
        }

        /**
         * Provides the input to the service.
         *
         * @param input the input value
         * @return a builder for executing the service
         */
        public ExecuteBuilder<I, R> input(I input) {
            return new ExecuteBuilder<>(contract, input);
        }
    }

    /**
     * Builder stage for executing the service.
     *
     * @param <I> the input type
     * @param <R> the result type
     */
    public static final class ExecuteBuilder<I, R> {

        private final ServiceContract<I, R> contract;
        private final I input;

        private ExecuteBuilder(ServiceContract<I, R> contract, I input) {
            this.contract = contract;
            this.input = input;
        }

        /**
         * Executes the service function and captures timing.
         *
         * <p>The execution time is automatically measured from before the function
         * is called until after it returns. The timestamp is captured at the start
         * of execution.
         *
         * @param function the service function to execute
         * @return a builder for adding metadata and building the outcome
         */
        public MetadataBuilder<R> execute(Function<I, R> function) {
            Objects.requireNonNull(function, "function must not be null");

            Instant start = Instant.now();
            R result = function.apply(input);
            Duration executionTime = Duration.between(start, Instant.now());

            return new MetadataBuilder<>(contract, result, executionTime, start);
        }
    }

    /**
     * Builder stage for adding metadata and building the outcome.
     *
     * @param <R> the result type
     */
    public static final class MetadataBuilder<R> {

        private final PostconditionEvaluator<R> evaluator;
        private final R result;
        private final Duration executionTime;
        private final Instant timestamp;
        private final Map<String, Object> metadata = new HashMap<>();

        private MetadataBuilder(PostconditionEvaluator<R> evaluator, R result, Duration executionTime, Instant timestamp) {
            this.evaluator = evaluator;
            this.result = result;
            this.executionTime = executionTime;
            this.timestamp = timestamp;
        }

        /**
         * Adds metadata to the outcome.
         *
         * <p>Use this for service-specific data like token counts that cannot
         * be standardized by the framework.
         *
         * @param key the metadata key
         * @param value the metadata value
         * @return this builder
         */
        public MetadataBuilder<R> meta(String key, Object value) {
            Objects.requireNonNull(key, "key must not be null");
            metadata.put(key, value);
            return this;
        }

        /**
         * Extracts metadata from the execution result.
         *
         * <p>This method provides access to the captured result, enabling extraction
         * of result-derived values (token counts, request IDs, etc.) as metadata.
         *
         * <p>Example:
         * <pre>{@code
         * .execute(this::callService)
         * .withResult((response, meta) -> meta
         *     .meta("tokensUsed", response.totalTokens())
         *     .meta("requestId", response.requestId()))
         * .build();
         * }</pre>
         *
         * @param extractor a function receiving the result and this builder for metadata extraction
         * @return this builder for method chaining
         * @throws NullPointerException if extractor is null
         */
        public MetadataBuilder<R> withResult(BiConsumer<R, MetadataBuilder<R>> extractor) {
            Objects.requireNonNull(extractor, "extractor must not be null");
            extractor.accept(result, this);
            return this;
        }

        /**
         * Builds the use case outcome.
         *
         * @return the immutable outcome
         */
        public UseCaseOutcome<R> build() {
            return new UseCaseOutcome<>(result, executionTime, timestamp, metadata, evaluator);
        }
    }
}

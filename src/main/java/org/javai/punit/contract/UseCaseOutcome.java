package org.javai.punit.contract;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The outcome of a use case execution, containing the result, timing, and postcondition evaluation.
 *
 * <p>A {@code UseCaseOutcome} captures:
 * <ul>
 *   <li>The raw result from the service</li>
 *   <li>Execution time (automatically captured)</li>
 *   <li>Arbitrary metadata (e.g., token counts)</li>
 *   <li>Lazy postcondition evaluation via the bound contract</li>
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
 * @param <R> the result type
 * @see ServiceContract
 */
public final class UseCaseOutcome<R> {

    private final R result;
    private final Duration executionTime;
    private final Map<String, Object> metadata;
    private final ServiceContract<?, R> contract;

    private UseCaseOutcome(
            R result,
            Duration executionTime,
            Map<String, Object> metadata,
            ServiceContract<?, R> contract) {
        this.result = result;
        this.executionTime = Objects.requireNonNull(executionTime, "executionTime must not be null");
        this.metadata = Collections.unmodifiableMap(new HashMap<>(metadata));
        this.contract = Objects.requireNonNull(contract, "contract must not be null");
    }

    /**
     * Returns the raw result from the service.
     *
     * @return the result
     */
    public R result() {
        return result;
    }

    /**
     * Returns the execution time of the service call.
     *
     * @return the duration
     */
    public Duration executionTime() {
        return executionTime;
    }

    /**
     * Returns the metadata associated with this outcome.
     *
     * @return unmodifiable map of metadata
     */
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Evaluates all postconditions from the contract against the result.
     *
     * <p>This includes both direct postconditions and derivation postconditions.
     * Postconditions are evaluated lazily on each call.
     *
     * @return list of postcondition results
     */
    public List<PostconditionResult> evaluatePostconditions() {
        return contract.evaluatePostconditions(result);
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
     * Returns the total number of postconditions in the contract.
     *
     * @return the postcondition count
     */
    public int postconditionCount() {
        return contract.postconditionCount();
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

    @Override
    public String toString() {
        return "UseCaseOutcome[result=" + result +
                ", executionTime=" + executionTime +
                ", metadata=" + metadata.keySet() + "]";
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
         * Provides the input to the service and checks preconditions.
         *
         * <p>Preconditions are evaluated eagerly. If any precondition fails,
         * a {@link PreconditionException} is thrown immediately.
         *
         * @param input the input value
         * @return a builder for executing the service
         * @throws PreconditionException if any precondition fails
         */
        public ExecuteBuilder<I, R> input(I input) {
            contract.checkPreconditions(input);
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
         * is called until after it returns.
         *
         * @param function the service function to execute
         * @return a builder for adding metadata and building the outcome
         */
        public MetadataBuilder<R> execute(Function<I, R> function) {
            Objects.requireNonNull(function, "function must not be null");

            Instant start = Instant.now();
            R result = function.apply(input);
            Duration executionTime = Duration.between(start, Instant.now());

            return new MetadataBuilder<>(contract, result, executionTime);
        }
    }

    /**
     * Builder stage for adding metadata and building the outcome.
     *
     * @param <R> the result type
     */
    public static final class MetadataBuilder<R> {

        private final ServiceContract<?, R> contract;
        private final R result;
        private final Duration executionTime;
        private final Map<String, Object> metadata = new HashMap<>();

        private MetadataBuilder(ServiceContract<?, R> contract, R result, Duration executionTime) {
            this.contract = contract;
            this.result = result;
            this.executionTime = executionTime;
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
         * Builds the use case outcome.
         *
         * @return the immutable outcome
         */
        public UseCaseOutcome<R> build() {
            return new UseCaseOutcome<>(result, executionTime, metadata, contract);
        }
    }
}

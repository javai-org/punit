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

import org.javai.punit.contract.match.ResultExtractor;
import org.javai.punit.contract.match.VerificationMatcher;
import org.javai.punit.contract.match.VerificationMatcher.MatchResult;

/**
 * The outcome of a use case execution, containing the result, timing, and postcondition evaluation.
 *
 * <p>A {@code UseCaseOutcome} captures:
 * <ul>
 *   <li>The raw result from the service</li>
 *   <li>Execution time (automatically captured)</li>
 *   <li>Arbitrary metadata (e.g., token counts)</li>
 *   <li>Lazy postcondition evaluation</li>
 *   <li>Optional expected value matching (instance conformance)</li>
 *   <li>Optional duration constraint evaluation (timing conformance)</li>
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
 * <h2>Instance Conformance</h2>
 * <p>For testing against expected results:
 * <pre>{@code
 * return UseCaseOutcome
 *     .withContract(CONTRACT)
 *     .input(input)
 *     .execute(service::call)
 *     .expecting("expected result", ResultExtractor.identity(), StringMatcher.exact())
 *     .build();
 *
 * // Check full conformance
 * if (outcome.fullySatisfied()) {
 *     // Both postconditions passed AND expected value matched
 * }
 * }</pre>
 *
 * @param result the raw result from the service
 * @param executionTime the duration of the service execution
 * @param metadata arbitrary key-value metadata (e.g., token counts)
 * @param postconditionEvaluator evaluates postconditions against the result
 * @param expectedValue the expected value for instance conformance (nullable)
 * @param matchResult the result of matching expected vs actual (nullable)
 * @param durationResult the result of duration constraint evaluation (nullable)
 * @param <R> the result type
 * @see ServiceContract
 * @see PostconditionEvaluator
 * @see VerificationMatcher
 * @see DurationConstraint
 */
public record UseCaseOutcome<R>(
        R result,
        Duration executionTime,
        Instant timestamp,
        Map<String, Object> metadata,
        PostconditionEvaluator<R> postconditionEvaluator,
        Object expectedValue,
        MatchResult matchResult,
        DurationResult durationResult
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
     * Returns whether this outcome has an expected value for instance conformance.
     *
     * @return true if an expected value was specified
     */
    public boolean hasExpectedValue() {
        return expectedValue != null;
    }

    /**
     * Returns whether the actual result matches the expected value.
     *
     * <p>If no expected value was specified, this returns true (no expectation = trivially satisfied).
     *
     * @return true if no expectation exists or if the actual value matches the expected value
     */
    public boolean matchesExpected() {
        return matchResult == null || matchResult.matches();
    }

    /**
     * Returns the match result as an optional.
     *
     * @return the match result, or empty if no expected value was specified
     */
    public Optional<MatchResult> getMatchResult() {
        return Optional.ofNullable(matchResult);
    }

    /**
     * Returns whether a duration constraint was specified.
     *
     * @return true if a duration constraint was evaluated
     */
    public boolean hasDurationConstraint() {
        return durationResult != null;
    }

    /**
     * Returns the duration result as an optional.
     *
     * @return the duration result, or empty if no constraint was specified
     */
    public Optional<DurationResult> getDurationResult() {
        return Optional.ofNullable(durationResult);
    }

    /**
     * Returns whether execution completed within the duration limit.
     *
     * <p>If no duration constraint was specified, returns true (no constraint = trivially satisfied).
     *
     * @return true if no constraint exists or if execution was within the limit
     */
    public boolean withinDurationLimit() {
        return durationResult == null || durationResult.passed();
    }

    /**
     * Returns whether this outcome is fully satisfied.
     *
     * <p>An outcome is fully satisfied when:
     * <ul>
     *   <li>All postconditions pass (behavioral conformance)</li>
     *   <li>The expected value matches, if specified (instance conformance)</li>
     *   <li>Execution completed within the duration limit, if specified (timing conformance)</li>
     * </ul>
     *
     * @return true if all criteria are met
     */
    public boolean fullySatisfied() {
        return allPostconditionsSatisfied() && matchesExpected() && withinDurationLimit();
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
     * Asserts that all contract criteria pass.
     *
     * <p>All postconditions and duration constraints are evaluated and any
     * failures are accumulated. If any criteria fail, an {@link AssertionError}
     * is thrown with messages describing all failures.
     *
     * @throws AssertionError if any postcondition or duration constraint fails
     */
    public void assertAll() {
        List<String> failures = new java.util.ArrayList<>(evaluatePostconditions().stream()
                .filter(PostconditionResult::failed)
                .map(PostconditionResult::failureMessage)
                .toList());

        if (durationResult != null && durationResult.failed()) {
            failures.add(durationResult.failureMessage());
        }

        if (!failures.isEmpty()) {
            throw new AssertionError("Contract violations:\n  - " + String.join("\n  - ", failures));
        }
    }

    /**
     * Asserts that all contract criteria pass, throwing a custom message on failure.
     *
     * <p>All postconditions and duration constraints are evaluated and any
     * failures are accumulated. If any criteria fail, an {@link AssertionError}
     * is thrown with the context message and descriptions of all failures.
     *
     * @param contextMessage additional context for the error message
     * @throws AssertionError if any postcondition or duration constraint fails
     */
    public void assertAll(String contextMessage) {
        List<String> failures = new java.util.ArrayList<>(evaluatePostconditions().stream()
                .filter(PostconditionResult::failed)
                .map(PostconditionResult::failureMessage)
                .toList());

        if (durationResult != null && durationResult.failed()) {
            failures.add(durationResult.failureMessage());
        }

        if (!failures.isEmpty()) {
            throw new AssertionError(contextMessage + " - Contract violations:\n  - " + String.join("\n  - ", failures));
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

        private final ServiceContract<?, R> contract;
        private final R result;
        private final Duration executionTime;
        private final Instant timestamp;
        private final Map<String, Object> metadata = new HashMap<>();
        private Object expectedValue;
        private MatchResult matchResult;

        private MetadataBuilder(ServiceContract<?, R> contract, R result, Duration executionTime, Instant timestamp) {
            this.contract = contract;
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
         * Specifies an expected value for instance conformance checking.
         *
         * <p>The extractor transforms the result into a matchable value, which is then
         * compared against the expected value using the provided matcher. This enables
         * comparison when the result type differs from the expected value type.
         *
         * <p>Example:
         * <pre>{@code
         * .execute(this::callLLM)
         * .expecting("Hello", LLMResponse::content, StringMatcher.exact())
         * .build();
         * }</pre>
         *
         * @param expected the expected value
         * @param extractor extracts the matchable value from the result
         * @param matcher compares the expected and actual values
         * @param <T> the type of value being compared
         * @return this builder for method chaining
         * @throws NullPointerException if extractor or matcher is null
         */
        public <T> MetadataBuilder<R> expecting(
                T expected,
                ResultExtractor<R, T> extractor,
                VerificationMatcher<T> matcher) {
            Objects.requireNonNull(extractor, "extractor must not be null");
            Objects.requireNonNull(matcher, "matcher must not be null");

            this.expectedValue = expected;
            T actualValue = extractor.extract(result);
            this.matchResult = matcher.match(expected, actualValue);
            return this;
        }

        /**
         * Specifies an expected value for instance conformance checking.
         *
         * <p>This is a convenience method for when the result type matches the expected
         * value type directly. Equivalent to:
         * <pre>{@code
         * expecting(expected, ResultExtractor.identity(), matcher)
         * }</pre>
         *
         * @param expected the expected value
         * @param matcher compares the expected and actual values
         * @return this builder for method chaining
         * @throws NullPointerException if matcher is null
         */
        public MetadataBuilder<R> expecting(R expected, VerificationMatcher<R> matcher) {
            return expecting(expected, ResultExtractor.identity(), matcher);
        }

        /**
         * Builds the use case outcome.
         *
         * @return the immutable outcome
         */
        public UseCaseOutcome<R> build() {
            DurationResult durationResult = contract.durationConstraint()
                    .map(constraint -> constraint.evaluate(executionTime))
                    .orElse(null);

            return new UseCaseOutcome<>(
                    result, executionTime, timestamp, metadata,
                    contract, expectedValue, matchResult, durationResult);
        }
    }
}

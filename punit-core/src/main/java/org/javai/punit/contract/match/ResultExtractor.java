package org.javai.punit.contract.match;

/**
 * Extracts a matchable value from an execution result.
 *
 * <p>Result extractors bridge the gap between complex result types and simple
 * matchable values. For example, when comparing LLM responses, you might extract
 * just the response text from a larger response object that also contains metadata.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Extract the content field from an LLM response
 * ResultExtractor<LLMResponse, String> extractor = LLMResponse::content;
 *
 * // Use in an outcome
 * outcome.expecting("Hello, world!", extractor, StringMatcher.exact());
 * }</pre>
 *
 * <h2>Identity Extractor</h2>
 * <p>When the result type is already the type you want to match against, use
 * the identity extractor:
 * <pre>{@code
 * outcome.expecting("expected", ResultExtractor.identity(), StringMatcher.exact());
 * }</pre>
 *
 * @param <R> the result type
 * @param <T> the extracted type to be matched
 * @see VerificationMatcher
 * @see org.javai.punit.contract.UseCaseOutcome.MetadataBuilder#expecting
 */
@FunctionalInterface
public interface ResultExtractor<R, T> {

    /**
     * Extracts a matchable value from the result.
     *
     * @param result the execution result
     * @return the extracted value to be matched
     */
    T extract(R result);

    /**
     * Returns an identity extractor that returns the result unchanged.
     *
     * <p>Use this when the result type is the same as the type you want to match.
     *
     * @param <T> the result and extracted type
     * @return an identity extractor
     */
    static <T> ResultExtractor<T, T> identity() {
        return result -> result;
    }
}

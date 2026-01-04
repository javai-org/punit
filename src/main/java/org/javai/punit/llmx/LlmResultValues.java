package org.javai.punit.llmx;

/**
 * Common value keys for LLM use case results.
 *
 * <p>Use these constants when building or accessing {@code UseCaseResult}
 * values in LLM-based experiments.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * UseCaseResult result = UseCaseResult.builder()
 *     .value(LlmResultValues.CONTENT, response.getContent())
 *     .value(LlmResultValues.TOKENS_USED, response.getTotalTokens())
 *     .value(LlmResultValues.PROMPT_TOKENS, response.getPromptTokens())
 *     .value(LlmResultValues.COMPLETION_TOKENS, response.getCompletionTokens())
 *     .value(LlmResultValues.LATENCY_MS, latency)
 *     .value(LlmResultValues.IS_VALID, isValid)
 *     .build();
 * }</pre>
 */
public final class LlmResultValues {

	private LlmResultValues() {
	}

	// ========== Content ==========

	/**
	 * The main content/response from the LLM.
	 */
	public static final String CONTENT = "content";

	/**
	 * The response text (alias for CONTENT).
	 */
	public static final String RESPONSE = "response";

	// ========== Token Counts ==========

	/**
	 * Total tokens used (prompt + completion).
	 */
	public static final String TOKENS_USED = "tokensUsed";

	/**
	 * Total tokens (alias for TOKENS_USED).
	 */
	public static final String TOTAL_TOKENS = "totalTokens";

	/**
	 * Tokens used for the prompt/input.
	 */
	public static final String PROMPT_TOKENS = "promptTokens";

	/**
	 * Tokens used for the completion/output.
	 */
	public static final String COMPLETION_TOKENS = "completionTokens";

	// ========== Timing ==========

	/**
	 * Latency in milliseconds.
	 */
	public static final String LATENCY_MS = "latencyMs";

	/**
	 * Time to first token in milliseconds.
	 */
	public static final String TIME_TO_FIRST_TOKEN_MS = "timeToFirstTokenMs";

	// ========== Validation ==========

	/**
	 * Whether the response is valid.
	 */
	public static final String IS_VALID = "isValid";

	/**
	 * Whether the response is valid JSON.
	 */
	public static final String IS_VALID_JSON = "isValidJson";

	/**
	 * Whether required fields are present.
	 */
	public static final String HAS_REQUIRED_FIELDS = "hasRequiredFields";

	// ========== Errors ==========

	/**
	 * Error type if the request failed.
	 */
	public static final String ERROR_TYPE = "errorType";

	/**
	 * Error message if the request failed.
	 */
	public static final String ERROR_MESSAGE = "errorMessage";

	/**
	 * Whether a rate limit was hit.
	 */
	public static final String RATE_LIMITED = "rateLimited";

	/**
	 * Whether the context length was exceeded.
	 */
	public static final String CONTEXT_LENGTH_EXCEEDED = "contextLengthExceeded";

	// ========== Model Info ==========

	/**
	 * The model that was actually used (may differ from requested).
	 */
	public static final String MODEL_USED = "modelUsed";

	/**
	 * The provider that handled the request.
	 */
	public static final String PROVIDER = "provider";

	// ========== Quality Metrics ==========

	/**
	 * Quality score (0.0 - 1.0).
	 */
	public static final String QUALITY_SCORE = "qualityScore";

	/**
	 * Relevance score (0.0 - 1.0).
	 */
	public static final String RELEVANCE_SCORE = "relevanceScore";

	/**
	 * Coherence score (0.0 - 1.0).
	 */
	public static final String COHERENCE_SCORE = "coherenceScore";
}


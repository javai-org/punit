package org.javai.punit.examples2.infrastructure.llm;

/**
 * Interface for a simple chat-based LLM interaction with token tracking.
 *
 * <p>This abstraction represents a single-turn chat completion where:
 * <ul>
 *   <li>A system message establishes context and instructions</li>
 *   <li>A user message provides the specific request</li>
 *   <li>Temperature controls response variability</li>
 * </ul>
 *
 * <h2>Token Tracking</h2>
 * <p>The interface supports token usage tracking, which is essential for:
 * <ul>
 *   <li>Budget management in probabilistic tests</li>
 *   <li>Cost estimation and monitoring</li>
 *   <li>Rate limiting based on token consumption</li>
 * </ul>
 *
 * <p>Use {@link #getTotalTokensUsed()} to retrieve cumulative token usage and
 * {@link #resetTokenCount()} to reset the counter between test runs.
 *
 * <p>In the examples, this is implemented by {@link MockChatLlm} which
 * simulates realistic LLM behavior including various failure modes.
 *
 * @see ChatResponse
 * @see MockChatLlm
 */
public interface ChatLlm {

    /**
     * Sends a chat request to the LLM and returns the response.
     *
     * <p>This is the simple form that returns just the response text.
     * Use {@link #chatWithMetadata(String, String, double)} if you need
     * token usage information for the individual call.
     *
     * @param systemMessage the system prompt establishing context and instructions
     * @param userMessage the user's request
     * @param temperature controls randomness (0.0 = deterministic, 1.0 = creative)
     * @return the LLM's response as a string
     */
    String chat(String systemMessage, String userMessage, double temperature);

    /**
     * Sends a chat request and returns both the response and metadata.
     *
     * <p>This method returns a {@link ChatResponse} that includes:
     * <ul>
     *   <li>The response content</li>
     *   <li>Token usage for this specific call</li>
     * </ul>
     *
     * <p>Use this method when you need to track tokens for individual calls,
     * such as when using {@link org.javai.punit.api.TokenChargeRecorder}.
     *
     * @param systemMessage the system prompt establishing context and instructions
     * @param userMessage the user's request
     * @param temperature controls randomness (0.0 = deterministic, 1.0 = creative)
     * @return the response with metadata including token usage
     */
    ChatResponse chatWithMetadata(String systemMessage, String userMessage, double temperature);

    /**
     * Returns the total number of tokens used across all calls since the last reset.
     *
     * <p>This is useful for:
     * <ul>
     *   <li>Monitoring cumulative token consumption in tests</li>
     *   <li>Verifying token budget behavior</li>
     *   <li>Cost tracking across multiple calls</li>
     * </ul>
     *
     * @return the cumulative token count
     */
    long getTotalTokensUsed();

    /**
     * Resets the token counter to zero.
     *
     * <p>Call this between test runs or experiments to start fresh token tracking.
     * This is particularly useful when testing budget exhaustion scenarios.
     */
    void resetTokenCount();
}

package org.javai.punit.examples2.infrastructure.llm;

/**
 * Response from a chat LLM call including metadata.
 *
 * <p>This record bundles the response content with usage information,
 * mimicking how real LLM APIs (OpenAI, Anthropic, etc.) return responses.
 *
 * <h2>Token Breakdown</h2>
 * <ul>
 *   <li>{@code promptTokens} - Tokens used for the system message + user message</li>
 *   <li>{@code completionTokens} - Tokens used for the generated response</li>
 *   <li>{@code totalTokens()} - Sum of prompt and completion tokens</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ChatLlm llm = MockChatLlm.instance();
 * ChatResponse response = llm.chatWithMetadata(systemPrompt, userMessage, 0.3);
 *
 * String content = response.content();
 * int tokens = response.totalTokens();
 *
 * // Record tokens for budget tracking
 * tokenRecorder.recordTokens(tokens);
 * }</pre>
 *
 * @param content the response text from the LLM
 * @param promptTokens tokens used for the input (system + user messages)
 * @param completionTokens tokens used for the generated response
 */
public record ChatResponse(
        String content,
        int promptTokens,
        int completionTokens
) {
    /**
     * Returns the total tokens used for this request (prompt + completion).
     *
     * @return total token count
     */
    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}

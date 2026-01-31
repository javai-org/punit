package org.javai.punit.examples.infrastructure.llm;

/**
 * Exception thrown when LLM configuration is invalid.
 *
 * <p>This exception indicates a configuration problem that prevents the LLM
 * from being used, such as:
 * <ul>
 *   <li>Missing API key for a provider</li>
 *   <li>Invalid LLM mode (not "mock" or "real")</li>
 *   <li>Unknown model name that no provider supports</li>
 * </ul>
 *
 * <p>Error messages are designed to be actionable, indicating how to fix
 * the configuration problem.
 *
 * <h2>Example Messages</h2>
 * <pre>
 * OpenAI API key required. Set OPENAI_API_KEY environment variable or -Dpunit.llm.openai.key system property.
 * Unknown model: 'gemini-pro'. Supported patterns: gpt-*, o1-*, o3-*, text-*, davinci*, claude-*
 * </pre>
 */
public class LlmConfigurationException extends RuntimeException {

    /**
     * Creates a new configuration exception with the specified message.
     *
     * @param message a clear, actionable description of the configuration problem
     */
    public LlmConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates a new configuration exception with the specified message and cause.
     *
     * @param message a clear, actionable description of the configuration problem
     * @param cause the underlying cause
     */
    public LlmConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

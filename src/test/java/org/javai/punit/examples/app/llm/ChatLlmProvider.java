package org.javai.punit.examples.app.llm;

/**
 * Factory for creating ChatLlm instances based on configuration.
 *
 * <p>Resolution order: System property → Environment variable → Default (mock)
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li>{@code mock} - Returns {@link MockChatLlm} (default, no API keys required)</li>
 *   <li>{@code real} - Returns {@link RoutingChatLlm} that routes to providers based on model name</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Set the mode via:
 * <ul>
 *   <li>System property: {@code -Dpunit.llm.mode=real}</li>
 *   <li>Environment variable: {@code PUNIT_LLM_MODE=real}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ChatLlm llm = ChatLlmProvider.resolve();
 *
 * // Model is passed explicitly in each call
 * // In real mode, routes to appropriate provider based on model name
 * llm.chat("You are helpful.", "Hello", "gpt-4o-mini", 0.3);        // Routes to OpenAI
 * llm.chat("You are helpful.", "Hello", "claude-haiku-4-5-20251001", 0.3);  // Routes to Anthropic
 * }</pre>
 *
 * <h2>Running with Real LLMs</h2>
 * <pre>{@code
 * # Set mode and API keys
 * export PUNIT_LLM_MODE=real
 * export OPENAI_API_KEY=sk-...
 * export ANTHROPIC_API_KEY=sk-ant-...
 *
 * # Run experiments
 * ./gradlew exp -Prun=ShoppingBasketExplore.compareModels
 * }</pre>
 *
 * @see MockChatLlm
 * @see RoutingChatLlm
 */
public final class ChatLlmProvider {

    private static final String MODE_PROPERTY = "punit.llm.mode";
    private static final String MODE_ENV_VAR = "PUNIT_LLM_MODE";
    private static final String DEFAULT_MODE = "mock";

    private ChatLlmProvider() {
        // Static utility class
    }

    /**
     * Resolves the ChatLlm implementation based on mode configuration.
     *
     * @return {@link MockChatLlm} for mock mode, {@link RoutingChatLlm} for real mode
     * @throws LlmConfigurationException if the mode is invalid
     */
    public static ChatLlm resolve() {
        String mode = resolvedMode();

        return switch (mode.toLowerCase()) {
            case "mock" -> MockChatLlm.instance();
            case "real" -> new RoutingChatLlm();
            default -> throw new LlmConfigurationException(
                    "Unknown LLM mode: '%s'. Supported: mock, real".formatted(mode));
        };
    }

    /**
     * Returns the currently configured mode.
     *
     * @return "mock" or "real" (or custom value if configured)
     */
    public static String resolvedMode() {
        return resolveProperty(MODE_PROPERTY, MODE_ENV_VAR, DEFAULT_MODE);
    }

    /**
     * Returns true if running in real mode.
     *
     * @return true if real LLM providers will be used
     */
    public static boolean isRealMode() {
        return "real".equalsIgnoreCase(resolvedMode());
    }

    /**
     * Returns true if running in mock mode.
     *
     * @return true if the mock LLM will be used
     */
    public static boolean isMockMode() {
        return "mock".equalsIgnoreCase(resolvedMode());
    }

    private static String resolveProperty(String sysProp, String envVar, String defaultValue) {
        String value = System.getProperty(sysProp);
        if (value != null && !value.isBlank()) return value;

        value = System.getenv(envVar);
        if (value != null && !value.isBlank()) return value;

        return defaultValue;
    }
}

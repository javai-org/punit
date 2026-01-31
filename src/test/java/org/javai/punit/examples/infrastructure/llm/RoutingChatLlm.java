package org.javai.punit.examples.infrastructure.llm;

/**
 * Routes LLM requests to the appropriate provider based on model name.
 *
 * <p>Provider instances are created lazily when first needed, and API keys
 * are validated at that time. This means an experiment using only OpenAI
 * models doesn't require an Anthropic API key.
 *
 * <p>This implementation is stateless with respect to model selection—the model
 * is passed explicitly in each call. Model compatibility is determined by asking
 * each provider class directly via their static {@code supportsModel()} methods.
 *
 * <h2>Supported Providers</h2>
 * <ul>
 *   <li>{@link OpenAiChatLlm} - models matching: gpt-*, o1-*, o3-*, text-*, davinci*</li>
 *   <li>{@link AnthropicChatLlm} - models matching: claude-*</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>API keys and base URLs are resolved from system properties or environment variables:
 * <ul>
 *   <li>OpenAI: {@code punit.llm.openai.key} / {@code OPENAI_API_KEY}</li>
 *   <li>Anthropic: {@code punit.llm.anthropic.key} / {@code ANTHROPIC_API_KEY}</li>
 * </ul>
 *
 * @see ChatLlmProvider
 * @see OpenAiChatLlm
 * @see AnthropicChatLlm
 */
final class RoutingChatLlm implements ChatLlm {

    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private OpenAiChatLlm openAiLlm;      // Lazy initialized
    private AnthropicChatLlm anthropicLlm; // Lazy initialized
    private long totalTokensUsed;

    RoutingChatLlm() {
        this.totalTokensUsed = 0;
    }

    @Override
    public String chat(String systemMessage, String userMessage, String model, double temperature) {
        return chatWithMetadata(systemMessage, userMessage, model, temperature).content();
    }

    @Override
    public ChatResponse chatWithMetadata(String systemMessage, String userMessage, String model, double temperature) {
        ChatLlm provider = resolveProvider(model);
        ChatResponse response = provider.chatWithMetadata(systemMessage, userMessage, model, temperature);
        totalTokensUsed += response.totalTokens();
        return response;
    }

    @Override
    public long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    @Override
    public void resetTokenCount() {
        totalTokensUsed = 0;
        if (openAiLlm != null) openAiLlm.resetTokenCount();
        if (anthropicLlm != null) anthropicLlm.resetTokenCount();
    }

    private ChatLlm resolveProvider(String model) {
        // Ask each provider if it supports this model
        if (OpenAiChatLlm.supportsModel(model)) {
            return getOrCreateOpenAi();
        }
        if (AnthropicChatLlm.supportsModel(model)) {
            return getOrCreateAnthropic();
        }
        throw new LlmConfigurationException(
                "Unknown model: '%s'. Supported patterns: %s, %s".formatted(
                        model,
                        OpenAiChatLlm.supportedModelPatterns(),
                        AnthropicChatLlm.supportedModelPatterns()));
    }

    private synchronized OpenAiChatLlm getOrCreateOpenAi() {
        if (openAiLlm == null) {
            String apiKey = resolveApiKey("punit.llm.openai.key", "OPENAI_API_KEY", "OpenAI");
            String baseUrl = resolveProperty("punit.llm.openai.baseUrl", "OPENAI_BASE_URL",
                    "https://api.openai.com/v1");
            int timeout = resolveTimeout();
            openAiLlm = new OpenAiChatLlm(apiKey, baseUrl, timeout);
        }
        return openAiLlm;
    }

    private synchronized AnthropicChatLlm getOrCreateAnthropic() {
        if (anthropicLlm == null) {
            String apiKey = resolveApiKey("punit.llm.anthropic.key", "ANTHROPIC_API_KEY", "Anthropic");
            String baseUrl = resolveProperty("punit.llm.anthropic.baseUrl", "ANTHROPIC_BASE_URL",
                    "https://api.anthropic.com/v1");
            int timeout = resolveTimeout();
            anthropicLlm = new AnthropicChatLlm(apiKey, baseUrl, timeout);
        }
        return anthropicLlm;
    }

    private String resolveApiKey(String sysProp, String envVar, String providerName) {
        String apiKey = resolveProperty(sysProp, envVar, null);
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmConfigurationException(
                    "%s API key required. Set %s environment variable or -%s system property."
                            .formatted(providerName, envVar, sysProp));
        }
        return apiKey;
    }

    private int resolveTimeout() {
        String timeoutStr = resolveProperty("punit.llm.timeout", "PUNIT_LLM_TIMEOUT", null);
        if (timeoutStr != null && !timeoutStr.isBlank()) {
            try {
                return Integer.parseInt(timeoutStr);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return DEFAULT_TIMEOUT_MS;
    }

    private static String resolveProperty(String sysProp, String envVar, String defaultValue) {
        String value = System.getProperty(sysProp);
        if (value != null && !value.isBlank()) return value;

        value = System.getenv(envVar);
        if (value != null && !value.isBlank()) return value;

        return defaultValue;
    }
}

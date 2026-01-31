package org.javai.punit.examples.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Anthropic Messages API implementation.
 *
 * <p>Uses {@link java.net.http.HttpClient} for zero external dependencies
 * (aside from Jackson for JSON parsing).
 *
 * <h2>Model Naming</h2>
 * <p>Pass Anthropic model names directly in each call (e.g., "claude-3-5-haiku-20241022",
 * "claude-sonnet-4-20250514"). Use {@link #supportsModel(String)} to check if a model
 * name is supported.
 *
 * <h2>API Differences from OpenAI</h2>
 * <ul>
 *   <li>Uses {@code x-api-key} header instead of {@code Authorization: Bearer}</li>
 *   <li>Requires {@code anthropic-version} header</li>
 *   <li>System message is a top-level field, not in messages array</li>
 *   <li>Requires explicit {@code max_tokens} parameter</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <p>Throws {@link LlmApiException} for API errors. Transient errors (429, 5xx, timeouts)
 * are automatically retried up to 3 times with exponential backoff.
 *
 * <h2>Cost Tracking</h2>
 * <p>Logs estimated costs at FINE level after each successful call.
 *
 * @see ChatLlm
 * @see OpenAiChatLlm
 */
public final class AnthropicChatLlm implements ChatLlm {

    private static final Logger LOG = Logger.getLogger(AnthropicChatLlm.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String MESSAGES_PATH = "/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final String MODEL_PREFIX = "claude-";

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 500;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration timeout;
    private long totalTokensUsed;

    /**
     * Returns true if this provider supports the given model.
     *
     * @param model the model identifier to check
     * @return true if this provider can handle the model
     */
    public static boolean supportsModel(String model) {
        return model != null && model.startsWith(MODEL_PREFIX);
    }

    /**
     * Returns a human-readable description of supported model patterns.
     *
     * @return supported patterns for error messages
     */
    public static String supportedModelPatterns() {
        return "claude-*";
    }

    /**
     * Creates a new Anthropic chat LLM client.
     *
     * @param apiKey the Anthropic API key
     * @param baseUrl the API base URL (e.g., "https://api.anthropic.com/v1")
     * @param timeoutMs request timeout in milliseconds
     */
    public AnthropicChatLlm(String apiKey, String baseUrl, int timeoutMs) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.totalTokensUsed = 0;
    }

    @Override
    public String chat(String systemMessage, String userMessage, String model, double temperature) {
        return chatWithMetadata(systemMessage, userMessage, model, temperature).content();
    }

    @Override
    public ChatResponse chatWithMetadata(String systemMessage, String userMessage, String model, double temperature) {
        HttpRequest request = buildRequest(systemMessage, userMessage, model, temperature);
        return executeWithRetry(request, model);
    }

    @Override
    public long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    @Override
    public void resetTokenCount() {
        totalTokensUsed = 0;
    }

    private HttpRequest buildRequest(String systemMessage, String userMessage, String model, double temperature) {
        String body = buildRequestBody(systemMessage, userMessage, model, temperature);
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + MESSAGES_PATH))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(timeout)
                .build();
    }

    private String buildRequestBody(String systemMessage, String userMessage, String model, double temperature) {
        // Anthropic API uses "system" as a top-level field, not in messages array
        return """
                {
                  "model": "%s",
                  "max_tokens": %d,
                  "temperature": %s,
                  "system": %s,
                  "messages": [
                    {"role": "user", "content": %s}
                  ]
                }
                """.formatted(
                escapeJson(model),
                DEFAULT_MAX_TOKENS,
                temperature,
                jsonString(systemMessage),
                jsonString(userMessage)
        );
    }

    private ChatResponse executeWithRetry(HttpRequest request, String model) {
        LlmApiException lastException = null;
        long delayMs = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return handleResponse(response, model);
            } catch (HttpTimeoutException e) {
                lastException = new LlmApiException("Request timed out after " + timeout.toMillis() + "ms", e);
                LOG.log(Level.WARNING, "Attempt {0}/{1} timed out, retrying...",
                        new Object[]{attempt, MAX_RETRY_ATTEMPTS});
            } catch (IOException e) {
                lastException = new LlmApiException("Network error: " + e.getMessage(), e);
                LOG.log(Level.WARNING, "Attempt {0}/{1} failed with network error, retrying...",
                        new Object[]{attempt, MAX_RETRY_ATTEMPTS});
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmApiException("Request interrupted", e);
            } catch (LlmApiException e) {
                if (!e.isTransient()) {
                    throw e; // Don't retry permanent errors
                }
                lastException = e;
                LOG.log(Level.WARNING, "Attempt {0}/{1} failed with transient error (HTTP {2}), retrying...",
                        new Object[]{attempt, MAX_RETRY_ATTEMPTS, e.getStatusCode()});
            }

            // Wait before retry (except on last attempt)
            if (attempt < MAX_RETRY_ATTEMPTS) {
                try {
                    Thread.sleep(delayMs);
                    delayMs = (long) (delayMs * BACKOFF_MULTIPLIER);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LlmApiException("Retry interrupted", e);
                }
            }
        }

        throw lastException;
    }

    private ChatResponse handleResponse(HttpResponse<String> response, String model) {
        int statusCode = response.statusCode();
        String body = response.body();

        if (statusCode != 200) {
            throw new LlmApiException("Anthropic API error", statusCode, body);
        }

        try {
            return parseResponse(body, model);
        } catch (Exception e) {
            throw new LlmApiException("Failed to parse Anthropic response: " + e.getMessage(), e);
        }
    }

    private ChatResponse parseResponse(String json, String model) throws Exception {
        JsonNode root = MAPPER.readTree(json);

        // Anthropic returns content as an array: content[0].text
        String content = root.at("/content/0/text").asText();
        // Anthropic uses input_tokens and output_tokens
        int promptTokens = root.at("/usage/input_tokens").asInt();
        int completionTokens = root.at("/usage/output_tokens").asInt();

        // Track cumulative usage
        totalTokensUsed += promptTokens + completionTokens;

        // Log cost estimate
        logCostEstimate(model, promptTokens, completionTokens);

        return new ChatResponse(content, promptTokens, completionTokens);
    }

    private void logCostEstimate(String model, int promptTokens, int completionTokens) {
        if (LOG.isLoggable(Level.FINE)) {
            double cost = estimateCost(model, promptTokens, completionTokens);
            LOG.fine(() -> String.format(
                    "Anthropic API call: model=%s, input_tokens=%d, output_tokens=%d, est_cost=$%.6f",
                    model, promptTokens, completionTokens, cost));
        }
    }

    private double estimateCost(String model, int promptTokens, int completionTokens) {
        // Approximate costs per 1M tokens (as of Jan 2025)
        if (model.contains("opus")) {
            return (promptTokens * 15.00 + completionTokens * 75.00) / 1_000_000;
        } else if (model.contains("sonnet")) {
            return (promptTokens * 3.00 + completionTokens * 15.00) / 1_000_000;
        } else if (model.contains("haiku")) {
            return (promptTokens * 0.25 + completionTokens * 1.25) / 1_000_000;
        } else {
            // Conservative estimate for unknown models
            return (promptTokens * 3.00 + completionTokens * 15.00) / 1_000_000;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String jsonString(String s) {
        return "\"" + escapeJson(s) + "\"";
    }
}

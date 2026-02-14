package org.javai.punit.examples.app.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.javai.outcome.Failure;
import org.javai.outcome.FailureId;
import org.javai.outcome.Outcome;
import org.javai.outcome.boundary.Boundary;
import org.javai.outcome.retry.Retrier;
import org.javai.outcome.retry.RetryPolicy;

/**
 * Anthropic Messages API implementation.
 *
 * <p>Uses {@link java.net.http.HttpClient} for zero external dependencies
 * (aside from Jackson for JSON parsing).
 *
 * <h2>Model Naming</h2>
 * <p>Pass Anthropic model names directly in each call (e.g., "claude-haiku-4-5-20251001",
 * "claude-sonnet-4-5-20250929"). Use {@link #supportsModel(String)} to check if a model
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
 * <p>Uses the Outcome framework's {@link Retrier} for automatic retry of transient errors
 * (429, 5xx, timeouts) with exponential backoff. Permanent errors (401, 403, 400) fail
 * immediately.
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
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofMillis(500);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(5);

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final Boundary boundary;
    private final Retrier retrier;
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
        this.boundary = Boundary.of(new HttpFailureClassifier(), logReporter());
        this.retrier = Retrier.builder()
                .policy(RetryPolicy.backoff(MAX_RETRY_ATTEMPTS, INITIAL_RETRY_DELAY, MAX_RETRY_DELAY))
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

        Outcome<ChatResponse> result = retrier.execute(
                () -> executeRequest(request, model)
        );

        return switch (result) {
            case Outcome.Ok<ChatResponse> ok -> ok.value();
            case Outcome.Fail<ChatResponse> fail -> throw new LlmApiException(
                    "Anthropic API call failed: " + fail.failure().message(),
                    fail.failure().exception()
            );
        };
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

    private Outcome<ChatResponse> executeRequest(HttpRequest request, String model) {
        // Use Boundary to execute the HTTP call, converting exceptions to Outcome.Fail
        Outcome<HttpResponse<String>> httpResult = boundary.call(
                "Anthropic.messages",
                () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        );

        // Chain the response handling
        return httpResult.flatMap(response -> handleResponse(response, model));
    }

    private Outcome<ChatResponse> handleResponse(HttpResponse<String> response, String model) {
        int statusCode = response.statusCode();
        String body = response.body();

        if (statusCode == 200) {
            try {
                return Outcome.ok(parseResponse(body, model));
            } catch (Exception e) {
                return Outcome.fail(Failure.permanentFailure(
                        FailureId.of("llm", "parse_error"),
                        "Failed to parse Anthropic response: " + e.getMessage(),
                        "Anthropic.messages",
                        e
                ));
            }
        }

        // Classify HTTP errors as transient or permanent
        return Outcome.fail(classifyHttpError(statusCode, body));
    }

    private Failure classifyHttpError(int statusCode, String body) {
        String truncatedBody = body.length() > 200 ? body.substring(0, 200) + "..." : body;
        String message = "Anthropic API error [HTTP " + statusCode + "]: " + truncatedBody;

        // Rate limits and server errors are transient (retriable)
        if (statusCode == 429 || statusCode >= 500) {
            return Failure.transientFailure(
                    FailureId.of("llm", "http_" + statusCode),
                    message,
                    "Anthropic.messages",
                    null
            );
        }

        // Client errors (400, 401, 403, 404) are permanent
        return Failure.permanentFailure(
                FailureId.of("llm", "http_" + statusCode),
                message,
                "Anthropic.messages",
                null
        );
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
        // Approximate costs per 1M tokens (as of Feb 2026 - Claude 4.5 family)
        if (model.contains("opus")) {
            return (promptTokens * 5.00 + completionTokens * 25.00) / 1_000_000;
        } else if (model.contains("sonnet")) {
            return (promptTokens * 3.00 + completionTokens * 15.00) / 1_000_000;
        } else if (model.contains("haiku")) {
            return (promptTokens * 1.00 + completionTokens * 5.00) / 1_000_000;
        } else {
            // Conservative estimate for unknown models
            return (promptTokens * 3.00 + completionTokens * 15.00) / 1_000_000;
        }
    }

    private static org.javai.outcome.ops.OpReporter logReporter() {
        return failure -> {
            if (LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, "LLM API failure: {0}", failure.message());
            }
        };
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

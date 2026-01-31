package org.javai.punit.examples.infrastructure.llm;

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
 * OpenAI Chat Completions API implementation.
 *
 * <p>Uses {@link java.net.http.HttpClient} for zero external dependencies
 * (aside from Jackson for JSON parsing). Supports the Chat Completions endpoint only.
 *
 * <h2>Model Naming</h2>
 * <p>Pass OpenAI model names directly in each call (e.g., "gpt-4o", "gpt-4o-mini").
 * Use {@link #supportsModel(String)} to check if a model name is supported.
 *
 * <h2>Error Handling</h2>
 * <p>Throws {@link LlmApiException} for API errors. Transient errors (429, 5xx, timeouts)
 * are automatically retried up to 3 times with exponential backoff.
 *
 * <h2>Cost Tracking</h2>
 * <p>Logs estimated costs at FINE level after each successful call.
 *
 * @see ChatLlm
 * @see AnthropicChatLlm
 */
public final class OpenAiChatLlm implements ChatLlm {

    private static final Logger LOG = Logger.getLogger(OpenAiChatLlm.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String[] MODEL_PREFIXES = {"gpt-", "o1-", "o3-", "text-", "davinci"};

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
        if (model == null) return false;
        for (String prefix : MODEL_PREFIXES) {
            if (model.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Returns a human-readable description of supported model patterns.
     *
     * @return supported patterns for error messages
     */
    public static String supportedModelPatterns() {
        return "gpt-*, o1-*, o3-*, text-*, davinci*";
    }

    /**
     * Creates a new OpenAI chat LLM client.
     *
     * @param apiKey the OpenAI API key
     * @param baseUrl the API base URL (e.g., "https://api.openai.com/v1")
     * @param timeoutMs request timeout in milliseconds
     */
    public OpenAiChatLlm(String apiKey, String baseUrl, int timeoutMs) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.boundary = Boundary.of(new HttpFailureClassifier(), logReporter());
        this.retrier = Retrier.builder()
                .policy(RetryPolicy.exponentialBackoff(MAX_RETRY_ATTEMPTS, INITIAL_RETRY_DELAY, MAX_RETRY_DELAY))
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
                    "OpenAI API call failed: " + fail.failure().message(),
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
                .uri(URI.create(baseUrl + CHAT_COMPLETIONS_PATH))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(timeout)
                .build();
    }

    private String buildRequestBody(String systemMessage, String userMessage, String model, double temperature) {
        return """
                {
                  "model": "%s",
                  "temperature": %s,
                  "messages": [
                    {"role": "system", "content": %s},
                    {"role": "user", "content": %s}
                  ]
                }
                """.formatted(
                escapeJson(model),
                temperature,
                jsonString(systemMessage),
                jsonString(userMessage)
        );
    }

    private Outcome<ChatResponse> executeRequest(HttpRequest request, String model) {
        // Use Boundary to execute the HTTP call, converting exceptions to Outcome.Fail
        Outcome<HttpResponse<String>> httpResult = boundary.call(
                "OpenAI.chat.completions",
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
                        "Failed to parse OpenAI response: " + e.getMessage(),
                        "OpenAI.chat.completions",
                        e
                ));
            }
        }

        // Classify HTTP errors as transient or permanent
        return Outcome.fail(classifyHttpError(statusCode, body));
    }

    private Failure classifyHttpError(int statusCode, String body) {
        String truncatedBody = body.length() > 200 ? body.substring(0, 200) + "..." : body;
        String message = "OpenAI API error [HTTP " + statusCode + "]: " + truncatedBody;

        // Rate limits and server errors are transient (retriable)
        if (statusCode == 429 || statusCode >= 500) {
            return Failure.transientFailure(
                    FailureId.of("llm", "http_" + statusCode),
                    message,
                    "OpenAI.chat.completions",
                    null
            );
        }

        // Client errors (400, 401, 403, 404) are permanent
        return Failure.permanentFailure(
                FailureId.of("llm", "http_" + statusCode),
                message,
                "OpenAI.chat.completions",
                null
        );
    }

    private ChatResponse parseResponse(String json, String model) throws Exception {
        JsonNode root = MAPPER.readTree(json);

        String content = root.at("/choices/0/message/content").asText();
        int promptTokens = root.at("/usage/prompt_tokens").asInt();
        int completionTokens = root.at("/usage/completion_tokens").asInt();

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
                    "OpenAI API call: model=%s, prompt_tokens=%d, completion_tokens=%d, est_cost=$%.6f",
                    model, promptTokens, completionTokens, cost));
        }
    }

    private double estimateCost(String model, int promptTokens, int completionTokens) {
        // Approximate costs per 1M tokens (as of Jan 2025)
        return switch (model) {
            case "gpt-4o" -> (promptTokens * 2.50 + completionTokens * 10.00) / 1_000_000;
            case "gpt-4o-mini" -> (promptTokens * 0.15 + completionTokens * 0.60) / 1_000_000;
            case "o1-preview" -> (promptTokens * 15.00 + completionTokens * 60.00) / 1_000_000;
            case "o1-mini" -> (promptTokens * 3.00 + completionTokens * 12.00) / 1_000_000;
            default -> (promptTokens * 1.00 + completionTokens * 2.00) / 1_000_000; // Conservative estimate
        };
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

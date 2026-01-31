# Design: Real LLM Integration for Examples

## Status
**Draft** | Author: Claude | Date: 2026-01-31

## Overview

This document describes the design for adding optional real LLM provider support to PUnit's example infrastructure. The implementation enables switching between **mock** and **real** modes, where real mode routes requests to the appropriate provider based on the model name specified in the experiment.

## Motivation

PUnit's examples currently use `MockChatLlm` to simulate LLM behavior. While this keeps demos cheap and fast, it limits the demonstration of PUnit's core value proposition: testing and measuring non-deterministic systems. Real LLM integration would:

1. **Demonstrate genuine variance** - Real models exhibit actual probabilistic behavior
2. **Enable meaningful factor exploration** - Model and temperature comparisons become authentic
3. **Validate pacing controls** - Real APIs have rate limits, making PUnit's pacing features demonstrably useful
4. **Increase credibility** - Users can verify PUnit's capabilities against systems they actually use

## Key Design Insight

Experiments like `ShoppingBasketExplore.compareModels` deliberately explore **multiple models from different providers** in a single run. Therefore:

- The switch is **`mock` vs `real`** — not a provider selection
- In `real` mode, the **model name determines the provider** (e.g., `gpt-4o-mini` → OpenAI, `claude-3-5-haiku-20241022` → Anthropic)
- API keys are looked up **per provider** as needed

This also applies to **mutators**: `ShoppingBasketPromptMutator` currently uses deterministic prompt progression, but in `real` mode could use an LLM to analyze failure patterns and generate targeted improvements.

## Design Goals

| Goal                       | Description                                                                  |
|----------------------------|------------------------------------------------------------------------------|
| **Zero dependencies**      | Use `java.net.http.HttpClient` exclusively; no SDK dependencies              |
| **Opt-in only**            | Mock remains the default; real mode requires explicit configuration          |
| **CI-safe**                | Unit tests never invoke real APIs; architecture tests enforce this           |
| **Minimal implementation** | Each provider implementation ≤100 lines of code                              |
| **Cost-aware**             | Log cost estimates; track token usage for budget enforcement                 |
| **Fail-fast**              | Missing API keys produce clear, actionable error messages                    |
| **Multi-provider**         | Single experiment can invoke multiple providers based on model names         |

## Non-Goals

- Full-featured LLM client library
- Support for streaming responses
- Support for function calling / tool use
- Support for vision / multimodal inputs
- Retry logic with exponential backoff (may add later)

## Architecture

### Package Structure

```
org.javai.punit.examples.infrastructure.llm/
├── ChatLlm.java                  # Interface (existing)
├── ChatResponse.java             # Response record (existing)
├── ChatLlmProvider.java          # NEW: Factory with mode resolution
├── RoutingChatLlm.java           # NEW: Routes to provider based on model name
├── MockChatLlm.java              # Existing mock implementation
├── OpenAiChatLlm.java            # NEW: OpenAI implementation
├── AnthropicChatLlm.java         # NEW: Anthropic implementation
├── LlmApiException.java          # NEW: API error
└── LlmConfigurationException.java # NEW: Configuration error
```

### Class Diagram

```
                         ┌─────────────┐
                         │   ChatLlm   │ (interface)
                         └──────┬──────┘
                                │
       ┌───────────────┬────────┼────────┬───────────────┐
       │               │        │        │               │
       ▼               ▼        ▼        ▼               ▼
┌─────────────┐ ┌────────────┐ ┌──────────────┐ ┌──────────────┐
│ MockChatLlm │ │RoutingChat │ │OpenAiChatLlm │ │AnthropicChat │
│ (singleton) │ │    Llm     │ │              │ │     Llm      │
└─────────────┘ └──────┬─────┘ └──────────────┘ └──────────────┘
                       │              ▲               ▲
                       │   delegates  │               │
                       └──────────────┴───────────────┘

                         ┌─────────────────┐
                         │ ChatLlmProvider │ creates based on mode
                         └─────────────────┘
```

### Model → Provider Routing

`RoutingChatLlm` determines the provider by asking each provider class if it supports the given model via static `supportsModel(String)` methods. This keeps model knowledge encapsulated in each provider.

| Provider | Static Method | Patterns | Examples |
|----------|---------------|----------|----------|
| `OpenAiChatLlm` | `supportsModel(model)` | `gpt-*`, `o1-*`, `o3-*`, `text-*`, `davinci*` | `gpt-4o`, `gpt-4o-mini`, `o1-preview` |
| `AnthropicChatLlm` | `supportsModel(model)` | `claude-*` | `claude-3-5-haiku-20241022`, `claude-sonnet-4-20250514` |
| Unknown | — | — | Error with message listing `supportedModelPatterns()` from each provider |

**Benefits of this approach:**
- Each provider owns its model knowledge (single responsibility)
- Adding a new provider doesn't require modifying `RoutingChatLlm`
- Error messages automatically include current supported patterns

## Configuration

### Resolution Order

Configuration follows PUnit's standard resolution order:

1. **System property** (highest priority)
2. **Environment variable**
3. **Default value** (mock)

### Configuration Properties

| Property                      | Env Var              | Default                        | Description                             |
|-------------------------------|----------------------|--------------------------------|-----------------------------------------|
| `punit.llm.mode`              | `PUNIT_LLM_MODE`     | `mock`                         | Mode: `mock` or `real`                  |
| `punit.llm.openai.key`        | `OPENAI_API_KEY`     | —                              | OpenAI API key (required for OpenAI models) |
| `punit.llm.anthropic.key`     | `ANTHROPIC_API_KEY`  | —                              | Anthropic API key (required for Anthropic models) |
| `punit.llm.openai.baseUrl`    | `OPENAI_BASE_URL`    | `https://api.openai.com/v1`    | OpenAI API base URL                     |
| `punit.llm.anthropic.baseUrl` | `ANTHROPIC_BASE_URL` | `https://api.anthropic.com/v1` | Anthropic API base URL                  |
| `punit.llm.timeout`           | `PUNIT_LLM_TIMEOUT`  | `30000`                        | Request timeout in milliseconds         |

### Example Usage

```bash
# Run with real LLMs - experiment explores multiple models/providers
export PUNIT_LLM_MODE=real
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew exp -Prun=ShoppingBasketExplore.compareModels

# Using system properties
./gradlew exp -Prun=ShoppingBasketExplore.compareModels \
  -Dpunit.llm.mode=real

# Only need API keys for providers you're actually using
# If experiment only uses OpenAI models, only OPENAI_API_KEY is needed
```

## Component Specifications

### ChatLlmProvider

Factory class that returns either `MockChatLlm` or `RoutingChatLlm` based on mode.

```java
package org.javai.punit.examples.infrastructure.llm;

/**
 * Factory for creating ChatLlm instances based on configuration.
 *
 * <p>Resolution order: System property → Environment variable → Default (mock)
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li>{@code mock} - Returns MockChatLlm (default, no API keys required)</li>
 *   <li>{@code real} - Returns RoutingChatLlm that routes to providers based on model name</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ChatLlm llm = ChatLlmProvider.resolve();
 * // Model is passed explicitly in each call - in real mode, routes to appropriate provider
 * llm.chat("You are helpful.", "Hello", "gpt-4o-mini", 0.3);        // Routes to OpenAI
 * llm.chat("You are helpful.", "Hello", "claude-3-5-haiku-20241022", 0.3);  // Routes to Anthropic
 * }</pre>
 */
public final class ChatLlmProvider {

    private ChatLlmProvider() {}

    /**
     * Resolves the ChatLlm implementation based on mode configuration.
     *
     * @return MockChatLlm for mock mode, RoutingChatLlm for real mode
     */
    public static ChatLlm resolve() { ... }

    /**
     * Returns the currently configured mode.
     *
     * @return "mock" or "real"
     */
    public static String resolvedMode() { ... }

    /**
     * Returns true if running in real mode.
     */
    public static boolean isRealMode() { ... }
}
```

**Resolution Logic:**

```java
public static ChatLlm resolve() {
    String mode = resolveProperty("punit.llm.mode", "PUNIT_LLM_MODE", "mock");

    return switch (mode.toLowerCase()) {
        case "mock" -> MockChatLlm.instance();
        case "real" -> new RoutingChatLlm();
        default -> throw new LlmConfigurationException(
            "Unknown LLM mode: '%s'. Supported: mock, real".formatted(mode));
    };
}

private static String resolveProperty(String sysProp, String envVar, String defaultValue) {
    String value = System.getProperty(sysProp);
    if (value != null && !value.isBlank()) return value;

    value = System.getenv(envVar);
    if (value != null && !value.isBlank()) return value;

    return defaultValue;
}
```

### RoutingChatLlm

Routes requests to the appropriate provider based on model name. Provider instances are created lazily on first use.

**Key Design:** The router delegates model compatibility checks to the provider classes themselves via static `supportsModel(String)` methods. This keeps model knowledge where it belongs and makes adding new providers straightforward.

```java
package org.javai.punit.examples.infrastructure.llm;

/**
 * Routes LLM requests to the appropriate provider based on model name.
 *
 * <p>Provider instances are created lazily when first needed, and API keys
 * are validated at that time. This means an experiment using only OpenAI
 * models doesn't require an Anthropic API key.
 *
 * <p>This implementation is stateless—the model is passed explicitly in each call.
 * Model compatibility is determined by asking each provider class directly.
 */
final class RoutingChatLlm implements ChatLlm {

    private OpenAiChatLlm openAiLlm;      // Lazy initialized
    private AnthropicChatLlm anthropicLlm; // Lazy initialized
    private long totalTokensUsed;

    @Override
    public Outcome<ChatResponse> chatWithMetadata(String systemMessage, String userMessage, String model, double temperature) {
        ChatLlm provider = resolveProvider(model);
        Outcome<ChatResponse> result = provider.chatWithMetadata(systemMessage, userMessage, model, temperature);
        result.ifOk(response -> totalTokensUsed += response.totalTokens());
        return result;
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

    @Override
    public long getTotalTokensUsed() { return totalTokensUsed; }

    @Override
    public void resetTokenCount() {
        totalTokensUsed = 0;
        if (openAiLlm != null) openAiLlm.resetTokenCount();
        if (anthropicLlm != null) anthropicLlm.resetTokenCount();
    }
}
```

### OpenAiChatLlm

Minimal OpenAI Chat Completions API implementation.

**API Reference:** https://platform.openai.com/docs/api-reference/chat/create

```java
package org.javai.punit.examples.infrastructure.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI Chat Completions API implementation.
 *
 * <p>Uses {@link java.net.http.HttpClient} for zero external dependencies.
 * Supports the Chat Completions endpoint only.
 *
 * <h2>Model Naming</h2>
 * <p>Pass OpenAI model names directly in each call (e.g., "gpt-4o", "gpt-4o-mini").
 *
 * <h2>Error Handling</h2>
 * <p>Returns Outcome.fail for API errors. Transient errors are automatically
 * retried by the Retrier.
 */
final class OpenAiChatLlm implements ChatLlm {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String[] MODEL_PREFIXES = {"gpt-", "o1-", "o3-", "text-", "davinci"};

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration timeout;
    private long totalTokensUsed;

    /**
     * Returns true if this provider supports the given model.
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
     */
    public static String supportedModelPatterns() {
        return "gpt-*, o1-*, o3-*, text-*, davinci*";
    }

    OpenAiChatLlm(String apiKey, String baseUrl, int timeoutMs) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
        this.totalTokensUsed = 0;
    }

    @Override
    public Outcome<ChatResponse> chatWithMetadata(String systemMessage, String userMessage, String model, double temperature) {
        HttpRequest request = buildRequest(systemMessage, userMessage, model, temperature);
        return Retrier.of(() -> executeRequest(request))
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(500))
            .backoffMultiplier(2.0)
            .retryOn(Failure::isTransient)
            .execute();
    }

    @Override
    public long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    @Override
    public void resetTokenCount() {
        totalTokensUsed = 0;
    }
}
```

**Request Format:**

```json
{
  "model": "gpt-4o-mini",
  "temperature": 0.3,
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ]
}
```

**Response Parsing:**

Extract from response JSON:
- `choices[0].message.content` → content
- `usage.prompt_tokens` → promptTokens
- `usage.completion_tokens` → completionTokens

### AnthropicChatLlm

Minimal Anthropic Messages API implementation.

**API Reference:** https://docs.anthropic.com/en/api/messages

```java
package org.javai.punit.examples.infrastructure.llm;

/**
 * Anthropic Messages API implementation.
 *
 * <p>Uses {@link java.net.http.HttpClient} for zero external dependencies.
 *
 * <h2>Model Naming</h2>
 * <p>Pass Anthropic model names directly in each call (e.g., "claude-3-5-haiku-20241022",
 * "claude-sonnet-4-20250514").
 *
 * <h2>API Differences from OpenAI</h2>
 * <ul>
 *   <li>Uses {@code x-api-key} header instead of {@code Authorization: Bearer}</li>
 *   <li>Requires {@code anthropic-version} header</li>
 *   <li>System message is a top-level field, not in messages array</li>
 *   <li>Requires explicit {@code max_tokens} parameter</li>
 * </ul>
 */
final class AnthropicChatLlm implements ChatLlm {

    private static final String MESSAGES_PATH = "/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final String MODEL_PREFIX = "claude-";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration timeout;
    private long totalTokensUsed;

    /**
     * Returns true if this provider supports the given model.
     */
    public static boolean supportsModel(String model) {
        return model != null && model.startsWith(MODEL_PREFIX);
    }

    /**
     * Returns a human-readable description of supported model patterns.
     */
    public static String supportedModelPatterns() {
        return "claude-*";
    }

    AnthropicChatLlm(String apiKey, String baseUrl, int timeoutMs) { ... }

    @Override
    public Outcome<ChatResponse> chatWithMetadata(String systemMessage, String userMessage, String model, double temperature) {
        HttpRequest request = buildRequest(systemMessage, userMessage, model, temperature);
        return Retrier.of(() -> executeRequest(request))
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(500))
            .backoffMultiplier(2.0)
            .retryOn(Failure::isTransient)
            .execute();
    }

    @Override
    public long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    @Override
    public void resetTokenCount() {
        totalTokensUsed = 0;
    }
}
```

**Request Format:**

```json
{
  "model": "claude-3-5-haiku-20241022",
  "max_tokens": 1024,
  "temperature": 0.3,
  "system": "...",
  "messages": [
    {"role": "user", "content": "..."}
  ]
}
```

**Required Headers:**

```
x-api-key: sk-ant-...
anthropic-version: 2023-06-01
content-type: application/json
```

**Response Parsing:**

Extract from response JSON:
- `content[0].text` → content
- `usage.input_tokens` → promptTokens
- `usage.output_tokens` → completionTokens

### LlmApiException

Runtime exception for API errors.

```java
package org.javai.punit.examples.infrastructure.llm;

/**
 * Exception thrown when an LLM API call fails.
 *
 * <p>Contains the HTTP status code and response body for debugging.
 */
public class LlmApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public LlmApiException(String message, int statusCode, String responseBody) {
        super(message + " [HTTP " + statusCode + "]: " + truncate(responseBody, 200));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }

    public boolean isRateLimited() { return statusCode == 429; }
    public boolean isAuthError() { return statusCode == 401 || statusCode == 403; }
    public boolean isServerError() { return statusCode >= 500; }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
```

### LlmConfigurationException

Exception for configuration errors (missing keys, invalid provider).

```java
package org.javai.punit.examples.infrastructure.llm;

/**
 * Exception thrown when LLM configuration is invalid.
 *
 * <p>Provides actionable error messages indicating how to fix the configuration.
 */
public class LlmConfigurationException extends RuntimeException {
    public LlmConfigurationException(String message) {
        super(message);
    }
}
```

## LLM-Powered Mutators

### Motivation

`ShoppingBasketPromptMutator` currently uses a deterministic prompt progression—a scripted sequence of improvements. This is useful for demonstration, but doesn't showcase genuine optimization.

In `real` mode, mutators could use an actual LLM to:
1. Analyze failure patterns from previous iterations
2. Generate targeted prompt improvements
3. Demonstrate genuine AI-assisted prompt engineering

### Design Pattern

The mutator can internally switch between mock and real strategies:

```java
public class ShoppingBasketPromptMutator implements FactorMutator<String> {

    private final MutationStrategy strategy;

    public ShoppingBasketPromptMutator() {
        this.strategy = ChatLlmProvider.isRealMode()
            ? new LlmMutationStrategy()
            : new DeterministicMutationStrategy();
    }

    @Override
    public String mutate(String currentPrompt, OptimizeHistory history) throws MutationException {
        return strategy.mutate(currentPrompt, history);
    }

    private interface MutationStrategy {
        String mutate(String currentPrompt, OptimizeHistory history) throws MutationException;
    }
}
```

### DeterministicMutationStrategy

The existing behavior—a scripted sequence of prompt improvements:

```java
private static class DeterministicMutationStrategy implements MutationStrategy {

    private static final String[] PROMPT_PROGRESSION = { /* existing prompts */ };

    @Override
    public String mutate(String currentPrompt, OptimizeHistory history) {
        int index = history.iterationCount() - 1;
        if (index >= 0 && index < PROMPT_PROGRESSION.length) {
            return PROMPT_PROGRESSION[index];
        }
        return PROMPT_PROGRESSION[PROMPT_PROGRESSION.length - 1];
    }
}
```

### LlmMutationStrategy

Uses an LLM to generate prompt improvements based on failure analysis:

```java
private static class LlmMutationStrategy implements MutationStrategy {

    private static final String MUTATION_MODEL = "gpt-4o-mini";  // Or configurable

    private static final String META_PROMPT = """
        You are an expert prompt engineer. Analyze the following prompt and its failure cases,
        then produce an improved version.

        CURRENT PROMPT:
        %s

        RECENT FAILURE PATTERNS:
        %s

        SUCCESS RATE: %.1f%% (%d/%d samples)

        Produce an improved prompt that addresses the failure patterns.
        Output ONLY the new prompt text, no explanations.
        """;

    @Override
    public String mutate(String currentPrompt, OptimizeHistory history) throws MutationException {
        String failurePatterns = extractFailurePatterns(history);
        double successRate = history.lastIterationScore().orElse(0.0) * 100;
        int successes = history.lastIterationSuccesses();
        int total = history.lastIterationSamples();

        String metaPrompt = META_PROMPT.formatted(currentPrompt, failurePatterns, successRate, successes, total);

        ChatLlm llm = ChatLlmProvider.resolve();

        // Model is passed explicitly in the call
        String improved = llm.chat(
            "You are an expert prompt engineer.",
            metaPrompt,
            MUTATION_MODEL,  // Model specified here
            0.7              // Higher temperature for creative improvements
        );

        return improved.trim();
    }

    private String extractFailurePatterns(OptimizeHistory history) {
        // Extract common failure reasons from history
        // e.g., "JSON parse errors: 3, Invalid action: 2, Missing quantity: 1"
        return history.lastIterationFailureReasons()
            .entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining("\n"));
    }
}
```

### Configuration

The mutation model can be configured separately from the main experiment models:

| Property | Env Var | Default | Description |
|----------|---------|---------|-------------|
| `punit.llm.mutation.model` | `PUNIT_LLM_MUTATION_MODEL` | `gpt-4o-mini` | Model used for prompt mutation |

This allows using a cost-effective model for mutations while the experiment explores more expensive models.

### Benefits

1. **Genuine Optimization** - Demonstrates real AI-assisted prompt engineering
2. **Adaptive** - Responds to actual failure patterns, not scripted
3. **Educational** - Shows how meta-prompting can improve prompts
4. **Compelling Demo** - Users see PUnit doing intelligent optimization

### Implementation Priority

This is a **Phase 2** enhancement. The core mock/real switching for `ChatLlm` should be implemented first. The mutator enhancement can follow once the infrastructure is proven.

## Interface Changes

### ChatLlm Interface Update

The interface changes to:
1. Return `Outcome<ChatResponse>` for proper error handling
2. **Pass model explicitly in method signature** (no mutable state)

This is a raw API design—each call is self-contained with all parameters specified explicitly.

```java
public interface ChatLlm {

    /**
     * Sends a chat request and returns the response wrapped in an Outcome.
     *
     * <p>For real implementations, transient errors are automatically retried.
     * Permanent errors return Outcome.fail immediately.
     *
     * @param systemMessage the system prompt
     * @param userMessage the user message
     * @param model the model identifier (e.g., "gpt-4o-mini", "claude-3-5-haiku-20241022")
     * @param temperature the sampling temperature (0.0 to 1.0)
     * @return Outcome containing ChatResponse on success, or Failure on error
     */
    Outcome<ChatResponse> chatWithMetadata(String systemMessage, String userMessage, String model, double temperature);

    /**
     * Convenience method that extracts content or throws on failure.
     *
     * @throws LlmApiException if the call fails after retries
     */
    default String chat(String systemMessage, String userMessage, String model, double temperature) {
        return chatWithMetadata(systemMessage, userMessage, model, temperature)
            .map(ChatResponse::content)
            .orElseThrow(failure -> new LlmApiException(failure.message()));
    }

    long getTotalTokensUsed();

    void resetTokenCount();
}
```

**Rationale:** Passing model explicitly in each call:
- Eliminates mutable state in LLM instances
- Makes each call self-documenting
- Simplifies `RoutingChatLlm` (no need to track "current model")
- Aligns with the raw, minimal API philosophy

### MockChatLlm Update

The mock implementation accepts the model parameter (ignoring it) and returns `Outcome.ok()` for successful responses:

```java
@Override
public Outcome<ChatResponse> chatWithMetadata(String systemMessage, String userMessage, String model, double temperature) {
    // Model parameter is ignored in mock - behavior is determined by temperature only
    // ... existing mock logic ...
    return Outcome.ok(new ChatResponse(response, promptTokens, completionTokens));
}
```

### ShoppingBasketUseCase Update

The use case stores the model and passes it explicitly in each LLM call:

```java
public class ShoppingBasketUseCase {

    private final ChatLlm llm;
    private String model = "gpt-4o-mini";  // Default model
    private double temperature = 0.3;

    public ShoppingBasketUseCase() {
        this(ChatLlmProvider.resolve());
    }

    public ShoppingBasketUseCase(ChatLlm llm) {
        this.llm = llm;
    }

    @FactorSetter("llm_model")
    public void setModel(String model) {
        this.model = model;
    }

    @FactorSetter("temperature")
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public ShoppingAction translateInstruction(String instruction) {
        // Model is passed explicitly to each LLM call
        Outcome<ChatResponse> result = llm.chatWithMetadata(
            buildSystemPrompt(),
            instruction,
            model,          // <-- explicit model parameter
            temperature
        );

        return result
            .map(ChatResponse::content)
            .map(this::parseAction)
            .orElseThrow(failure -> new TranslationException(failure.message()));
    }
}
```

## JSON Handling

Use a minimal JSON builder/parser to avoid dependencies. Two approaches:

### Option A: String Concatenation (Simplest)

```java
private String buildRequestBody(String model, String systemMessage, String userMessage, double temperature) {
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

private String escapeJson(String s) {
    return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
}

private String jsonString(String s) {
    return "\"" + escapeJson(s) + "\"";
}
```

### Option B: Simple JSON Parser for Response

```java
private ChatResponse parseResponse(String json) {
    // Extract using indexOf/substring - brittle but dependency-free
    String content = extractJsonString(json, "content");
    int promptTokens = extractJsonInt(json, "prompt_tokens");
    int completionTokens = extractJsonInt(json, "completion_tokens");
    return new ChatResponse(content, promptTokens, completionTokens);
}
```

### Option C: Use Jackson (Already in Test Scope)

The project already uses Jackson in test scope for `ShoppingActionValidator`. Reusing it here is pragmatic:

```java
private static final ObjectMapper MAPPER = new ObjectMapper();

private ChatResponse parseResponse(String json) throws JsonProcessingException {
    JsonNode root = MAPPER.readTree(json);
    String content = root.at("/choices/0/message/content").asText();
    int promptTokens = root.at("/usage/prompt_tokens").asInt();
    int completionTokens = root.at("/usage/completion_tokens").asInt();
    return new ChatResponse(content, promptTokens, completionTokens);
}
```

**Recommendation:** Option C (Jackson) since it's already available and more robust.

## Error Handling Strategy

The implementation uses the **Outcome library's `Boundary`** type to wrap checked exceptions from HTTP calls, returning `Outcome<ChatResponse>` instead of throwing exceptions. For transient errors, **`Retrier`** provides automatic retry with configurable policies.

### Return Type Change

The `ChatLlm` interface methods return `Outcome<ChatResponse>` rather than throwing exceptions:

```java
public interface ChatLlm {

    /**
     * Sends a chat request and returns the response wrapped in an Outcome.
     *
     * <p>Transient errors (timeouts, 429, 5xx) are automatically retried.
     * Permanent errors (401, 403, 400) return Outcome.fail immediately.
     */
    Outcome<ChatResponse> chatWithMetadata(String systemMessage, String userMessage, double temperature);

    /**
     * Convenience method that extracts content or throws on failure.
     */
    default String chat(String systemMessage, String userMessage, double temperature) {
        return chatWithMetadata(systemMessage, userMessage, temperature)
            .orElseThrow(failure -> new LlmApiException(failure.message()));
    }
}
```

### HTTP Status Classification

| Status | Classification | Outcome | Retry? |
|--------|---------------|---------|--------|
| 200 | Success | `Outcome.ok(response)` | N/A |
| 400 | Permanent (bad request) | `Outcome.fail("bad_request", ...)` | No |
| 401 | Permanent (auth) | `Outcome.fail("unauthorized", ...)` | No |
| 403 | Permanent (forbidden) | `Outcome.fail("forbidden", ...)` | No |
| 429 | Transient (rate limit) | Retry with backoff | Yes |
| 500+ | Transient (server) | Retry with backoff | Yes |
| Timeout | Transient (network) | Retry with backoff | Yes |
| IOException | Transient (network) | Retry with backoff | Yes |

### Using Boundary for Exception Wrapping

`Boundary` wraps code that throws checked exceptions and converts to `Outcome`:

```java
private Outcome<HttpResponse<String>> sendRequest(HttpRequest request) {
    return Boundary.of(() -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()))
        .mapFailure(e -> classifyException(e));
}

private Failure classifyException(Throwable e) {
    if (e instanceof HttpTimeoutException) {
        return Failure.transient_("timeout", "Request timed out after " + timeout.toMillis() + "ms");
    }
    if (e instanceof IOException) {
        return Failure.transient_("network", "Network error: " + e.getMessage());
    }
    return Failure.of("unexpected", e.getMessage());
}
```

### Using Retrier for Transient Errors

`Retrier` automatically retries operations that return transient failures:

```java
@Override
public Outcome<ChatResponse> chatWithMetadata(String systemMessage, String userMessage, double temperature) {
    HttpRequest request = buildRequest(systemMessage, userMessage, temperature);

    return Retrier.of(() -> executeRequest(request))
        .maxAttempts(3)
        .initialDelay(Duration.ofMillis(500))
        .backoffMultiplier(2.0)          // 500ms, 1s, 2s
        .maxDelay(Duration.ofSeconds(5))
        .retryOn(Failure::isTransient)   // Only retry transient failures
        .execute();
}

private Outcome<ChatResponse> executeRequest(HttpRequest request) {
    return sendRequest(request)
        .flatMap(this::classifyHttpResponse)
        .map(this::parseResponse);
}

private Outcome<String> classifyHttpResponse(HttpResponse<String> response) {
    int status = response.statusCode();
    String body = response.body();

    if (status == 200) {
        return Outcome.ok(body);
    }

    // Transient errors - will be retried
    if (status == 429) {
        return Outcome.fail(Failure.transient_("rate_limited", "Rate limited: " + body));
    }
    if (status >= 500) {
        return Outcome.fail(Failure.transient_("server_error", "Server error " + status + ": " + body));
    }

    // Permanent errors - no retry
    return Outcome.fail(Failure.of(classifyPermanentError(status), body));
}

private String classifyPermanentError(int status) {
    return switch (status) {
        case 400 -> "bad_request";
        case 401 -> "unauthorized";
        case 403 -> "forbidden";
        default -> "client_error";
    };
}
```

### Retry Policy for Demo

A sensible demo-friendly retry policy:

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Max attempts | 3 | Enough for transient issues, not excessive |
| Initial delay | 500ms | Quick first retry |
| Backoff multiplier | 2.0 | Standard exponential backoff |
| Max delay | 5s | Caps wait time for rate limits |
| Retry on | Transient failures only | Don't retry auth errors |

### Caller Usage

Callers can handle success/failure explicitly:

```java
Outcome<ChatResponse> result = llm.chatWithMetadata(system, user, temp);

result.fold(
    response -> {
        // Success: process response
        processResponse(response);
    },
    failure -> {
        // Failure: all retries exhausted or permanent error
        if (failure.isTransient()) {
            log.warn("LLM call failed after retries: {}", failure.message());
        } else {
            log.error("LLM call permanently failed: {}", failure.message());
        }
    }
);
```

Or use the convenience method that throws:

```java
String content = llm.chat(system, user, temp);  // Throws LlmApiException on failure
```

## Cost Tracking

### Logging

Log each API call with cost estimate:

```java
private static final Logger LOG = Logger.getLogger(OpenAiChatLlm.class.getName());

// After successful response
LOG.fine(() -> String.format(
    "OpenAI API call: model=%s, prompt_tokens=%d, completion_tokens=%d, est_cost=$%.4f",
    model, promptTokens, completionTokens,
    estimateCost(model, promptTokens, completionTokens)));

private double estimateCost(String model, int promptTokens, int completionTokens) {
    // Approximate costs per 1M tokens (as of Jan 2025)
    return switch (model) {
        case "gpt-4o" -> (promptTokens * 2.50 + completionTokens * 10.00) / 1_000_000;
        case "gpt-4o-mini" -> (promptTokens * 0.15 + completionTokens * 0.60) / 1_000_000;
        default -> (promptTokens * 1.00 + completionTokens * 2.00) / 1_000_000;  // Conservative estimate
    };
}
```

### Budget Integration

The existing `CostBudgetMonitor` tracks token usage. Real LLM implementations contribute to this:

```java
@Override
public ChatResponse chatWithMetadata(...) {
    // ... make API call ...

    int totalTokens = promptTokens + completionTokens;
    totalTokensUsed += totalTokens;

    return new ChatResponse(content, promptTokens, completionTokens);
}
```

## Testing Strategy

### Unit Tests

Test configuration resolution, JSON building, response parsing:

```java
@Nested
class ChatLlmProviderTest {

    @Test
    void defaultsToMock() {
        // No env vars set
        assertThat(ChatLlmProvider.resolvedProviderName()).isEqualTo("mock");
    }

    @Test
    void readsSystemProperty() {
        System.setProperty("punit.llm.provider", "openai");
        try {
            assertThat(ChatLlmProvider.resolvedProviderName()).isEqualTo("openai");
        } finally {
            System.clearProperty("punit.llm.provider");
        }
    }

    @Test
    void failsWithoutApiKey() {
        System.setProperty("punit.llm.provider", "openai");
        try {
            assertThatThrownBy(ChatLlmProvider::resolve)
                .isInstanceOf(LlmConfigurationException.class)
                .hasMessageContaining("OPENAI_API_KEY");
        } finally {
            System.clearProperty("punit.llm.provider");
        }
    }
}
```

### Integration Tests (Manual)

Create a separate test class that requires real credentials:

```java
/**
 * Integration tests for real LLM providers.
 *
 * <p>Run manually with credentials:
 * <pre>
 * OPENAI_API_KEY=sk-... ./gradlew test --tests "RealLlmIntegrationTest"
 * </pre>
 */
@Disabled("Requires API credentials - run manually")
class RealLlmIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void openAiBasicCall() {
        ChatLlm llm = new OpenAiChatLlm(
            System.getenv("OPENAI_API_KEY"),
            "https://api.openai.com/v1",
            30000
        );

        Outcome<ChatResponse> result = llm.chatWithMetadata(
            "You are a helpful assistant.",
            "Say 'hello' and nothing else.",
            "gpt-4o-mini",  // Model passed explicitly
            0.0
        );

        assertThat(result.isOk()).isTrue();
        assertThat(result.get().content().toLowerCase()).contains("hello");
        assertThat(result.get().totalTokens()).isGreaterThan(0);
    }
}
```

### Architecture Test

Ensure unit tests never instantiate real LLM providers:

```java
@Test
@DisplayName("Unit tests must not instantiate real LLM providers")
void unitTestsMustNotInstantiateRealLlmProviders() {
    JavaClasses testClasses = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
        .importPackages("org.javai.punit");

    ArchRule rule = noClasses()
        .that().resideOutsideOfPackage("..integration..")
        .should().dependOnClassesThat()
        .haveSimpleNameEndingWith("ChatLlm")
        .andShould().callConstructor(OpenAiChatLlm.class)
        .orShould().callConstructor(AnthropicChatLlm.class);

    rule.check(testClasses);
}
```

## Security Considerations

1. **API Key Handling**
   - Never log API keys
   - Mask keys in error messages: `sk-...abc` → `sk-***abc`
   - Keys only read from env vars or system properties, never from files in repo

2. **Request/Response Logging**
   - Use `FINE` level for request/response bodies (disabled by default)
   - Never log at `INFO` or higher with full content

3. **Base URL Validation**
   - Validate URLs start with `https://` for production providers
   - Allow `http://localhost` for local testing/proxies

## Implementation Checklist

### Phase 1: Core Infrastructure
- [x] Create `LlmConfigurationException`
- [x] Create `LlmApiException`
- [x] Update `ChatLlm` interface to accept model parameter in method signature
- [x] Update `MockChatLlm` to accept (and ignore) model parameter

### Phase 2: Provider Implementations
- [x] Create `OpenAiChatLlm` with raw HTTP
- [x] Create `AnthropicChatLlm` with raw HTTP
- [x] Implement JSON request building
- [x] Implement response parsing (using Jackson)
- [x] Add cost estimation logging

### Phase 3: Routing and Factory
- [x] Create `RoutingChatLlm` with model → provider routing
- [x] Create `ChatLlmProvider` with mock/real mode resolution
- [x] Implement lazy provider instantiation
- [x] Add unit tests for mode resolution and routing

### Phase 4: Use Case Integration
- [x] Update `ShoppingBasketUseCase` constructor to use `ChatLlmProvider.resolve()`
- [x] Update `translateInstruction` to pass model explicitly to LLM
- [x] Update `ShoppingBasketExplore` factor providers with real model names
- [x] Add manual integration tests (require API keys)

### Phase 5: Documentation
- [x] Update example documentation with configuration instructions
- [x] Add cost warnings to experiment documentation
- [x] Document model → provider mapping

### Phase 6: LLM-Powered Mutators (Optional Enhancement)
- [x] Create `MutationStrategy` interface
- [x] Refactor `ShoppingBasketPromptMutator` to use strategy pattern
- [x] Implement `LlmMutationStrategy` for real mode
- [x] Add `punit.llm.mutation.model` configuration
- [x] Extend `OptimizeHistory` with failure pattern extraction (not needed - existing API sufficient)

## Future Considerations

1. **Retry with Backoff** - Add configurable retry for transient errors (429, 5xx)
2. **Streaming** - Support streaming responses for long generations
3. **Additional Providers** - Google Gemini, Azure OpenAI, local Ollama
4. **Response Caching** - Cache identical requests for development (hash system+user message)
5. **Structured Output** - JSON mode / schema enforcement when available
6. **Provider Auto-Detection** - Detect available API keys and warn about missing ones upfront

## Appendix: API Request Examples

### OpenAI Chat Completions

```bash
curl https://api.openai.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "model": "gpt-4o-mini",
    "temperature": 0.3,
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "Hello"}
    ]
  }'
```

### Anthropic Messages

```bash
curl https://api.anthropic.com/v1/messages \
  -H "Content-Type: application/json" \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -d '{
    "model": "claude-3-5-haiku-20241022",
    "max_tokens": 1024,
    "temperature": 0.3,
    "system": "You are a helpful assistant.",
    "messages": [
      {"role": "user", "content": "Hello"}
    ]
  }'
```

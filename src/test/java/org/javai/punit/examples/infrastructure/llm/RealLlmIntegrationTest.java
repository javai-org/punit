package org.javai.punit.examples.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Manual integration tests for real LLM providers.
 *
 * <p>These tests are disabled by default because they require API keys.
 * To run manually:
 *
 * <pre>{@code
 * # Set up API keys
 * export OPENAI_API_KEY=sk-...
 * export ANTHROPIC_API_KEY=sk-ant-...
 *
 * # Run a specific test
 * ./gradlew test --tests "RealLlmIntegrationTest.OpenAiIntegration" -Dpunit.llm.mode=real
 *
 * # Or run all integration tests
 * ./gradlew test --tests "RealLlmIntegrationTest" -Dpunit.llm.mode=real
 * }</pre>
 *
 * <p>These tests verify that the real LLM implementations work correctly
 * with actual API calls. They are not part of the regular CI pipeline.
 */
@Disabled("Manual integration tests - require API keys. See class javadoc.")
@DisplayName("Real LLM Integration Tests")
class RealLlmIntegrationTest {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String ANTHROPIC_BASE_URL = "https://api.anthropic.com/v1";
    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    @Nested
    @DisplayName("OpenAI Integration")
    class OpenAiIntegration {

        private OpenAiChatLlm llm;

        @BeforeEach
        void setUp() {
            String apiKey = resolveApiKey("OPENAI_API_KEY");
            assumeTrue(apiKey != null && !apiKey.isBlank(),
                    "OPENAI_API_KEY not set - skipping OpenAI integration tests");
            llm = new OpenAiChatLlm(apiKey, OPENAI_BASE_URL, DEFAULT_TIMEOUT_MS);
        }

        @Test
        @DisplayName("gpt-4o-mini can complete a simple chat")
        void gpt4oMiniCanCompleteSimpleChat() {
            ChatResponse response = llm.chatWithMetadata(
                    "You are a helpful assistant. Respond with just 'Hello!' and nothing else.",
                    "Say hello",
                    "gpt-4o-mini",
                    0.0
            );

            assertThat(response.content()).isNotBlank();
            assertThat(response.totalTokens()).isPositive();
            System.out.println("Response: " + response.content());
            System.out.println("Tokens: " + response.totalTokens());
        }

        @Test
        @DisplayName("gpt-4o can complete a simple chat")
        void gpt4oCanCompleteSimpleChat() {
            ChatResponse response = llm.chatWithMetadata(
                    "You are a helpful assistant. Respond with just 'Hello!' and nothing else.",
                    "Say hello",
                    "gpt-4o",
                    0.0
            );

            assertThat(response.content()).isNotBlank();
            assertThat(response.totalTokens()).isPositive();
            System.out.println("Response: " + response.content());
            System.out.println("Tokens: " + response.totalTokens());
        }

        @Test
        @DisplayName("can generate JSON output")
        void canGenerateJsonOutput() {
            String response = llm.chat(
                    """
                    You are a JSON generator. Output ONLY valid JSON, no markdown.
                    Always respond with: {"greeting": "hello"}
                    """,
                    "Generate a greeting",
                    "gpt-4o-mini",
                    0.0
            );

            assertThat(response).contains("greeting");
            assertThat(response).contains("hello");
            System.out.println("JSON Response: " + response);
        }
    }

    @Nested
    @DisplayName("Anthropic Integration")
    class AnthropicIntegration {

        private AnthropicChatLlm llm;

        @BeforeEach
        void setUp() {
            String apiKey = resolveApiKey("ANTHROPIC_API_KEY");
            assumeTrue(apiKey != null && !apiKey.isBlank(),
                    "ANTHROPIC_API_KEY not set - skipping Anthropic integration tests");
            llm = new AnthropicChatLlm(apiKey, ANTHROPIC_BASE_URL, DEFAULT_TIMEOUT_MS);
        }

        @Test
        @DisplayName("claude-3-5-haiku can complete a simple chat")
        void claudeHaikuCanCompleteSimpleChat() {
            ChatResponse response = llm.chatWithMetadata(
                    "You are a helpful assistant. Respond with just 'Hello!' and nothing else.",
                    "Say hello",
                    "claude-3-5-haiku-20241022",
                    0.0
            );

            assertThat(response.content()).isNotBlank();
            assertThat(response.totalTokens()).isPositive();
            System.out.println("Response: " + response.content());
            System.out.println("Tokens: " + response.totalTokens());
        }

        @Test
        @DisplayName("claude-3-5-sonnet can complete a simple chat")
        void claudeSonnetCanCompleteSimpleChat() {
            ChatResponse response = llm.chatWithMetadata(
                    "You are a helpful assistant. Respond with just 'Hello!' and nothing else.",
                    "Say hello",
                    "claude-3-5-sonnet-20241022",
                    0.0
            );

            assertThat(response.content()).isNotBlank();
            assertThat(response.totalTokens()).isPositive();
            System.out.println("Response: " + response.content());
            System.out.println("Tokens: " + response.totalTokens());
        }

        @Test
        @DisplayName("can generate JSON output")
        void canGenerateJsonOutput() {
            String response = llm.chat(
                    """
                    You are a JSON generator. Output ONLY valid JSON, no markdown.
                    Always respond with: {"greeting": "hello"}
                    """,
                    "Generate a greeting",
                    "claude-3-5-haiku-20241022",
                    0.0
            );

            assertThat(response).contains("greeting");
            assertThat(response).contains("hello");
            System.out.println("JSON Response: " + response);
        }
    }

    @Nested
    @DisplayName("Routing Integration")
    class RoutingIntegration {

        @BeforeEach
        void setUp() {
            String openAiKey = resolveApiKey("OPENAI_API_KEY");
            String anthropicKey = resolveApiKey("ANTHROPIC_API_KEY");
            assumeTrue(
                    (openAiKey != null && !openAiKey.isBlank())
                            || (anthropicKey != null && !anthropicKey.isBlank()),
                    "At least one API key required - skipping routing tests"
            );
        }

        @Test
        @DisplayName("RoutingChatLlm routes to correct provider")
        void routingChatLlmRoutesToCorrectProvider() {
            ChatLlm router = new RoutingChatLlm();

            // Test OpenAI routing if key available
            String openAiKey = resolveApiKey("OPENAI_API_KEY");
            if (openAiKey != null && !openAiKey.isBlank()) {
                ChatResponse openAiResponse = router.chatWithMetadata(
                        "Respond with 'OpenAI works'",
                        "Test",
                        "gpt-4o-mini",
                        0.0
                );
                assertThat(openAiResponse.content()).isNotBlank();
                System.out.println("OpenAI via router: " + openAiResponse.content());
            }

            // Test Anthropic routing if key available
            String anthropicKey = resolveApiKey("ANTHROPIC_API_KEY");
            if (anthropicKey != null && !anthropicKey.isBlank()) {
                ChatResponse anthropicResponse = router.chatWithMetadata(
                        "Respond with 'Anthropic works'",
                        "Test",
                        "claude-3-5-haiku-20241022",
                        0.0
                );
                assertThat(anthropicResponse.content()).isNotBlank();
                System.out.println("Anthropic via router: " + anthropicResponse.content());
            }
        }

        @Test
        @DisplayName("ChatLlmProvider resolves to RoutingChatLlm in real mode")
        void chatLlmProviderResolvesInRealMode() {
            assumeTrue(ChatLlmProvider.isRealMode(),
                    "Test requires -Dpunit.llm.mode=real");

            ChatLlm llm = ChatLlmProvider.resolve();
            assertThat(llm).isInstanceOf(RoutingChatLlm.class);
        }
    }

    private static String resolveApiKey(String envVar) {
        String value = System.getenv(envVar);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return null;
    }
}

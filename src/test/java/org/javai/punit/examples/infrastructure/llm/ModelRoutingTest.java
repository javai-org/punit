package org.javai.punit.examples.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Model Routing")
class ModelRoutingTest {

    @Nested
    @DisplayName("OpenAiChatLlm.supportsModel()")
    class OpenAiSupportsModel {

        @ParameterizedTest
        @ValueSource(strings = {
                "gpt-4o",
                "gpt-4o-mini",
                "gpt-4-turbo",
                "gpt-3.5-turbo",
                "o1-preview",
                "o1-mini",
                "o3-mini",
                "text-embedding-ada-002",
                "davinci-002"
        })
        @DisplayName("returns true for OpenAI models")
        void returnsTrueForOpenAiModels(String model) {
            assertThat(OpenAiChatLlm.supportsModel(model)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "claude-3-5-haiku-20241022",
                "claude-3-5-sonnet-20241022",
                "claude-opus-4-20250514",
                "gemini-pro",
                "llama-2-70b"
        })
        @DisplayName("returns false for non-OpenAI models")
        void returnsFalseForNonOpenAiModels(String model) {
            assertThat(OpenAiChatLlm.supportsModel(model)).isFalse();
        }

        @Test
        @DisplayName("returns false for null")
        void returnsFalseForNull() {
            assertThat(OpenAiChatLlm.supportsModel(null)).isFalse();
        }

        @Test
        @DisplayName("supportedModelPatterns returns descriptive string")
        void supportedModelPatternsReturnsDescriptiveString() {
            String patterns = OpenAiChatLlm.supportedModelPatterns();

            assertThat(patterns).contains("gpt-*");
            assertThat(patterns).contains("o1-*");
            assertThat(patterns).contains("o3-*");
        }
    }

    @Nested
    @DisplayName("AnthropicChatLlm.supportsModel()")
    class AnthropicSupportsModel {

        @ParameterizedTest
        @ValueSource(strings = {
                "claude-3-5-haiku-20241022",
                "claude-3-5-sonnet-20241022",
                "claude-opus-4-20250514",
                "claude-sonnet-4-20250514",
                "claude-3-opus-20240229",
                "claude-2.1",
                "claude-instant-1.2"
        })
        @DisplayName("returns true for Anthropic models")
        void returnsTrueForAnthropicModels(String model) {
            assertThat(AnthropicChatLlm.supportsModel(model)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "gpt-4o",
                "gpt-4o-mini",
                "o1-preview",
                "gemini-pro",
                "llama-2-70b"
        })
        @DisplayName("returns false for non-Anthropic models")
        void returnsFalseForNonAnthropicModels(String model) {
            assertThat(AnthropicChatLlm.supportsModel(model)).isFalse();
        }

        @Test
        @DisplayName("returns false for null")
        void returnsFalseForNull() {
            assertThat(AnthropicChatLlm.supportsModel(null)).isFalse();
        }

        @Test
        @DisplayName("supportedModelPatterns returns 'claude-*'")
        void supportedModelPatternsReturnsClaudePattern() {
            assertThat(AnthropicChatLlm.supportedModelPatterns()).isEqualTo("claude-*");
        }
    }

    @Nested
    @DisplayName("Model routing coverage")
    class RoutingCoverage {

        @Test
        @DisplayName("all common models are covered by exactly one provider")
        void allCommonModelsAreCoveredByExactlyOneProvider() {
            String[] openAiModels = {"gpt-4o", "gpt-4o-mini", "o1-preview", "o1-mini"};
            String[] anthropicModels = {"claude-3-5-haiku-20241022", "claude-3-5-sonnet-20241022"};

            for (String model : openAiModels) {
                assertThat(OpenAiChatLlm.supportsModel(model))
                        .as("OpenAI should support %s", model)
                        .isTrue();
                assertThat(AnthropicChatLlm.supportsModel(model))
                        .as("Anthropic should not support %s", model)
                        .isFalse();
            }

            for (String model : anthropicModels) {
                assertThat(AnthropicChatLlm.supportsModel(model))
                        .as("Anthropic should support %s", model)
                        .isTrue();
                assertThat(OpenAiChatLlm.supportsModel(model))
                        .as("OpenAI should not support %s", model)
                        .isFalse();
            }
        }
    }
}

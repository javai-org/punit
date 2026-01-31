package org.javai.punit.examples.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ChatLlmProvider")
class ChatLlmProviderTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("punit.llm.mode");
    }

    @Nested
    @DisplayName("resolve()")
    class Resolve {

        @Test
        @DisplayName("returns MockChatLlm by default")
        void returnsMockChatLlmByDefault() {
            ChatLlm llm = ChatLlmProvider.resolve();

            assertThat(llm).isInstanceOf(MockChatLlm.class);
        }

        @Test
        @DisplayName("returns MockChatLlm when mode is 'mock'")
        void returnsMockChatLlmWhenModeIsMock() {
            System.setProperty("punit.llm.mode", "mock");

            ChatLlm llm = ChatLlmProvider.resolve();

            assertThat(llm).isInstanceOf(MockChatLlm.class);
        }

        @Test
        @DisplayName("returns RoutingChatLlm when mode is 'real'")
        void returnsRoutingChatLlmWhenModeIsReal() {
            System.setProperty("punit.llm.mode", "real");

            ChatLlm llm = ChatLlmProvider.resolve();

            assertThat(llm).isInstanceOf(RoutingChatLlm.class);
        }

        @Test
        @DisplayName("mode is case-insensitive")
        void modeIsCaseInsensitive() {
            System.setProperty("punit.llm.mode", "REAL");

            ChatLlm llm = ChatLlmProvider.resolve();

            assertThat(llm).isInstanceOf(RoutingChatLlm.class);
        }

        @Test
        @DisplayName("throws for invalid mode")
        void throwsForInvalidMode() {
            System.setProperty("punit.llm.mode", "invalid");

            assertThatThrownBy(ChatLlmProvider::resolve)
                    .isInstanceOf(LlmConfigurationException.class)
                    .hasMessageContaining("Unknown LLM mode: 'invalid'")
                    .hasMessageContaining("mock, real");
        }
    }

    @Nested
    @DisplayName("resolvedMode()")
    class ResolvedMode {

        @Test
        @DisplayName("returns 'mock' by default")
        void returnsMockByDefault() {
            assertThat(ChatLlmProvider.resolvedMode()).isEqualTo("mock");
        }

        @Test
        @DisplayName("returns system property value")
        void returnsSystemPropertyValue() {
            System.setProperty("punit.llm.mode", "real");

            assertThat(ChatLlmProvider.resolvedMode()).isEqualTo("real");
        }
    }

    @Nested
    @DisplayName("isRealMode()")
    class IsRealMode {

        @Test
        @DisplayName("returns false by default")
        void returnsFalseByDefault() {
            assertThat(ChatLlmProvider.isRealMode()).isFalse();
        }

        @Test
        @DisplayName("returns true when mode is 'real'")
        void returnsTrueWhenModeIsReal() {
            System.setProperty("punit.llm.mode", "real");

            assertThat(ChatLlmProvider.isRealMode()).isTrue();
        }

        @Test
        @DisplayName("is case-insensitive")
        void isCaseInsensitive() {
            System.setProperty("punit.llm.mode", "REAL");

            assertThat(ChatLlmProvider.isRealMode()).isTrue();
        }
    }

    @Nested
    @DisplayName("isMockMode()")
    class IsMockMode {

        @Test
        @DisplayName("returns true by default")
        void returnsTrueByDefault() {
            assertThat(ChatLlmProvider.isMockMode()).isTrue();
        }

        @Test
        @DisplayName("returns false when mode is 'real'")
        void returnsFalseWhenModeIsReal() {
            System.setProperty("punit.llm.mode", "real");

            assertThat(ChatLlmProvider.isMockMode()).isFalse();
        }
    }
}

package org.javai.punit.sentinel.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.javai.punit.reporting.VerdictEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebhookVerdictSinkTest {

    private static VerdictEvent sampleEvent() {
        return new VerdictEvent(
                "v:abc123", "testMethod", "useCase1", true,
                Map.of("passRate", "0.95"),
                Map.of("environment", "staging"),
                Instant.parse("2026-01-15T10:30:00Z"));
    }

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("builds with minimal configuration")
        void buildsWithMinimalConfig() {
            var sink = WebhookVerdictSink.builder("https://example.com/webhook").build();

            assertThat(sink).isNotNull();
        }

        @Test
        @DisplayName("accepts custom timeout")
        void acceptsCustomTimeout() {
            var sink = WebhookVerdictSink.builder("https://example.com/webhook")
                    .timeout(Duration.ofSeconds(5))
                    .build();

            assertThat(sink).isNotNull();
        }

        @Test
        @DisplayName("accepts custom headers")
        void acceptsCustomHeaders() {
            var sink = WebhookVerdictSink.builder("https://example.com/webhook")
                    .header("Authorization", "Bearer token123")
                    .header("X-Custom", "value")
                    .build();

            assertThat(sink).isNotNull();
        }
    }

    @Nested
    @DisplayName("toJson()")
    class ToJson {

        @Test
        @DisplayName("produces valid JSON with all fields")
        void producesValidJsonWithAllFields() {
            var event = sampleEvent();
            String json = WebhookVerdictSink.toJson(event);

            assertThat(json).contains("\"correlationId\":\"v:abc123\"");
            assertThat(json).contains("\"testName\":\"testMethod\"");
            assertThat(json).contains("\"useCaseId\":\"useCase1\"");
            assertThat(json).contains("\"passed\":true");
            assertThat(json).contains("\"timestamp\":\"2026-01-15T10:30:00Z\"");
        }

        @Test
        @DisplayName("serialises report entries")
        void serialisesReportEntries() {
            var event = sampleEvent();
            String json = WebhookVerdictSink.toJson(event);

            assertThat(json).contains("\"reportEntries\":{");
            assertThat(json).contains("\"passRate\":\"0.95\"");
        }

        @Test
        @DisplayName("serialises environment metadata")
        void serialisesEnvironmentMetadata() {
            var event = sampleEvent();
            String json = WebhookVerdictSink.toJson(event);

            assertThat(json).contains("\"environmentMetadata\":{");
            assertThat(json).contains("\"environment\":\"staging\"");
        }

        @Test
        @DisplayName("serialises false passed value")
        void serialisesFalsePassedValue() {
            var event = new VerdictEvent(
                    "v:fail01", "failingTest", "uc2", false,
                    Map.of(), Map.of(), Instant.now());
            String json = WebhookVerdictSink.toJson(event);

            assertThat(json).contains("\"passed\":false");
        }

        @Test
        @DisplayName("escapes quotes in string values")
        void escapesQuotesInValues() {
            var event = new VerdictEvent(
                    "v:esc001", "test\"Name", "use\"Case", true,
                    Map.of(), Map.of(), Instant.now());
            String json = WebhookVerdictSink.toJson(event);

            assertThat(json).contains("\"testName\":\"test\\\"Name\"");
            assertThat(json).contains("\"useCaseId\":\"use\\\"Case\"");
        }

        @Test
        @DisplayName("escapes backslashes in string values")
        void escapesBackslashesInValues() {
            var event = new VerdictEvent(
                    "v:esc002", "test\\Name", "useCase", true,
                    Map.of(), Map.of(), Instant.now());
            String json = WebhookVerdictSink.toJson(event);

            assertThat(json).contains("\"testName\":\"test\\\\Name\"");
        }

        @Test
        @DisplayName("handles empty maps")
        void handlesEmptyMaps() {
            var event = new VerdictEvent(
                    "v:empty1", "test", "uc", true,
                    Map.of(), Map.of(), Instant.now());
            String json = WebhookVerdictSink.toJson(event);

            assertThat(json).contains("\"reportEntries\":{}");
            assertThat(json).contains("\"environmentMetadata\":{}");
        }
    }
}

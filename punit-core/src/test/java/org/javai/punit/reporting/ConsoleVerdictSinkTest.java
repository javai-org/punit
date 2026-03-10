package org.javai.punit.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConsoleVerdictSink")
class ConsoleVerdictSinkTest {

    @Nested
    @DisplayName("passing verdict")
    class PassingVerdict {

        @Test
        @DisplayName("renders pass verdict with observed and required pass rates")
        void rendersPassVerdict() {
            String output = renderVerdict(passingEntries());

            assertThat(output).contains("VERDICT: PASS");
            assertThat(output).contains("Observed pass rate:");
            assertThat(output).contains(">= required:");
        }

        @Test
        @DisplayName("includes test name in output")
        void includesTestName() {
            String output = renderVerdict(passingEntries());

            assertThat(output).contains("MySpec.shouldPass");
        }

        @Test
        @DisplayName("includes elapsed time")
        void includesElapsedTime() {
            String output = renderVerdict(passingEntries());

            assertThat(output).contains("Elapsed:");
            assertThat(output).contains("150ms");
        }
    }

    @Nested
    @DisplayName("failing verdict")
    class FailingVerdict {

        @Test
        @DisplayName("renders fail verdict with less-than comparison")
        void rendersFailVerdict() {
            String output = renderVerdict(failingEntries(), false);

            assertThat(output).contains("VERDICT: FAIL");
            assertThat(output).contains("< required:");
        }

        @Test
        @DisplayName("renders budget exhaustion")
        void rendersBudgetExhaustion() {
            Map<String, String> entries = failingEntries();
            entries.put("punit.terminationReason", "TIME_BUDGET_EXHAUSTED");
            entries.put("punit.samples", "100");
            entries.put("punit.samplesExecuted", "42");

            String output = renderVerdict(entries, false);

            assertThat(output).contains("budget exhausted");
            assertThat(output).contains("Termination:");
        }
    }

    @Nested
    @DisplayName("dimension breakdown")
    class DimensionBreakdown {

        @Test
        @DisplayName("shows per-dimension breakdown when both dimensions are asserted")
        void showsBothDimensions() {
            Map<String, String> entries = passingEntries();
            entries.put("punit.dimension.functional", "true");
            entries.put("punit.dimension.functional.successes", "95");
            entries.put("punit.dimension.functional.failures", "5");
            entries.put("punit.dimension.latency", "true");
            entries.put("punit.dimension.latency.successes", "90");
            entries.put("punit.dimension.latency.failures", "10");

            String output = renderVerdict(entries);

            assertThat(output).contains("Contract:");
            assertThat(output).contains("95/100 passed");
            assertThat(output).contains("Latency:");
            assertThat(output).contains("90/100 within limit");
        }

        @Test
        @DisplayName("omits breakdown when only one dimension is asserted")
        void omitsForSingleDimension() {
            Map<String, String> entries = passingEntries();
            entries.put("punit.dimension.functional", "true");
            entries.put("punit.dimension.functional.successes", "95");
            entries.put("punit.dimension.functional.failures", "5");

            String output = renderVerdict(entries);

            assertThat(output).doesNotContain("Contract:");
            assertThat(output).doesNotContain("Latency:");
        }
    }

    @Nested
    @DisplayName("framing")
    class Framing {

        @Test
        @DisplayName("includes PUnit header and footer dividers")
        void includesFraming() {
            String output = renderVerdict(passingEntries());

            assertThat(output).contains("PUnit");
            // Footer is a line of ═ characters
            assertThat(output).contains("═".repeat(78));
        }
    }

    // ── Test helpers ──────────────────────────────────────────────────────

    private Map<String, String> passingEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("punit.samples", "100");
        entries.put("punit.samplesExecuted", "100");
        entries.put("punit.successes", "95");
        entries.put("punit.failures", "5");
        entries.put("punit.minPassRate", "0.9000");
        entries.put("punit.observedPassRate", "0.9500");
        entries.put("punit.verdict", "PASS");
        entries.put("punit.terminationReason", "COMPLETED");
        entries.put("punit.elapsedMs", "150");
        return entries;
    }

    private Map<String, String> failingEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("punit.samples", "100");
        entries.put("punit.samplesExecuted", "100");
        entries.put("punit.successes", "80");
        entries.put("punit.failures", "20");
        entries.put("punit.minPassRate", "0.9000");
        entries.put("punit.observedPassRate", "0.8000");
        entries.put("punit.verdict", "FAIL");
        entries.put("punit.terminationReason", "COMPLETED");
        entries.put("punit.elapsedMs", "200");
        return entries;
    }

    private String renderVerdict(Map<String, String> entries) {
        return renderVerdict(entries, true);
    }

    private String renderVerdict(Map<String, String> entries, boolean passed) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        ConsoleVerdictSink sink = new ConsoleVerdictSink(ps);

        VerdictEvent event = new VerdictEvent(
                "v:test01", "MySpec.shouldPass", "MyUseCase",
                passed, entries, Map.of(), Instant.now());

        sink.accept(event);
        return baos.toString(StandardCharsets.UTF_8);
    }
}

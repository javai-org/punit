package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LatencySummaryRenderer")
class LatencySummaryRendererTest {

    private final LatencySummaryRenderer renderer = new LatencySummaryRenderer();

    @Nested
    @DisplayName("When latency was not evaluated")
    class NotEvaluated {

        @Test
        @DisplayName("should return empty string for not-requested result")
        void shouldReturnEmptyForNotRequested() {
            LatencyAssertionResult result = LatencyAssertionResult.notRequested();

            String rendered = renderer.render(result);

            assertThat(rendered).isEmpty();
        }

        @Test
        @DisplayName("should return empty string for skipped result")
        void shouldReturnEmptyForSkipped() {
            LatencyAssertionResult result = LatencyAssertionResult.skipped("No successes");

            String rendered = renderer.render(result);

            assertThat(rendered).isEmpty();
        }
    }

    @Nested
    @DisplayName("Passing results")
    class PassingResults {

        @Test
        @DisplayName("should render passing single percentile")
        void shouldRenderPassingSinglePercentile() {
            var pr = new LatencyAssertionResult.PercentileResult("p95", 420, 500, true, false);
            LatencyAssertionResult result = new LatencyAssertionResult(
                    true, List.of(pr), 199, false, List.of());

            String rendered = renderer.render(result);

            assertThat(rendered).contains("Latency (n=199)");
            assertThat(rendered).contains("p95 420ms <= 500ms");
        }

        @Test
        @DisplayName("should render multiple passing percentiles")
        void shouldRenderMultiplePassingPercentiles() {
            var p95 = new LatencyAssertionResult.PercentileResult("p95", 420, 500, true, false);
            var p99 = new LatencyAssertionResult.PercentileResult("p99", 810, 1000, true, false);
            LatencyAssertionResult result = new LatencyAssertionResult(
                    true, List.of(p95, p99), 199, false, List.of());

            String rendered = renderer.render(result);

            assertThat(rendered).contains("p95 420ms <= 500ms");
            assertThat(rendered).contains("p99 810ms <= 1000ms");
        }
    }

    @Nested
    @DisplayName("Failing results")
    class FailingResults {

        @Test
        @DisplayName("should render breach marker for failing percentile")
        void shouldRenderBreachMarker() {
            var pr = new LatencyAssertionResult.PercentileResult("p99", 1200, 1000, false, false);
            LatencyAssertionResult result = new LatencyAssertionResult(
                    false, List.of(pr), 199, false, List.of());

            String rendered = renderer.render(result);

            assertThat(rendered).contains("p99 1200ms > 1000ms");
            assertThat(rendered).contains("BREACH");
        }

        @Test
        @DisplayName("should render mixed pass and fail")
        void shouldRenderMixedPassAndFail() {
            var p95 = new LatencyAssertionResult.PercentileResult("p95", 420, 500, true, false);
            var p99 = new LatencyAssertionResult.PercentileResult("p99", 1200, 1000, false, false);
            LatencyAssertionResult result = new LatencyAssertionResult(
                    false, List.of(p95, p99), 199, false, List.of());

            String rendered = renderer.render(result);

            assertThat(rendered).contains("p95 420ms <= 500ms");
            assertThat(rendered).contains("p99 1200ms > 1000ms");
            assertThat(rendered).contains("BREACH");
        }
    }

    @Nested
    @DisplayName("Indicative marker")
    class IndicativeMarker {

        @Test
        @DisplayName("should render indicative marker for undersized percentile")
        void shouldRenderIndicativeMarker() {
            var pr = new LatencyAssertionResult.PercentileResult("p99", 500, 1000, true, true);
            LatencyAssertionResult result = new LatencyAssertionResult(
                    true, List.of(pr), 5, false, List.of("Small sample"));

            String rendered = renderer.render(result);

            assertThat(rendered).contains("(indicative)");
        }
    }

    @Nested
    @DisplayName("appendTo")
    class AppendTo {

        @Test
        @DisplayName("should not append anything for not-evaluated result")
        void shouldNotAppendForNotEvaluated() {
            LatencyAssertionResult result = LatencyAssertionResult.notRequested();
            StringBuilder sb = new StringBuilder();

            renderer.appendTo(sb, result);

            assertThat(sb).isEmpty();
        }

        @Test
        @DisplayName("should append latency line and caveats for evaluated result")
        void shouldAppendLatencyLineAndCaveats() {
            var pr = new LatencyAssertionResult.PercentileResult("p95", 420, 500, true, true);
            LatencyAssertionResult result = new LatencyAssertionResult(
                    true, List.of(pr), 5, false, List.of("Indicative results"));
            StringBuilder sb = new StringBuilder();

            renderer.appendTo(sb, result);

            String output = sb.toString();
            assertThat(output).contains("Latency:");
            assertThat(output).contains("Note:");
            assertThat(output).contains("Indicative results");
        }
    }
}

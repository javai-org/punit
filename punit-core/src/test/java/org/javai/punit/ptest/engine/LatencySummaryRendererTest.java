package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.javai.punit.verdict.ProbabilisticTestVerdict.LatencyDimension;
import org.javai.punit.verdict.ProbabilisticTestVerdict.PercentileAssertion;
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
        @DisplayName("should return empty string for skipped result with no reason")
        void shouldReturnEmptyForNotRequested() {
            LatencyDimension lat = skippedDimension(null);

            String rendered = renderer.render(lat);

            assertThat(rendered).isEmpty();
        }

        @Test
        @DisplayName("should return empty string for skipped result with reason")
        void shouldReturnEmptyForSkipped() {
            LatencyDimension lat = skippedDimension("No successes");

            String rendered = renderer.render(lat);

            assertThat(rendered).isEmpty();
        }
    }

    @Nested
    @DisplayName("Passing results")
    class PassingResults {

        @Test
        @DisplayName("should render passing single percentile")
        void shouldRenderPassingSinglePercentile() {
            var pa = new PercentileAssertion("p95", 420, 500, true, false, null);
            LatencyDimension lat = latencyDimension(199, List.of(pa), List.of());

            String rendered = renderer.render(lat);

            assertThat(rendered).contains("Latency (n=199)");
            assertThat(rendered).contains("p95 420ms <= 500ms");
        }

        @Test
        @DisplayName("should render multiple passing percentiles")
        void shouldRenderMultiplePassingPercentiles() {
            var p95 = new PercentileAssertion("p95", 420, 500, true, false, null);
            var p99 = new PercentileAssertion("p99", 810, 1000, true, false, null);
            LatencyDimension lat = latencyDimension(199, List.of(p95, p99), List.of());

            String rendered = renderer.render(lat);

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
            var pa = new PercentileAssertion("p99", 1200, 1000, false, false, null);
            LatencyDimension lat = latencyDimension(199, List.of(pa), List.of());

            String rendered = renderer.render(lat);

            assertThat(rendered).contains("p99 1200ms > 1000ms");
            assertThat(rendered).contains("BREACH");
        }

        @Test
        @DisplayName("should render mixed pass and fail")
        void shouldRenderMixedPassAndFail() {
            var p95 = new PercentileAssertion("p95", 420, 500, true, false, null);
            var p99 = new PercentileAssertion("p99", 1200, 1000, false, false, null);
            LatencyDimension lat = latencyDimension(199, List.of(p95, p99), List.of());

            String rendered = renderer.render(lat);

            assertThat(rendered).contains("p95 420ms <= 500ms");
            assertThat(rendered).contains("p99 1200ms > 1000ms");
            assertThat(rendered).contains("BREACH");
        }
    }

    @Nested
    @DisplayName("Advisory label")
    class AdvisoryLabel {

        @Test
        @DisplayName("should not show advisory for breach with explicit thresholds")
        void shouldNotShowAdvisoryForExplicitThresholds() {
            var pa = new PercentileAssertion("p99", 1200, 1000, false, false, "explicit");
            LatencyDimension lat = latencyDimension(199, List.of(pa), List.of());

            String rendered = renderer.render(lat);

            assertThat(rendered).contains("BREACH");
            assertThat(rendered).doesNotContain("(advisory)");
        }

        @Test
        @DisplayName("should show advisory for breach with baseline-derived thresholds")
        void shouldShowAdvisoryForBaselineDerivedThresholds() {
            var pa = new PercentileAssertion("p99", 1200, 1000, false, false, "from baseline");
            LatencyDimension lat = latencyDimension(199, List.of(pa), List.of());

            String rendered = renderer.render(lat);

            assertThat(rendered).contains("BREACH");
            assertThat(rendered).contains("(advisory)");
        }
    }

    @Nested
    @DisplayName("Indicative marker")
    class IndicativeMarker {

        @Test
        @DisplayName("should render indicative marker for undersized percentile")
        void shouldRenderIndicativeMarker() {
            var pa = new PercentileAssertion("p99", 500, 1000, true, true, null);
            LatencyDimension lat = latencyDimension(5, List.of(pa), List.of("Small sample"));

            String rendered = renderer.render(lat);

            assertThat(rendered).contains("(indicative)");
        }
    }

    @Nested
    @DisplayName("appendTo")
    class AppendTo {

        @Test
        @DisplayName("should not append anything for skipped result")
        void shouldNotAppendForNotEvaluated() {
            LatencyDimension lat = skippedDimension(null);
            StringBuilder sb = new StringBuilder();

            renderer.appendTo(sb, lat);

            assertThat(sb).isEmpty();
        }

        @Test
        @DisplayName("should append latency line and caveats for evaluated result")
        void shouldAppendLatencyLineAndCaveats() {
            var pa = new PercentileAssertion("p95", 420, 500, true, true, null);
            LatencyDimension lat = latencyDimension(5, List.of(pa), List.of("Indicative results"));
            StringBuilder sb = new StringBuilder();

            renderer.appendTo(sb, lat);

            String output = sb.toString();
            assertThat(output).contains("Latency:");
            assertThat(output).contains("Note:");
            assertThat(output).contains("Indicative results");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private LatencyDimension skippedDimension(String reason) {
        return new LatencyDimension(
                0, 0, true, Optional.ofNullable(reason),
                0, 0, 0, 0, 0,
                List.of(), List.of(), 0, 0);
    }

    private LatencyDimension latencyDimension(
            int successfulSamples, List<PercentileAssertion> assertions, List<String> caveats) {
        return new LatencyDimension(
                successfulSamples, successfulSamples, false, Optional.empty(),
                100, 200, 300, 500, 1000,
                assertions, caveats,
                successfulSamples, 0);
    }
}

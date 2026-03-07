package org.javai.punit.statistics.transparent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the LATENCY ANALYSIS section in {@link TextExplanationRenderer}.
 */
@DisplayName("TextExplanationRenderer — Latency Analysis")
class TextExplanationRendererLatencyTest {

    private TextExplanationRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new TextExplanationRenderer(true, TransparentStatsConfig.DetailLevel.VERBOSE);
    }

    @Nested
    @DisplayName("Pass case")
    class PassCase {

        @Test
        @DisplayName("should render LATENCY ANALYSIS section")
        void shouldRenderLatencySection() {
            StatisticalExplanation explanation = createWithLatency(
                    createLatencyPass(199, 200));

            String output = renderer.render(explanation);

            assertThat(output).contains("LATENCY ANALYSIS");
        }

        @Test
        @DisplayName("should show population line with successful sample count")
        void shouldShowPopulationLine() {
            StatisticalExplanation explanation = createWithLatency(
                    createLatencyPass(199, 200));

            String output = renderer.render(explanation);

            assertThat(output).contains("Successful samples only (n=199 of 200)");
        }

        @Test
        @DisplayName("should show observed distribution")
        void shouldShowObservedDistribution() {
            StatisticalExplanation explanation = createWithLatency(
                    createLatencyPass(199, 200));

            String output = renderer.render(explanation);

            assertThat(output).contains("Observed distribution:");
            assertThat(output).contains("p50:");
            assertThat(output).contains("180ms");
            assertThat(output).contains("p90:");
            assertThat(output).contains("320ms");
            assertThat(output).contains("p95:");
            assertThat(output).contains("420ms");
            assertThat(output).contains("p99:");
            assertThat(output).contains("810ms");
            assertThat(output).contains("max:");
            assertThat(output).contains("1480ms");
        }

        @Test
        @DisplayName("should show percentile thresholds with PASS")
        void shouldShowPercentileThresholdsPass() {
            StatisticalExplanation explanation = createWithLatency(
                    createLatencyPass(199, 200));

            String output = renderer.render(explanation);

            assertThat(output).contains("Percentile thresholds:");
            assertThat(output).contains("420ms <= 500ms");
            assertThat(output).contains("PASS");
            assertThat(output).contains("810ms <= 1000ms");
        }
    }

    @Nested
    @DisplayName("Breach case")
    class BreachCase {

        @Test
        @DisplayName("should show FAIL for breached percentile")
        void shouldShowFailForBreachedPercentile() {
            var assertions = List.of(
                    new StatisticalExplanation.PercentileAssertion("p95", 420, 500, true, false, "explicit"),
                    new StatisticalExplanation.PercentileAssertion("p99", 1200, 1000, false, false, "explicit")
            );
            var latency = new StatisticalExplanation.LatencyAnalysis(
                    199, 200, false, null,
                    180, 320, 420, 1200, 3400,
                    assertions, List.of(), "explicit", null);

            String output = renderer.render(createWithLatency(latency));

            assertThat(output).contains("1200ms > 1000ms");
            assertThat(output).contains("FAIL");
        }
    }

    @Nested
    @DisplayName("Indicative result")
    class IndicativeResult {

        @Test
        @DisplayName("should show indicative marker for undersized percentile")
        void shouldShowIndicativeMarker() {
            var assertions = List.of(
                    new StatisticalExplanation.PercentileAssertion("p95", 380, 500, true, false, "explicit"),
                    new StatisticalExplanation.PercentileAssertion("p99", 720, 1000, true, true, "explicit")
            );
            var latency = new StatisticalExplanation.LatencyAnalysis(
                    49, 50, false, null,
                    175, 310, 380, 720, 980,
                    assertions,
                    List.of("Latency p99 is indicative rather than conclusive."),
                    "explicit", null);

            String output = renderer.render(createWithLatency(latency));

            assertThat(output).contains("PASS (indicative)");
            assertThat(output).contains("Caveat:");
            assertThat(output).contains("indicative");
        }
    }

    @Nested
    @DisplayName("Zero successful samples")
    class ZeroSuccessfulSamples {

        @Test
        @DisplayName("should show not evaluated message")
        void shouldShowNotEvaluatedMessage() {
            var latency = new StatisticalExplanation.LatencyAnalysis(
                    0, 200, true, "No successful samples — latency evaluation skipped.",
                    -1, -1, -1, -1, -1,
                    List.of(), List.of(), null, null);

            String output = renderer.render(createWithLatency(latency));

            assertThat(output).contains("LATENCY ANALYSIS");
            assertThat(output).contains("No successful samples available (0 of 200)");
            assertThat(output).contains("Not evaluated");
        }
    }

    @Nested
    @DisplayName("Baseline-derived thresholds")
    class BaselineDerived {

        @Test
        @DisplayName("should show 'from baseline' in threshold header")
        void shouldShowFromBaselineInHeader() {
            var assertions = List.of(
                    new StatisticalExplanation.PercentileAssertion("p95", 395, 456, true, false, "from baseline"),
                    new StatisticalExplanation.PercentileAssertion("p99", 540, 624, true, false, "from baseline")
            );
            var latency = new StatisticalExplanation.LatencyAnalysis(
                    85, 100, false, null,
                    190, 305, 395, 540, 1100,
                    assertions, List.of(), "from baseline", "ShoppingBasketUseCase.yaml");

            String output = renderer.render(createWithLatency(latency));

            assertThat(output).contains("Percentile thresholds (from baseline):");
            assertThat(output).contains("Baseline reference:");
            assertThat(output).contains("ShoppingBasketUseCase.yaml");
        }
    }

    @Nested
    @DisplayName("No latency analysis")
    class NoLatencyAnalysis {

        @Test
        @DisplayName("should not render latency section when no analysis present")
        void shouldNotRenderLatencySection() {
            StatisticalExplanation explanation = createWithoutLatency();

            String output = renderer.render(explanation);

            assertThat(output).doesNotContain("LATENCY ANALYSIS");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private StatisticalExplanation.LatencyAnalysis createLatencyPass(int successfulSamples, int totalSamples) {
        var assertions = List.of(
                new StatisticalExplanation.PercentileAssertion("p95", 420, 500, true, false, "explicit"),
                new StatisticalExplanation.PercentileAssertion("p99", 810, 1000, true, false, "explicit")
        );
        return new StatisticalExplanation.LatencyAnalysis(
                successfulSamples, totalSamples, false, null,
                180, 320, 420, 810, 1480,
                assertions, List.of(), "explicit", null);
    }

    private StatisticalExplanation createWithLatency(StatisticalExplanation.LatencyAnalysis latency) {
        return new StatisticalExplanation(
                "testPaymentLatency",
                new StatisticalExplanation.HypothesisStatement(
                        "H0: π ≥ 0.99", "H1: π < 0.99", "One-sided binomial proportion test"),
                StatisticalExplanation.ObservedData.of(200, 199),
                new StatisticalExplanation.BaselineReference(
                        "PaymentGateway.yaml", Instant.parse("2026-01-10T10:00:00Z"),
                        1000, 990, 0.99, "Wilson lower bound", 0.99),
                new StatisticalExplanation.StatisticalInference(
                        0.005, 0.98, 1.0, 0.95, 0.71, 0.239),
                new StatisticalExplanation.VerdictInterpretation(
                        true, "PASS", "Passed.", List.of()),
                new StatisticalExplanation.Provenance("UNSPECIFIED", ""),
                latency
        );
    }

    private StatisticalExplanation createWithoutLatency() {
        return new StatisticalExplanation(
                "testNoLatency",
                new StatisticalExplanation.HypothesisStatement(
                        "H0: π ≥ 0.85", "H1: π < 0.85", "One-sided test"),
                StatisticalExplanation.ObservedData.of(100, 87),
                new StatisticalExplanation.BaselineReference(
                        "Test.yaml", Instant.parse("2026-01-10T10:00:00Z"),
                        1000, 870, 0.87, "Wilson lower bound", 0.85),
                new StatisticalExplanation.StatisticalInference(
                        0.0336, 0.804, 0.936, 0.95, 0.56, 0.288),
                new StatisticalExplanation.VerdictInterpretation(
                        true, "PASS", "Passed.", List.of()),
                new StatisticalExplanation.Provenance("UNSPECIFIED", "")
        );
    }
}

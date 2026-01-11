package org.javai.punit.statistics.transparent;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ConsoleExplanationRenderer}.
 */
class ConsoleExplanationRendererTest {

    private ConsoleExplanationRenderer renderer;

    @BeforeEach
    void setUp() {
        // Use Unicode for consistent test output
        renderer = new ConsoleExplanationRenderer(true, TransparentStatsConfig.DetailLevel.STANDARD);
    }

    @Nested
    @DisplayName("render()")
    class RenderTests {

        @Test
        @DisplayName("includes test name in header")
        void includesTestNameInHeader() {
            StatisticalExplanation explanation = createExplanation("shouldReturnValidJson", true);
            
            String output = renderer.render(explanation);
            
            assertThat(output).contains("STATISTICAL ANALYSIS: shouldReturnValidJson");
        }

        @Test
        @DisplayName("includes hypothesis section")
        void includesHypothesisSection() {
            StatisticalExplanation explanation = createExplanation("test", true);
            
            String output = renderer.render(explanation);
            
            assertThat(output).contains("HYPOTHESIS TEST");
            assertThat(output).contains("H₀ (null):");
            assertThat(output).contains("H₁ (alternative):");
            assertThat(output).contains("Test type:");
        }

        @Test
        @DisplayName("includes observed data section")
        void includesObservedDataSection() {
            StatisticalExplanation explanation = createExplanation("test", true);
            
            String output = renderer.render(explanation);
            
            assertThat(output).contains("OBSERVED DATA");
            assertThat(output).contains("Sample size (n):");
            assertThat(output).contains("Successes (k):");
            assertThat(output).contains("Observed rate (p̂):");
        }

        @Test
        @DisplayName("includes baseline reference section")
        void includesBaselineReferenceSection() {
            StatisticalExplanation explanation = createExplanation("test", true);
            
            String output = renderer.render(explanation);
            
            assertThat(output).contains("BASELINE REFERENCE");
            assertThat(output).contains("Source:");
        }

        @Test
        @DisplayName("includes statistical inference section")
        void includesStatisticalInferenceSection() {
            StatisticalExplanation explanation = createExplanation("test", true);
            
            String output = renderer.render(explanation);
            
            assertThat(output).contains("STATISTICAL INFERENCE");
            assertThat(output).contains("Standard error:");
            assertThat(output).contains("Confidence interval:");
        }

        @Test
        @DisplayName("includes verdict section with PASS")
        void includesVerdictSectionPass() {
            StatisticalExplanation explanation = createExplanation("test", true);
            
            String output = renderer.render(explanation);
            
            assertThat(output).contains("VERDICT");
            assertThat(output).contains("Result:              PASS");
            assertThat(output).contains("Interpretation:");
        }

        @Test
        @DisplayName("includes verdict section with FAIL")
        void includesVerdictSectionFail() {
            StatisticalExplanation explanation = createExplanation("test", false);
            
            String output = renderer.render(explanation);
            
            assertThat(output).contains("Result:              FAIL");
        }

        @Test
        @DisplayName("uses box drawing characters")
        void usesBoxDrawingCharacters() {
            StatisticalExplanation explanation = createExplanation("test", true);
            
            String output = renderer.render(explanation);
            
            assertThat(output).contains("═");
        }

        @Test
        @DisplayName("includes caveats when present")
        void includesCaveats() {
            StatisticalExplanation.VerdictInterpretation verdict = 
                    new StatisticalExplanation.VerdictInterpretation(
                            true, "PASS", "Test passed.",
                            List.of("Sample size is small.", "Consider increasing samples.")
                    );
            
            StatisticalExplanation explanation = new StatisticalExplanation(
                    "test",
                    new StatisticalExplanation.HypothesisStatement(
                            "H0: π ≤ 0.85", "H1: π > 0.85", "One-sided binomial proportion test"
                    ),
                    StatisticalExplanation.ObservedData.of(100, 87),
                    new StatisticalExplanation.BaselineReference(
                            "Test.yaml", Instant.now(), 1000, 870, 0.87, "95% CI lower bound", 0.85
                    ),
                    new StatisticalExplanation.StatisticalInference(
                            0.0336, 0.804, 0.936, 0.95, null, null
                    ),
                    verdict
            );
            
            String output = renderer.render(explanation);
            
            assertThat(output).contains("Caveat:");
            assertThat(output).contains("Sample size is small");
        }
    }

    @Nested
    @DisplayName("ASCII fallback")
    class AsciiFallbackTests {

        @Test
        @DisplayName("uses ASCII symbols when Unicode is disabled")
        void usesAsciiSymbols() {
            ConsoleExplanationRenderer asciiRenderer = 
                    new ConsoleExplanationRenderer(false, TransparentStatsConfig.DetailLevel.STANDARD);
            StatisticalExplanation explanation = createExplanation("test", true);
            
            String output = asciiRenderer.render(explanation);
            
            // Should use ASCII fallback for box drawing
            assertThat(output).contains("=");
            // Should still have the section headers
            assertThat(output).contains("STATISTICAL ANALYSIS");
        }
    }

    private StatisticalExplanation createExplanation(String testName, boolean passed) {
        return new StatisticalExplanation(
                testName,
                new StatisticalExplanation.HypothesisStatement(
                        "True success rate π ≤ 0.85 (system does not meet spec)",
                        "True success rate π > 0.85 (system meets spec)",
                        "One-sided binomial proportion test"
                ),
                StatisticalExplanation.ObservedData.of(100, 87),
                new StatisticalExplanation.BaselineReference(
                        "TestUseCase.yaml",
                        Instant.parse("2026-01-10T10:00:00Z"),
                        1000, 870, 0.87,
                        "Lower bound of 95% CI = 85.1%, applied threshold = 85%",
                        0.85
                ),
                new StatisticalExplanation.StatisticalInference(
                        0.0336, 0.804, 0.936, 0.95, 0.56, 0.288
                ),
                new StatisticalExplanation.VerdictInterpretation(
                        passed,
                        passed ? "PASS" : "FAIL",
                        passed 
                                ? "The observed success rate of 87% meets the threshold."
                                : "The observed success rate of 87% falls below the threshold.",
                        List.of()
                )
        );
    }
}


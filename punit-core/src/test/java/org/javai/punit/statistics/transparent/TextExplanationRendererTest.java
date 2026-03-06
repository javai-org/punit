package org.javai.punit.statistics.transparent;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TextExplanationRenderer}.
 */
class TextExplanationRendererTest {

    private TextExplanationRenderer renderer;

    @BeforeEach
    void setUp() {
        // Use Unicode for consistent test output
        renderer = new TextExplanationRenderer(true, TransparentStatsConfig.DetailLevel.VERBOSE);
    }

    @Nested
    @DisplayName("render()")
    class RenderTests {

        @Test
        @DisplayName("includes test name in header")
        void includesTestNameInHeader() {
            StatisticalExplanation explanation = createExplanation("shouldReturnValidJson", true);

            String output = renderer.render(explanation);

            assertThat(output).contains("STATISTICAL ANALYSIS FOR: shouldReturnValidJson");
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
            assertThat(output).contains("Result:                PASS");
            assertThat(output).contains("Interpretation:");
        }

        @Test
        @DisplayName("includes verdict section with FAIL")
        void includesVerdictSectionFail() {
            StatisticalExplanation explanation = createExplanation("test", false);

            String output = renderer.render(explanation);

            assertThat(output).contains("Result:                FAIL");
        }

        @Test
        @DisplayName("uses box drawing characters")
        void usesBoxDrawingCharacters() {
            StatisticalExplanation explanation = createExplanation("test", true);

            String output = renderer.render(explanation);

            // Uses single-line horizontal character for section dividers
            assertThat(output).contains("─");
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
                    verdict,
                    new StatisticalExplanation.Provenance("UNSPECIFIED", "")
            );

            String output = renderer.render(explanation);

            assertThat(output).contains("Caveat:");
            assertThat(output).contains("Sample size is small");
        }
    }

    @Nested
    @DisplayName("SUMMARY detail level")
    class SummaryDetailLevelTests {

        private final TextExplanationRenderer summaryRenderer =
                new TextExplanationRenderer(true, TransparentStatsConfig.DetailLevel.SUMMARY);

        @Test
        @DisplayName("omits hypothesis section")
        void omitsHypothesisSection() {
            StatisticalExplanation explanation = createExplanation("test", true);

            String output = summaryRenderer.render(explanation);

            assertThat(output).doesNotContain("HYPOTHESIS TEST");
        }

        @Test
        @DisplayName("omits statistical inference section")
        void omitsStatisticalInferenceSection() {
            StatisticalExplanation explanation = createExplanation("test", true);

            String output = summaryRenderer.render(explanation);

            assertThat(output).doesNotContain("STATISTICAL INFERENCE");
            assertThat(output).doesNotContain("Standard error:");
            assertThat(output).doesNotContain("Confidence interval:");
            assertThat(output).doesNotContain("Test statistic:");
            assertThat(output).doesNotContain("p-value:");
        }

        @Test
        @DisplayName("still includes observed data and verdict")
        void includesObservedDataAndVerdict() {
            StatisticalExplanation explanation = createExplanation("test", true);

            String output = summaryRenderer.render(explanation);

            assertThat(output).contains("OBSERVED DATA");
            assertThat(output).contains("VERDICT");
            assertThat(output).contains("BASELINE REFERENCE");
        }
    }

    @Nested
    @DisplayName("ASCII fallback")
    class AsciiFallbackTests {

        @Test
        @DisplayName("uses ASCII symbols when Unicode is disabled")
        void usesAsciiSymbols() {
            TextExplanationRenderer asciiRenderer =
                    new TextExplanationRenderer(false, TransparentStatsConfig.DetailLevel.VERBOSE);
            StatisticalExplanation explanation = createExplanation("test", true);

            String output = asciiRenderer.render(explanation);

            // Should use ASCII fallback for box drawing
            assertThat(output).contains("=");
            // Should still have the section headers
            assertThat(output).contains("STATISTICAL ANALYSIS");
        }
    }

    @Nested
    @DisplayName("constructors")
    class ConstructorTests {

        @Test
        @DisplayName("default constructor creates a working renderer")
        void defaultConstructor() {
            TextExplanationRenderer defaultRenderer = new TextExplanationRenderer();
            StatisticalExplanation explanation = createExplanation("test", true);

            String output = defaultRenderer.render(explanation);

            assertThat(output).contains("STATISTICAL ANALYSIS FOR: test");
        }

        @Test
        @DisplayName("config constructor creates a working renderer")
        void configConstructor() {
            TransparentStatsConfig config = new TransparentStatsConfig(
                    true, TransparentStatsConfig.DetailLevel.SUMMARY,
                    TransparentStatsConfig.OutputFormat.CONSOLE);
            TextExplanationRenderer configRenderer = new TextExplanationRenderer(config);
            StatisticalExplanation explanation = createExplanation("test", true);

            String output = configRenderer.render(explanation);

            assertThat(output).contains("STATISTICAL ANALYSIS FOR: test");
            // SUMMARY should omit hypothesis
            assertThat(output).doesNotContain("HYPOTHESIS TEST");
        }
    }

    @Nested
    @DisplayName("render(explanation, config)")
    class RenderWithConfigTests {

        @Test
        @DisplayName("overrides detail level when config differs from renderer")
        void overridesDetailLevel() {
            // Renderer is VERBOSE, but config says SUMMARY
            TransparentStatsConfig summaryConfig = new TransparentStatsConfig(
                    true, TransparentStatsConfig.DetailLevel.SUMMARY,
                    TransparentStatsConfig.OutputFormat.CONSOLE);
            StatisticalExplanation explanation = createExplanation("test", true);

            String output = renderer.render(explanation, summaryConfig);

            // Should behave as SUMMARY despite renderer being VERBOSE
            assertThat(output).doesNotContain("HYPOTHESIS TEST");
            assertThat(output).contains("VERDICT");
        }

        @Test
        @DisplayName("uses existing renderer when config matches")
        void usesExistingRendererWhenConfigMatches() {
            TransparentStatsConfig verboseConfig = new TransparentStatsConfig(
                    true, TransparentStatsConfig.DetailLevel.VERBOSE,
                    TransparentStatsConfig.OutputFormat.CONSOLE);
            StatisticalExplanation explanation = createExplanation("test", true);

            String output = renderer.render(explanation, verboseConfig);

            assertThat(output).contains("HYPOTHESIS TEST");
            assertThat(output).contains("STATISTICAL INFERENCE");
        }
    }

    @Nested
    @DisplayName("non-spec-driven baseline")
    class NonSpecDrivenBaselineTests {

        @Test
        @DisplayName("renders inline threshold when no baseline data")
        void rendersInlineThreshold() {
            StatisticalExplanation explanation = new StatisticalExplanation(
                    "inlineTest",
                    new StatisticalExplanation.HypothesisStatement(
                            "H0: p <= 0.90", "H1: p > 0.90", "One-sided test"
                    ),
                    StatisticalExplanation.ObservedData.of(50, 48),
                    new StatisticalExplanation.BaselineReference(
                            "inline", null, 0, 0, 0.0,
                            "explicit minPassRate", 0.90
                    ),
                    new StatisticalExplanation.StatisticalInference(
                            0.0424, 0.880, 0.990, 0.95, null, null
                    ),
                    new StatisticalExplanation.VerdictInterpretation(
                            true, "PASS", "Passed.", List.of()
                    ),
                    new StatisticalExplanation.Provenance("UNSPECIFIED", "")
            );

            String output = renderer.render(explanation);

            assertThat(output).contains("BASELINE REFERENCE");
            assertThat(output).contains("Source:");
            assertThat(output).contains("Threshold:");
            assertThat(output).contains("explicit minPassRate");
            // Should NOT contain empirical basis section
            assertThat(output).doesNotContain("Empirical basis:");
        }
    }

    @Nested
    @DisplayName("provenance section")
    class ProvenanceSectionTests {

        @Test
        @DisplayName("renders provenance with threshold origin")
        void rendersProvenanceWithThresholdOrigin() {
            StatisticalExplanation explanation = new StatisticalExplanation(
                    "provenanceTest",
                    new StatisticalExplanation.HypothesisStatement(
                            "H0: p <= 0.85", "H1: p > 0.85", "One-sided test"
                    ),
                    StatisticalExplanation.ObservedData.of(100, 90),
                    new StatisticalExplanation.BaselineReference(
                            "Test.yaml", Instant.now(), 1000, 900, 0.90,
                            "Wilson lower bound", 0.85
                    ),
                    new StatisticalExplanation.StatisticalInference(
                            0.03, 0.84, 0.96, 0.95, null, null
                    ),
                    new StatisticalExplanation.VerdictInterpretation(
                            true, "PASS", "Passed.", List.of()
                    ),
                    new StatisticalExplanation.Provenance("SLA_CONTRACT", "SLA-2024-001")
            );

            String output = renderer.render(explanation);

            assertThat(output).contains("THRESHOLD PROVENANCE");
            assertThat(output).contains("Threshold origin:");
            assertThat(output).contains("SLA_CONTRACT");
            assertThat(output).contains("Contract:");
            assertThat(output).contains("SLA-2024-001");
        }

        @Test
        @DisplayName("omits provenance section when not specified")
        void omitsProvenanceWhenNotSpecified() {
            StatisticalExplanation explanation = createExplanation("test", true);

            String output = renderer.render(explanation);

            assertThat(output).doesNotContain("THRESHOLD PROVENANCE");
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
                        "Lower bound of 95% CI = 85.1%, min pass rate = 85%",
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
                ),
                new StatisticalExplanation.Provenance("UNSPECIFIED", "")
        );
    }
}

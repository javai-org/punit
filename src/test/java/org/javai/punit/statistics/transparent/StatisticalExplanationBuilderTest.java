package org.javai.punit.statistics.transparent;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.javai.punit.statistics.ComplianceEvidenceEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StatisticalExplanationBuilder}.
 */
class StatisticalExplanationBuilderTest {

    private StatisticalExplanationBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new StatisticalExplanationBuilder();
    }

    @Nested
    @DisplayName("build() with baseline")
    class BuildWithBaselineTests {

        @Test
        @DisplayName("creates explanation with correct test name")
        void setsTestName() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "shouldReturnValidJson",
                    100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.testName()).isEqualTo("shouldReturnValidJson");
        }

        @Test
        @DisplayName("creates hypothesis statement for threshold")
        void createsHypothesisStatement() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.hypothesis().testType())
                    .isEqualTo("One-sided binomial proportion test");
            assertThat(explanation.hypothesis().nullHypothesis())
                    .contains("0.85");
            assertThat(explanation.hypothesis().alternativeHypothesis())
                    .contains("0.85");
        }

        @Test
        @DisplayName("captures observed data correctly")
        void capturesObservedData() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.observed().sampleSize()).isEqualTo(100);
            assertThat(explanation.observed().successes()).isEqualTo(87);
            assertThat(explanation.observed().observedRate()).isCloseTo(0.87, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("includes baseline reference from baseline data")
        void includesBaselineReference() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.baseline().hasBaselineData()).isTrue();
            assertThat(explanation.baseline().baselineSamples()).isEqualTo(1000);
            assertThat(explanation.baseline().baselineSuccesses()).isEqualTo(870);
            assertThat(explanation.baseline().baselineRate()).isCloseTo(0.87, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("calculates statistical inference")
        void calculatesStatisticalInference() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.inference().standardError()).isGreaterThan(0);
            assertThat(explanation.inference().ciLower()).isLessThan(0.87);
            assertThat(explanation.inference().ciUpper()).isGreaterThan(0.87);
            assertThat(explanation.inference().confidenceLevel()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("creates passing verdict interpretation")
        void createsPassingVerdict() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.verdict().passed()).isTrue();
            assertThat(explanation.verdict().technicalResult()).isEqualTo("PASS");
            assertThat(explanation.verdict().plainEnglish()).isNotEmpty();
        }

        @Test
        @DisplayName("creates failing verdict interpretation")
        void createsFailingVerdict() {
            BaselineData baseline = createBaseline(1000, 950);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 80, baseline, 0.90, false, 0.95
            );
            
            assertThat(explanation.verdict().passed()).isFalse();
            assertThat(explanation.verdict().technicalResult()).isEqualTo("FAIL");
            assertThat(explanation.verdict().plainEnglish()).contains("falls below");
        }

        @Test
        @DisplayName("includes caveats for small sample sizes")
        void includesCaveatsForSmallSamples() {
            BaselineData baseline = createBaseline(1000, 870);
            
            StatisticalExplanation explanation = builder.build(
                    "test", 20, 17, baseline, 0.85, true, 0.95
            );
            
            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.toLowerCase().contains("sample size"));
        }
        
        @Test
        @DisplayName("handles null baseline gracefully")
        void handlesNullBaseline() {
            StatisticalExplanation explanation = builder.build(
                    "test", 100, 87, null, 0.85, true, 0.95
            );
            
            assertThat(explanation.baseline().hasBaselineData()).isFalse();
            assertThat(explanation.baseline().sourceFile()).contains("inline");
        }
    }

    @Nested
    @DisplayName("buildWithInlineThreshold()")
    class BuildWithInlineThresholdTests {

        @Test
        @DisplayName("creates explanation without baseline data")
        void createsExplanationWithoutBaseline() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "inlineTest", 100, 87, 0.85, true
            );
            
            assertThat(explanation.testName()).isEqualTo("inlineTest");
            assertThat(explanation.baseline().hasBaselineData()).isFalse();
            assertThat(explanation.baseline().sourceFile()).contains("inline");
        }

        @Test
        @DisplayName("includes inline threshold caveat")
        void includesInlineThresholdCaveat() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "inlineTest", 100, 87, 0.85, true
            );
            
            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.toLowerCase().contains("inline threshold"));
        }
    }

    @Nested
    @DisplayName("Compliance evidence sizing caveat")
    class ComplianceEvidenceSizingTests {

        @Test
        @DisplayName("SLA origin with undersized N=200 includes exact sizing note via build()")
        void slaUndersizedWithBaselineIncludesNote() {
            BaselineData baseline = createBaseline(1000, 999);

            StatisticalExplanation explanation = builder.build(
                    "testSla", 200, 200, baseline, 0.9999, true, 0.95,
                    "SLA", "Payment SLA v2.3"
            );

            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.contains(ComplianceEvidenceEvaluator.SIZING_NOTE));
        }

        @Test
        @DisplayName("SLA origin with undersized N=500 includes exact sizing note via buildWithInlineThreshold()")
        void slaUndersizedInlineIncludesNote() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSla", 500, 500, 0.9999, true, "SLA", "Payment SLA v2.3"
            );

            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.contains(ComplianceEvidenceEvaluator.SIZING_NOTE));
        }

        @Test
        @DisplayName("SLA origin with N=10000 and target 0.9999 is still undersized")
        void slaUndersizedAt10000() {
            // With α=0.001, even n=10,000 is insufficient for p₀=0.9999
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSla", 10000, 10000, 0.9999, true, "SLA", ""
            );

            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.contains(ComplianceEvidenceEvaluator.SIZING_NOTE));
        }

        @Test
        @DisplayName("non-SLA origin without contract ref does NOT include sizing note")
        void nonSlaDoesNotIncludeNote() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testGeneric", 200, 200, 0.9999, true, "UNSPECIFIED", ""
            );

            assertThat(explanation.verdict().caveats())
                    .noneMatch(c -> c.contains(ComplianceEvidenceEvaluator.SIZING_NOTE));
        }

        @Test
        @DisplayName("EMPIRICAL origin without contract ref does NOT include sizing note")
        void empiricalDoesNotIncludeNote() {
            BaselineData baseline = createBaseline(1000, 999);

            StatisticalExplanation explanation = builder.build(
                    "testEmpirical", 200, 200, baseline, 0.9999, true, 0.95,
                    "EMPIRICAL", ""
            );

            assertThat(explanation.verdict().caveats())
                    .noneMatch(c -> c.contains(ComplianceEvidenceEvaluator.SIZING_NOTE));
        }

        @Test
        @DisplayName("non-SLA origin WITH contract ref includes sizing note")
        void contractRefTriggersNote() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSlo", 200, 200, 0.9999, true, "SLO", "Internal SLO Doc"
            );

            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.contains(ComplianceEvidenceEvaluator.SIZING_NOTE));
        }

        @Test
        @DisplayName("SLA-anchored test with sufficient samples does NOT include sizing note")
        void slaSufficientSamplesNoNote() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSla", 100000, 100000, 0.9999, true, "SLA", "Payment SLA v2.3"
            );

            assertThat(explanation.verdict().caveats())
                    .noneMatch(c -> c.contains(ComplianceEvidenceEvaluator.SIZING_NOTE));
        }

        @Test
        @DisplayName("sizing note appears regardless of FAIL verdict")
        void noteAppearsOnFailVerdict() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSla", 200, 190, 0.9999, false, "SLA", "Payment SLA v2.3"
            );

            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.contains(ComplianceEvidenceEvaluator.SIZING_NOTE));
        }

        @Test
        @DisplayName("sizing note includes PASS/FAIL asymmetry guidance")
        void noteIncludesAsymmetryGuidance() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSla", 200, 200, 0.9999, true, "SLA", ""
            );

            String sizingCaveat = explanation.verdict().caveats().stream()
                    .filter(c -> c.contains(ComplianceEvidenceEvaluator.SIZING_NOTE))
                    .findFirst()
                    .orElseThrow();

            assertThat(sizingCaveat).contains("FAIL verdict remains a reliable indication");
        }
    }

    @Nested
    @DisplayName("SMOKE intent framing")
    class SmokeIntentFraming {

        @Test
        @DisplayName("SMOKE + SLA hypothesis uses softened language")
        void smokeSlahypothesisSoftened() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSmoke", 50, 48, 0.90, true, "SLA", "", true
            );

            assertThat(explanation.hypothesis().nullHypothesis())
                    .contains("observed rate consistent with target");
            assertThat(explanation.hypothesis().alternativeHypothesis())
                    .contains("observed rate inconsistent with target");
        }

        @Test
        @DisplayName("VERIFICATION + SLA hypothesis uses full compliance language")
        void verificationSlaHypothesisFullCompliance() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testVerification", 100, 95, 0.90, true, "SLA", "", false
            );

            assertThat(explanation.hypothesis().nullHypothesis())
                    .contains("system meets SLA requirement");
        }

        @Test
        @DisplayName("SMOKE + SLA verdict avoids compliance language")
        void smokeVerdictAvoidsComplianceLanguage() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSmoke", 50, 48, 0.90, true, "SLA", "", true
            );

            assertThat(explanation.verdict().plainEnglish())
                    .contains("consistent with the target");
            assertThat(explanation.verdict().plainEnglish())
                    .doesNotContain("SLA requirement");
        }

        @Test
        @DisplayName("SMOKE + SLA FAIL verdict avoids compliance language")
        void smokeFailVerdictAvoidsComplianceLanguage() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSmoke", 50, 30, 0.90, false, "SLA", "", true
            );

            assertThat(explanation.verdict().plainEnglish())
                    .contains("inconsistent with the target");
            assertThat(explanation.verdict().plainEnglish())
                    .doesNotContain("SLA obligation");
        }

        @Test
        @DisplayName("SMOKE + normative + undersized adds sizing caveat")
        void smokeNormativeUndersizedAddsSizingCaveat() {
            // N=10, p₀=0.95, SLA → undersized for verification (N_min=52)
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSmoke", 10, 10, 0.95, true, "SLA", "", true
            );

            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.contains("Sample not sized for verification"));
        }

        @Test
        @DisplayName("SMOKE + normative + sized adds hint caveat")
        void smokeNormativeSizedAddsHintCaveat() {
            // N=100, p₀=0.90, SLA → sized for verification (N_min=25)
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSmoke", 100, 95, 0.90, true, "SLA", "", true
            );

            assertThat(explanation.verdict().caveats())
                    .anyMatch(c -> c.contains("Consider setting intent = VERIFICATION"));
        }

        @Test
        @DisplayName("VERIFICATION intent does not add smoke sizing caveats")
        void verificationDoesNotAddSmokeCaveats() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testVerification", 10, 10, 0.95, true, "SLA", "", false
            );

            assertThat(explanation.verdict().caveats())
                    .noneMatch(c -> c.contains("Sample not sized for verification"));
            assertThat(explanation.verdict().caveats())
                    .noneMatch(c -> c.contains("Consider setting intent = VERIFICATION"));
        }

        @Test
        @DisplayName("SMOKE + non-normative does not add smoke sizing caveats")
        void smokeNonNormativeDoesNotAddCaveats() {
            StatisticalExplanation explanation = builder.buildWithInlineThreshold(
                    "testSmoke", 10, 10, 0.95, true, "EMPIRICAL", "", true
            );

            assertThat(explanation.verdict().caveats())
                    .noneMatch(c -> c.contains("Sample not sized for verification"));
            assertThat(explanation.verdict().caveats())
                    .noneMatch(c -> c.contains("Consider setting intent = VERIFICATION"));
        }
    }

    private BaselineData createBaseline(int samples, int successes) {
        return new BaselineData(
                "TestUseCase.yaml",
                Instant.now(),
                samples,
                successes
        );
    }
}

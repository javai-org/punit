package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.statistics.ComplianceEvidenceEvaluator;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdictBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ResultPublisher}.
 */
class ResultPublisherTest {

    private ResultPublisher publisher;
    private PUnitReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new PUnitReporter();
        publisher = new ResultPublisher(reporter);
    }

    private ProbabilisticTestVerdictBuilder minimalBuilder(boolean passed) {
        int successes = passed ? 95 : 80;
        int failures = passed ? 5 : 20;
        double observedRate = passed ? 0.95 : 0.80;
        return new ProbabilisticTestVerdictBuilder()
                .identity("TestClass", "testMethod", null)
                .execution(100, 100, successes, failures, 0.9, observedRate, 1500)
                .junitPassed(passed)
                .passedStatistically(passed);
    }

    private ProbabilisticTestVerdict createVerdict(boolean passed) {
        return minimalBuilder(passed).build();
    }

    private ProbabilisticTestVerdict createVerdictWithBudgets() {
        return new ProbabilisticTestVerdictBuilder()
                .identity("TestClass", "testMethod", null)
                .execution(100, 50, 45, 5, 0.9, 0.90, 2500)
                .appliedMultiplier(2.0)
                .junitPassed(true)
                .passedStatistically(true)
                .cost(250, 5000, 500, CostBudgetMonitor.TokenMode.DYNAMIC)
                .provenance(ThresholdOrigin.SLA, "SLA-2024-001", null)
                .build();
    }

    @Nested
    @DisplayName("buildReportEntries")
    class BuildReportEntries {

        @Test
        @DisplayName("includes basic test metrics")
        void includesBasicTestMetrics() {
            ProbabilisticTestVerdict verdict = createVerdict(true);

            Map<String, String> entries = publisher.buildReportEntries(verdict, null);

            assertThat(entries).containsEntry("punit.samples", "100");
            assertThat(entries).containsEntry("punit.samplesExecuted", "100");
            assertThat(entries).containsEntry("punit.successes", "95");
            assertThat(entries).containsEntry("punit.failures", "5");
            assertThat(entries).containsEntry("punit.verdict", "PASS");
        }

        @Test
        @DisplayName("includes FAIL verdict for failed test")
        void includesFailVerdictForFailedTest() {
            ProbabilisticTestVerdict verdict = createVerdict(false);

            Map<String, String> entries = publisher.buildReportEntries(verdict, null);

            assertThat(entries).containsEntry("punit.verdict", "FAIL");
        }

        @Test
        @DisplayName("includes pass rate metrics")
        void includesPassRateMetrics() {
            ProbabilisticTestVerdict verdict = createVerdict(true);

            Map<String, String> entries = publisher.buildReportEntries(verdict, null);

            assertThat(entries).containsEntry("punit.minPassRate", "0.9000");
            assertThat(entries).containsEntry("punit.observedPassRate", "0.9500");
        }

        @Test
        @DisplayName("includes elapsed time")
        void includesElapsedTime() {
            ProbabilisticTestVerdict verdict = createVerdict(true);

            Map<String, String> entries = publisher.buildReportEntries(verdict, null);

            assertThat(entries).containsEntry("punit.elapsedMs", "1500");
        }

        @Test
        @DisplayName("includes termination reason")
        void includesTerminationReason() {
            ProbabilisticTestVerdict verdict = new ProbabilisticTestVerdictBuilder()
                    .identity("TestClass", "testMethod", null)
                    .execution(100, 50, 20, 30, 0.9, 0.40, 1000)
                    .termination(TerminationReason.IMPOSSIBILITY, "Cannot achieve 90% pass rate")
                    .junitPassed(false)
                    .passedStatistically(false)
                    .build();

            Map<String, String> entries = publisher.buildReportEntries(verdict, null);

            assertThat(entries).containsEntry("punit.terminationReason", "IMPOSSIBILITY");
        }

        @Test
        @DisplayName("includes multiplier when applied")
        void includesMultiplierWhenApplied() {
            ProbabilisticTestVerdict verdict = createVerdictWithBudgets();

            Map<String, String> entries = publisher.buildReportEntries(verdict, null);

            assertThat(entries).containsEntry("punit.samplesMultiplier", "2.00");
        }

        @Test
        @DisplayName("includes method budget info")
        void includesMethodBudgetInfo() {
            ProbabilisticTestVerdict verdict = createVerdictWithBudgets();

            Map<String, String> entries = publisher.buildReportEntries(verdict, null);

            assertThat(entries).containsEntry("punit.method.timeBudgetMs", "5000");
            assertThat(entries).containsEntry("punit.method.tokenBudget", "500");
            assertThat(entries).containsEntry("punit.method.tokensConsumed", "250");
            assertThat(entries).containsEntry("punit.tokenMode", "DYNAMIC");
        }

        @Test
        @DisplayName("excludes budget info when not configured")
        void excludesBudgetInfoWhenNotConfigured() {
            ProbabilisticTestVerdict verdict = createVerdict(true);

            Map<String, String> entries = publisher.buildReportEntries(verdict, null);

            assertThat(entries).doesNotContainKey("punit.method.timeBudgetMs");
            assertThat(entries).doesNotContainKey("punit.method.tokenBudget");
            assertThat(entries).doesNotContainKey("punit.samplesMultiplier");
        }
    }

    @Nested
    @DisplayName("appendProvenance")
    class AppendProvenance {

        @Test
        @DisplayName("appends threshold origin when set")
        void appendsThresholdOriginWhenSet() {
            ProbabilisticTestVerdict verdict = createVerdictWithBudgets();
            StringBuilder sb = new StringBuilder();

            publisher.appendProvenance(sb, verdict);

            assertThat(sb.toString()).contains("Threshold origin:").contains("SLA");
        }

        @Test
        @DisplayName("appends contract ref when set")
        void appendsContractRefWhenSet() {
            ProbabilisticTestVerdict verdict = createVerdictWithBudgets();
            StringBuilder sb = new StringBuilder();

            publisher.appendProvenance(sb, verdict);

            assertThat(sb.toString()).contains("Contract:").contains("SLA-2024-001");
        }

        @Test
        @DisplayName("appends nothing when not configured")
        void appendsNothingWhenNotConfigured() {
            ProbabilisticTestVerdict verdict = createVerdict(true);
            StringBuilder sb = new StringBuilder();

            publisher.appendProvenance(sb, verdict);

            assertThat(sb.toString()).isEmpty();
        }
    }

    @Nested
    @DisplayName("appendComplianceEvidenceNote")
    class AppendComplianceEvidenceNote {

        @Test
        @DisplayName("appends sizing note for SLA-anchored undersized test")
        void appendsNoteForSlaUndersized() {
            ProbabilisticTestVerdict verdict = createSlaVerdict(200, 0.9999, ThresholdOrigin.SLA, "SLA v2.3");
            StringBuilder sb = new StringBuilder();

            publisher.appendComplianceEvidenceNote(sb, verdict);

            assertThat(sb.toString()).contains(ComplianceEvidenceEvaluator.SIZING_NOTE);
        }

        @Test
        @DisplayName("does NOT append note for non-SLA test without contract ref")
        void doesNotAppendForNonSla() {
            ProbabilisticTestVerdict verdict = createSlaVerdict(200, 0.9999, ThresholdOrigin.UNSPECIFIED, null);
            StringBuilder sb = new StringBuilder();

            publisher.appendComplianceEvidenceNote(sb, verdict);

            assertThat(sb.toString()).doesNotContain(ComplianceEvidenceEvaluator.SIZING_NOTE);
        }

        @Test
        @DisplayName("does NOT append note when sample size is sufficient")
        void doesNotAppendWhenSufficient() {
            ProbabilisticTestVerdict verdict = createSlaVerdict(100000, 0.9999, ThresholdOrigin.SLA, "");
            StringBuilder sb = new StringBuilder();

            publisher.appendComplianceEvidenceNote(sb, verdict);

            assertThat(sb.toString()).doesNotContain(ComplianceEvidenceEvaluator.SIZING_NOTE);
        }

        @Test
        @DisplayName("does NOT append note for SMOKE with normative origin (deferred to smoke note)")
        void doesNotAppendForSmokeWithNormativeOrigin() {
            ProbabilisticTestVerdict verdict = new ProbabilisticTestVerdictBuilder()
                    .identity("TestClass", "testSla", null)
                    .execution(50, 50, 50, 0, 0.9999, 1.0, 1000)
                    .intent(TestIntent.SMOKE, 0.95)
                    .provenance(ThresholdOrigin.SLA, "SLA v2.3", null)
                    .junitPassed(true)
                    .passedStatistically(true)
                    .build();
            StringBuilder sb = new StringBuilder();

            publisher.appendComplianceEvidenceNote(sb, verdict);

            assertThat(sb.toString()).doesNotContain(ComplianceEvidenceEvaluator.SIZING_NOTE);
        }

        private ProbabilisticTestVerdict createSlaVerdict(int samples, double minPassRate,
                ThresholdOrigin origin, String contractRef) {
            return new ProbabilisticTestVerdictBuilder()
                    .identity("TestClass", "testSla", null)
                    .execution(samples, samples, samples, 0, minPassRate, 1.0, 1000)
                    .provenance(origin, contractRef, null)
                    .junitPassed(true)
                    .passedStatistically(true)
                    .build();
        }
    }

    @Nested
    @DisplayName("appendSmokeIntentNote")
    class AppendSmokeIntentNote {

        @Test
        @DisplayName("appends undersized note for SMOKE + normative + undersized")
        void appendsUndersizedNoteForSmokeNormativeUndersized() {
            ProbabilisticTestVerdict verdict = createIntentVerdict(true, TestIntent.SMOKE,
                    10, 0.95, ThresholdOrigin.SLA);
            StringBuilder sb = new StringBuilder();

            publisher.appendSmokeIntentNote(sb, verdict);

            assertThat(sb.toString()).contains("Sample not sized for verification");
            assertThat(sb.toString()).contains("N=10");
        }

        @Test
        @DisplayName("appends sized hint for SMOKE + normative + sized")
        void appendsSizedHintForSmokeNormativeSized() {
            ProbabilisticTestVerdict verdict = createIntentVerdict(true, TestIntent.SMOKE,
                    100, 0.90, ThresholdOrigin.SLA);
            StringBuilder sb = new StringBuilder();

            publisher.appendSmokeIntentNote(sb, verdict);

            assertThat(sb.toString()).contains("Sample is sized for verification");
            assertThat(sb.toString()).contains("intent = VERIFICATION");
        }

        @Test
        @DisplayName("does not append note for VERIFICATION intent")
        void doesNotAppendForVerificationIntent() {
            ProbabilisticTestVerdict verdict = createIntentVerdict(true, TestIntent.VERIFICATION,
                    10, 0.95, ThresholdOrigin.SLA);
            StringBuilder sb = new StringBuilder();

            publisher.appendSmokeIntentNote(sb, verdict);

            assertThat(sb.toString()).isEmpty();
        }

        @Test
        @DisplayName("does not append note for SMOKE without normative origin")
        void doesNotAppendForSmokeWithoutNormative() {
            ProbabilisticTestVerdict verdict = createIntentVerdict(true, TestIntent.SMOKE,
                    10, 0.95, ThresholdOrigin.UNSPECIFIED);
            StringBuilder sb = new StringBuilder();

            publisher.appendSmokeIntentNote(sb, verdict);

            assertThat(sb.toString()).isEmpty();
        }
    }

    private ProbabilisticTestVerdict createIntentVerdict(boolean passed, TestIntent intent,
            int samples, double minPassRate, ThresholdOrigin origin) {
        int successes = passed ? (int) (samples * 0.95) : (int) (samples * 0.50);
        int failures = samples - successes;
        double observedRate = (double) successes / samples;
        return new ProbabilisticTestVerdictBuilder()
                .identity("TestClass", "testMethod", null)
                .execution(samples, samples, successes, failures, minPassRate, observedRate, 1000)
                .intent(intent, 0.95)
                .provenance(origin, null, null)
                .junitPassed(passed)
                .passedStatistically(passed)
                .build();
    }
}

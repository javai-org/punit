package org.javai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.punit.api.TestIntent;
import org.javai.punit.controls.budget.CostBudgetMonitor.TokenMode;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdict.BaselineSummary;
import org.javai.punit.verdict.ProbabilisticTestVerdict.CostSummary;
import org.javai.punit.verdict.ProbabilisticTestVerdict.CovariateStatus;
import org.javai.punit.verdict.ProbabilisticTestVerdict.ExecutionSummary;
import org.javai.punit.verdict.ProbabilisticTestVerdict.FunctionalDimension;
import org.javai.punit.verdict.ProbabilisticTestVerdict.LatencyDimension;
import org.javai.punit.verdict.ProbabilisticTestVerdict.Misalignment;
import org.javai.punit.verdict.ProbabilisticTestVerdict.PercentileAssertion;
import org.javai.punit.verdict.ProbabilisticTestVerdict.SpecProvenance;
import org.javai.punit.verdict.ProbabilisticTestVerdict.StatisticalAnalysis;
import org.javai.punit.verdict.ProbabilisticTestVerdict.Termination;
import org.javai.punit.verdict.ProbabilisticTestVerdict.TestIdentity;
import org.javai.punit.verdict.PunitVerdict;
import org.javai.punit.verdict.VerdictTextRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VerdictTextRenderer")
class VerdictTextRendererTest {

    @Nested
    @DisplayName("renderSummary")
    class RenderSummary {

        @Test
        @DisplayName("renders passing verdict with pass rate comparison")
        void rendersPassingVerdict() {
            String text = VerdictTextRenderer.renderSummary(passingVerdict());

            assertThat(text).contains("Observed pass rate:");
            assertThat(text).contains(">= required:");
            assertThat(text).contains("95/100");
        }

        @Test
        @DisplayName("renders failing verdict with pass rate comparison")
        void rendersFailingVerdict() {
            String text = VerdictTextRenderer.renderSummary(failingVerdict());

            assertThat(text).contains("Observed pass rate:");
            assertThat(text).contains("< required:");
        }

        @Test
        @DisplayName("renders budget exhaustion verdict")
        void rendersBudgetExhaustion() {
            String text = VerdictTextRenderer.renderSummary(budgetExhaustedVerdict());

            assertThat(text).contains("Samples executed:");
            assertThat(text).contains("budget exhausted");
            assertThat(text).contains("Termination:");
        }

        @Test
        @DisplayName("includes elapsed time")
        void includesElapsed() {
            String text = VerdictTextRenderer.renderSummary(passingVerdict());

            assertThat(text).contains("Elapsed:");
            assertThat(text).contains("150ms");
        }

        @Test
        @DisplayName("includes dimension breakdown when both present")
        void includesDimensionBreakdown() {
            String text = VerdictTextRenderer.renderSummary(verdictWithBothDimensions());

            assertThat(text).contains("Contract:");
            assertThat(text).contains("95/100 passed");
            assertThat(text).contains("Latency:");
            assertThat(text).contains("90/100 within limit");
        }

        @Test
        @DisplayName("omits dimension breakdown when latency is skipped")
        void omitsDimensionBreakdownWhenSkipped() {
            String text = VerdictTextRenderer.renderSummary(verdictWithSkippedLatency());

            assertThat(text).doesNotContain("Contract:");
            assertThat(text).doesNotContain("Latency:");
        }

        @Test
        @DisplayName("includes baseline line with bundled label when provenance has spec filename")
        void includesBaselineLineBundled() {
            String text = VerdictTextRenderer.renderSummary(verdictWithBundledProvenance());

            assertThat(text).contains("Baseline:");
            assertThat(text).contains("my-spec.yaml (bundled)");
        }

        @Test
        @DisplayName("includes baseline line with env-local path when provenance has env-local source")
        void includesBaselineLineEnvLocal() {
            String text = VerdictTextRenderer.renderSummary(verdictWithEnvLocalProvenance());

            assertThat(text).contains("Baseline:");
            assertThat(text).contains("my-spec.yaml /opt/punit/specs");
        }

        @Test
        @DisplayName("omits baseline line when no provenance")
        void omitsBaselineLineWhenNoProvenance() {
            String text = VerdictTextRenderer.renderSummary(passingVerdict());

            assertThat(text).doesNotContain("Baseline:");
        }
    }

    @Nested
    @DisplayName("renderStatisticalAnalysis")
    class RenderStatisticalAnalysis {

        @Test
        @DisplayName("includes confidence level and standard error with p̂ notation")
        void includesConfidenceAndError() {
            String text = VerdictTextRenderer.renderStatisticalAnalysis(passingVerdict());

            assertThat(text).contains("Confidence level:");
            assertThat(text).contains("95.0%");
            assertThat(text).contains("SE(p̂):");
        }

        @Test
        @DisplayName("includes CI lower bound")
        void includesCiLowerBound() {
            String text = VerdictTextRenderer.renderStatisticalAnalysis(passingVerdict());

            assertThat(text).contains("CI lower bound:");
        }

        @Test
        @DisplayName("includes Z and p-value with proper notation")
        void includesZAndPValue() {
            String text = VerdictTextRenderer.renderStatisticalAnalysis(passingVerdict());

            assertThat(text).contains("Z:");
            assertThat(text).contains("p-value:");
        }

        @Test
        @DisplayName("includes baseline details when present")
        void includesBaseline() {
            String text = VerdictTextRenderer.renderStatisticalAnalysis(verdictWithBaseline());

            assertThat(text).contains("Baseline spec:");
            assertThat(text).contains("my-spec.yaml");
            assertThat(text).contains("Baseline samples:");
            assertThat(text).contains("Derived threshold:");
        }

        @Test
        @DisplayName("includes latency assertions when present")
        void includesLatencyAssertions() {
            String text = VerdictTextRenderer.renderStatisticalAnalysis(verdictWithLatencyAssertions());

            assertThat(text).contains("Latency assertions:");
            assertThat(text).contains("p95:");
            assertThat(text).contains("420ms");
        }

        @Test
        @DisplayName("includes covariate misalignments when present")
        void includesMisalignments() {
            String text = VerdictTextRenderer.renderStatisticalAnalysis(verdictWithMisalignment());

            assertThat(text).contains("Covariate misalignments:");
            assertThat(text).contains("model: baseline=gpt-4, test=gpt-4o");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ProbabilisticTestVerdict passingVerdict() {
        return minimalVerdict(true, PunitVerdict.PASS);
    }

    private ProbabilisticTestVerdict failingVerdict() {
        return minimalVerdict(false, PunitVerdict.FAIL);
    }

    private ProbabilisticTestVerdict minimalVerdict(boolean passed, PunitVerdict punitVerdict) {
        int successes = passed ? 95 : 80;
        int failures = passed ? 5 : 20;
        double observedRate = passed ? 0.95 : 0.80;
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-03-11T14:30:00Z"),
                new TestIdentity("com.example.MyTest", "shouldPass", Optional.empty()),
                new ExecutionSummary(100, 100, successes, failures, 0.9, observedRate, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95),
                Optional.empty(), Optional.empty(),
                new StatisticalAnalysis(0.95, 0.0218, 0.8948, 0.9798,
                        Optional.of(2.29), Optional.of(0.011),
                        Optional.empty(), Optional.empty(), List.of()),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(), Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(), passed, punitVerdict
        );
    }

    private ProbabilisticTestVerdict budgetExhaustedVerdict() {
        ProbabilisticTestVerdict base = minimalVerdict(false, PunitVerdict.FAIL);
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(),
                new ExecutionSummary(100, 50, 40, 10, 0.9, 0.8, 5000,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(),
                new Termination(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED,
                        Optional.of("Time budget exceeded")),
                base.environmentMetadata(), false, PunitVerdict.FAIL
        );
    }

    private ProbabilisticTestVerdict verdictWithBothDimensions() {
        ProbabilisticTestVerdict base = passingVerdict();
        LatencyDimension latency = new LatencyDimension(
                90, 100, false, Optional.empty(),
                120, 340, 420, 810, 1250,
                List.of(), List.of(), 90, 10
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                Optional.of(new FunctionalDimension(95, 5, 0.95)),
                Optional.of(latency),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithSkippedLatency() {
        ProbabilisticTestVerdict base = passingVerdict();
        LatencyDimension latency = new LatencyDimension(
                0, 100, true, Optional.of("No successes"),
                0, 0, 0, 0, 0,
                List.of(), List.of(), 0, 0
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                Optional.of(new FunctionalDimension(95, 5, 0.95)),
                Optional.of(latency),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithBaseline() {
        ProbabilisticTestVerdict base = passingVerdict();
        StatisticalAnalysis stats = new StatisticalAnalysis(
                0.95, 0.0218, 0.8948, 0.9798,
                Optional.of(2.29), Optional.of(0.011),
                Optional.of("Wilson score lower bound"),
                Optional.of(new BaselineSummary(
                        "my-spec.yaml", Instant.parse("2026-02-15T00:00:00Z"),
                        1000, 940, 0.94, 0.92)),
                List.of()
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                stats, base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithLatencyAssertions() {
        ProbabilisticTestVerdict base = passingVerdict();
        LatencyDimension latency = new LatencyDimension(
                90, 100, false, Optional.empty(),
                120, 340, 420, 810, 1250,
                List.of(new PercentileAssertion("p95", 420, 500, true, false, "from baseline")),
                List.of(), 90, 10
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), Optional.of(latency),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithBundledProvenance() {
        ProbabilisticTestVerdict base = passingVerdict();
        SpecProvenance prov = new SpecProvenance("EMPIRICAL", null, "my-spec.yaml",
                Optional.empty(), Optional.of("(bundled)"));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), Optional.of(prov), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithEnvLocalProvenance() {
        ProbabilisticTestVerdict base = passingVerdict();
        SpecProvenance prov = new SpecProvenance("EMPIRICAL", null, "my-spec.yaml",
                Optional.empty(), Optional.of("/opt/punit/specs"));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), Optional.of(prov), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithMisalignment() {
        ProbabilisticTestVerdict base = minimalVerdict(false, PunitVerdict.INCONCLUSIVE);
        CovariateStatus cov = new CovariateStatus(false,
                List.of(new Misalignment("model", "gpt-4", "gpt-4o")));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), cov, base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }
}

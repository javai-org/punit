package org.javai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.punit.api.TestIntent;
import org.javai.punit.controls.budget.CostBudgetMonitor.TokenMode;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HtmlReportWriter")
class HtmlReportWriterTest {

    @Nested
    @DisplayName("HTML structure")
    class HtmlStructure {

        @Test
        @DisplayName("produces valid HTML with DOCTYPE and head")
        void producesValidHtml() {
            String html = HtmlReportWriter.generate(List.of());

            assertThat(html).startsWith("<!DOCTYPE html>");
            assertThat(html).contains("<html lang=\"en\">");
            assertThat(html).contains("<title>PUnit Test Report</title>");
            assertThat(html).contains("<style>");
            assertThat(html).contains("</html>");
        }

        @Test
        @DisplayName("includes summary statistics")
        void includesSummaryStats() {
            String html = HtmlReportWriter.generate(List.of(
                    passingVerdict(), failingVerdict()));

            assertThat(html).contains("Total: 2");
            assertThat(html).contains("Pass: 1");
            assertThat(html).contains("Fail: 1");
        }

        @Test
        @DisplayName("includes inconclusive count when present")
        void includesInconclusiveCount() {
            String html = HtmlReportWriter.generate(List.of(inconclusiveVerdict()));

            assertThat(html).contains("Inconclusive: 1");
        }

        @Test
        @DisplayName("omits inconclusive count when zero")
        void omitsInconclusiveWhenZero() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).doesNotContain("Inconclusive");
        }
    }

    @Nested
    @DisplayName("table rows")
    class TableRows {

        @Test
        @DisplayName("renders test method name as expandable summary")
        void rendersMethodName() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<summary>shouldPass</summary>");
        }

        @Test
        @DisplayName("applies correct CSS classes for passing verdict")
        void appliesPassCssClasses() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("class=\"junit-pass\"");
            assertThat(html).contains("class=\"punit-pass\"");
        }

        @Test
        @DisplayName("applies correct CSS classes for failing verdict")
        void appliesFailCssClasses() {
            String html = HtmlReportWriter.generate(List.of(failingVerdict()));

            assertThat(html).contains("class=\"junit-fail\"");
            assertThat(html).contains("class=\"punit-fail\"");
        }

        @Test
        @DisplayName("JUnit FAIL with PUnit PASS renders divergent CSS classes")
        void junitFailWithPunitPassRendersDivergentClasses() {
            ProbabilisticTestVerdict verdict = divergentVerdict();

            String html = HtmlReportWriter.generate(List.of(verdict));

            assertThat(html).contains("class=\"junit-fail\"");
            assertThat(html).contains("class=\"punit-pass\"");
        }

        @Test
        @DisplayName("applies correct CSS class for inconclusive verdict")
        void appliesInconclusiveCssClass() {
            String html = HtmlReportWriter.generate(List.of(inconclusiveVerdict()));

            assertThat(html).contains("class=\"punit-inconclusive\"");
        }

        @Test
        @DisplayName("shows functional dimension when present")
        void showsFunctionalDimension() {
            String html = HtmlReportWriter.generate(List.of(verdictWithFunctional()));

            assertThat(html).contains("95/100");
        }

        @Test
        @DisplayName("shows dash for absent functional dimension")
        void showsDashForAbsentFunctional() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<td>-</td>");
        }

        @Test
        @DisplayName("shows latency p50, p95, and p99 when present")
        void showsLatencyPercentiles() {
            String html = HtmlReportWriter.generate(List.of(verdictWithLatency()));

            assertThat(html).contains("<th>p50</th>");
            assertThat(html).contains("<th>p95</th>");
            assertThat(html).contains("<th>p99</th>");
            assertThat(html).contains("120ms");   // p50
            assertThat(html).contains("420ms");   // p95
            assertThat(html).contains("810ms");   // p99
        }

        @Test
        @DisplayName("shows samples as executed/planned")
        void showsSamples() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("100/100");
        }

        @Test
        @DisplayName("shows elapsed time")
        void showsElapsed() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("150ms");
        }
    }

    @Nested
    @DisplayName("expand/collapse structure")
    class ExpandCollapse {

        @Test
        @DisplayName("includes level 2 summary text in pre block")
        void includesLevel2Summary() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("class=\"level2\"");
            assertThat(html).contains("Observed pass rate:");
        }

        @Test
        @DisplayName("includes nested details for statistical analysis")
        void includesNestedStatisticalAnalysis() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<summary>Statistical Analysis</summary>");
            assertThat(html).contains("class=\"level3\"");
            assertThat(html).contains("Confidence level:");
        }
    }

    @Nested
    @DisplayName("grouping")
    class Grouping {

        @Test
        @DisplayName("groups by use-case-id when present")
        void groupsByUseCaseId() {
            ProbabilisticTestVerdict verdict = verdictWithUseCaseId("payment-gateway");

            String html = HtmlReportWriter.generate(List.of(verdict));

            assertThat(html).contains("<h2>payment-gateway</h2>");
        }

        @Test
        @DisplayName("groups by class name when use-case-id absent")
        void groupsByClassName() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<h2>com.example.MyTest</h2>");
        }
    }

    @Nested
    @DisplayName("detail panel indentation")
    class DetailPanelIndentation {

        @Test
        @DisplayName("level2 and level3 blocks have left margin in CSS")
        void detailPanelsAreIndented() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("margin: 0.5rem 0 0.5rem 1.5rem");
            assertThat(html).contains("details details");
        }
    }

    @Nested
    @DisplayName("inconclusive guidance")
    class InconclusiveGuidance {

        @Test
        @DisplayName("shows report-level banner when inconclusive verdicts exist")
        void showsReportLevelBanner() {
            String html = HtmlReportWriter.generate(List.of(inconclusiveVerdictWithMisalignment()));

            assertThat(html).contains("banner-inconclusive");
            assertThat(html).contains("1 test has an inconclusive verdict");
            assertThat(html).contains("Re-run experiments to produce baselines");
            assertThat(html).contains("./gradlew exp");
        }

        @Test
        @DisplayName("banner uses plural form for multiple inconclusive verdicts")
        void showsPluralBanner() {
            String html = HtmlReportWriter.generate(List.of(
                    inconclusiveVerdictWithMisalignment(),
                    inconclusiveVerdictWithMisalignment()));

            assertThat(html).contains("2 tests have an inconclusive verdict");
        }

        @Test
        @DisplayName("no banner when no inconclusive verdicts")
        void noBannerWhenNoneInconclusive() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).doesNotContain("class=\"banner-inconclusive\"");
        }

        @Test
        @DisplayName("shows test-level misalignment guidance with experiment command")
        void showsTestLevelGuidance() {
            String html = HtmlReportWriter.generate(List.of(inconclusiveVerdictWithMisalignment()));

            assertThat(html).contains("misalignment-guidance");
            assertThat(html).contains("Covariate misalignment");
            assertThat(html).contains("model: baseline=");
            assertThat(html).contains("./gradlew exp -Prun=MyTest");
        }

        @Test
        @DisplayName("no test-level guidance for passing verdicts")
        void noGuidanceForPassingVerdicts() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).doesNotContain("class=\"misalignment-guidance\"");
        }
    }

    @Nested
    @DisplayName("baseline provenance in detail")
    class BaselineProvenance {

        @Test
        @DisplayName("level 2 block contains baseline filename when provenance present")
        void level2ContainsBaselineFilename() {
            String html = HtmlReportWriter.generate(List.of(verdictWithProvenance()));

            assertThat(html).contains("Baseline:");
            assertThat(html).contains("payment-gateway.yaml");
        }
    }

    @Nested
    @DisplayName("statistical tooltips")
    class StatisticalTooltips {

        @Test
        @DisplayName("level 3 block contains tooltip spans on labels only")
        void level3ContainsTooltipSpans() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<span class=\"tip\" data-tip=");
            assertThat(html).contains("\">Confidence level:</span>");
        }

        @Test
        @DisplayName("CSS includes tooltip styles for hover pseudo-element")
        void cssIncludesTooltipStyles() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("pre.level3 span.tip");
            assertThat(html).contains("cursor: help");
            assertThat(html).contains("span.tip:hover::after");
            assertThat(html).contains("content: attr(data-tip)");
        }
    }

    @Nested
    @DisplayName("statistical assumptions")
    class StatisticalAssumptions {

        @Test
        @DisplayName("includes collapsed assumptions section in every report")
        void includesAssumptionsSection() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("<details class=\"assumptions\">");
            assertThat(html).contains("<summary>Statistical assumptions and limitations</summary>");
        }

        @Test
        @DisplayName("assumptions section is present even for empty reports")
        void presentForEmptyReports() {
            String html = HtmlReportWriter.generate(List.of());

            assertThat(html).contains("<details class=\"assumptions\">");
        }

        @Test
        @DisplayName("assumptions section appears between header and main content")
        void appearsBeforeMainContent() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            int headerEnd = html.indexOf("</header>");
            int assumptionsStart = html.indexOf("<details class=\"assumptions\">");
            int mainStart = html.indexOf("<main>");

            assertThat(assumptionsStart).isGreaterThan(headerEnd);
            assertThat(assumptionsStart).isLessThan(mainStart);
        }

        @Test
        @DisplayName("contains key assumption bullet points")
        void containsKeyAssumptions() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html)
                    .contains("Binary outcome")
                    .contains("Same question each time")
                    .contains("Unchanged threshold")
                    .contains("Independence")
                    .contains("No major drift during sampling");
        }

        @Test
        @DisplayName("contains warning about assumption-violating test patterns")
        void containsWarning() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains("assumptions-warning");
            assertThat(html).contains("warm up, exhaust, mutate, learn, cache, throttle, or degrade");
        }

        @Test
        @DisplayName("includes CSS for assumptions styling")
        void includesCss() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains(".assumptions");
            assertThat(html).contains(".assumptions-body");
            assertThat(html).contains(".assumptions-warning");
        }
    }

    @Nested
    @DisplayName("HTML escaping")
    class HtmlEscaping {

        @Test
        @DisplayName("escapes special characters in test names")
        void escapesSpecialCharacters() {
            ProbabilisticTestVerdict verdict = verdictWithMethodName("test<script>alert('xss')</script>");

            String html = HtmlReportWriter.generate(List.of(verdict));

            assertThat(html).doesNotContain("<script>");
            assertThat(html).contains("&lt;script&gt;");
        }
    }

    @Nested
    @DisplayName("latency colour coding")
    class LatencyColourCoding {

        @Test
        @DisplayName("observational latency renders values without colour class")
        void observationalLatencyRendersWithoutColour() {
            String html = HtmlReportWriter.generate(List.of(verdictWithLatency()));

            assertThat(html).contains("class=\"latency-observed\">120ms</td>");
            assertThat(html).contains("class=\"latency-observed\">420ms</td>");
            assertThat(html).contains("class=\"latency-observed\">810ms</td>");
            assertThat(html).doesNotContain("class=\"latency-pass\">");
            assertThat(html).doesNotContain("class=\"latency-fail\">");
        }

        @Test
        @DisplayName("passing latency assertion renders with latency-pass class")
        void passingAssertionRendersGreen() {
            String html = HtmlReportWriter.generate(List.of(
                    verdictWithLatencyAssertions(List.of(
                            new PercentileAssertion("p95", 420, 500, true, false, "explicit")))));

            assertThat(html).contains("class=\"latency-pass\">420ms</td>");
        }

        @Test
        @DisplayName("failing latency assertion renders with latency-fail class")
        void failingAssertionRendersRed() {
            String html = HtmlReportWriter.generate(List.of(
                    verdictWithLatencyAssertions(List.of(
                            new PercentileAssertion("p99", 810, 500, false, false, "explicit")))));

            assertThat(html).contains("class=\"latency-fail\">810ms</td>");
        }

        @Test
        @DisplayName("mixed assertions render correct colour per cell")
        void mixedAssertionsRenderPerCell() {
            String html = HtmlReportWriter.generate(List.of(
                    verdictWithLatencyAssertions(List.of(
                            new PercentileAssertion("p95", 420, 500, true, false, "explicit"),
                            new PercentileAssertion("p99", 810, 500, false, false, "explicit")))));

            assertThat(html).contains("class=\"latency-observed\">120ms</td>");  // p50 — no assertion, muted
            assertThat(html).contains("class=\"latency-pass\">420ms</td>");  // p95 — passed
            assertThat(html).contains("class=\"latency-fail\">810ms</td>");  // p99 — failed
        }

        @Test
        @DisplayName("CSS includes latency-pass and latency-fail rules")
        void cssIncludesLatencyClasses() {
            String html = HtmlReportWriter.generate(List.of(passingVerdict()));

            assertThat(html).contains(".latency-observed");
            assertThat(html).contains(".latency-pass");
            assertThat(html).contains(".latency-fail");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ProbabilisticTestVerdict passingVerdict() {
        return minimalVerdict("shouldPass", true, PunitVerdict.PASS);
    }

    private ProbabilisticTestVerdict failingVerdict() {
        return minimalVerdict("shouldFail", false, PunitVerdict.FAIL);
    }

    private ProbabilisticTestVerdict inconclusiveVerdict() {
        return minimalVerdict("shouldBeInconclusive", false, PunitVerdict.INCONCLUSIVE);
    }

    private ProbabilisticTestVerdict divergentVerdict() {
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-03-11T14:30:00Z"),
                new TestIdentity("com.example.MyTest", "shouldDiverge", Optional.empty()),
                new ExecutionSummary(100, 100, 80, 20, 0.7, 0.80, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
                Optional.empty(), Optional.empty(),
                new StatisticalAnalysis(0.95, 0.04, 0.72, 0.88,
                        Optional.of(2.50), Optional.of(0.006),
                        Optional.empty(), Optional.empty(), List.of()),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(), Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(), false, PunitVerdict.PASS
        );
    }

    private ProbabilisticTestVerdict minimalVerdict(String methodName, boolean passed, PunitVerdict punitVerdict) {
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-03-11T14:30:00Z"),
                new TestIdentity("com.example.MyTest", methodName, Optional.empty()),
                new ExecutionSummary(100, 100, 95, 5, 0.9, 0.95, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
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

    private ProbabilisticTestVerdict verdictWithFunctional() {
        ProbabilisticTestVerdict base = passingVerdict();
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                Optional.of(new FunctionalDimension(95, 5, 0.95)),
                base.latency(), base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithLatency() {
        ProbabilisticTestVerdict base = passingVerdict();
        LatencyDimension latency = new LatencyDimension(
                90, 100, false, Optional.empty(),
                120, 340, 420, 810, 1250,
                List.of(), List.of(), 90, 10
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), Optional.of(latency),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithLatencyAssertions(List<PercentileAssertion> assertions) {
        ProbabilisticTestVerdict base = passingVerdict();
        LatencyDimension latency = new LatencyDimension(
                90, 100, false, Optional.empty(),
                120, 340, 420, 810, 1250,
                assertions, List.of(), 90, 10
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), Optional.of(latency),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithUseCaseId(String useCaseId) {
        ProbabilisticTestVerdict base = passingVerdict();
        TestIdentity identity = new TestIdentity(
                base.identity().className(), base.identity().methodName(), Optional.of(useCaseId));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), identity, base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithMethodName(String methodName) {
        return minimalVerdict(methodName, true, PunitVerdict.PASS);
    }

    private ProbabilisticTestVerdict verdictWithProvenance() {
        ProbabilisticTestVerdict base = passingVerdict();
        SpecProvenance prov = new SpecProvenance("SLA", "SLA-PAY-001", "payment-gateway.yaml",
                Optional.empty(), Optional.of("(bundled)"));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), Optional.of(prov), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict inconclusiveVerdictWithMisalignment() {
        ProbabilisticTestVerdict base = minimalVerdict("shouldBeInconclusive", false, PunitVerdict.INCONCLUSIVE);
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

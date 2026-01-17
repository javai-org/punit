package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.SharedBudgetMonitor;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.ptest.engine.ResultPublisher.PublishContext;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.statistics.transparent.BaselineData;
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

    private PublishContext createContext(boolean passed) {
        return new PublishContext(
                "testMethod",
                100,
                100,
                passed ? 95 : 80,
                passed ? 5 : 20,
                0.9,
                passed ? 0.95 : 0.80,
                passed,
                Optional.empty(),
                null,
                1500,
                false,
                1.0,
                0,
                0,
                0,
                CostBudgetMonitor.TokenMode.NONE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                BaselineData.empty(),
                List.of(),
                null
        );
    }

    private PublishContext createContextWithBudgets() {
        SharedBudgetMonitor classBudget = new SharedBudgetMonitor(
                SharedBudgetMonitor.Scope.CLASS, 5000, 1000, BudgetExhaustedBehavior.FAIL);
        SharedBudgetMonitor suiteBudget = new SharedBudgetMonitor(
                SharedBudgetMonitor.Scope.SUITE, 30000, 10000, BudgetExhaustedBehavior.FAIL);

        return new PublishContext(
                "testMethod",
                100,
                50,
                45,
                5,
                0.9,
                0.90,
                true,
                Optional.empty(),
                null,
                2500,
                true,
                2.0,
                5000,
                500,
                250,
                CostBudgetMonitor.TokenMode.DYNAMIC,
                classBudget,
                suiteBudget,
                null,
                null,
                ThresholdOrigin.SLA,
                "SLA-2024-001",
                0.95,
                BaselineData.empty(),
                List.of(),
                null
        );
    }

    @Nested
    @DisplayName("buildReportEntries")
    class BuildReportEntries {

        @Test
        @DisplayName("includes basic test metrics")
        void includesBasicTestMetrics() {
            PublishContext ctx = createContext(true);

            Map<String, String> entries = publisher.buildReportEntries(ctx);

            assertThat(entries).containsEntry("punit.samples", "100");
            assertThat(entries).containsEntry("punit.samplesExecuted", "100");
            assertThat(entries).containsEntry("punit.successes", "95");
            assertThat(entries).containsEntry("punit.failures", "5");
            assertThat(entries).containsEntry("punit.verdict", "PASS");
        }

        @Test
        @DisplayName("includes FAIL verdict for failed test")
        void includesFailVerdictForFailedTest() {
            PublishContext ctx = createContext(false);

            Map<String, String> entries = publisher.buildReportEntries(ctx);

            assertThat(entries).containsEntry("punit.verdict", "FAIL");
        }

        @Test
        @DisplayName("includes pass rate metrics")
        void includesPassRateMetrics() {
            PublishContext ctx = createContext(true);

            Map<String, String> entries = publisher.buildReportEntries(ctx);

            assertThat(entries).containsEntry("punit.minPassRate", "0.9000");
            assertThat(entries).containsEntry("punit.observedPassRate", "0.9500");
        }

        @Test
        @DisplayName("includes elapsed time")
        void includesElapsedTime() {
            PublishContext ctx = createContext(true);

            Map<String, String> entries = publisher.buildReportEntries(ctx);

            assertThat(entries).containsEntry("punit.elapsedMs", "1500");
        }

        @Test
        @DisplayName("includes termination reason")
        void includesTerminationReason() {
            PublishContext ctx = new PublishContext(
                    "testMethod", 100, 50, 20, 30, 0.9, 0.40, false,
                    Optional.of(TerminationReason.IMPOSSIBILITY),
                    "Cannot achieve 90% pass rate",
                    1000, false, 1.0, 0, 0, 0,
                    CostBudgetMonitor.TokenMode.NONE,
                    null, null, null, null, null, null, null,
                    BaselineData.empty(), List.of(), null
            );

            Map<String, String> entries = publisher.buildReportEntries(ctx);

            assertThat(entries).containsEntry("punit.terminationReason", "IMPOSSIBILITY");
        }

        @Test
        @DisplayName("includes multiplier when applied")
        void includesMultiplierWhenApplied() {
            PublishContext ctx = createContextWithBudgets();

            Map<String, String> entries = publisher.buildReportEntries(ctx);

            assertThat(entries).containsEntry("punit.samplesMultiplier", "2.00");
        }

        @Test
        @DisplayName("includes method budget info")
        void includesMethodBudgetInfo() {
            PublishContext ctx = createContextWithBudgets();

            Map<String, String> entries = publisher.buildReportEntries(ctx);

            assertThat(entries).containsEntry("punit.method.timeBudgetMs", "5000");
            assertThat(entries).containsEntry("punit.method.tokenBudget", "500");
            assertThat(entries).containsEntry("punit.method.tokensConsumed", "250");
            assertThat(entries).containsEntry("punit.tokenMode", "DYNAMIC");
        }

        @Test
        @DisplayName("includes class budget info when present")
        void includesClassBudgetInfo() {
            PublishContext ctx = createContextWithBudgets();

            Map<String, String> entries = publisher.buildReportEntries(ctx);

            assertThat(entries).containsKey("punit.class.timeBudgetMs");
            assertThat(entries).containsKey("punit.class.tokenBudget");
            assertThat(entries).containsKey("punit.class.tokensConsumed");
        }

        @Test
        @DisplayName("includes suite budget info when present")
        void includesSuiteBudgetInfo() {
            PublishContext ctx = createContextWithBudgets();

            Map<String, String> entries = publisher.buildReportEntries(ctx);

            assertThat(entries).containsKey("punit.suite.timeBudgetMs");
            assertThat(entries).containsKey("punit.suite.tokenBudget");
            assertThat(entries).containsKey("punit.suite.tokensConsumed");
        }

        @Test
        @DisplayName("excludes budget info when not configured")
        void excludesBudgetInfoWhenNotConfigured() {
            PublishContext ctx = createContext(true);

            Map<String, String> entries = publisher.buildReportEntries(ctx);

            assertThat(entries).doesNotContainKey("punit.method.timeBudgetMs");
            assertThat(entries).doesNotContainKey("punit.method.tokenBudget");
            assertThat(entries).doesNotContainKey("punit.samplesMultiplier");
        }
    }

    @Nested
    @DisplayName("PublishContext")
    class PublishContextTests {

        @Test
        @DisplayName("hasTimeBudget returns true when time budget set")
        void hasTimeBudgetReturnsTrueWhenSet() {
            PublishContext ctx = createContextWithBudgets();

            assertThat(ctx.hasTimeBudget()).isTrue();
        }

        @Test
        @DisplayName("hasTimeBudget returns false when no time budget")
        void hasTimeBudgetReturnsFalseWhenNotSet() {
            PublishContext ctx = createContext(true);

            assertThat(ctx.hasTimeBudget()).isFalse();
        }

        @Test
        @DisplayName("hasThresholdOrigin returns true for SLA")
        void hasThresholdOriginReturnsTrueForSla() {
            PublishContext ctx = createContextWithBudgets();

            assertThat(ctx.hasThresholdOrigin()).isTrue();
        }

        @Test
        @DisplayName("hasThresholdOrigin returns false for UNSPECIFIED")
        void hasThresholdOriginReturnsFalseForUnspecified() {
            PublishContext ctx = new PublishContext(
                    "test", 100, 100, 90, 10, 0.9, 0.9, true,
                    Optional.empty(), null, 1000, false, 1.0, 0, 0, 0,
                    CostBudgetMonitor.TokenMode.NONE, null, null, null, null,
                    ThresholdOrigin.UNSPECIFIED, null, null,
                    BaselineData.empty(), List.of(), null
            );

            assertThat(ctx.hasThresholdOrigin()).isFalse();
        }

        @Test
        @DisplayName("hasContractRef returns true when set")
        void hasContractRefReturnsTrueWhenSet() {
            PublishContext ctx = createContextWithBudgets();

            assertThat(ctx.hasContractRef()).isTrue();
        }

        @Test
        @DisplayName("hasContractRef returns false when empty")
        void hasContractRefReturnsFalseWhenEmpty() {
            PublishContext ctx = createContext(true);

            assertThat(ctx.hasContractRef()).isFalse();
        }
    }

    @Nested
    @DisplayName("appendProvenance")
    class AppendProvenance {

        @Test
        @DisplayName("appends threshold origin when set")
        void appendsThresholdOriginWhenSet() {
            PublishContext ctx = createContextWithBudgets();
            StringBuilder sb = new StringBuilder();

            publisher.appendProvenance(sb, ctx);

            assertThat(sb.toString()).contains("Threshold origin: SLA");
        }

        @Test
        @DisplayName("appends contract ref when set")
        void appendsContractRefWhenSet() {
            PublishContext ctx = createContextWithBudgets();
            StringBuilder sb = new StringBuilder();

            publisher.appendProvenance(sb, ctx);

            assertThat(sb.toString()).contains("Contract ref: SLA-2024-001");
        }

        @Test
        @DisplayName("appends nothing when not configured")
        void appendsNothingWhenNotConfigured() {
            PublishContext ctx = createContext(true);
            StringBuilder sb = new StringBuilder();

            publisher.appendProvenance(sb, ctx);

            assertThat(sb.toString()).isEmpty();
        }
    }
}


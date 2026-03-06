package org.javai.punit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.javai.punit.testsubjects.BaselineDerivedThresholdSubjects.AlwaysPassingWithDerivedThresholdTest;
import org.javai.punit.testsubjects.BaselineDerivedThresholdSubjects.BarelyFailingWithDerivedThresholdTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests that verify verdict correctness when minPassRate is derived
 * from a baseline spec rather than specified explicitly on the annotation.
 *
 * <p>These tests guard against the "verdict/display desync" bug where
 * {@code BernoulliTrialsConfig.minPassRate} could remain {@code NaN} after
 * baseline derivation, causing an unconditional FAIL verdict regardless of
 * actual pass rates.
 *
 * <p>Each test verifies that the reported verdict, the displayed threshold,
 * and the actual test outcome are all consistent with each other — the three
 * must be inextricably linked.
 */
class BaselineDerivedThresholdIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @Nested
    @DisplayName("Verdict matches derived threshold")
    class VerdictMatchesDerivedThreshold {

        @Test
        @DisplayName("all-pass test: verdict=PASS, report=PASS, and threshold is derived (not NaN)")
        void allPassTestShouldPassWithDerivedThreshold() {
            // Baseline declares minPassRate=0.80; all samples pass (100% >= 80%) → PASS
            // With 80% of 10 = 8 required, SUCCESS_GUARANTEED triggers after 8 successes
            var allEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(AlwaysPassingWithDerivedThresholdTest.class))
                    .execute()
                    .allEvents()
                    .list();

            // 1. Verify the actual test outcome: no failures
            var failures = allEvents.stream()
                    .filter(e -> e.getPayload(TestExecutionResult.class)
                            .map(r -> r.getStatus() == TestExecutionResult.Status.FAILED)
                            .orElse(false))
                    .toList();
            assertThat(failures)
                    .as("Test outcome should have zero failures")
                    .isEmpty();

            // 2. Extract the PUnit report entry and verify verdict-display consistency
            Map<String, String> report = extractReportEntry(allEvents);

            assertThat(report.get("punit.verdict"))
                    .as("Report verdict must say PASS")
                    .isEqualTo("PASS");

            assertThat(report.get("punit.minPassRate"))
                    .as("Report minPassRate must be the derived threshold (0.8000), not NaN")
                    .doesNotContain("NaN")
                    .isEqualTo("0.8000");

            double observed = Double.parseDouble(report.get("punit.observedPassRate"));
            double required = Double.parseDouble(report.get("punit.minPassRate"));

            assertThat(observed)
                    .as("Observed pass rate must meet or exceed the required threshold for a PASS verdict")
                    .isGreaterThanOrEqualTo(required);
        }

        @Test
        @DisplayName("below-threshold test: verdict=FAIL, report=FAIL, and threshold is derived (not NaN)")
        void belowThresholdTestShouldFailWithDerivedThreshold() {
            BarelyFailingWithDerivedThresholdTest.resetCounter();

            // Baseline declares minPassRate=0.80; test passes 70% → FAIL
            var allEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(BarelyFailingWithDerivedThresholdTest.class))
                    .execute()
                    .allEvents()
                    .list();

            // 1. Verify actual test outcome includes failures
            var testFailures = allEvents.stream()
                    .filter(e -> e.getPayload(TestExecutionResult.class)
                            .map(r -> r.getStatus() == TestExecutionResult.Status.FAILED)
                            .orElse(false))
                    .toList();
            assertThat(testFailures)
                    .as("Test outcome should have failures")
                    .isNotEmpty();

            // 2. Extract the PUnit report entry and verify verdict-display consistency
            Map<String, String> report = extractReportEntry(allEvents);

            assertThat(report.get("punit.verdict"))
                    .as("Report verdict must say FAIL")
                    .isEqualTo("FAIL");

            assertThat(report.get("punit.minPassRate"))
                    .as("Report minPassRate must be the derived threshold (0.8000), not NaN")
                    .doesNotContain("NaN")
                    .isEqualTo("0.8000");

            double observed = Double.parseDouble(report.get("punit.observedPassRate"));
            double required = Double.parseDouble(report.get("punit.minPassRate"));

            assertThat(observed)
                    .as("Observed pass rate must be below the required threshold for a FAIL verdict")
                    .isLessThan(required);
        }
    }

    @Nested
    @DisplayName("Verdict message references derived threshold")
    class VerdictMessageReferencesThreshold {

        @Test
        @DisplayName("failure message should contain the derived minPassRate, not NaN")
        void failureMessageContainsDerivedThreshold() {
            BarelyFailingWithDerivedThresholdTest.resetCounter();

            var events = EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(BarelyFailingWithDerivedThresholdTest.class))
                    .execute()
                    .allEvents()
                    .list();

            // Find the verdict failure (the PUnit final verdict, not individual sample failures)
            var verdictFailure = events.stream()
                    .filter(e -> e.getPayload(TestExecutionResult.class)
                            .map(r -> r.getStatus() == TestExecutionResult.Status.FAILED)
                            .orElse(false))
                    .filter(e -> e.getPayload(TestExecutionResult.class)
                            .flatMap(TestExecutionResult::getThrowable)
                            .map(t -> t.getMessage() != null && t.getMessage().contains("PUnit"))
                            .orElse(false))
                    .findFirst();

            assertThat(verdictFailure)
                    .as("Should have a PUnit verdict failure")
                    .isPresent();

            String message = verdictFailure.get()
                    .getPayload(TestExecutionResult.class).orElseThrow()
                    .getThrowable().orElseThrow()
                    .getMessage();

            // The verdict message must reference the actual derived threshold (80%), not NaN
            assertThat(message)
                    .as("Verdict message should contain the derived threshold, not NaN")
                    .doesNotContain("NaN")
                    .contains("80");
        }
    }

    /**
     * Extracts the PUnit report entry from a list of test events.
     * The report entry is published as a REPORTING_ENTRY_PUBLISHED event
     * and contains verdict, threshold, and pass rate data.
     */
    private static Map<String, String> extractReportEntry(
            java.util.List<org.junit.platform.testkit.engine.Event> events) {
        var reportEntry = events.stream()
                .filter(e -> e.getPayload(ReportEntry.class).isPresent())
                .map(e -> e.getPayload(ReportEntry.class).orElseThrow())
                .filter(r -> r.getKeyValuePairs().containsKey("punit.verdict"))
                .findFirst();

        assertThat(reportEntry)
                .as("Should have a PUnit report entry with verdict")
                .isPresent();

        return reportEntry.get().getKeyValuePairs();
    }
}

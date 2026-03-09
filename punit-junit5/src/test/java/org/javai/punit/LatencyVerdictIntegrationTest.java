package org.javai.punit;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.testsubjects.LatencyVerdictTestSubjects.AdvisoryLatencyBreachTest;
import org.javai.punit.testsubjects.LatencyVerdictTestSubjects.SmokeUndersizedLatencyTest;
import org.javai.punit.testsubjects.LatencyVerdictTestSubjects.TransparentStatsWithLatencyTest;
import org.javai.punit.testsubjects.LatencyVerdictTestSubjects.VerificationUndersizedLatencyTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests for verbose (transparent stats) latency output
 * and latency feasibility gate for VERIFICATION intent.
 */
@DisplayName("Latency verdict integration tests")
class LatencyVerdictIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @AfterEach
    void clearEnforceProperty() {
        System.clearProperty("punit.latency.enforce");
    }

    @Test
    @DisplayName("should pass with transparent stats and latency enabled")
    void shouldPassWithTransparentStatsAndLatency() {
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(TransparentStatsWithLatencyTest.class))
                .execute()
                .testEvents();

        // Test should pass — transparent stats mode with latency should not cause errors
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("VERIFICATION intent with undersized latency p99 should trigger feasibility gate for explicit thresholds")
    void verificationUndersizedLatencyShouldTriggerFeasibilityGate() {
        // No global flag needed — explicit @Latency thresholds trigger the feasibility gate
        var events = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(VerificationUndersizedLatencyTest.class))
                .execute()
                .allEvents()
                .list();

        // The container should report a failure due to ExtensionConfigurationException
        var failures = events.stream()
                .filter(e -> e.getPayload(TestExecutionResult.class)
                        .map(r -> r.getStatus() == TestExecutionResult.Status.FAILED)
                        .orElse(false))
                .toList();

        assertThat(failures).isNotEmpty();

        // The failure message should mention latency p99 infeasibility
        var failureMessage = failures.stream()
                .map(e -> e.getPayload(TestExecutionResult.class).orElseThrow())
                .map(r -> r.getThrowable().orElseThrow())
                .map(Throwable::getMessage)
                .filter(msg -> msg != null && msg.contains("p99"))
                .findFirst();

        assertThat(failureMessage)
                .as("Should mention p99 latency infeasibility")
                .isPresent();

        assertThat(failureMessage.get())
                .contains("at least 100");
    }

    @Test
    @DisplayName("SMOKE intent with undersized latency p99 should bypass feasibility gate")
    void smokeUndersizedLatencyShouldBypassGate() {
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(SmokeUndersizedLatencyTest.class))
                .execute()
                .testEvents();

        // SMOKE should bypass the gate and execute successfully
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("should fail when explicit latency threshold is breached even without global flag")
    void shouldFailWhenExplicitLatencyThresholdBreached() {
        // Explicit @Latency thresholds are enforced by default — no global flag needed
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(AdvisoryLatencyBreachTest.class))
                .execute()
                .testEvents();

        // Test should fail — explicit latency thresholds are enforced
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isGreaterThan(0);
    }
}

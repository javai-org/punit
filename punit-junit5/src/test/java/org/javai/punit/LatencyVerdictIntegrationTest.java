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
                .selectors(DiscoverySelectors.selectClass(TransparentStatsWithLatencyTest.class))
                .execute()
                .testEvents();

        // Test should pass — transparent stats mode with latency should not cause errors
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("VERIFICATION intent with undersized latency p99 should trigger feasibility gate when enforced")
    void verificationUndersizedLatencyShouldTriggerFeasibilityGate() {
        System.setProperty("punit.latency.enforce", "true");

        var events = EngineTestKit.engine(JUNIT_ENGINE_ID)
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
                .selectors(DiscoverySelectors.selectClass(SmokeUndersizedLatencyTest.class))
                .execute()
                .testEvents();

        // SMOKE should bypass the gate and execute successfully
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("should warn but not fail when latency breaches in advisory mode")
    void shouldWarnButNotFailWhenLatencyBreachesInAdvisoryMode() {
        // Advisory mode is the default (enforce not set)
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(AdvisoryLatencyBreachTest.class))
                .execute()
                .testEvents();

        // Test should pass despite latency breach — advisory mode doesn't fail tests
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);

        long succeededCount = testEvents.succeeded().count();
        assertThat(succeededCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("VERIFICATION intent with undersized latency should bypass feasibility gate in advisory mode")
    void verificationUndersizedLatencyShouldBypassGateInAdvisoryMode() {
        // Advisory mode is the default (enforce not set)
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(VerificationUndersizedLatencyTest.class))
                .execute()
                .testEvents();

        // Should pass — feasibility gate is skipped in advisory mode
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }
}

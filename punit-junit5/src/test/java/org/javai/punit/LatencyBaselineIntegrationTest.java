package org.javai.punit;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.testsubjects.LatencyBaselineTestSubjects.BaselineOnlyTest;
import org.javai.punit.testsubjects.LatencyBaselineTestSubjects.DisabledLatencyTest;
import org.javai.punit.testsubjects.LatencyBaselineTestSubjects.ExplicitWithBaselineTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests for baseline-derived latency thresholds.
 * Uses JUnit Platform TestKit to run test subjects that exercise
 * automatic baseline derivation, disabled latency, and misconfiguration detection.
 */
@DisplayName("Latency baseline integration tests")
class LatencyBaselineIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @Test
    @DisplayName("should pass with automatic baseline-derived latency thresholds")
    void shouldPassWithAutomaticBaselineDerivation() {
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(BaselineOnlyTest.class))
                .execute()
                .testEvents();

        // Latency thresholds are automatically derived from baseline (p50~380, p90~620, p95~750, p99~1100).
        // Sub-millisecond execution should easily pass all.
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("should pass when latency is disabled via @Latency(disabled = true)")
    void shouldPassWhenLatencyDisabled() {
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(DisabledLatencyTest.class))
                .execute()
                .testEvents();

        // Latency assertions are suppressed — only pass-rate matters.
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("should fail when explicit latency thresholds conflict with baseline")
    void shouldFailWhenExplicitThresholdsWithBaseline() {
        var events = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(ExplicitWithBaselineTest.class))
                .execute()
                .allEvents()
                .list();

        // Explicit @Latency(p95Ms=500) + baseline with latency data → misconfiguration error.
        // ExtensionConfigurationException manifests as a failed event payload.
        var failureMessages = events.stream()
                .filter(e -> e.getPayload(TestExecutionResult.class)
                        .map(r -> r.getStatus() == TestExecutionResult.Status.FAILED)
                        .orElse(false))
                .map(e -> e.getPayload(TestExecutionResult.class).orElseThrow())
                .map(r -> r.getThrowable().map(Throwable::getMessage).orElse("(no message)"))
                .toList();

        assertThat(failureMessages)
                .as("Should contain misconfiguration error about explicit @Latency with baseline")
                .anyMatch(msg -> msg.contains("Explicit @Latency"));
    }
}

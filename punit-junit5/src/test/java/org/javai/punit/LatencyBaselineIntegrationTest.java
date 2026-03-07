package org.javai.punit;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.testsubjects.LatencyBaselineTestSubjects.BaselineOnlyTest;
import org.javai.punit.testsubjects.LatencyBaselineTestSubjects.DisabledLatencyTest;
import org.javai.punit.testsubjects.LatencyBaselineTestSubjects.ExplicitWithBaselineTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests for baseline-derived latency thresholds.
 * Uses JUnit Platform TestKit to run test subjects that exercise
 * automatic baseline derivation, disabled latency, and explicit override of baseline.
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
    @DisplayName("should pass when explicit latency thresholds override baseline")
    void shouldPassWhenExplicitThresholdsOverrideBaseline() {
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(ExplicitWithBaselineTest.class))
                .execute()
                .testEvents();

        // Explicit @Latency(p95Ms=500) overrides baseline-derived thresholds.
        // Sub-millisecond execution should easily pass the explicit 500ms threshold.
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }
}

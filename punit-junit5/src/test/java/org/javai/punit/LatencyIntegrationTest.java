package org.javai.punit;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.testsubjects.LatencyTestSubjects.LatencyFailingTest;
import org.javai.punit.testsubjects.LatencyTestSubjects.LatencyPassingTest;
import org.javai.punit.testsubjects.LatencyTestSubjects.NoLatencyTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests for latency assertions using JUnit Platform TestKit.
 */
@DisplayName("Latency integration tests")
class LatencyIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";
    private static final String PROP_LATENCY_ENFORCE = "punit.latency.enforce";

    @AfterEach
    void clearEnforceProperty() {
        System.clearProperty(PROP_LATENCY_ENFORCE);
    }

    @Test
    @DisplayName("should pass when latency is within thresholds")
    void shouldPassWhenLatencyWithinThresholds() {
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(LatencyPassingTest.class))
                .execute()
                .testEvents();

        // Test should complete with pass-rate and latency both passing
        // With 80% of 10 = 8 required, after 8 consecutive successes SUCCESS_GUARANTEED triggers
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("should fail when explicit latency threshold is breached")
    void shouldFailWhenExplicitLatencyThresholdBreached() {
        // No global flag needed — explicit @Latency thresholds are enforced by default
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(LatencyFailingTest.class))
                .execute()
                .testEvents();

        // The test should fail because p50 latency (>= 10ms) exceeds threshold (0ms)
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("should pass when no latency thresholds are set")
    void shouldPassWhenNoLatencyThresholds() {
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(DiscoverySelectors.selectClass(NoLatencyTest.class))
                .execute()
                .testEvents();

        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }
}

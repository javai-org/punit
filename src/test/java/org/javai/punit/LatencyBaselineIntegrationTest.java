package org.javai.punit;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.testsubjects.LatencyBaselineTestSubjects.BaselineOnlyTest;
import org.javai.punit.testsubjects.LatencyBaselineTestSubjects.BaselineWithoutSpecTest;
import org.javai.punit.testsubjects.LatencyBaselineTestSubjects.MixedModeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Integration tests for Phase 3: baseline-derived latency thresholds.
 * Uses JUnit Platform TestKit to run test subjects that use
 * latencyBaseline=true and mixed mode (explicit + baseline).
 */
@DisplayName("Latency baseline integration tests")
class LatencyBaselineIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @Test
    @DisplayName("should pass with latencyBaseline=true when execution is fast")
    void shouldPassWithBaselineOnly() {
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(BaselineOnlyTest.class))
                .execute()
                .testEvents();

        // All percentile thresholds derived from baseline (p50~380, p90~620, p95~750, p99~1100)
        // Sub-millisecond execution should easily pass all
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("should pass with mixed mode (explicit + baseline)")
    void shouldPassWithMixedMode() {
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(MixedModeTest.class))
                .execute()
                .testEvents();

        // Explicit p99=5000ms is looser than baseline-derived (~1100+margin),
        // baseline wins. Other percentiles from baseline. All should pass.
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("should fail when latencyBaseline=true but no baseline spec exists")
    void shouldFailWhenBaselineRequestedButMissing() {
        var testEvents = EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(BaselineWithoutSpecTest.class))
                .execute()
                .testEvents();

        // Should fail because latencyBaseline=true but no use case/spec to derive from
        long failedCount = testEvents.failed().count();
        assertThat(failedCount).isGreaterThan(0);
    }
}

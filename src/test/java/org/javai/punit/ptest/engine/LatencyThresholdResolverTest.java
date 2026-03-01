package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.javai.punit.spec.model.LatencyBaseline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

@DisplayName("LatencyThresholdResolver")
class LatencyThresholdResolverTest {

    private final LatencyThresholdResolver resolver = new LatencyThresholdResolver();

    @Nested
    @DisplayName("Not requested")
    class NotRequested {

        @Test
        @DisplayName("should return not-asserted thresholds when latency not requested")
        void shouldReturnNotAssertedWhenNotRequested() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, false);

            var resolved = resolver.resolve(config, null, 0.95);

            assertThat(resolved.toConfig().hasExplicitThresholds()).isFalse();
        }
    }

    @Nested
    @DisplayName("Explicit only (no baseline)")
    class ExplicitOnly {

        @Test
        @DisplayName("should use explicit thresholds as-is")
        void shouldUseExplicitThresholds() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, 1000, false);

            var resolved = resolver.resolve(config, null, 0.95);

            assertThat(resolved.p95().thresholdMs()).isEqualTo(500);
            assertThat(resolved.p99().thresholdMs()).isEqualTo(1000);
            assertThat(resolved.p95().source()).isEqualTo("explicit");
            assertThat(resolved.p99().source()).isEqualTo("explicit");
            assertThat(resolved.p50().thresholdMs()).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("Baseline only (latencyBaseline=true)")
    class BaselineOnly {

        @Test
        @DisplayName("should derive all thresholds from baseline")
        void shouldDeriveAllFromBaseline() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, true);
            LatencyBaseline baseline = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            var resolved = resolver.resolve(config, baseline, 0.95);

            assertThat(resolved.p50().thresholdMs()).isGreaterThanOrEqualTo(380);
            assertThat(resolved.p90().thresholdMs()).isGreaterThanOrEqualTo(620);
            assertThat(resolved.p95().thresholdMs()).isGreaterThanOrEqualTo(750);
            assertThat(resolved.p99().thresholdMs()).isGreaterThanOrEqualTo(1100);

            assertThat(resolved.p50().source()).isEqualTo("from baseline");
            assertThat(resolved.p90().source()).isEqualTo("from baseline");
            assertThat(resolved.p95().source()).isEqualTo("from baseline");
            assertThat(resolved.p99().source()).isEqualTo("from baseline");
        }

        @Test
        @DisplayName("should throw when baseline requested but no baseline available")
        void shouldThrowWhenBaselineRequestedButMissing() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, true);

            assertThatThrownBy(() -> resolver.resolve(config, null, 0.95))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("latencyBaseline = true")
                    .hasMessageContaining("no latency data");
        }
    }

    @Nested
    @DisplayName("Mixed mode (explicit + baseline)")
    class MixedMode {

        @Test
        @DisplayName("explicit ceiling wins when stricter than baseline-derived")
        void explicitWinsWhenStricter() {
            // Baseline p95=750ms with stddev=120, n=100
            // Derived upper bound ≈ 750 + 1.645 * 12 ≈ 770
            // Explicit p95=500ms → explicit is stricter
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, -1, false);
            LatencyBaseline baseline = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            var resolved = resolver.resolve(config, baseline, 0.95);

            assertThat(resolved.p95().thresholdMs()).isEqualTo(500);
            assertThat(resolved.p95().source()).contains("explicit");
            assertThat(resolved.p95().source()).contains("stricter");
        }

        @Test
        @DisplayName("baseline-derived wins when stricter than explicit")
        void baselineWinsWhenStricter() {
            // Baseline p95=750ms, explicit p95=5000ms → baseline is stricter
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 5000, -1, false);
            LatencyBaseline baseline = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            var resolved = resolver.resolve(config, baseline, 0.95);

            assertThat(resolved.p95().thresholdMs()).isLessThan(5000);
            assertThat(resolved.p95().source()).contains("baseline");
            assertThat(resolved.p95().source()).contains("stricter");
        }

        @Test
        @DisplayName("baseline fills unspecified percentiles")
        void baselineFillsUnspecifiedPercentiles() {
            // Only p99 explicit, p50/p90/p95 should come from baseline
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, 2000, false);
            LatencyBaseline baseline = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            var resolved = resolver.resolve(config, baseline, 0.95);

            // p50/p90/p95 should be from baseline
            assertThat(resolved.p50().thresholdMs()).isGreaterThanOrEqualTo(380);
            assertThat(resolved.p50().source()).isEqualTo("from baseline");
            assertThat(resolved.p90().source()).isEqualTo("from baseline");
            assertThat(resolved.p95().source()).isEqualTo("from baseline");

            // p99 should use the explicit value (2000) since it's looser than derived
            // The derived would be ~1100 + small margin, so 2000 is looser
            // Baseline-derived should win since it's stricter
            assertThat(resolved.p99().thresholdMs()).isLessThan(2000);
            assertThat(resolved.p99().source()).contains("baseline");
        }
    }

    @Nested
    @DisplayName("Source tracking")
    class SourceTracking {

        @Test
        @DisplayName("sourceFor should return correct source for each label")
        void sourceForShouldReturnCorrectSource() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, true);
            LatencyBaseline baseline = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            var resolved = resolver.resolve(config, baseline, 0.95);

            assertThat(resolved.sourceFor("p50")).isEqualTo("from baseline");
            assertThat(resolved.sourceFor("p95")).isEqualTo("from baseline");
            assertThat(resolved.sourceFor("unknown")).isEqualTo("explicit");
        }
    }
}

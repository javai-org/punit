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
        @DisplayName("should return not-asserted thresholds when no explicit and no baseline")
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
    @DisplayName("Automatic baseline derivation")
    class AutomaticBaselineDerivation {

        @Test
        @DisplayName("should derive all thresholds from baseline when no explicit thresholds")
        void shouldDeriveAllFromBaseline() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, false);
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
    }

    @Nested
    @DisplayName("Disabled")
    class Disabled {

        @Test
        @DisplayName("should return not-asserted when disabled without explicit thresholds")
        void shouldReturnNotAssertedWhenDisabled() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, true);

            var resolved = resolver.resolve(config, null, 0.95);

            assertThat(resolved.toConfig().hasExplicitThresholds()).isFalse();
        }

        @Test
        @DisplayName("should return not-asserted when disabled even with baseline present")
        void shouldReturnNotAssertedWhenDisabledWithBaseline() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, true);
            LatencyBaseline baseline = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            var resolved = resolver.resolve(config, baseline, 0.95);

            assertThat(resolved.toConfig().hasExplicitThresholds()).isFalse();
        }

        @Test
        @DisplayName("should throw when disabled with explicit thresholds")
        void shouldThrowWhenDisabledWithExplicit() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, -1, true);

            assertThatThrownBy(() -> resolver.resolve(config, null, 0.95))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("disabled = true")
                    .hasMessageContaining("explicit");
        }
    }

    @Nested
    @DisplayName("Misconfiguration")
    class Misconfiguration {

        @Test
        @DisplayName("should throw when explicit thresholds combined with baseline")
        void shouldThrowWhenExplicitWithBaseline() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, -1, false);
            LatencyBaseline baseline = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            assertThatThrownBy(() -> resolver.resolve(config, baseline, 0.95))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("Explicit @Latency thresholds")
                    .hasMessageContaining("baseline");
        }

        @Test
        @DisplayName("should throw when disabled with explicit thresholds even with baseline")
        void shouldThrowWhenDisabledWithExplicitAndBaseline() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, -1, true);
            LatencyBaseline baseline = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            assertThatThrownBy(() -> resolver.resolve(config, baseline, 0.95))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("disabled = true")
                    .hasMessageContaining("explicit");
        }
    }

    @Nested
    @DisplayName("Source tracking")
    class SourceTracking {

        @Test
        @DisplayName("sourceFor should return correct source for each label")
        void sourceForShouldReturnCorrectSource() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, false);
            LatencyBaseline baseline = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            var resolved = resolver.resolve(config, baseline, 0.95);

            assertThat(resolved.sourceFor("p50")).isEqualTo("from baseline");
            assertThat(resolved.sourceFor("p95")).isEqualTo("from baseline");
            assertThat(resolved.sourceFor("unknown")).isEqualTo("explicit");
        }
    }
}

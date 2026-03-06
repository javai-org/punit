package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LatencyAssertionConfig")
class LatencyAssertionConfigTest {

    @Nested
    @DisplayName("isLatencyRequested")
    class IsLatencyRequested {

        @Test
        @DisplayName("should return false when no thresholds and not disabled")
        void shouldReturnFalseWhenNoThresholdsAndNotDisabled() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, false);

            assertThat(config.isLatencyRequested()).isFalse();
        }

        @Test
        @DisplayName("should return true when explicit threshold set")
        void shouldReturnTrueWhenExplicitThresholdSet() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, -1, false);

            assertThat(config.isLatencyRequested()).isTrue();
        }

        @Test
        @DisplayName("should return false when disabled even with explicit thresholds")
        void shouldReturnFalseWhenDisabled() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, 500, -1, true);

            assertThat(config.isLatencyRequested()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasExplicitThresholds")
    class HasExplicitThresholds {

        @Test
        @DisplayName("should return false when all defaults")
        void shouldReturnFalseWhenAllDefaults() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, -1, -1, -1, false);

            assertThat(config.hasExplicitThresholds()).isFalse();
        }

        @Test
        @DisplayName("should return true when p50 is set")
        void shouldReturnTrueWhenP50Set() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(100, -1, -1, -1, false);

            assertThat(config.hasExplicitThresholds()).isTrue();
            assertThat(config.hasP50()).isTrue();
            assertThat(config.hasP90()).isFalse();
        }

        @Test
        @DisplayName("should return true when p90 is set")
        void shouldReturnTrueWhenP90Set() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(-1, 200, -1, -1, false);

            assertThat(config.hasExplicitThresholds()).isTrue();
            assertThat(config.hasP90()).isTrue();
        }

        @Test
        @DisplayName("should accept zero as a valid threshold")
        void shouldAcceptZeroAsValidThreshold() {
            LatencyAssertionConfig config = new LatencyAssertionConfig(0, -1, -1, -1, false);

            assertThat(config.hasExplicitThresholds()).isTrue();
            assertThat(config.hasP50()).isTrue();
        }
    }

    @Nested
    @DisplayName("Global enforce flag")
    class GlobalEnforceFlag {

        @AfterEach
        void clearSystemProperty() {
            System.clearProperty(LatencyAssertionConfig.PROP_LATENCY_ENFORCE);
        }

        @Test
        @DisplayName("should not be enforced by default")
        void shouldNotBeEnforcedByDefault() {
            assertThat(LatencyAssertionConfig.isEnforced()).isFalse();
        }

        @Test
        @DisplayName("should be enforced when system property is true")
        void shouldBeEnforcedWhenSystemPropertyTrue() {
            System.setProperty(LatencyAssertionConfig.PROP_LATENCY_ENFORCE, "true");

            assertThat(LatencyAssertionConfig.isEnforced()).isTrue();
        }

        @Test
        @DisplayName("should not be enforced when system property is false")
        void shouldNotBeEnforcedWhenSystemPropertyFalse() {
            System.setProperty(LatencyAssertionConfig.PROP_LATENCY_ENFORCE, "false");

            assertThat(LatencyAssertionConfig.isEnforced()).isFalse();
        }
    }
}

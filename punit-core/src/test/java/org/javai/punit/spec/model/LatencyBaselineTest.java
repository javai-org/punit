package org.javai.punit.spec.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LatencyBaseline")
class LatencyBaselineTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create with valid values")
        void shouldCreateWithValidValues() {
            LatencyBaseline baseline = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            assertThat(baseline.sampleCount()).isEqualTo(100);
            assertThat(baseline.meanMs()).isEqualTo(450);
            assertThat(baseline.standardDeviationMs()).isEqualTo(120);
            assertThat(baseline.p50Ms()).isEqualTo(380);
            assertThat(baseline.p90Ms()).isEqualTo(620);
            assertThat(baseline.p95Ms()).isEqualTo(750);
            assertThat(baseline.p99Ms()).isEqualTo(1100);
            assertThat(baseline.maxMs()).isEqualTo(1400);
        }

        @Test
        @DisplayName("should reject negative sample count")
        void shouldRejectNegativeSampleCount() {
            assertThatThrownBy(() -> new LatencyBaseline(-1, 450, 120, 380, 620, 750, 1100, 1400))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sampleCount");
        }

        @Test
        @DisplayName("should reject negative mean")
        void shouldRejectNegativeMean() {
            assertThatThrownBy(() -> new LatencyBaseline(100, -1, 120, 380, 620, 750, 1100, 1400))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("meanMs");
        }

        @Test
        @DisplayName("should allow zero sample count")
        void shouldAllowZeroSampleCount() {
            LatencyBaseline baseline = new LatencyBaseline(0, 0, 0, 0, 0, 0, 0, 0);

            assertThat(baseline.sampleCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Record equality")
    class RecordEquality {

        @Test
        @DisplayName("should be equal for same values")
        void shouldBeEqualForSameValues() {
            LatencyBaseline a = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);
            LatencyBaseline b = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different values")
        void shouldNotBeEqualForDifferentValues() {
            LatencyBaseline a = new LatencyBaseline(100, 450, 120, 380, 620, 750, 1100, 1400);
            LatencyBaseline b = new LatencyBaseline(200, 450, 120, 380, 620, 750, 1100, 1400);

            assertThat(a).isNotEqualTo(b);
        }
    }
}

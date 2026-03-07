package org.javai.punit.ptest.bernoulli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SampleResultAggregator — Latency tracking")
class SampleResultAggregatorLatencyTest {

    @Nested
    @DisplayName("Successful latencies tracking")
    class SuccessfulLatenciesTracking {

        @Test
        @DisplayName("should start with empty latencies")
        void shouldStartWithEmptyLatencies() {
            SampleResultAggregator aggregator = new SampleResultAggregator(10);

            assertThat(aggregator.getSuccessfulLatenciesMs()).isEmpty();
        }

        @Test
        @DisplayName("should track latency from successful sample")
        void shouldTrackLatencyFromSuccessfulSample() {
            SampleResultAggregator aggregator = new SampleResultAggregator(10);

            aggregator.recordSuccess(250);

            assertThat(aggregator.getSuccessfulLatenciesMs())
                    .hasSize(1)
                    .containsExactly(250L);
            assertThat(aggregator.getSuccesses()).isEqualTo(1);
        }

        @Test
        @DisplayName("should track multiple successful latencies")
        void shouldTrackMultipleSuccessfulLatencies() {
            SampleResultAggregator aggregator = new SampleResultAggregator(10);

            aggregator.recordSuccess(100);
            aggregator.recordSuccess(200);
            aggregator.recordSuccess(300);

            assertThat(aggregator.getSuccessfulLatenciesMs())
                    .hasSize(3)
                    .containsExactly(100L, 200L, 300L);
            assertThat(aggregator.getSuccesses()).isEqualTo(3);
        }

        @Test
        @DisplayName("should not track latencies for failures")
        void shouldNotTrackLatenciesForFailures() {
            SampleResultAggregator aggregator = new SampleResultAggregator(10);

            aggregator.recordSuccess(100);
            aggregator.recordFailure(new AssertionError("fail"));
            aggregator.recordSuccess(300);

            assertThat(aggregator.getSuccessfulLatenciesMs())
                    .hasSize(2)
                    .containsExactly(100L, 300L);
        }

        @Test
        @DisplayName("should track latency alongside no-latency success")
        void shouldTrackLatencyAlongsideNoLatencySuccess() {
            SampleResultAggregator aggregator = new SampleResultAggregator(10);

            aggregator.recordSuccess(); // no latency
            aggregator.recordSuccess(200); // with latency

            assertThat(aggregator.getSuccesses()).isEqualTo(2);
            assertThat(aggregator.getSuccessfulLatenciesMs())
                    .hasSize(1)
                    .containsExactly(200L);
        }

        @Test
        @DisplayName("should return unmodifiable list")
        void shouldReturnUnmodifiableList() {
            SampleResultAggregator aggregator = new SampleResultAggregator(10);
            aggregator.recordSuccess(100);

            List<Long> latencies = aggregator.getSuccessfulLatenciesMs();

            assertThat(latencies).isUnmodifiable();
        }
    }
}

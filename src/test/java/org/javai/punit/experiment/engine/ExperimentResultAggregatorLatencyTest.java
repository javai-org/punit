package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.javai.punit.contract.PostconditionEvaluator;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExperimentResultAggregator — Latency tracking")
class ExperimentResultAggregatorLatencyTest {

    @Nested
    @DisplayName("Successful durations tracking")
    class SuccessfulDurationsTracking {

        @Test
        @DisplayName("should start with empty durations")
        void shouldStartWithEmptyDurations() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 10);

            assertThat(aggregator.getSuccessfulDurations()).isEmpty();
        }

        @Test
        @DisplayName("should track duration from successful outcome")
        void shouldTrackDurationFromSuccessfulOutcome() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 10);

            aggregator.recordSuccess(createOutcome(Duration.ofMillis(250)));

            assertThat(aggregator.getSuccessfulDurations())
                    .hasSize(1)
                    .containsExactly(Duration.ofMillis(250));
        }

        @Test
        @DisplayName("should track multiple successful durations")
        void shouldTrackMultipleSuccessfulDurations() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 10);

            aggregator.recordSuccess(createOutcome(Duration.ofMillis(100)));
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(200)));
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(300)));

            assertThat(aggregator.getSuccessfulDurations())
                    .hasSize(3)
                    .containsExactly(
                            Duration.ofMillis(100),
                            Duration.ofMillis(200),
                            Duration.ofMillis(300));
        }

        @Test
        @DisplayName("should not track durations for failures")
        void shouldNotTrackDurationsForFailures() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 10);

            aggregator.recordSuccess(createOutcome(Duration.ofMillis(100)));
            aggregator.recordFailure(createOutcome(Duration.ofMillis(200)), "TIMEOUT");
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(300)));

            assertThat(aggregator.getSuccessfulDurations())
                    .hasSize(2)
                    .containsExactly(Duration.ofMillis(100), Duration.ofMillis(300));
        }

        @Test
        @DisplayName("should not track durations for exceptions")
        void shouldNotTrackDurationsForExceptions() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 10);

            aggregator.recordSuccess(createOutcome(Duration.ofMillis(100)));
            aggregator.recordException(new RuntimeException("boom"));

            assertThat(aggregator.getSuccessfulDurations())
                    .hasSize(1)
                    .containsExactly(Duration.ofMillis(100));
        }

        @Test
        @DisplayName("should return unmodifiable list")
        void shouldReturnUnmodifiableList() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 10);
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(100)));

            List<Duration> durations = aggregator.getSuccessfulDurations();

            assertThat(durations).isUnmodifiable();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private static final PostconditionEvaluator<String> NO_OP_EVALUATOR = new PostconditionEvaluator<>() {
        @Override
        public List<PostconditionResult> evaluate(String result) {
            return List.of();
        }

        @Override
        public int postconditionCount() {
            return 0;
        }
    };

    private UseCaseOutcome<String> createOutcome(Duration executionTime) {
        return new UseCaseOutcome<>(
                "result",
                executionTime,
                Instant.now(),
                Map.of(),
                NO_OP_EVALUATOR,
                null,
                null,
                null,
                null);
    }
}

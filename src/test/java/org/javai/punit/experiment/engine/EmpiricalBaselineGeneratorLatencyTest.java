package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.javai.punit.contract.PostconditionEvaluator;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.statistics.LatencyDistribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EmpiricalBaselineGenerator — Latency")
class EmpiricalBaselineGeneratorLatencyTest {

    private final EmpiricalBaselineGenerator generator = new EmpiricalBaselineGenerator();

    @Nested
    @DisplayName("Latency distribution generation")
    class LatencyDistributionGeneration {

        @Test
        @DisplayName("should include latency distribution when successes are present")
        void shouldIncludeLatencyDistribution() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 5);
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(100)));
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(200)));
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(300)));
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(400)));
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(500)));
            aggregator.setCompleted();

            EmpiricalBaseline baseline = generator.generate(aggregator, null, null, null);

            assertThat(baseline.hasLatencyDistribution()).isTrue();
            LatencyDistribution dist = baseline.getLatencyDistribution();
            assertThat(dist.sampleCount()).isEqualTo(5);
            assertThat(dist.meanMs()).isEqualTo(300);
            assertThat(dist.maxMs()).isEqualTo(500);
        }

        @Test
        @DisplayName("should compute latency only from successful samples")
        void shouldComputeLatencyOnlyFromSuccessfulSamples() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 5);
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(100)));
            aggregator.recordFailure(createOutcome(Duration.ofMillis(9999)), "TIMEOUT");
            aggregator.recordSuccess(createOutcome(Duration.ofMillis(200)));
            aggregator.setCompleted();

            EmpiricalBaseline baseline = generator.generate(aggregator, null, null, null);

            assertThat(baseline.hasLatencyDistribution()).isTrue();
            LatencyDistribution dist = baseline.getLatencyDistribution();
            assertThat(dist.sampleCount()).isEqualTo(2);
            assertThat(dist.maxMs()).isEqualTo(200);
        }

        @Test
        @DisplayName("should not include latency distribution when no successes")
        void shouldNotIncludeLatencyWhenNoSuccesses() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 3);
            aggregator.recordFailure(createOutcome(Duration.ofMillis(100)), "ERROR");
            aggregator.recordException(new RuntimeException("boom"));
            aggregator.setCompleted();

            EmpiricalBaseline baseline = generator.generate(aggregator, null, null, null);

            assertThat(baseline.hasLatencyDistribution()).isFalse();
            assertThat(baseline.getLatencyDistribution()).isNull();
        }

        @Test
        @DisplayName("should compute correct percentiles in generated baseline")
        void shouldComputeCorrectPercentiles() {
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator("test", 10);
            for (int i = 1; i <= 10; i++) {
                aggregator.recordSuccess(createOutcome(Duration.ofMillis(i * 100)));
            }
            aggregator.setCompleted();

            EmpiricalBaseline baseline = generator.generate(aggregator, null, null, null);

            LatencyDistribution dist = baseline.getLatencyDistribution();
            assertThat(dist.p50Ms()).isEqualTo(500);
            assertThat(dist.p90Ms()).isEqualTo(900);
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

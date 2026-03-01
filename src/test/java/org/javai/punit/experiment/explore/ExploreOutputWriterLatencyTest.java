package org.javai.punit.experiment.explore;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.EmpiricalBaseline.CostSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.ExecutionSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.StatisticsSummary;
import org.javai.punit.statistics.LatencyDistribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the latency section in ExploreOutputWriter output.
 *
 * <p>Explore output includes descriptive latency data per configuration —
 * no CI bounds or requirements, consistent with explore's comparative philosophy.
 */
@DisplayName("ExploreOutputWriter — Latency")
class ExploreOutputWriterLatencyTest {

    private ExploreOutputWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ExploreOutputWriter();
    }

    @Nested
    @DisplayName("Latency section present")
    class LatencyPresent {

        @Test
        @DisplayName("should include latency section when distribution is present")
        void shouldIncludeLatencySection() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("latency:");
        }

        @Test
        @DisplayName("should include all percentile fields")
        void shouldIncludeAllPercentileFields() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                    .contains("sampleCount:")
                    .contains("meanMs:")
                    .contains("standardDeviationMs:")
                    .contains("p50Ms:")
                    .contains("p90Ms:")
                    .contains("p95Ms:")
                    .contains("p99Ms:")
                    .contains("maxMs:");
        }

        @Test
        @DisplayName("should write correct latency values")
        void shouldWriteCorrectLatencyValues() {
            long[] millis = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
            LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
            EmpiricalBaseline baseline = createBaselineWith(dist);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                    .contains("sampleCount: 10")
                    .contains("p50Ms: " + dist.p50Ms())
                    .contains("p90Ms: " + dist.p90Ms())
                    .contains("maxMs: " + dist.maxMs());
        }

        @Test
        @DisplayName("latency section should appear after statistics section")
        void latencyShouldAppearAfterStatistics() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toYaml(baseline);

            int statisticsPos = yaml.indexOf("statistics:");
            int latencyPos = yaml.indexOf("latency:");
            int costPos = yaml.indexOf("cost:");

            assertThat(latencyPos)
                    .as("latency should appear after statistics")
                    .isGreaterThan(statisticsPos);
            assertThat(latencyPos)
                    .as("latency should appear before cost")
                    .isLessThan(costPos);
        }
    }

    @Nested
    @DisplayName("Latency section absent")
    class LatencyAbsent {

        @Test
        @DisplayName("should omit latency section when no distribution")
        void shouldOmitLatencyWhenNoDistribution() {
            EmpiricalBaseline baseline = createBaselineWithout();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).doesNotContain("latency:");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private EmpiricalBaseline createBaselineWithLatency() {
        long[] millis = {100, 150, 200, 250, 300, 350, 400, 450, 500, 800};
        return createBaselineWith(LatencyDistribution.fromMillis(millis));
    }

    private EmpiricalBaseline createBaselineWith(LatencyDistribution latency) {
        StatisticsSummary stats = new StatisticsSummary(
                0.70, 0.1, 0.5, 0.9, 14, 6, Map.of());

        return EmpiricalBaseline.builder()
                .useCaseId("ExploreLatencyUseCase")
                .generatedAt(Instant.parse("2026-02-15T10:00:00Z"))
                .execution(new ExecutionSummary(20, 20, "COMPLETED", null))
                .statistics(stats)
                .cost(new CostSummary(100, 5, 2000, 100))
                .latencyDistribution(latency)
                .build();
    }

    private EmpiricalBaseline createBaselineWithout() {
        StatisticsSummary stats = new StatisticsSummary(
                0.70, 0.1, 0.5, 0.9, 14, 6, Map.of());

        return EmpiricalBaseline.builder()
                .useCaseId("ExploreNoLatencyUseCase")
                .generatedAt(Instant.parse("2026-02-15T10:00:00Z"))
                .execution(new ExecutionSummary(20, 20, "COMPLETED", null))
                .statistics(stats)
                .cost(new CostSummary(100, 5, 2000, 100))
                .build();
    }
}

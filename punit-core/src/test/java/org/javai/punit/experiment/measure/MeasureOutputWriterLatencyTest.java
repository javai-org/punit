package org.javai.punit.experiment.measure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.EmpiricalBaseline.CostSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.ExecutionSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.StatisticsSummary;
import org.javai.punit.spec.model.LatencyBaseline;
import org.javai.punit.spec.registry.SpecSchemaValidator;
import org.javai.punit.spec.registry.SpecSchemaValidator.ValidationResult;
import org.javai.punit.spec.registry.SpecificationLoader;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.statistics.LatencyDistribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the latency section in MeasureOutputWriter output.
 */
@DisplayName("MeasureOutputWriter — Latency")
class MeasureOutputWriterLatencyTest {

    private MeasureOutputWriter writer;

    @BeforeEach
    void setUp() {
        writer = new MeasureOutputWriter();
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
        @DisplayName("should produce valid schema output with latency")
        void shouldProduceValidSchemaWithLatency() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toYaml(baseline);

            ValidationResult result = SpecSchemaValidator.validate(yaml);
            assertThat(result.isValid())
                    .as("Output with latency should pass schema validation. Errors: %s", result.errors())
                    .isTrue();
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

    @Nested
    @DisplayName("Dimension separation")
    class DimensionSeparation {

        @Test
        @DisplayName("functional YAML should not contain latency section")
        void functionalYamlShouldNotContainLatency() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toFunctionalYaml(baseline);

            assertThat(yaml)
                    .contains("statistics:")
                    .contains("requirements:")
                    .doesNotContain("latency:");
        }

        @Test
        @DisplayName("latency YAML should not contain statistics or requirements sections")
        void latencyYamlShouldNotContainStatistics() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toLatencyYaml(baseline);

            assertThat(yaml)
                    .contains("latency:")
                    .doesNotContain("statistics:")
                    .doesNotContain("requirements:");
        }

        @Test
        @DisplayName("latency YAML should include shared sections")
        void latencyYamlShouldIncludeSharedSections() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toLatencyYaml(baseline);

            assertThat(yaml)
                    .contains("schemaVersion:")
                    .contains("useCaseId:")
                    .contains("execution:")
                    .contains("cost:");
        }

        @Test
        @DisplayName("functional YAML should include shared sections")
        void functionalYamlShouldIncludeSharedSections() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toFunctionalYaml(baseline);

            assertThat(yaml)
                    .contains("schemaVersion:")
                    .contains("useCaseId:")
                    .contains("execution:")
                    .contains("cost:");
        }

        @Test
        @DisplayName("combined YAML should contain both dimensions")
        void combinedYamlShouldContainBothDimensions() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                    .contains("statistics:")
                    .contains("requirements:")
                    .contains("latency:");
        }

        @Test
        @DisplayName("functional YAML should produce valid schema output")
        void functionalYamlShouldProduceValidSchema() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toFunctionalYaml(baseline);

            ValidationResult result = SpecSchemaValidator.validate(yaml);
            assertThat(result.isValid())
                    .as("Functional YAML should pass schema validation. Errors: %s", result.errors())
                    .isTrue();
        }

        @Test
        @DisplayName("latency YAML should produce valid schema output")
        void latencyYamlShouldProduceValidSchema() {
            EmpiricalBaseline baseline = createBaselineWithLatency();

            String yaml = writer.toLatencyYaml(baseline);

            ValidationResult result = SpecSchemaValidator.validate(yaml);
            assertThat(result.isValid())
                    .as("Latency YAML should pass schema validation. Errors: %s", result.errors())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Round-trip: write then parse")
    class RoundTrip {

        @Test
        @DisplayName("should preserve latency data through write-parse cycle")
        void shouldPreserveLatencyDataThroughWriteParseCycle() {
            long[] millis = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
            LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
            EmpiricalBaseline baseline = createBaselineWith(dist);

            String yaml = writer.toYaml(baseline);
            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.hasLatencyBaseline()).isTrue();
            LatencyBaseline lb = spec.getLatencyBaseline();
            assertThat(lb.sampleCount()).isEqualTo(dist.sampleCount());
            assertThat(lb.meanMs()).isEqualTo(dist.meanMs());
            assertThat(lb.standardDeviationMs()).isEqualTo(dist.standardDeviationMs());
            assertThat(lb.p50Ms()).isEqualTo(dist.p50Ms());
            assertThat(lb.p90Ms()).isEqualTo(dist.p90Ms());
            assertThat(lb.p95Ms()).isEqualTo(dist.p95Ms());
            assertThat(lb.p99Ms()).isEqualTo(dist.p99Ms());
            assertThat(lb.maxMs()).isEqualTo(dist.maxMs());
        }

        @Test
        @DisplayName("should parse spec without latency as no latency baseline")
        void shouldParseSpecWithoutLatency() {
            EmpiricalBaseline baseline = createBaselineWithout();

            String yaml = writer.toYaml(baseline);
            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.hasLatencyBaseline()).isFalse();
            assertThat(spec.getLatencyBaseline()).isNull();
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
                0.90, 0.0095, 0.88, 0.92, 900, 100, Map.of());

        return EmpiricalBaseline.builder()
                .useCaseId("LatencyTestUseCase")
                .generatedAt(Instant.parse("2026-02-15T10:00:00Z"))
                .execution(new ExecutionSummary(1000, 1000, "COMPLETED", null))
                .statistics(stats)
                .cost(new CostSummary(5000, 5, 100000, 100))
                .latencyDistribution(latency)
                .build();
    }

    private EmpiricalBaseline createBaselineWithout() {
        StatisticsSummary stats = new StatisticsSummary(
                0.90, 0.0095, 0.88, 0.92, 900, 100, Map.of());

        return EmpiricalBaseline.builder()
                .useCaseId("NoLatencyUseCase")
                .generatedAt(Instant.parse("2026-02-15T10:00:00Z"))
                .execution(new ExecutionSummary(1000, 1000, "COMPLETED", null))
                .statistics(stats)
                .cost(new CostSummary(5000, 5, 100000, 100))
                .build();
    }
}

package org.javai.punit.experiment.measure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.EmpiricalBaseline.CostSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.ExecutionSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.StatisticsSummary;
import org.javai.punit.spec.registry.SpecSchemaValidator;
import org.javai.punit.spec.registry.SpecSchemaValidator.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for MeasureOutputWriter.
 *
 * <p>Verifies that measure output:
 * <ul>
 *   <li>Includes requirements section with minPassRate</li>
 *   <li>Includes full inferential statistics (standardError, confidenceInterval95)</li>
 *   <li>Passes schema validation for spec-driven tests</li>
 * </ul>
 */
@DisplayName("MeasureOutputWriter")
class MeasureOutputWriterTest {

    private MeasureOutputWriter writer;

    @BeforeEach
    void setUp() {
        writer = new MeasureOutputWriter();
    }

    @Nested
    @DisplayName("Requirements Section")
    class RequirementsSection {

        @Test
        @DisplayName("should include requirements section")
        void shouldIncludeRequirementsSection() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("requirements:");
        }

        @Test
        @DisplayName("should include minPassRate derived from CI lower bound")
        void shouldIncludeMinPassRateDerivedFromCiLower() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            // With 90% success and 1000 samples, CI lower is ~0.88
            assertThat(yaml)
                .contains("minPassRate:")
                .containsPattern("minPassRate: 0\\.8[0-9]+");
        }

        @Test
        @DisplayName("minPassRate should equal confidence interval lower bound")
        void minPassRateShouldEqualCiLower() {
            // Create baseline with known CI bounds
            StatisticsSummary stats = new StatisticsSummary(
                0.85,   // observed
                0.011,  // standard error
                0.8285, // CI lower
                0.8715, // CI upper
                850,    // successes
                150,    // failures
                Map.of()
            );

            EmpiricalBaseline baseline = EmpiricalBaseline.builder()
                .useCaseId("TestUseCase")
                .generatedAt(Instant.now())
                .execution(new ExecutionSummary(1000, 1000, "COMPLETED", null))
                .statistics(stats)
                .cost(new CostSummary(1000, 1, 10000, 10))
                .build();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("minPassRate: 0.8285");
        }
    }

    @Nested
    @DisplayName("Statistics Section")
    class StatisticsSection {

        @Test
        @DisplayName("should include observed success rate")
        void shouldIncludeObservedSuccessRate() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("observed: 0.9000");
        }

        @Test
        @DisplayName("should include standardError")
        void shouldIncludeStandardError() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("standardError:");
        }

        @Test
        @DisplayName("should include confidenceInterval95")
        void shouldIncludeConfidenceInterval() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("confidenceInterval95:");
        }

        @Test
        @DisplayName("should include success and failure counts")
        void shouldIncludeSuccessAndFailureCounts() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("successes: 900")
                .contains("failures: 100");
        }

        @Test
        @DisplayName("should include failure distribution when present")
        void shouldIncludeFailureDistributionWhenPresent() {
            Map<String, Integer> failures = new LinkedHashMap<>();
            failures.put("TIMEOUT", 50);
            failures.put("VALIDATION_ERROR", 50);
            EmpiricalBaseline baseline = createMeasureBaselineWithFailures(0.90, 900, 100, failures);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("failureDistribution:")
                .contains("TIMEOUT: 50")
                .contains("VALIDATION_ERROR: 50");
        }
    }

    @Nested
    @DisplayName("Common Sections")
    class CommonSections {

        @Test
        @DisplayName("should include schemaVersion")
        void shouldIncludeSchemaVersion() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("schemaVersion: punit-spec-1");
        }

        @Test
        @DisplayName("should include execution section")
        void shouldIncludeExecutionSection() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("execution:")
                .contains("samplesPlanned: 1000")
                .contains("samplesExecuted: 1000");
        }

        @Test
        @DisplayName("should include cost section")
        void shouldIncludeCostSection() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("cost:")
                .contains("totalTimeMs:")
                .contains("totalTokens:");
        }

        @Test
        @DisplayName("should include contentFingerprint")
        void shouldIncludeContentFingerprint() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).containsPattern("contentFingerprint: [a-f0-9]{64}");
        }
    }

    @Nested
    @DisplayName("Schema Validation")
    class SchemaValidation {

        @Test
        @DisplayName("measure output should pass schema validation")
        void measureOutputShouldPassSchemaValidation() {
            EmpiricalBaseline baseline = createMeasureBaseline(0.90, 900, 100);

            String yaml = writer.toYaml(baseline);

            ValidationResult result = SpecSchemaValidator.validate(yaml);
            assertThat(result.isValid())
                .as("Measure output should be valid. Errors: %s", result.errors())
                .isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private EmpiricalBaseline createMeasureBaseline(
            double successRate, int successes, int failures) {
        return createMeasureBaselineWithFailures(successRate, successes, failures, Map.of());
    }

    private EmpiricalBaseline createMeasureBaselineWithFailures(
            double successRate, int successes, int failures,
            Map<String, Integer> failureDistribution) {

        int total = successes + failures;
        double se = Math.sqrt(successRate * (1 - successRate) / total);
        double ciLower = Math.max(0, successRate - 1.96 * se);
        double ciUpper = Math.min(1, successRate + 1.96 * se);

        StatisticsSummary stats = new StatisticsSummary(
            successRate, se, ciLower, ciUpper, successes, failures, failureDistribution
        );

        return EmpiricalBaseline.builder()
            .useCaseId("TestUseCase")
            .generatedAt(Instant.parse("2026-02-02T10:00:00Z"))
            .execution(new ExecutionSummary(total, total, "COMPLETED", null))
            .statistics(stats)
            .cost(new CostSummary(5000, 5, 100000, 100))
            .build();
    }
}

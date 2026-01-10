package org.javai.punit.experiment.engine;

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
 * Tests for BaselineWriter.
 *
 * <p>These tests ensure that generated specs conform to the expected schema,
 * preventing regressions in spec generation.
 */
@DisplayName("BaselineWriter")
class BaselineWriterTest {

    private BaselineWriter writer;

    @BeforeEach
    void setUp() {
        writer = new BaselineWriter();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCHEMA VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Schema Validation")
    class SchemaValidation {

        @Test
        @DisplayName("generated YAML should pass schema validation")
        void generatedYamlShouldPassSchemaValidation() {
            EmpiricalBaseline baseline = createCompleteBaseline();

            String yaml = writer.toYaml(baseline);

            ValidationResult result = SpecSchemaValidator.validate(yaml);
            assertThat(result.isValid())
                .as("Generated YAML should be valid. Errors: %s", result.errors())
                .isTrue();
        }

        @Test
        @DisplayName("generated YAML should include schemaVersion")
        void generatedYamlShouldIncludeSchemaVersion() {
            EmpiricalBaseline baseline = createCompleteBaseline();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("schemaVersion: punit-spec-1");
        }

        @Test
        @DisplayName("generated YAML should include contentFingerprint")
        void generatedYamlShouldIncludeContentFingerprint() {
            EmpiricalBaseline baseline = createCompleteBaseline();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).containsPattern("contentFingerprint: [a-f0-9]{64}");
        }

        @Test
        @DisplayName("fingerprint should be stable for same input")
        void fingerprintShouldBeStableForSameInput() {
            EmpiricalBaseline baseline = createCompleteBaseline();

            String yaml1 = writer.toYaml(baseline);
            String yaml2 = writer.toYaml(baseline);

            // Extract fingerprints
            String fingerprint1 = extractFingerprint(yaml1);
            String fingerprint2 = extractFingerprint(yaml2);

            assertThat(fingerprint1).isEqualTo(fingerprint2);
        }

        @Test
        @DisplayName("fingerprint should change when content changes")
        void fingerprintShouldChangeWhenContentChanges() {
            EmpiricalBaseline baseline1 = createBaselineWithSuccessRate(0.85);
            EmpiricalBaseline baseline2 = createBaselineWithSuccessRate(0.95);

            String yaml1 = writer.toYaml(baseline1);
            String yaml2 = writer.toYaml(baseline2);

            String fingerprint1 = extractFingerprint(yaml1);
            String fingerprint2 = extractFingerprint(yaml2);

            assertThat(fingerprint1).isNotEqualTo(fingerprint2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REQUIRED FIELDS TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Required Fields")
    class RequiredFields {

        @Test
        @DisplayName("should include useCaseId")
        void shouldIncludeUseCaseId() {
            EmpiricalBaseline baseline = createCompleteBaseline();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("useCaseId: TestUseCase");
        }

        @Test
        @DisplayName("should include generatedAt timestamp")
        void shouldIncludeGeneratedAt() {
            EmpiricalBaseline baseline = createCompleteBaseline();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).containsPattern("generatedAt: \\d{4}-\\d{2}-\\d{2}T");
        }

        @Test
        @DisplayName("should include execution section")
        void shouldIncludeExecutionSection() {
            EmpiricalBaseline baseline = createCompleteBaseline();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("execution:")
                .contains("samplesPlanned: 1000")
                .contains("samplesExecuted: 950")
                .contains("terminationReason: COMPLETED");
        }

        @Test
        @DisplayName("should include statistics section")
        void shouldIncludeStatisticsSection() {
            EmpiricalBaseline baseline = createCompleteBaseline();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("statistics:")
                .contains("successRate:")
                .contains("observed:")
                .contains("standardError:")
                .contains("confidenceInterval95:")
                .contains("successes:")
                .contains("failures:");
        }

        @Test
        @DisplayName("should include cost section")
        void shouldIncludeCostSection() {
            EmpiricalBaseline baseline = createCompleteBaseline();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("cost:")
                .contains("totalTimeMs:")
                .contains("totalTokens:");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OPTIONAL FIELDS TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Optional Fields")
    class OptionalFields {

        @Test
        @DisplayName("should include experimentId when present")
        void shouldIncludeExperimentIdWhenPresent() {
            EmpiricalBaseline baseline = EmpiricalBaseline.builder()
                .useCaseId("TestUseCase")
                .experimentId("test-experiment-v1")
                .generatedAt(Instant.now())
                .execution(new ExecutionSummary(1000, 1000, "COMPLETED", null))
                .statistics(createStatistics(0.9, 100, 900, 100))
                .cost(new CostSummary(1000, 1, 10000, 10))
                .build();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml).contains("experimentId: test-experiment-v1");
        }

        @Test
        @DisplayName("should include experiment class and method when present")
        void shouldIncludeExperimentMetadataWhenPresent() {
            EmpiricalBaseline baseline = EmpiricalBaseline.builder()
                .useCaseId("TestUseCase")
                .experimentClass("com.example.MyExperiment")
                .experimentMethod("measureBaseline")
                .generatedAt(Instant.now())
                .execution(new ExecutionSummary(1000, 1000, "COMPLETED", null))
                .statistics(createStatistics(0.9, 100, 900, 100))
                .cost(new CostSummary(1000, 1, 10000, 10))
                .build();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("experimentClass: com.example.MyExperiment")
                .contains("experimentMethod: measureBaseline");
        }

        @Test
        @DisplayName("should include failure distribution when present")
        void shouldIncludeFailureDistributionWhenPresent() {
            Map<String, Integer> failureDistribution = new LinkedHashMap<>();
            failureDistribution.put("TIMEOUT", 5);
            failureDistribution.put("VALIDATION_ERROR", 3);

            StatisticsSummary stats = new StatisticsSummary(
                0.92, 0.01, 0.90, 0.94, 92, 8, failureDistribution
            );

            EmpiricalBaseline baseline = EmpiricalBaseline.builder()
                .useCaseId("TestUseCase")
                .generatedAt(Instant.now())
                .execution(new ExecutionSummary(100, 100, "COMPLETED", null))
                .statistics(stats)
                .cost(new CostSummary(1000, 10, 5000, 50))
                .build();

            String yaml = writer.toYaml(baseline);

            assertThat(yaml)
                .contains("failureDistribution:")
                .contains("TIMEOUT: 5")
                .contains("VALIDATION_ERROR: 3");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private EmpiricalBaseline createCompleteBaseline() {
        return createBaselineWithSuccessRate(0.90);
    }

    private EmpiricalBaseline createBaselineWithSuccessRate(double successRate) {
        int total = 1000;
        int successes = (int) (total * successRate);
        int failures = total - successes;
        
        // Adjust executed to be slightly less for realism
        int executed = 950;
        
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("backend", "test");

        return EmpiricalBaseline.builder()
            .useCaseId("TestUseCase")
            .generatedAt(Instant.parse("2024-01-15T10:30:00Z"))
            .execution(new ExecutionSummary(total, executed, "COMPLETED", null))
            .statistics(createStatistics(successRate, total, successes, failures))
            .cost(new CostSummary(5000, 5, 100000, 100))
            .context(context)
            .build();
    }

    private StatisticsSummary createStatistics(
            double successRate, int total, int successes, int failures) {
        double se = Math.sqrt(successRate * (1 - successRate) / total);
        double ciLower = Math.max(0, successRate - 1.96 * se);
        double ciUpper = Math.min(1, successRate + 1.96 * se);

        return new StatisticsSummary(
            successRate, se, ciLower, ciUpper, successes, failures, Map.of()
        );
    }

    private String extractFingerprint(String yaml) {
        int idx = yaml.indexOf("contentFingerprint:");
        if (idx < 0) return null;
        int start = idx + "contentFingerprint:".length();
        int end = yaml.indexOf('\n', start);
        if (end < 0) end = yaml.length();
        return yaml.substring(start, end).trim();
    }
}


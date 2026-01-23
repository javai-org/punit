package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.HashableFactorSource;
import org.javai.punit.experiment.engine.DefaultHashableFactorSource;
import org.javai.punit.ptest.engine.FactorConsistencyValidator.ValidationResult;
import org.javai.punit.ptest.engine.FactorConsistencyValidator.ValidationStatus;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests demonstrating the factor consistency validation workflow.
 *
 * <p>These tests simulate the experiment→test lifecycle and verify that
 * factor consistency is correctly validated.
 */
@DisplayName("Factor Consistency Integration")
class FactorConsistencyIntegrationTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // SIMULATED FACTOR SOURCES
    // ═══════════════════════════════════════════════════════════════════════════

    private static final List<FactorArguments> PRODUCTION_QUERIES = List.of(
            FactorArguments.of("wireless headphones", "gpt-4", 0.7),
            FactorArguments.of("laptop stand", "gpt-4", 0.7),
            FactorArguments.of("USB-C hub", "gpt-4", 0.7),
            FactorArguments.of("mechanical keyboard", "gpt-4", 0.7),
            FactorArguments.of("webcam 4k", "gpt-4", 0.7)
    );

    private static final List<FactorArguments> MODIFIED_QUERIES = List.of(
            FactorArguments.of("earbuds", "gpt-4", 0.7),  // Different first query
            FactorArguments.of("laptop stand", "gpt-4", 0.7),
            FactorArguments.of("USB-C hub", "gpt-4", 0.7)
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // WORKFLOW SIMULATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Experiment → Test Workflow")
    class ExperimentTestWorkflow {

        @Test
        @DisplayName("should validate successfully when same factor source is used")
        void shouldValidateSuccessfullyWhenSameFactorSourceUsed() {
            // ARRANGE: Simulate experiment phase
            HashableFactorSource experimentSource = DefaultHashableFactorSource.fromList(
                    "productQueries", PRODUCTION_QUERIES);
            String experimentHash = experimentSource.getSourceHash();
            int experimentSamples = 1000;

            // Simulate storing the spec after experiment
            ExecutionSpecification spec = ExecutionSpecification.builder()
                    .useCaseId("ProductSearchSpec")
                    .useCaseId("ProductSearchUseCase")
                    .generatedAt(Instant.now())
                    .empiricalBasis(experimentSamples, 940)
                    .factorSourceMetadata(experimentHash, "productQueries", experimentSamples)
                    .build();

            // ARRANGE: Simulate test phase - using same factor source
            HashableFactorSource testSource = DefaultHashableFactorSource.fromList(
                    "productQueries", PRODUCTION_QUERIES);  // Same factors!
            int testSamples = 100;

            // ACT: Validate factor consistency
            ValidationResult result = FactorConsistencyValidator.validateWithSampleCount(
                    testSource, spec, testSamples);

            // ASSERT
            assertThat(result.status()).isEqualTo(ValidationStatus.MATCH);
            assertThat(result.isMatch()).isTrue();
            assertThat(result.message())
                    .contains("match")
                    .contains("Experiment used 1000 samples")
                    .contains("test uses 100");
        }

        @Test
        @DisplayName("should warn when different factor source is used")
        void shouldWarnWhenDifferentFactorSourceUsed() {
            // ARRANGE: Simulate experiment phase
            HashableFactorSource experimentSource = DefaultHashableFactorSource.fromList(
                    "productQueries", PRODUCTION_QUERIES);
            String experimentHash = experimentSource.getSourceHash();
            int experimentSamples = 1000;

            ExecutionSpecification spec = ExecutionSpecification.builder()
                    .useCaseId("ProductSearchSpec")
                    .useCaseId("ProductSearchUseCase")
                    .generatedAt(Instant.now())
                    .empiricalBasis(experimentSamples, 940)
                    .factorSourceMetadata(experimentHash, "productQueries", experimentSamples)
                    .build();

            // ARRANGE: Simulate test phase - using DIFFERENT factor source!
            HashableFactorSource testSource = DefaultHashableFactorSource.fromList(
                    "modifiedQueries", MODIFIED_QUERIES);
            int testSamples = 100;

            // ACT: Validate factor consistency
            ValidationResult result = FactorConsistencyValidator.validateWithSampleCount(
                    testSource, spec, testSamples);

            // ASSERT
            assertThat(result.status()).isEqualTo(ValidationStatus.MISMATCH);
            assertThat(result.shouldWarn()).isTrue();
            assertThat(result.message())
                    .contains("mismatch")
                    .contains("productQueries")
                    .contains("modifiedQueries");
        }

        @Test
        @DisplayName("should skip validation for legacy specs without factor metadata")
        void shouldSkipValidationForLegacySpecs() {
            // ARRANGE: Legacy spec without factor metadata
            ExecutionSpecification legacySpec = ExecutionSpecification.builder()
                    .useCaseId("LegacySpec")
                    .useCaseId("LegacyUseCase")
                    .empiricalBasis(500, 475)
                    // No factorSourceMetadata!
                    .build();

            HashableFactorSource testSource = DefaultHashableFactorSource.fromList(
                    "queries", PRODUCTION_QUERIES);

            // ACT
            ValidationResult result = FactorConsistencyValidator.validate(testSource, legacySpec);

            // ASSERT
            assertThat(result.status()).isEqualTo(ValidationStatus.NOT_APPLICABLE);
            assertThat(result.message()).contains("does not contain factor source metadata");
        }
    }

    @Nested
    @DisplayName("First-N Prefix Semantics")
    class FirstNPrefixSemantics {

        @Test
        @DisplayName("test using subset of experiment factors should match")
        void testUsingSubsetShouldMatch() {
            // The key insight: hash is source-owned, not consumption-based.
            // If the test and experiment use the same source, the hash matches
            // regardless of how many factors each consumed.

            List<FactorArguments> fullSource = List.of(
                    FactorArguments.of("q1"),
                    FactorArguments.of("q2"),
                    FactorArguments.of("q3"),
                    FactorArguments.of("q4"),
                    FactorArguments.of("q5")
            );

            // Same source for both experiment and test
            HashableFactorSource experimentSource = DefaultHashableFactorSource.fromList("source", fullSource);
            HashableFactorSource testSource = DefaultHashableFactorSource.fromList("source", fullSource);

            ExecutionSpecification spec = ExecutionSpecification.builder()
                    .useCaseId("spec")
                    .useCaseId("useCase")
                    .factorSourceMetadata(experimentSource.getSourceHash(), "source", 5)
                    .build();

            // Test uses only 2 samples, but from the same source
            ValidationResult result = FactorConsistencyValidator.validateWithSampleCount(testSource, spec, 2);

            assertThat(result.status()).isEqualTo(ValidationStatus.MATCH);
            // Hashes match because they're from the same source
            assertThat(result.testHash()).isEqualTo(result.baselineHash());
        }
    }

    @Nested
    @DisplayName("Output Formatting")
    class OutputFormatting {

        @Test
        @DisplayName("match result should format with checkmark")
        void matchResultShouldFormatWithCheckmark() {
            HashableFactorSource source = DefaultHashableFactorSource.fromList("src", PRODUCTION_QUERIES);
            ExecutionSpecification spec = ExecutionSpecification.builder()
                    .useCaseId("spec")
                    .useCaseId("uc")
                    .factorSourceMetadata(source.getSourceHash(), "src", 100)
                    .build();

            ValidationResult result = FactorConsistencyValidator.validate(source, spec);
            String formatted = result.formatForLog();

            assertThat(formatted).startsWith("✓");
        }

        @Test
        @DisplayName("mismatch result should format as warning")
        void mismatchResultShouldFormatAsWarning() {
            HashableFactorSource experimentSource = DefaultHashableFactorSource.fromList("exp", PRODUCTION_QUERIES);
            HashableFactorSource testSource = DefaultHashableFactorSource.fromList("test", MODIFIED_QUERIES);

            ExecutionSpecification spec = ExecutionSpecification.builder()
                    .useCaseId("spec")
                    .useCaseId("uc")
                    .factorSourceMetadata(experimentSource.getSourceHash(), "exp", 100)
                    .build();

            ValidationResult result = FactorConsistencyValidator.validate(testSource, spec);
            String formatted = result.formatForLog();

            assertThat(formatted)
                    .contains("⚠️")
                    .contains("FACTOR CONSISTENCY WARNING")
                    .contains("Statistical conclusions may be less reliable");
        }
    }
}


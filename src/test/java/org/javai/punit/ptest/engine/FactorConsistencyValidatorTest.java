package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

@DisplayName("FactorConsistencyValidator")
class FactorConsistencyValidatorTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST FIXTURES
    // ═══════════════════════════════════════════════════════════════════════════

    private static final List<FactorArguments> STANDARD_FACTORS = List.of(
            FactorArguments.of("wireless headphones", "gpt-4"),
            FactorArguments.of("laptop stand", "gpt-4"),
            FactorArguments.of("USB-C hub", "gpt-4")
    );

    private static final List<FactorArguments> DIFFERENT_FACTORS = List.of(
            FactorArguments.of("earbuds", "gpt-4"),
            FactorArguments.of("monitor arm", "gpt-4")
    );

    private HashableFactorSource createSource(String name, List<FactorArguments> factors) {
        return DefaultHashableFactorSource.fromList(name, factors);
    }

    private ExecutionSpecification createSpecWithFactorMetadata(String hash, String sourceName, int samplesUsed) {
        return ExecutionSpecification.builder()
                .useCaseId("test-spec")
                .useCaseId("TestUseCase")
                .factorSourceMetadata(hash, sourceName, samplesUsed)
                .build();
    }

    private ExecutionSpecification createSpecWithoutFactorMetadata() {
        return ExecutionSpecification.builder()
                .useCaseId("test-spec")
                .useCaseId("TestUseCase")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("should return MATCH when hashes are identical")
        void shouldReturnMatchWhenHashesAreIdentical() {
            HashableFactorSource testSource = createSource("standardQueries", STANDARD_FACTORS);
            String expectedHash = testSource.getSourceHash();

            ExecutionSpecification spec = createSpecWithFactorMetadata(expectedHash, "standardQueries", 1000);

            ValidationResult result = FactorConsistencyValidator.validate(testSource, spec);

            assertThat(result.status()).isEqualTo(ValidationStatus.MATCH);
            assertThat(result.isMatch()).isTrue();
            assertThat(result.shouldWarn()).isFalse();
            assertThat(result.testHash()).isEqualTo(expectedHash);
            assertThat(result.baselineHash()).isEqualTo(expectedHash);
        }

        @Test
        @DisplayName("should return MISMATCH when hashes differ")
        void shouldReturnMismatchWhenHashesDiffer() {
            HashableFactorSource testSource = createSource("differentQueries", DIFFERENT_FACTORS);
            HashableFactorSource baselineSource = createSource("standardQueries", STANDARD_FACTORS);

            ExecutionSpecification spec = createSpecWithFactorMetadata(
                    baselineSource.getSourceHash(), "standardQueries", 1000);

            ValidationResult result = FactorConsistencyValidator.validate(testSource, spec);

            assertThat(result.status()).isEqualTo(ValidationStatus.MISMATCH);
            assertThat(result.isMatch()).isFalse();
            assertThat(result.shouldWarn()).isTrue();
            assertThat(result.testHash()).isEqualTo(testSource.getSourceHash());
            assertThat(result.baselineHash()).isEqualTo(baselineSource.getSourceHash());
        }

        @Test
        @DisplayName("should return NOT_APPLICABLE when test has no factor source")
        void shouldReturnNotApplicableWhenTestHasNoFactorSource() {
            ExecutionSpecification spec = createSpecWithFactorMetadata("hash123", "source", 100);

            ValidationResult result = FactorConsistencyValidator.validate(null, spec);

            assertThat(result.status()).isEqualTo(ValidationStatus.NOT_APPLICABLE);
            assertThat(result.message()).contains("does not use a factor source");
        }

        @Test
        @DisplayName("should return NOT_APPLICABLE when spec has no factor metadata")
        void shouldReturnNotApplicableWhenSpecHasNoFactorMetadata() {
            HashableFactorSource testSource = createSource("queries", STANDARD_FACTORS);
            ExecutionSpecification spec = createSpecWithoutFactorMetadata();

            ValidationResult result = FactorConsistencyValidator.validate(testSource, spec);

            assertThat(result.status()).isEqualTo(ValidationStatus.NOT_APPLICABLE);
            assertThat(result.message()).contains("does not contain factor source metadata");
        }

        @Test
        @DisplayName("should reject null spec")
        void shouldRejectNullSpec() {
            HashableFactorSource testSource = createSource("queries", STANDARD_FACTORS);

            assertThatThrownBy(() -> FactorConsistencyValidator.validate(testSource, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("validateWithSampleCount()")
    class ValidateWithSampleCount {

        @Test
        @DisplayName("should include sample count note when counts differ")
        void shouldIncludeSampleCountNoteWhenCountsDiffer() {
            HashableFactorSource testSource = createSource("queries", STANDARD_FACTORS);
            String hash = testSource.getSourceHash();

            ExecutionSpecification spec = createSpecWithFactorMetadata(hash, "queries", 1000);

            ValidationResult result = FactorConsistencyValidator.validateWithSampleCount(testSource, spec, 100);

            assertThat(result.status()).isEqualTo(ValidationStatus.MATCH);
            assertThat(result.message()).contains("Experiment used 1000 samples");
            assertThat(result.message()).contains("test uses 100");
        }

        @Test
        @DisplayName("should not include sample count note when counts are equal")
        void shouldNotIncludeSampleCountNoteWhenCountsAreEqual() {
            HashableFactorSource testSource = createSource("queries", STANDARD_FACTORS);
            String hash = testSource.getSourceHash();

            ExecutionSpecification spec = createSpecWithFactorMetadata(hash, "queries", 100);

            ValidationResult result = FactorConsistencyValidator.validateWithSampleCount(testSource, spec, 100);

            assertThat(result.status()).isEqualTo(ValidationStatus.MATCH);
            assertThat(result.message()).doesNotContain("Note:");
        }

        @Test
        @DisplayName("should not modify mismatch result")
        void shouldNotModifyMismatchResult() {
            HashableFactorSource testSource = createSource("different", DIFFERENT_FACTORS);
            HashableFactorSource baselineSource = createSource("standard", STANDARD_FACTORS);

            ExecutionSpecification spec = createSpecWithFactorMetadata(
                    baselineSource.getSourceHash(), "standard", 1000);

            ValidationResult result = FactorConsistencyValidator.validateWithSampleCount(testSource, spec, 100);

            assertThat(result.status()).isEqualTo(ValidationStatus.MISMATCH);
            assertThat(result.message()).contains("mismatch");
        }
    }

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("formatForLog() should format MATCH with checkmark")
        void formatForLogShouldFormatMatchWithCheckmark() {
            ValidationResult result = new ValidationResult(
                    ValidationStatus.MATCH, "Sources match.", "a", "s", "a", "s", 100);

            assertThat(result.formatForLog()).startsWith("✓");
        }

        @Test
        @DisplayName("formatForLog() should format MISMATCH with warning")
        void formatForLogShouldFormatMismatchWithWarning() {
            ValidationResult result = new ValidationResult(
                    ValidationStatus.MISMATCH, "Sources differ.", "a", "s", "b", "t", 100);

            assertThat(result.formatForLog()).contains("⚠️ FACTOR CONSISTENCY WARNING");
        }

        @Test
        @DisplayName("formatForLog() should format NOT_APPLICABLE with info")
        void formatForLogShouldFormatNotApplicableWithInfo() {
            ValidationResult result = new ValidationResult(
                    ValidationStatus.NOT_APPLICABLE, "Skipped.", null, null, null, null, null);

            assertThat(result.formatForLog()).startsWith("ℹ️");
        }
    }

    @Nested
    @DisplayName("Mismatch message formatting")
    class MismatchMessageFormatting {

        @Test
        @DisplayName("should include both baseline and test details")
        void shouldIncludeBothBaselineAndTestDetails() {
            HashableFactorSource testSource = createSource("testQueries", DIFFERENT_FACTORS);
            HashableFactorSource baselineSource = createSource("baselineQueries", STANDARD_FACTORS);

            ExecutionSpecification spec = createSpecWithFactorMetadata(
                    baselineSource.getSourceHash(), "baselineQueries", 500);

            ValidationResult result = FactorConsistencyValidator.validate(testSource, spec);

            assertThat(result.message())
                    .contains("Baseline:")
                    .contains("Test:")
                    .contains("baselineQueries")
                    .contains("testQueries")
                    .contains("samples=500");
        }

        @Test
        @DisplayName("should include guidance about using same factor source")
        void shouldIncludeGuidance() {
            HashableFactorSource testSource = createSource("test", DIFFERENT_FACTORS);
            HashableFactorSource baselineSource = createSource("baseline", STANDARD_FACTORS);

            ExecutionSpecification spec = createSpecWithFactorMetadata(
                    baselineSource.getSourceHash(), "baseline", 100);

            ValidationResult result = FactorConsistencyValidator.validate(testSource, spec);

            assertThat(result.message()).contains("Ensure the same @FactorSource");
        }
    }

    @Nested
    @DisplayName("Hash truncation")
    class HashTruncation {

        @Test
        @DisplayName("should truncate long hashes in mismatch message")
        void shouldTruncateLongHashesInMismatchMessage() {
            HashableFactorSource testSource = createSource("test", DIFFERENT_FACTORS);
            HashableFactorSource baselineSource = createSource("baseline", STANDARD_FACTORS);

            ExecutionSpecification spec = createSpecWithFactorMetadata(
                    baselineSource.getSourceHash(), "baseline", 100);

            ValidationResult result = FactorConsistencyValidator.validate(testSource, spec);

            // SHA-256 produces 64 char hashes; message should show truncated versions
            assertThat(result.message()).contains("...");
        }
    }
}


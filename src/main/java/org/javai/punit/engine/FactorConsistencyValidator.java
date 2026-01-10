package org.javai.punit.engine;

import java.util.Objects;
import org.javai.punit.experiment.api.HashableFactorSource;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.spec.model.ExecutionSpecification.FactorSourceMetadata;

/**
 * Validates factor source consistency between experiments and probabilistic tests.
 *
 * <p>Factor consistency verification ensures statistical integrity by confirming that
 * the test uses the same factor source as the experiment that generated the baseline spec.
 *
 * <h2>Comparison Logic</h2>
 * <ul>
 *   <li><b>Same source hash</b>: Factor sources match; first-N factors are identical</li>
 *   <li><b>Different source hash</b>: Factor sources differ; statistical comparisons may be invalid</li>
 *   <li><b>No baseline metadata</b>: Spec predates factor consistency feature; comparison skipped</li>
 *   <li><b>No test factor source</b>: Test doesn't use @FactorSource; comparison skipped</li>
 * </ul>
 *
 * <h2>Result Types</h2>
 * <ul>
 *   <li>{@link ValidationStatus#MATCH}: Sources match</li>
 *   <li>{@link ValidationStatus#MISMATCH}: Sources differ (warning)</li>
 *   <li>{@link ValidationStatus#NOT_APPLICABLE}: Comparison not possible or not needed</li>
 * </ul>
 *
 * @see HashableFactorSource
 * @see ExecutionSpecification.FactorSourceMetadata
 */
public final class FactorConsistencyValidator {

    private FactorConsistencyValidator() {
        // Utility class
    }

    /**
     * Validates factor source consistency between a test's factor source and a baseline spec.
     *
     * @param testFactorSource the factor source used by the test (may be null)
     * @param spec the execution specification with baseline metadata
     * @return the validation result
     */
    public static ValidationResult validate(HashableFactorSource testFactorSource, ExecutionSpecification spec) {
        Objects.requireNonNull(spec, "spec must not be null");

        // No factor source in test
        if (testFactorSource == null) {
            return new ValidationResult(
                    ValidationStatus.NOT_APPLICABLE,
                    "Test does not use a factor source; factor consistency check skipped.",
                    null, null, null, null, null
            );
        }

        // No factor metadata in spec
        if (!spec.hasFactorSourceMetadata()) {
            return new ValidationResult(
                    ValidationStatus.NOT_APPLICABLE,
                    "Baseline spec does not contain factor source metadata; factor consistency check skipped.",
                    null, null, null, null, null
            );
        }

        FactorSourceMetadata baseline = spec.getFactorSourceMetadata();
        String testHash = testFactorSource.getSourceHash();
        String testSourceName = testFactorSource.getSourceName();

        // Compare hashes
        if (testHash.equals(baseline.sourceHash())) {
            return new ValidationResult(
                    ValidationStatus.MATCH,
                    "Factor sources match.",
                    testHash, testSourceName, baseline.sourceHash(), baseline.sourceName(), baseline.samplesUsed()
            );
        } else {
            return new ValidationResult(
                    ValidationStatus.MISMATCH,
                    buildMismatchMessage(testHash, testSourceName, baseline),
                    testHash, testSourceName, baseline.sourceHash(), baseline.sourceName(), baseline.samplesUsed()
            );
        }
    }

    /**
     * Validates factor source consistency and logs appropriate warnings/notes.
     *
     * @param testFactorSource the factor source used by the test (may be null)
     * @param spec the execution specification with baseline metadata
     * @param testSamples the number of samples the test will use
     * @return the validation result
     */
    public static ValidationResult validateWithSampleCount(
            HashableFactorSource testFactorSource,
            ExecutionSpecification spec,
            int testSamples) {
        
        ValidationResult baseResult = validate(testFactorSource, spec);
        
        // Add sample count note if both have factor metadata and counts differ
        if (baseResult.status() == ValidationStatus.MATCH && spec.hasFactorSourceMetadata()) {
            FactorSourceMetadata baseline = spec.getFactorSourceMetadata();
            if (baseline.samplesUsed() != testSamples && baseline.samplesUsed() > 0) {
                String extendedMessage = baseResult.message() +
                        String.format(" Note: Experiment used %d samples; test uses %d.",
                                baseline.samplesUsed(), testSamples);
                return new ValidationResult(
                        baseResult.status(),
                        extendedMessage,
                        baseResult.testHash(),
                        baseResult.testSourceName(),
                        baseResult.baselineHash(),
                        baseResult.baselineSourceName(),
                        baseline.samplesUsed()
                );
            }
        }
        
        return baseResult;
    }

    private static String buildMismatchMessage(String testHash, String testSourceName, FactorSourceMetadata baseline) {
        StringBuilder sb = new StringBuilder();
        sb.append("Factor source mismatch detected.\n");
        sb.append("  Baseline: hash=").append(truncateHash(baseline.sourceHash()));
        sb.append(", source=").append(baseline.sourceName());
        sb.append(", samples=").append(baseline.samplesUsed()).append("\n");
        sb.append("  Test:     hash=").append(truncateHash(testHash));
        sb.append(", source=").append(testSourceName).append("\n");
        sb.append("Statistical conclusions may be less reliable.\n");
        sb.append("Ensure the same @FactorSource is used for experiments and tests.");
        return sb.toString();
    }

    private static String truncateHash(String hash) {
        if (hash == null) return "null";
        if (hash.length() <= 12) return hash;
        return hash.substring(0, 8) + "..." + hash.substring(hash.length() - 4);
    }

    /**
     * Validation status.
     */
    public enum ValidationStatus {
        /** Factor sources match - same hash */
        MATCH,
        /** Factor sources differ - different hash */
        MISMATCH,
        /** Validation not applicable (no factor source or no baseline metadata) */
        NOT_APPLICABLE
    }

    /**
     * Result of factor consistency validation.
     *
     * @param status the validation status
     * @param message human-readable description
     * @param testHash the test's factor source hash (null if not applicable)
     * @param testSourceName the test's factor source name (null if not applicable)
     * @param baselineHash the baseline's factor source hash (null if not applicable)
     * @param baselineSourceName the baseline's factor source name (null if not applicable)
     * @param baselineSamplesUsed the number of samples used in the baseline experiment (null if not applicable)
     */
    public record ValidationResult(
            ValidationStatus status,
            String message,
            String testHash,
            String testSourceName,
            String baselineHash,
            String baselineSourceName,
            Integer baselineSamplesUsed
    ) {
        /**
         * Returns true if this result indicates a mismatch that should trigger a warning.
         */
        public boolean shouldWarn() {
            return status == ValidationStatus.MISMATCH;
        }

        /**
         * Returns true if factor sources match.
         */
        public boolean isMatch() {
            return status == ValidationStatus.MATCH;
        }

        /**
         * Formats the result for logging.
         */
        public String formatForLog() {
            return switch (status) {
                case MATCH -> "✓ " + message;
                case MISMATCH -> "⚠️ FACTOR CONSISTENCY WARNING\n" + message;
                case NOT_APPLICABLE -> "ℹ️ " + message;
            };
        }
    }
}


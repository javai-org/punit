package org.javai.punit.ptest.engine;

/**
 * Represents a warning generated during configuration resolution.
 *
 * <p>Warnings are returned as part of the resolved configuration rather than
 * being emitted directly, allowing the caller to decide how to report them
 * (logging, stderr, test report, etc.).
 *
 * @param type the category of warning
 * @param message the formatted warning message
 */
public record ConfigurationWarning(Type type, String message) {

    /**
     * Warning types for categorization.
     */
    public enum Type {
        /**
         * Configuration is statistically unsound (high false positive rate).
         */
        STATISTICALLY_UNSOUND
    }

    /**
     * Creates a warning for statistically unsound threshold configuration.
     *
     * @param useCaseId the use case identifier
     * @param threshold the configured threshold (minPassRate)
     * @param baselineRate the observed baseline rate
     * @param impliedConfidence the computed confidence level
     * @return a formatted warning
     */
    public static ConfigurationWarning statisticallyUnsound(
            String useCaseId,
            double threshold,
            double baselineRate,
            double impliedConfidence) {

        String message = String.format("""

            ⚠️  WARNING: Statistically Unsound Configuration
            ────────────────────────────────────────────────────────────────────────────
            Test: %s
            Threshold: %.1f%% (from minPassRate)
            Baseline rate: %.1f%%
            Implied confidence: %.1f%%

            The threshold equals or exceeds the baseline rate. This results in a very
            high false positive rate (%.1f%% of test runs will fail even when the
            system is working correctly).

            Consider:
              • Using Sample-Size-First approach (let framework derive threshold)
              • Lowering the threshold below the baseline rate
            ────────────────────────────────────────────────────────────────────────────
            """,
                useCaseId,
                threshold * 100,
                baselineRate * 100,
                impliedConfidence * 100,
                (1 - impliedConfidence) * 100);

        return new ConfigurationWarning(Type.STATISTICALLY_UNSOUND, message);
    }
}

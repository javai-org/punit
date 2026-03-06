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
import org.javai.punit.reporting.RateFormat;

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

        String message = String.format(
                "Test '%s': threshold %s (from minPassRate) equals or exceeds baseline rate %s. "
                + "Implied confidence: %.1f%%. "
                + "This results in a %.1f%% false positive rate. "
                + "Consider using Sample-Size-First approach or lowering the threshold.",
                useCaseId,
                RateFormat.format(threshold),
                RateFormat.format(baselineRate),
                impliedConfidence * 100,
                (1 - impliedConfidence) * 100);

        return new ConfigurationWarning(Type.STATISTICALLY_UNSOUND, message);
    }
}

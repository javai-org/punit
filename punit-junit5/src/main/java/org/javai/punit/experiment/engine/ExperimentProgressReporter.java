package org.javai.punit.experiment.engine;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Utility for reporting experiment progress via JUnit report entries.
 *
 * <p>Centralizes progress reporting format to ensure consistency across
 * experiment strategies (MEASURE, EXPLORE, OPTIMIZE).
 */
public final class ExperimentProgressReporter {

    private ExperimentProgressReporter() {
        // Utility class
    }

    /**
     * Reports standard progress entries for an experiment sample.
     *
     * @param context the JUnit extension context
     * @param mode the experiment mode (e.g., "MEASURE", "EXPLORE", "OPTIMIZE")
     * @param currentSample the current sample number (1-based)
     * @param totalSamples the total number of samples
     * @param successRate the observed success rate (0.0 to 1.0)
     */
    public static void reportProgress(
            ExtensionContext context,
            String mode,
            int currentSample,
            int totalSamples,
            double successRate) {

        context.publishReportEntry("punit.mode", mode);
        context.publishReportEntry("punit.sample", formatSampleProgress(currentSample, totalSamples));
        context.publishReportEntry("punit.successRate", formatSuccessRate(successRate));
    }

    /**
     * Reports progress with an additional configuration name (for EXPLORE mode).
     *
     * @param context the JUnit extension context
     * @param mode the experiment mode
     * @param configName the configuration name
     * @param currentSample the current sample number within the config
     * @param totalSamples the total samples per config
     * @param successRate the observed success rate
     */
    public static void reportProgressWithConfig(
            ExtensionContext context,
            String mode,
            String configName,
            int currentSample,
            int totalSamples,
            double successRate) {

        context.publishReportEntry("punit.mode", mode);
        context.publishReportEntry("punit.config", configName);
        context.publishReportEntry("punit.sample", formatSampleProgress(currentSample, totalSamples));
        context.publishReportEntry("punit.successRate", formatSuccessRate(successRate));
    }

    /**
     * Formats sample progress as "current/total".
     *
     * @param current the current sample number
     * @param total the total number of samples
     * @return formatted progress string (e.g., "42/100")
     */
    public static String formatSampleProgress(int current, int total) {
        return current + "/" + total;
    }

    /**
     * Formats a success rate as a percentage with 2 decimal places.
     *
     * @param rate the success rate (0.0 to 1.0)
     * @return formatted percentage string (e.g., "95.50%")
     */
    public static String formatSuccessRate(double rate) {
        return org.javai.punit.reporting.RateFormat.format(rate);
    }
}

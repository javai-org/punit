package org.javai.punit.experiment.measure;

import org.javai.punit.api.ExperimentMode;
import org.javai.punit.experiment.engine.ExperimentConfig;

/**
 * Configuration for @MeasureExperiment.
 *
 * <p>MEASURE mode establishes reliable statistics for a single configuration
 * by running many samples (default 1000) and generating an empirical spec.
 *
 * @param useCaseClass the use case class to test
 * @param useCaseId resolved use case identifier
 * @param samples number of samples to execute
 * @param timeBudgetMs time budget in milliseconds (0 = unlimited)
 * @param tokenBudget token budget (0 = unlimited)
 * @param experimentId experiment identifier for output naming
 * @param expiresInDays baseline expiration in days (0 = no expiration tracking)
 */
public record MeasureConfig(
        Class<?> useCaseClass,
        String useCaseId,
        int samples,
        long timeBudgetMs,
        long tokenBudget,
        String experimentId,
        int expiresInDays
) implements ExperimentConfig {

    @Override
    public ExperimentMode mode() {
        return ExperimentMode.MEASURE;
    }

    /**
     * Returns the effective sample count, using the mode default if not specified.
     *
     * @return the effective sample count
     */
    public int effectiveSamples() {
        return mode().getEffectiveSampleSize(samples);
    }
}

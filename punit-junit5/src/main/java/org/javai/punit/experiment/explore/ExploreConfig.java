package org.javai.punit.experiment.explore;

import org.javai.punit.api.ExperimentMode;
import org.javai.punit.experiment.engine.ExperimentConfig;

/**
 * Configuration for @ExploreExperiment.
 *
 * <p>EXPLORE mode compares multiple configurations to understand factor effects.
 * It runs a small number of samples per configuration (default 1) and generates
 * separate spec files for each configuration, enabling comparison via diff.
 *
 * @param useCaseClass the use case class to test
 * @param useCaseId resolved use case identifier
 * @param samplesPerConfig samples to run per factor configuration
 * @param timeBudgetMs time budget in milliseconds (0 = unlimited)
 * @param tokenBudget token budget (0 = unlimited)
 * @param experimentId experiment identifier for output naming
 * @param expiresInDays baseline expiration in days (0 = no expiration tracking)
 */
public record ExploreConfig(
        Class<?> useCaseClass,
        String useCaseId,
        int samplesPerConfig,
        long timeBudgetMs,
        long tokenBudget,
        String experimentId,
        int expiresInDays
) implements ExperimentConfig {

    @Override
    public ExperimentMode mode() {
        return ExperimentMode.EXPLORE;
    }

    /**
     * Returns the effective samples per config, using the mode default if not specified.
     *
     * @return the effective samples per configuration
     */
    public int effectiveSamplesPerConfig() {
        return mode().getEffectiveSampleSize(samplesPerConfig);
    }
}

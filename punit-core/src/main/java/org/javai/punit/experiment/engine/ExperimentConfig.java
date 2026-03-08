package org.javai.punit.experiment.engine;

import org.javai.punit.api.ExperimentMode;
import org.javai.punit.experiment.explore.ExploreConfig;
import org.javai.punit.experiment.measure.MeasureConfig;
import org.javai.punit.experiment.optimize.OptimizeConfig;

/**
 * Base interface for experiment configuration.
 *
 * <p>Each experiment mode provides its own implementation with mode-specific fields.
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link MeasureConfig} - Configuration for @MeasureExperiment</li>
 *   <li>{@link ExploreConfig} - Configuration for @ExploreExperiment</li>
 *   <li>{@link OptimizeConfig} - Configuration for @OptimizeExperiment</li>
 * </ul>
 *
 * <p>Note: This interface is not sealed because Java requires sealed interfaces
 * and their permitted implementations to be in the same package (without JPMS modules).
 * The implementations are nonetheless restricted to the three mode packages.
 */
public interface ExperimentConfig {

    /**
     * The experiment mode.
     *
     * @return the mode (MEASURE, EXPLORE, or OPTIMIZE)
     */
    ExperimentMode mode();

    /**
     * The use case class being tested.
     *
     * @return the use case class
     */
    Class<?> useCaseClass();

    /**
     * Resolved use case identifier.
     *
     * <p>Resolved from the use case class via {@code @UseCase} annotation
     * or simple class name.
     *
     * @return the use case ID
     */
    String useCaseId();

    /**
     * Time budget in milliseconds.
     *
     * <p>0 means unlimited.
     *
     * @return the time budget in milliseconds
     */
    long timeBudgetMs();

    /**
     * Token budget for LLM calls.
     *
     * <p>0 means unlimited.
     *
     * @return the token budget
     */
    long tokenBudget();

    /**
     * Experiment identifier for output naming.
     *
     * <p>Used in output file paths and reports.
     *
     * @return the experiment ID, or empty string if not specified
     */
    String experimentId();
}

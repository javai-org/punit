package org.javai.punit.experiment.spi;

import java.util.Optional;
import org.javai.punit.experiment.model.IterationFeedback;

/**
 * SPI for level refinement strategies in adaptive experiments.
 *
 * <p>Implementations are typically backend-specific.
 *
 * @param <T> the type of the level being refined
 */
public interface RefinementStrategy<T> {

	/**
	 * Generates a refined level based on feedback from the current iteration.
	 *
	 * @param currentLevel the current level that was used
	 * @param feedback the feedback from execution
	 * @return the refined level, or empty if no further refinement is possible
	 */
	Optional<T> refine(T currentLevel, IterationFeedback feedback);

	/**
	 * Returns a human-readable description of this strategy.
	 *
	 * @return the strategy description
	 */
	String description();
}


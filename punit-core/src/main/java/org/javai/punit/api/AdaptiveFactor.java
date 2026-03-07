package org.javai.punit.api;

import java.util.Optional;
import org.javai.punit.experiment.model.IterationFeedback;

/**
 * An adaptive factor generates levels dynamically based on execution feedback.
 *
 * <p>Each iteration produces a new level; iteration continues until:
 * <ul>
 *   <li>Acceptable results are achieved (goal met)</li>
 *   <li>Iteration budget is exhausted</li>
 *   <li>Refinement strategy signals no further improvement possible</li>
 * </ul>
 *
 * @param <T> the type of the factor level
 */
public interface AdaptiveFactor<T> {

	/**
	 * Returns the factor name (e.g., "systemPrompt").
	 *
	 * @return the factor name
	 */
	String name();

	/**
	 * Returns the initial level to start iteration.
	 *
	 * <p>May be sourced from production code via a Supplier.
	 *
	 * @return the initial level
	 */
	T initialLevel();

	/**
	 * Generates the next level based on feedback from previous execution.
	 *
	 * @param feedback the feedback from the previous iteration
	 * @return the refined level, or empty if no further refinement is possible
	 */
	Optional<T> refine(IterationFeedback feedback);

	/**
	 * Returns the maximum iterations allowed (safety bound).
	 *
	 * @return the maximum number of iterations
	 */
	int maxIterations();

	/**
	 * Returns the current level for this iteration.
	 *
	 * @return the current level
	 */
	T currentLevel();

	/**
	 * Sets the current level for the next iteration.
	 *
	 * @param level the new level
	 */
	void setCurrentLevel(T level);
}


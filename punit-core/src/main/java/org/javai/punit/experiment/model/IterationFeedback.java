package org.javai.punit.experiment.model;

import java.util.List;
import org.javai.punit.model.FailureObservation;

/**
 * Feedback from one iteration of an adaptive experiment.
 *
 * <p>Used to guide refinement of adaptive factor levels.
 */
public interface IterationFeedback {

	/**
	 * Returns the level used in this iteration.
	 *
	 * @return the level value
	 */
	Object level();

	/**
	 * Returns the aggregated results from this iteration's samples.
	 *
	 * @return the empirical summary
	 */
	EmpiricalSummary summary();

	/**
	 * Returns structured failure information for analysis.
	 *
	 * @return list of failure observations
	 */
	List<FailureObservation> failures();

	/**
	 * Returns the iteration number (0-indexed).
	 *
	 * @return the iteration number
	 */
	int iteration();
}


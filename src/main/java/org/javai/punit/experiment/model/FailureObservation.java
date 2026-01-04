package org.javai.punit.experiment.model;

import java.util.List;
import java.util.Optional;

/**
 * Observation of a single failure in an experiment, for refinement analysis.
 */
public interface FailureObservation {

	/**
	 * Returns the UseCaseResult from the failed sample.
	 *
	 * @return the result
	 */
	UseCaseResult result();

	/**
	 * Returns which success criteria were not met.
	 *
	 * @return list of unmet criteria descriptions
	 */
	List<String> unmetCriteria();

	/**
	 * Returns the exception if the failure was due to an error.
	 *
	 * @return optional containing the exception
	 */
	Optional<Throwable> exception();

	/**
	 * Returns the failure category for aggregation.
	 *
	 * @return the failure category
	 */
	String category();
}


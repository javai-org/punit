package org.javai.punit.spec.registry;

import java.util.Optional;
import org.javai.punit.spec.model.ExecutionSpecification;

/**
 * Abstraction for resolving execution specifications by ID.
 *
 * <p>Implementations may resolve specs from the filesystem, classpath,
 * environment-local directories, or layered combinations thereof.
 *
 * @see SpecificationRegistry
 * @see LayeredSpecRepository
 */
public interface SpecRepository {

	/**
	 * Resolves a specification by its ID.
	 *
	 * @param specId the specification ID (e.g., use case ID, or dimension-qualified ID like "MyUseCase.latency")
	 * @return the specification if found, or empty if not available in this repository
	 */
	Optional<ExecutionSpecification> resolve(String specId);
}

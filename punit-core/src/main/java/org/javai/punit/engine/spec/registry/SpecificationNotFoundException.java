package org.javai.punit.engine.spec.registry;

/**
 * Exception thrown when a specification cannot be found.
 */
public class SpecificationNotFoundException extends RuntimeException {

	public SpecificationNotFoundException(String message) {
		super(message);
	}

	public SpecificationNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}
}


package org.javai.punit.spec.registry;

/**
 * Exception thrown when a specification cannot be loaded.
 */
public class SpecificationLoadException extends RuntimeException {

	public SpecificationLoadException(String message) {
		super(message);
	}

	public SpecificationLoadException(String message, Throwable cause) {
		super(message, cause);
	}
}


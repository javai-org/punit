package org.javai.punit.spec.model;

/**
 * Exception thrown when specification validation fails.
 */
public class SpecificationValidationException extends RuntimeException {

	public SpecificationValidationException(String message) {
		super(message);
	}

	public SpecificationValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}


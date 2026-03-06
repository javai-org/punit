package org.javai.punit.ptest.engine;

import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Exception thrown when a probabilistic test is misconfigured.
 *
 * <p>This exception is specifically designed to provide developer-friendly
 * error messages that explain:
 * <ul>
 *   <li>What went wrong</li>
 *   <li>Why it matters</li>
 *   <li>How to fix it</li>
 * </ul>
 *
 * <p>Error messages include formatted examples and clear guidance on
 * the three operational approaches.
 *
 * @see OperationalApproachResolver
 */
public class ProbabilisticTestConfigurationException extends ExtensionConfigurationException {

    /**
     * Constructs a new configuration exception with a detailed message.
     *
     * @param message The detailed error message
     */
    public ProbabilisticTestConfigurationException(String message) {
        super(message);
    }

    /**
     * Constructs a new configuration exception with a message and cause.
     *
     * @param message The detailed error message
     * @param cause The underlying cause
     */
    public ProbabilisticTestConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}


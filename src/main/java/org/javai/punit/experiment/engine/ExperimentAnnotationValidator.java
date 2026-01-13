package org.javai.punit.experiment.engine;

import org.javai.punit.api.Experiment;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Validates {@link Experiment} annotation attributes.
 *
 * <p>Performs validation of annotation attributes that cannot be enforced
 * by the annotation processor, such as range constraints.
 */
public final class ExperimentAnnotationValidator {

    private ExperimentAnnotationValidator() {
        // Utility class
    }

    /**
     * Validates the experiment annotation attributes.
     *
     * @param annotation the experiment annotation to validate
     * @param methodName the name of the experiment method (for error messages)
     * @throws ExtensionConfigurationException if validation fails
     */
    public static void validate(Experiment annotation, String methodName) {
        validateExpiresInDays(annotation, methodName);
    }

    /**
     * Validates the expiresInDays attribute.
     *
     * @param annotation the experiment annotation
     * @param methodName the method name for error messages
     * @throws ExtensionConfigurationException if expiresInDays is negative
     */
    private static void validateExpiresInDays(Experiment annotation, String methodName) {
        if (annotation.expiresInDays() < 0) {
            throw new ExtensionConfigurationException(
                "Experiment method '" + methodName + "' has invalid expiresInDays: " +
                annotation.expiresInDays() + ". Value must be non-negative (0 = no expiration).");
        }
    }
}


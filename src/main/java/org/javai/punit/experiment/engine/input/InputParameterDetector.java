package org.javai.punit.experiment.engine.input;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.javai.punit.api.Factor;
import org.javai.punit.api.Input;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.TokenChargeRecorder;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Detects the input parameter type from method parameters for @InputSource injection.
 *
 * <p>Detection order:
 * <ol>
 *   <li>First, look for an explicit @Input annotation</li>
 *   <li>If not found, auto-detect by excluding framework types</li>
 * </ol>
 *
 * <p>Auto-detection excludes:
 * <ul>
 *   <li>{@link OutcomeCaptor} - handled by CaptorParameterResolver</li>
 *   <li>{@link Factor @Factor}-annotated parameters - handled by FactorParameterResolver</li>
 *   <li>UseCase types - handled by UseCaseProvider (detected by naming convention)</li>
 *   <li>{@link TokenChargeRecorder} - handled by TokenChargeRecorderParameterResolver</li>
 * </ul>
 */
public final class InputParameterDetector {

    private InputParameterDetector() {
        // Utility class
    }

    /**
     * Finds the input parameter type from method parameters.
     *
     * @param method the method to inspect
     * @return the input parameter type
     * @throws ExtensionConfigurationException if no suitable parameter is found
     */
    public static Class<?> findInputParameterType(Method method) {
        // First, look for explicit @Input annotation
        for (Parameter param : method.getParameters()) {
            if (param.isAnnotationPresent(Input.class)) {
                return param.getType();
            }
        }

        // Auto-detect by excluding framework types
        for (Parameter param : method.getParameters()) {
            Class<?> type = param.getType();

            if (shouldSkipParameter(param, type)) {
                continue;
            }

            return type;
        }

        throw new ExtensionConfigurationException(
                "@InputSource requires a method parameter to inject the input value. " +
                "Mark the parameter with @Input or ensure the parameter is not a framework type " +
                "(OutcomeCaptor, UseCase, TokenChargeRecorder) or @Factor-annotated.");
    }

    /**
     * Checks if a parameter should be considered as an input parameter candidate.
     *
     * @param param the parameter
     * @param type the parameter type
     * @return true if the parameter could receive input injection
     */
    public static boolean isInputParameterCandidate(Parameter param, Class<?> type) {
        // Explicit @Input annotation always wins
        if (param.isAnnotationPresent(Input.class)) {
            return true;
        }

        return !shouldSkipParameter(param, type);
    }

    /**
     * Checks if a parameter type is a UseCase type by naming convention.
     *
     * @param type the type to check
     * @return true if the type appears to be a UseCase
     */
    public static boolean isUseCaseType(Class<?> type) {
        String packageName = type.getPackageName();
        String simpleName = type.getSimpleName();

        return packageName.contains("usecase") ||
               packageName.contains("usecases") ||
               simpleName.endsWith("UseCase");
    }

    private static boolean shouldSkipParameter(Parameter param, Class<?> type) {
        // Skip OutcomeCaptor - handled by CaptorParameterResolver
        if (type == OutcomeCaptor.class) {
            return true;
        }

        // Skip @Factor-annotated parameters - handled by FactorParameterResolver
        if (param.isAnnotationPresent(Factor.class)) {
            return true;
        }

        // Skip UseCase types - handled by UseCaseProvider
        if (isUseCaseType(type)) {
            return true;
        }

        // Skip TokenChargeRecorder - handled by TokenChargeRecorderParameterResolver
        if (TokenChargeRecorder.class.isAssignableFrom(type)) {
            return true;
        }

        return false;
    }
}

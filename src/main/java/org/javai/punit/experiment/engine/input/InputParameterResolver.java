package org.javai.punit.experiment.engine.input;

import java.lang.reflect.Parameter;

import org.javai.punit.api.Input;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Parameter resolver that provides input values for experiment and test methods.
 *
 * <p>Resolves parameters that:
 * <ul>
 *   <li>Are annotated with @Input (explicit marking)</li>
 *   <li>Or match the expected input type and are valid input candidates</li>
 * </ul>
 *
 * <p>This resolver is used when a method is annotated with {@code @InputSource}
 * to inject the current input value for each invocation.
 *
 * @see Input
 * @see InputParameterDetector
 */
public class InputParameterResolver implements ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create("org.javai.punit.experiment");

    private final Object inputValue;
    private final Class<?> inputType;

    public InputParameterResolver(Object inputValue, Class<?> inputType) {
        this.inputValue = inputValue;
        this.inputType = inputType;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Parameter param = parameterContext.getParameter();
        Class<?> paramType = param.getType();

        // Explicit @Input annotation always matches if type is compatible
        if (param.isAnnotationPresent(Input.class)) {
            return paramType.isAssignableFrom(inputType);
        }

        // Check if this is a valid input parameter candidate
        if (!InputParameterDetector.isInputParameterCandidate(param, paramType)) {
            return false;
        }

        // Support if parameter type matches the input type
        return paramType.isAssignableFrom(inputType);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext)
            throws ParameterResolutionException {
        // Store in the extension context so the interceptor can access it
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        store.put("inputValue", inputValue);
        return inputValue;
    }
}

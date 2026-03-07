package org.javai.punit.experiment.optimize;

import org.javai.punit.api.ControlFactor;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Parameter resolver for {@link ControlFactor}-annotated parameters in OPTIMIZE experiments.
 *
 * <p>Resolves the current control factor value for injection into the experiment method.
 * The value is the mutated control factor for the current iteration.
 *
 * <h2>Single Factor (Default)</h2>
 * <p>When there is only one control factor, the annotation value is optional:
 * <pre>{@code
 * @ControlFactor String currentPrompt  // Factor name inferred
 * }</pre>
 *
 * <h2>Multiple Factors (Future)</h2>
 * <p>For multi-factor optimization, specify the factor name:
 * <pre>{@code
 * @ControlFactor("systemPrompt") String prompt,
 * @ControlFactor("temperature") double temp
 * }</pre>
 */
public class ControlFactorParameterResolver implements ParameterResolver {

    private final Object controlFactorValue;
    private final String controlFactorName;

    public ControlFactorParameterResolver(Object controlFactorValue, String controlFactorName) {
        this.controlFactorValue = controlFactorValue;
        this.controlFactorName = controlFactorName;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.isAnnotated(ControlFactor.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext)
            throws ParameterResolutionException {
        ControlFactor annotation = parameterContext.findAnnotation(ControlFactor.class)
                .orElseThrow(() -> new ParameterResolutionException(
                        "Parameter must be annotated with @ControlFactor"));

        // If annotation specifies a factor name, validate it matches (for future multi-factor support)
        String requestedFactor = annotation.value();
        if (!requestedFactor.isEmpty() && !requestedFactor.equals(controlFactorName)) {
            throw new ParameterResolutionException(
                    "Requested control factor '" + requestedFactor +
                            "' does not match current control factor '" + controlFactorName + "'");
        }

        return controlFactorValue;
    }
}

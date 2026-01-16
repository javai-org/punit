package org.javai.punit.experiment.optimize;

import org.javai.punit.api.TreatmentValue;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Parameter resolver for {@link TreatmentValue}-annotated parameters in OPTIMIZE experiments.
 *
 * <p>Resolves the current treatment factor value for injection into the experiment method.
 * The value is the mutated treatment factor for the current iteration.
 *
 * <h2>Single Factor (Default)</h2>
 * <p>When there is only one treatment factor, the annotation value is optional:
 * <pre>{@code
 * @TreatmentValue String initialPrompt  // Factor name inferred
 * }</pre>
 *
 * <h2>Multiple Factors (Future)</h2>
 * <p>For multi-factor optimization, specify the factor name:
 * <pre>{@code
 * @TreatmentValue("systemPrompt") String prompt,
 * @TreatmentValue("temperature") double temp
 * }</pre>
 */
public class TreatmentValueParameterResolver implements ParameterResolver {

    private final Object treatmentValue;
    private final String treatmentFactorName;

    public TreatmentValueParameterResolver(Object treatmentValue, String treatmentFactorName) {
        this.treatmentValue = treatmentValue;
        this.treatmentFactorName = treatmentFactorName;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.isAnnotated(TreatmentValue.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext)
            throws ParameterResolutionException {
        TreatmentValue annotation = parameterContext.findAnnotation(TreatmentValue.class)
                .orElseThrow(() -> new ParameterResolutionException(
                        "Parameter must be annotated with @TreatmentValue"));

        // If annotation specifies a factor name, validate it matches (for future multi-factor support)
        String requestedFactor = annotation.value();
        if (!requestedFactor.isEmpty() && !requestedFactor.equals(treatmentFactorName)) {
            throw new ParameterResolutionException(
                    "Requested treatment factor '" + requestedFactor +
                            "' does not match current treatment factor '" + treatmentFactorName + "'");
        }

        return treatmentValue;
    }
}

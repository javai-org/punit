package org.javai.punit.experiment.optimize;

import org.javai.punit.api.ResultCaptor;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Parameter resolver that provides the {@link ResultCaptor} for OPTIMIZE mode invocations.
 *
 * <p>Stores optimization metadata in the extension context store for access by the interceptor:
 * <ul>
 *   <li>{@code captor} - the ResultCaptor instance</li>
 *   <li>{@code iterationNumber} - the current iteration (0-indexed)</li>
 *   <li>{@code sampleInIteration} - the sample number within the iteration (1-indexed)</li>
 *   <li>{@code samplesPerIteration} - total samples per iteration</li>
 *   <li>{@code treatmentValue} - the current treatment factor value</li>
 *   <li>{@code treatmentFactorName} - the name of the treatment factor</li>
 * </ul>
 */
public class OptimizeCaptorParameterResolver implements ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create("org.javai.punit.experiment");

    private final OptimizeInvocationContext context;

    public OptimizeCaptorParameterResolver(OptimizeInvocationContext context) {
        this.context = context;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == ResultCaptor.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext)
            throws ParameterResolutionException {
        // Store in the extension context so the interceptor can access it
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        store.put("captor", context.captor());
        store.put("iterationNumber", context.iterationNumber());
        store.put("sampleInIteration", context.sampleInIteration());
        store.put("samplesPerIteration", context.samplesPerIteration());
        store.put("treatmentValue", context.treatmentValue());
        store.put("treatmentFactorName", context.treatmentFactorName());
        return context.captor();
    }
}

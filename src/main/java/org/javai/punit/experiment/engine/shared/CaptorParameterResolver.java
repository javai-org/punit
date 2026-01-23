package org.javai.punit.experiment.engine.shared;

import org.javai.punit.api.OutcomeCaptor;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Parameter resolver that provides the {@link OutcomeCaptor} for the current invocation.
 *
 * <p>Also stores metadata in the extension context store for access by the interceptor:
 * <ul>
 *   <li>{@code captor} - the OutcomeCaptor instance</li>
 *   <li>{@code configName} - the configuration name (for EXPLORE mode)</li>
 *   <li>{@code sampleInConfig} - the sample number within the configuration</li>
 *   <li>{@code factorValues} - the factor values (if present)</li>
 * </ul>
 */
public class CaptorParameterResolver implements ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create("org.javai.punit.experiment");

    private final OutcomeCaptor captor;
    private final String configName;
    private final int sampleInConfig;
    private final Object[] factorValues;

    public CaptorParameterResolver(OutcomeCaptor captor, String configName, int sampleInConfig) {
        this(captor, configName, sampleInConfig, null);
    }

    public CaptorParameterResolver(OutcomeCaptor captor, String configName,
                                   int sampleInConfig, Object[] factorValues) {
        this.captor = captor;
        this.configName = configName;
        this.sampleInConfig = sampleInConfig;
        this.factorValues = factorValues;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == OutcomeCaptor.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext)
            throws ParameterResolutionException {
        // Store in the extension context so the interceptor can access it
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        store.put("captor", captor);
        if (configName != null) {
            store.put("configName", configName);
            store.put("sampleInConfig", sampleInConfig);
        }
        if (factorValues != null) {
            store.put("factorValues", factorValues);
        }
        return captor;
    }
}

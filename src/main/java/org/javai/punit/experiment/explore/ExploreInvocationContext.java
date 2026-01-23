package org.javai.punit.experiment.explore;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.experiment.engine.shared.CaptorParameterResolver;
import org.javai.punit.experiment.engine.shared.FactorInfo;
import org.javai.punit.experiment.engine.shared.FactorParameterResolver;
import org.javai.punit.experiment.engine.shared.FactorValuesInitializer;
import org.javai.punit.experiment.engine.shared.FactorValuesResolver;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @ExploreExperiment (multiple configurations).
 *
 * <p>Each invocation represents a single sample within a specific factor configuration.
 * EXPLORE mode generates one spec file per configuration.
 */
public record ExploreInvocationContext(
        int sampleInConfig,
        int samplesPerConfig,
        int configIndex,
        int totalConfigs,
        String useCaseId,
        String configName,
        Object[] factorValues,
        List<FactorInfo> factorInfos,
        OutcomeCaptor captor
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return String.format("[%s] config %d/%d (%s) sample %d/%d",
                useCaseId, configIndex, totalConfigs, configName, sampleInConfig, samplesPerConfig);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        List<Extension> extensions = new ArrayList<>();
        // IMPORTANT: FactorValuesInitializer must be first to set factor values
        // on UseCaseProvider BEFORE any parameter resolution happens
        extensions.add(new FactorValuesInitializer(factorValues, factorInfos));
        extensions.add(new CaptorParameterResolver(captor, configName, sampleInConfig, factorValues));
        extensions.add(new FactorParameterResolver(factorValues, factorInfos));
        extensions.add(new FactorValuesResolver(factorValues, factorInfos));
        return extensions;
    }
}

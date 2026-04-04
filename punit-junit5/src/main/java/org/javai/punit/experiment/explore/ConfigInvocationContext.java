package org.javai.punit.experiment.explore;

import java.util.List;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.experiment.engine.shared.CaptorParameterResolver;
import org.javai.punit.experiment.engine.shared.ConfigInstanceInitializer;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @ExploreExperiment with @ConfigSource.
 *
 * <p>Each invocation represents a single sample within a named configuration.
 * The configuration is a pre-built, immutable use case instance — no factor
 * values or factor infos are needed.
 */
public record ConfigInvocationContext(
        int sampleInConfig,
        int samplesPerConfig,
        int configIndex,
        int totalConfigs,
        String useCaseId,
        String configName,
        Object configInstance,
        OutcomeCaptor captor
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return String.format("[%s] config %d/%d (%s) sample %d/%d",
                useCaseId, configIndex, totalConfigs, configName, sampleInConfig, samplesPerConfig);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(
                new ConfigInstanceInitializer(configInstance),
                new CaptorParameterResolver(captor, configName, sampleInConfig)
        );
    }
}

package org.javai.punit.experiment.explore;

import java.util.List;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.experiment.engine.input.InputParameterResolver;
import org.javai.punit.experiment.engine.shared.CaptorParameterResolver;
import org.javai.punit.experiment.engine.shared.ConfigInstanceInitializer;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @ExploreExperiment with both @ConfigSource and @InputSource.
 *
 * <p>Each invocation represents a single sample within a named configuration,
 * with an input value cycled via round-robin. Combines {@link ConfigInstanceInitializer}
 * for use case injection with {@link InputParameterResolver} for input injection.
 */
public record ConfigWithInputsInvocationContext(
        int sampleInConfig,
        int samplesPerConfig,
        int configIndex,
        int totalConfigs,
        String useCaseId,
        String configName,
        Object configInstance,
        OutcomeCaptor captor,
        Object inputValue,
        Class<?> inputType,
        int inputIndex,
        int totalInputs
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        String inputLabel = inputValue != null ? truncate(inputValue.toString(), 30) : "null";
        return String.format("[%s] config %d/%d (%s) sample %d/%d (input %d/%d: %s)",
                useCaseId, configIndex, totalConfigs, configName,
                sampleInConfig, samplesPerConfig,
                inputIndex + 1, totalInputs, inputLabel);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(
                new ConfigInstanceInitializer(configInstance),
                new CaptorParameterResolver(captor, configName, sampleInConfig),
                new InputParameterResolver(inputValue, inputType)
        );
    }

    private static String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength - 3) + "...";
    }
}

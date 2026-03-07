package org.javai.punit.experiment.explore;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.experiment.engine.input.InputParameterResolver;
import org.javai.punit.experiment.engine.shared.CaptorParameterResolver;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @ExploreExperiment with input source.
 *
 * <p>Each input becomes a configuration, and multiple samples are run per input.
 * EXPLORE mode generates one spec file per input configuration.
 */
public record ExploreWithInputsInvocationContext(
        int sampleInConfig,
        int samplesPerConfig,
        int inputIndex,
        int totalInputs,
        String useCaseId,
        String configName,
        Object inputValue,
        Class<?> inputType,
        OutcomeCaptor captor
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return String.format("[%s] input %d/%d (%s) sample %d/%d",
                useCaseId, inputIndex + 1, totalInputs, configName, sampleInConfig, samplesPerConfig);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        List<Extension> extensions = new ArrayList<>();
        extensions.add(new CaptorParameterResolver(captor, configName, sampleInConfig));
        extensions.add(new InputParameterResolver(inputValue, inputType));
        return extensions;
    }
}

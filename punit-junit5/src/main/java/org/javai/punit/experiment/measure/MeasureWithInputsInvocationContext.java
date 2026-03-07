package org.javai.punit.experiment.measure;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.experiment.engine.input.InputParameterResolver;
import org.javai.punit.experiment.engine.shared.CaptorParameterResolver;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @MeasureExperiment with input source (cycling).
 *
 * <p>Provides input values that cycle through the input source entries.
 * With samples=1000 and 10 inputs, each input is used ~100 times.
 */
public record MeasureWithInputsInvocationContext(
        int sampleNumber,
        int totalSamples,
        String useCaseId,
        OutcomeCaptor captor,
        Object inputValue,
        Class<?> inputType,
        int inputIndex,
        int totalInputs
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        String inputLabel = inputValue != null ? truncate(inputValue.toString(), 30) : "null";
        return String.format("[%s] sample %d/%d (input %d/%d: %s)",
                useCaseId, sampleNumber, totalSamples, inputIndex + 1, totalInputs, inputLabel);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        List<Extension> extensions = new ArrayList<>();
        extensions.add(new CaptorParameterResolver(captor, null, sampleNumber));
        extensions.add(new InputParameterResolver(inputValue, inputType));
        return extensions;
    }

    private static String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength - 3) + "...";
    }
}

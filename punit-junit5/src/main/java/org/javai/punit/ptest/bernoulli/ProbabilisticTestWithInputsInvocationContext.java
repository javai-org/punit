package org.javai.punit.ptest.bernoulli;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.controls.budget.DefaultTokenChargeRecorder;
import org.javai.punit.experiment.engine.input.InputParameterResolver;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @ProbabilisticTest with input source (cycling).
 *
 * <p>Provides input values that cycle through the input source entries.
 * With samples=100 and 10 inputs, each input is used 10 times.
 */
public record ProbabilisticTestWithInputsInvocationContext(
        int sampleNumber,
        int totalSamples,
        DefaultTokenChargeRecorder tokenRecorder,
        Object inputValue,
        Class<?> inputType,
        int inputIndex,
        int totalInputs
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        String inputLabel = inputValue != null ? truncate(inputValue.toString(), 30) : "null";
        return String.format("sample %d/%d (input %d/%d: %s)",
                sampleNumber, totalSamples, inputIndex + 1, totalInputs, inputLabel);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        List<Extension> extensions = new ArrayList<>();
        extensions.add(new InputParameterResolver(inputValue, inputType));
        if (tokenRecorder != null) {
            extensions.add(new TokenRecorderResolver(tokenRecorder));
        }
        return extensions;
    }

    private static String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength - 3) + "...";
    }
}

/**
 * ParameterResolver for injecting TokenChargeRecorder into test methods.
 */
record TokenRecorderResolver(TokenChargeRecorder recorder) implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return TokenChargeRecorder.class.isAssignableFrom(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return recorder;
    }
}

package org.javai.punit.ptest.engine;

import java.util.Collections;
import java.util.List;

import org.javai.punit.api.TokenChargeRecorder;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for a single sample execution in a probabilistic test.
 *
 * <p>This context provides:
 * <ul>
 *   <li>A display name showing the sample index (e.g., "Sample 1/100")</li>
 *   <li>Optional TokenChargeRecorder injection for dynamic token tracking</li>
 * </ul>
 *
 * <p>Package-private: internal implementation detail of the test extension.
 *
 * @param sampleIndex The 1-based index of this sample
 * @param totalSamples The total number of samples to be executed
 * @param tokenRecorder The token recorder for this sample (null if not using dynamic tokens)
 */
record ProbabilisticTestInvocationContext(
        int sampleIndex,
        int totalSamples,
        DefaultTokenChargeRecorder tokenRecorder) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return String.format("Sample %d/%d", sampleIndex, totalSamples);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        if (tokenRecorder != null) {
            return List.of(new TokenChargeRecorderParameterResolver(tokenRecorder));
        }
        return Collections.emptyList();
    }
}

/**
 * ParameterResolver for injecting TokenChargeRecorder into test methods.
 *
 * <p>This resolver enables test methods to receive a {@link TokenChargeRecorder}
 * parameter for recording token consumption during sample execution.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 *
 * @param recorder The token charge recorder to inject
 */
record TokenChargeRecorderParameterResolver(TokenChargeRecorder recorder) implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return TokenChargeRecorder.class.isAssignableFrom(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return recorder;
    }
}


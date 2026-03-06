package org.javai.punit.experiment.optimize;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.engine.input.InputParameterResolver;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @OptimizeExperiment with @InputSource.
 *
 * <p>Represents a single sample execution within an optimization iteration,
 * with an input value from the input source. Each invocation receives:
 * <ul>
 *   <li>A fresh {@link OutcomeCaptor}</li>
 *   <li>The current control factor value</li>
 *   <li>An input value (cycling through inputs)</li>
 * </ul>
 *
 * <p>The display name shows iteration, sample, and input:
 * {@code [useCaseId] iteration 1, sample 5/20 (input 5/10)}
 */
public record OptimizeWithInputsInvocationContext(
        int iterationNumber,
        int sampleInIteration,
        int samplesPerIteration,
        int maxIterations,
        String useCaseId,
        Object treatmentValue,
        String controlFactorName,
        OutcomeCaptor captor,
        Object inputValue,
        Class<?> inputType,
        int inputIndex,
        int totalInputs
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        String inputLabel = inputValue != null ? truncate(inputValue.toString(), 25) : "null";
        return String.format("[%s] iteration %d, sample %d/%d (input %d/%d: %s)",
                useCaseId, iterationNumber + 1, sampleInIteration, samplesPerIteration,
                inputIndex + 1, totalInputs, inputLabel);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        List<Extension> extensions = new ArrayList<>();

        // IMPORTANT: ControlFactorInitializer must be first to set factor value
        // before the use case is instantiated via parameter resolution
        extensions.add(new ControlFactorInitializer(treatmentValue, controlFactorName));

        extensions.add(new OptimizeCaptorParameterResolver(
                new OptimizeInvocationContext(
                        iterationNumber, sampleInIteration, samplesPerIteration,
                        maxIterations, useCaseId, treatmentValue, controlFactorName, captor)));
        extensions.add(new ControlFactorParameterResolver(treatmentValue, controlFactorName));
        extensions.add(new InputParameterResolver(inputValue, inputType));

        return extensions;
    }

    private static String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength - 3) + "...";
    }

    /**
     * Initializes the control factor on UseCaseProvider BEFORE parameter resolution.
     */
    private record ControlFactorInitializer(
            Object controlFactorValue,
            String controlFactorName
    ) implements BeforeEachCallback, AfterEachCallback {

        @Override
        public void beforeEach(ExtensionContext context) {
            findProvider(context).ifPresent(provider ->
                    provider.setCurrentFactorValues(
                            new Object[]{controlFactorValue},
                            List.of(controlFactorName)
                    )
            );
        }

        @Override
        public void afterEach(ExtensionContext context) {
            findProvider(context).ifPresent(UseCaseProvider::clearCurrentFactorValues);
        }

        private Optional<UseCaseProvider> findProvider(ExtensionContext context) {
            Object testInstance = context.getRequiredTestInstance();
            for (java.lang.reflect.Field field : testInstance.getClass().getDeclaredFields()) {
                if (UseCaseProvider.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        return Optional.of((UseCaseProvider) field.get(testInstance));
                    } catch (IllegalAccessException e) {
                        // Continue searching
                    }
                }
            }
            return Optional.empty();
        }
    }
}

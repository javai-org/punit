package org.javai.punit.experiment.optimize;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @OptimizeExperiment.
 *
 * <p>Represents a single sample execution within an optimization iteration.
 * Each invocation receives a fresh {@link OutcomeCaptor} and the current
 * treatment factor value.
 *
 * <p>The display name shows both the iteration and sample numbers:
 * {@code [useCaseId] iteration 1/20, sample 5/20}
 */
public record OptimizeInvocationContext(
        int iterationNumber,
        int sampleInIteration,
        int samplesPerIteration,
        int maxIterations,
        String useCaseId,
        Object treatmentValue,
        String controlFactorName,
        OutcomeCaptor captor
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return String.format("[%s] iteration %d, sample %d/%d",
                useCaseId, iterationNumber + 1, sampleInIteration, samplesPerIteration);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        List<Extension> extensions = new ArrayList<>();

        // IMPORTANT: ControlFactorInitializer must be first to set factor value
        // before the use case is instantiated via parameter resolution
        extensions.add(new ControlFactorInitializer(treatmentValue, controlFactorName));

        extensions.add(new OptimizeCaptorParameterResolver(this));
        extensions.add(new ControlFactorParameterResolver(treatmentValue, controlFactorName));

        return extensions;
    }

    /**
     * Initializes the control factor on UseCaseProvider BEFORE parameter resolution.
     *
     * <p>This ensures @FactorSetter methods are invoked when the use case is created,
     * allowing the control factor value to be applied to the use case instance.
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

package org.javai.punit.experiment.optimize;

import java.util.List;
import org.javai.punit.api.ResultCaptor;
import org.javai.punit.experiment.engine.shared.CaptorParameterResolver;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @OptimizeExperiment.
 *
 * <p>Represents a single sample execution within an optimization iteration.
 * Each invocation receives a fresh {@link ResultCaptor} and the current
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
        String treatmentFactorName,
        ResultCaptor captor
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return String.format("[%s] iteration %d, sample %d/%d",
                useCaseId, iterationNumber + 1, sampleInIteration, samplesPerIteration);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(
                new OptimizeCaptorParameterResolver(this),
                new TreatmentValueParameterResolver(treatmentValue, treatmentFactorName)
        );
    }
}

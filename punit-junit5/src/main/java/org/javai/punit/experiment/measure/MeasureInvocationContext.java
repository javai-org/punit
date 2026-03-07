package org.javai.punit.experiment.measure;

import java.util.List;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.experiment.engine.shared.CaptorParameterResolver;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @MeasureExperiment without factors.
 *
 * <p>Represents a single sample execution in MEASURE mode when no @FactorSource
 * is present. Each invocation receives a fresh {@link OutcomeCaptor}.
 */
public record MeasureInvocationContext(
        int sampleNumber,
        int totalSamples,
        String useCaseId,
        OutcomeCaptor captor
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return String.format("[%s] sample %d/%d", useCaseId, sampleNumber, totalSamples);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(new CaptorParameterResolver(captor, null, sampleNumber));
    }
}

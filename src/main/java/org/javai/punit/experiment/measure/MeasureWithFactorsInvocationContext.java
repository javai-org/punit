package org.javai.punit.experiment.measure;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.experiment.engine.shared.CaptorParameterResolver;
import org.javai.punit.experiment.engine.shared.FactorInfo;
import org.javai.punit.experiment.engine.shared.FactorParameterResolver;
import org.javai.punit.experiment.engine.shared.FactorValuesResolver;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Invocation context for @MeasureExperiment with factor source (cycling).
 *
 * <p>Provides factor values that cycle through the factor source entries.
 * With samples=1000 and 10 factor entries, each factor is used ~100 times.
 */
public record MeasureWithFactorsInvocationContext(
        int sampleNumber,
        int totalSamples,
        String useCaseId,
        OutcomeCaptor captor,
        Object[] factorValues,
        List<FactorInfo> factorInfos
) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return String.format("[%s] sample %d/%d", useCaseId, sampleNumber, totalSamples);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        List<Extension> extensions = new ArrayList<>();
        extensions.add(new CaptorParameterResolver(captor, null, sampleNumber, factorValues));
        extensions.add(new FactorParameterResolver(factorValues, factorInfos));
        extensions.add(new FactorValuesResolver(factorValues, factorInfos));
        return extensions;
    }
}

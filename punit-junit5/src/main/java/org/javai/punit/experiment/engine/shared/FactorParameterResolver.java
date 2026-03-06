package org.javai.punit.experiment.engine.shared;

import java.util.List;
import org.javai.punit.api.Factor;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Parameter resolver that provides factor values for @Factor-annotated parameters.
 *
 * <p>Matches factor values to parameters by the factor name specified in the
 * @Factor annotation.
 */
public class FactorParameterResolver implements ParameterResolver {

    private final Object[] factorValues;
    private final List<FactorInfo> factorInfos;

    public FactorParameterResolver(Object[] factorValues, List<FactorInfo> factorInfos) {
        this.factorValues = factorValues;
        this.factorInfos = factorInfos;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().isAnnotationPresent(Factor.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Factor factor = parameterContext.getParameter().getAnnotation(Factor.class);
        String factorName = factor.value();

        // Find the matching factor value
        for (int i = 0; i < factorInfos.size(); i++) {
            if (factorInfos.get(i).name().equals(factorName)) {
                return factorValues[i];
            }
        }

        throw new ParameterResolutionException(
                "No factor value found for @Factor(\"" + factorName + "\")");
    }
}

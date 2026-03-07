package org.javai.punit.experiment.engine.shared;

import java.util.List;
import org.javai.punit.api.FactorValues;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Parameter resolver that provides {@link FactorValues} for accessing factor data by name.
 *
 * <p>FactorValues provides a map-like interface to access factor values during test execution.
 */
public class FactorValuesResolver implements ParameterResolver {

    private final Object[] factorValues;
    private final List<FactorInfo> factorInfos;

    public FactorValuesResolver(Object[] factorValues, List<FactorInfo> factorInfos) {
        this.factorValues = factorValues;
        this.factorInfos = factorInfos;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == FactorValues.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {
        List<String> names = factorInfos.stream()
                .map(FactorInfo::name)
                .toList();
        return new FactorValues(factorValues, names);
    }
}

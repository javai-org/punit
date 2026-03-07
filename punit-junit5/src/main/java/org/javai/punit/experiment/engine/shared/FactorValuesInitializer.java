package org.javai.punit.experiment.engine.shared;

import java.util.List;
import java.util.Optional;
import org.javai.punit.usecase.UseCaseFactory;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Initializes factor values on the UseCaseFactory BEFORE parameter resolution
 * and clears them AFTER the test completes.
 *
 * <p>This is critical for auto-wired use case injection: the factory needs to know
 * the current factor values when resolving the use case parameter, which happens
 * before the test method is invoked (and before interceptTestTemplateMethod).
 */
public class FactorValuesInitializer implements BeforeEachCallback, AfterEachCallback {

    private final Object[] factorValues;
    private final List<FactorInfo> factorInfos;

    public FactorValuesInitializer(Object[] factorValues, List<FactorInfo> factorInfos) {
        this.factorValues = factorValues;
        this.factorInfos = factorInfos;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        findFactory(context).ifPresent(factory -> {
            if (factorValues != null) {
                List<String> factorNames = factorInfos.stream()
                        .map(FactorInfo::name)
                        .toList();
                factory.setCurrentFactorValues(factorValues, factorNames);
            }
        });
    }

    @Override
    public void afterEach(ExtensionContext context) {
        findFactory(context).ifPresent(UseCaseFactory::clearCurrentFactorValues);
    }

    private Optional<UseCaseFactory> findFactory(ExtensionContext context) {
        Object testInstance = context.getRequiredTestInstance();
        for (java.lang.reflect.Field field : testInstance.getClass().getDeclaredFields()) {
            if (UseCaseFactory.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                try {
                    return Optional.of((UseCaseFactory) field.get(testInstance));
                } catch (IllegalAccessException e) {
                    // Continue searching
                }
            }
        }
        return Optional.empty();
    }
}

package org.javai.punit.experiment.engine.shared;

import java.util.Optional;
import org.javai.punit.api.UseCaseProvider;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Sets a named config instance on the UseCaseProvider BEFORE parameter resolution
 * and clears it AFTER the test completes.
 *
 * <p>This is the config-mode equivalent of {@link FactorValuesInitializer}. Instead
 * of setting factor values that drive a factory, it sets a pre-built use case instance
 * directly.
 */
public class ConfigInstanceInitializer implements BeforeEachCallback, AfterEachCallback {

    private final Object configInstance;

    public ConfigInstanceInitializer(Object configInstance) {
        this.configInstance = configInstance;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        findProvider(context).ifPresent(provider ->
                provider.setCurrentConfigInstance(configInstance));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        findProvider(context).ifPresent(UseCaseProvider::clearCurrentConfigInstance);
    }

    private Optional<UseCaseProvider> findProvider(ExtensionContext context) {
        Object testInstance = context.getRequiredTestInstance();
        Class<?> clazz = testInstance.getClass();
        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (field.isSynthetic()) continue;
                if (UseCaseProvider.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        return Optional.of((UseCaseProvider) field.get(testInstance));
                    } catch (IllegalAccessException e) {
                        // Continue searching
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return Optional.empty();
    }
}

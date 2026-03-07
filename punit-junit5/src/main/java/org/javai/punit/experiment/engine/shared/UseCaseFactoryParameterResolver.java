package org.javai.punit.experiment.engine.shared;

import java.lang.reflect.Field;
import org.javai.punit.usecase.UseCaseFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Resolves use case parameters from a {@link UseCaseFactory} field on the test instance.
 *
 * <p>This resolver enables the DD-06 inheritance model where a JUnit test class
 * inherits from a {@code @Sentinel} reliability specification that uses a plain
 * {@code UseCaseFactory} (not a {@code UseCaseProvider}). Without this resolver,
 * use case parameters would not be injected because {@code UseCaseFactory} does not
 * implement JUnit's {@code ParameterResolver}.
 *
 * <p>When a {@code UseCaseProvider} is present (traditional model), it takes
 * precedence as a registered extension. This resolver acts as a fallback.
 */
public class UseCaseFactoryParameterResolver implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        UseCaseFactory factory = findFactory(extensionContext);
        return factory != null && factory.isRegistered(paramType);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        UseCaseFactory factory = findFactory(extensionContext);
        if (factory == null) {
            throw new ParameterResolutionException(
                    "No UseCaseFactory found for parameter type: " + paramType.getName());
        }
        return factory.getInstance(paramType);
    }

    private UseCaseFactory findFactory(ExtensionContext context) {
        Object testInstance = context.getTestInstance().orElse(null);
        if (testInstance == null) {
            return null;
        }
        Class<?> clazz = testInstance.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isSynthetic()) continue;
                if (UseCaseFactory.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        UseCaseFactory factory = (UseCaseFactory) field.get(testInstance);
                        if (factory != null) {
                            return factory;
                        }
                    } catch (IllegalAccessException e) {
                        // Continue searching
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}

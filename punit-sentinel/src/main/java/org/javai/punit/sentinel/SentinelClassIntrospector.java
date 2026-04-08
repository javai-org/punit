package org.javai.punit.sentinel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.Sentinel;
import org.javai.punit.experiment.engine.input.InputSourceResolver;
import org.javai.punit.usecase.UseCaseFactory;

/**
 * Discovers the structure of a {@code @Sentinel}-annotated reliability specification class.
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Validating that the class carries the {@code @Sentinel} annotation</li>
 *   <li>Finding the {@link UseCaseFactory} field on an instantiated class</li>
 *   <li>Discovering {@code @ProbabilisticTest} and {@code @MeasureExperiment} methods</li>
 *   <li>Resolving {@code @InputSource} annotations to input data</li>
 * </ul>
 *
 * <p>Package-private: internal implementation detail of the Sentinel runner.
 */
class SentinelClassIntrospector {

    private final InputSourceResolver inputSourceResolver = new InputSourceResolver();

    /**
     * Validates that the given class carries the {@code @Sentinel} annotation.
     *
     * @param sentinelClass the class to validate
     * @throws IllegalArgumentException if the annotation is missing
     */
    void validate(Class<?> sentinelClass) {
        if (!sentinelClass.isAnnotationPresent(Sentinel.class)) {
            throw new IllegalArgumentException(
                    sentinelClass.getName() + " is not annotated with @Sentinel");
        }
    }

    /**
     * Instantiates the sentinel class via its no-arg constructor.
     *
     * <p>Field initialisers and instance initialisers run during construction,
     * populating the {@link UseCaseFactory} field.
     *
     * @param sentinelClass the class to instantiate
     * @return the new instance
     * @throws SentinelExecutionException if instantiation fails
     */
    Object instantiate(Class<?> sentinelClass) {
        try {
            var constructor = sentinelClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new SentinelExecutionException(
                    sentinelClass.getName() + " must have a no-arg constructor", e);
        } catch (ReflectiveOperationException e) {
            throw new SentinelExecutionException(
                    "Failed to instantiate " + sentinelClass.getName(), e);
        }
    }

    /**
     * Finds the {@link UseCaseFactory} field on a sentinel instance.
     *
     * @param instance the sentinel instance
     * @return the use case factory
     * @throws SentinelExecutionException if no factory field is found
     */
    UseCaseFactory findUseCaseFactory(Object instance) {
        Class<?> current = instance.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (UseCaseFactory.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(instance);
                        if (value instanceof UseCaseFactory factory) {
                            return factory;
                        }
                    } catch (IllegalAccessException e) {
                        throw new SentinelExecutionException(
                                "Cannot access UseCaseFactory field on " + current.getName(), e);
                    }
                }
            }
            current = current.getSuperclass();
        }
        throw new SentinelExecutionException(
                instance.getClass().getName() + " does not declare a UseCaseFactory field");
    }

    /**
     * Finds all methods annotated with {@code @ProbabilisticTest}.
     *
     * @param sentinelClass the sentinel class
     * @return list of test methods (including inherited)
     */
    List<Method> findTestMethods(Class<?> sentinelClass) {
        return findAnnotatedMethods(sentinelClass, ProbabilisticTest.class);
    }

    /**
     * Finds all methods annotated with {@code @MeasureExperiment}.
     *
     * <p>The annotation is discovered reflectively via {@link MeasureExperimentDescriptor}
     * to avoid a compile-time dependency on punit-junit5 (and transitively JUnit).
     *
     * @param sentinelClass the sentinel class
     * @return list of experiment methods (including inherited), or empty if the annotation is not on the classpath
     */
    List<Method> findExperimentMethods(Class<?> sentinelClass) {
        if (!MeasureExperimentDescriptor.isAvailable()) {
            return List.of();
        }
        List<Method> result = new ArrayList<>();
        Class<?> current = sentinelClass;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (MeasureExperimentDescriptor.isPresent(method)) {
                    method.setAccessible(true);
                    result.add(method);
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }

    /**
     * Resolves the input data for a method's {@code @InputSource} annotation.
     *
     * @param method the annotated method
     * @param sentinelClass the class containing the method (for method source lookup)
     * @return the list of input values, or an empty list if no {@code @InputSource}
     */
    List<Object> resolveInputs(Method method, Class<?> sentinelClass) {
        InputSource inputSource = method.getAnnotation(InputSource.class);
        if (inputSource == null) {
            return List.of();
        }
        Class<?> inputType = resolveInputType(method);
        return inputSourceResolver.resolve(inputSource, sentinelClass, inputType);
    }

    /**
     * Determines the input parameter type for a method.
     *
     * <p>The input parameter is the one whose type does not match the use case class
     * from the annotation, and is not {@code OutcomeCaptor}.
     */
    Class<?> resolveInputType(Method method) {
        ProbabilisticTest ptAnnotation = method.getAnnotation(ProbabilisticTest.class);

        Class<?> useCaseClass = Void.class;
        if (ptAnnotation != null) {
            useCaseClass = ptAnnotation.useCase();
        } else if (MeasureExperimentDescriptor.isPresent(method)) {
            useCaseClass = MeasureExperimentDescriptor.from(method).useCase();
        }

        for (Class<?> paramType : method.getParameterTypes()) {
            if (paramType == useCaseClass) continue;
            if (paramType.getName().equals("org.javai.punit.api.OutcomeCaptor")) continue;
            return paramType;
        }
        return String.class; // fallback
    }

    private List<Method> findAnnotatedMethods(Class<?> clazz, Class<? extends java.lang.annotation.Annotation> annotation) {
        List<Method> result = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(annotation)) {
                    method.setAccessible(true);
                    result.add(method);
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }
}

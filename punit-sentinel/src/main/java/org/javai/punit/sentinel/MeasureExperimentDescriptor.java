package org.javai.punit.sentinel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Captures {@code @MeasureExperiment} annotation attributes via reflection,
 * keeping punit-sentinel free from compile-time JUnit dependencies.
 *
 * <p>{@code @MeasureExperiment} lives in punit-junit5 (which depends on JUnit).
 * Sentinel reads the annotation reflectively to avoid pulling JUnit onto the
 * sentinel runtime classpath.
 *
 * @param useCase the use case class
 * @param samples number of samples to execute
 * @param timeBudgetMs time budget in milliseconds (0 = unlimited)
 * @param tokenBudget token budget (0 = unlimited)
 * @param experimentId experiment identifier for output naming
 * @param expiresInDays baseline expiration in days (0 = no expiration)
 */
record MeasureExperimentDescriptor(
        Class<?> useCase,
        int samples,
        long timeBudgetMs,
        long tokenBudget,
        String experimentId,
        int expiresInDays
) {

    private static final String ANNOTATION_CLASS_NAME = "org.javai.punit.api.MeasureExperiment";

    private static final Class<? extends Annotation> ANNOTATION_CLASS = loadAnnotationClass();

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> loadAnnotationClass() {
        try {
            return (Class<? extends Annotation>) Class.forName(ANNOTATION_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns whether the {@code @MeasureExperiment} annotation is available on the classpath.
     */
    static boolean isAvailable() {
        return ANNOTATION_CLASS != null;
    }

    /**
     * Returns whether the given method is annotated with {@code @MeasureExperiment}.
     */
    static boolean isPresent(Method method) {
        return ANNOTATION_CLASS != null && method.isAnnotationPresent(ANNOTATION_CLASS);
    }

    /**
     * Reads the {@code @MeasureExperiment} annotation from the given method
     * and returns a descriptor capturing its attributes.
     *
     * @param method the annotated method
     * @return the descriptor
     * @throws SentinelExecutionException if the annotation is not present or cannot be read
     */
    static MeasureExperimentDescriptor from(Method method) {
        if (ANNOTATION_CLASS == null) {
            throw new SentinelExecutionException(
                    "@MeasureExperiment annotation not found on classpath. "
                            + "Add punit-junit5 to your dependencies.");
        }
        Annotation annotation = method.getAnnotation(ANNOTATION_CLASS);
        if (annotation == null) {
            throw new SentinelExecutionException(
                    "Method " + method.getName() + " is not annotated with @MeasureExperiment");
        }
        return fromAnnotation(annotation);
    }

    private static MeasureExperimentDescriptor fromAnnotation(Annotation annotation) {
        try {
            var type = annotation.annotationType();
            return new MeasureExperimentDescriptor(
                    (Class<?>) type.getMethod("useCase").invoke(annotation),
                    (int) type.getMethod("samples").invoke(annotation),
                    (long) type.getMethod("timeBudgetMs").invoke(annotation),
                    (long) type.getMethod("tokenBudget").invoke(annotation),
                    (String) type.getMethod("experimentId").invoke(annotation),
                    (int) type.getMethod("expiresInDays").invoke(annotation));
        } catch (ReflectiveOperationException e) {
            throw new SentinelExecutionException(
                    "Failed to read @MeasureExperiment attributes", e);
        }
    }
}

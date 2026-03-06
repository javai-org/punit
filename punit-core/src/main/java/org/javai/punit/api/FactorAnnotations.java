package org.javai.punit.api;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Utilities for working with {@link FactorGetter}, {@link FactorSetter}, and
 * {@link CovariateSource} annotations.
 */
public final class FactorAnnotations {

    private FactorAnnotations() {}

    /**
     * Resolves the covariate key for a method annotated with {@link CovariateSource}.
     *
     * <p>If the annotation's value is non-empty, returns that value.
     * Otherwise, derives the key from the method name by removing the "get"
     * prefix and lowercasing the first character.
     *
     * @param method the method with @CovariateSource
     * @param annotation the annotation instance
     * @return the resolved covariate key
     */
    public static String resolveCovariateSourceKey(Method method, CovariateSource annotation) {
        String explicit = annotation.value();
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return deriveFactorNameFromGetter(method.getName());
    }

    /**
     * Resolves the factor name for a method annotated with {@link FactorGetter}.
     *
     * <p>If the annotation's value is non-empty, returns that value.
     * Otherwise, derives the name from the method name by removing the "get"
     * prefix and lowercasing the first character.
     *
     * @param method the method with @FactorGetter
     * @param annotation the annotation instance
     * @return the resolved factor name
     */
    public static String resolveGetterFactorName(Method method, FactorGetter annotation) {
        String explicit = annotation.value();
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return deriveFactorNameFromGetter(method.getName());
    }

    /**
     * Resolves the factor name for a method annotated with {@link FactorSetter}.
     *
     * <p>If the annotation's value is non-empty, returns that value.
     * Otherwise, derives the name from the method name by removing the "set"
     * prefix and lowercasing the first character.
     *
     * @param method the method with @FactorSetter
     * @param annotation the annotation instance
     * @return the resolved factor name
     */
    public static String resolveSetterFactorName(Method method, FactorSetter annotation) {
        String explicit = annotation.value();
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return deriveFactorNameFromSetter(method.getName());
    }

    /**
     * Finds a method annotated with {@link FactorGetter} for the given factor name.
     *
     * @param clazz the class to search
     * @param factorName the factor name to find
     * @return the matching method, or empty if not found
     */
    public static Optional<Method> findFactorGetter(Class<?> clazz, String factorName) {
        for (Method method : clazz.getMethods()) {
            FactorGetter annotation = method.getAnnotation(FactorGetter.class);
            if (annotation != null) {
                String resolvedName = resolveGetterFactorName(method, annotation);
                if (resolvedName.equals(factorName)) {
                    return Optional.of(method);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a method annotated with {@link FactorSetter} for the given factor name.
     *
     * @param clazz the class to search
     * @param factorName the factor name to find
     * @return the matching method, or empty if not found
     */
    public static Optional<Method> findFactorSetter(Class<?> clazz, String factorName) {
        for (Method method : clazz.getMethods()) {
            FactorSetter annotation = method.getAnnotation(FactorSetter.class);
            if (annotation != null) {
                String resolvedName = resolveSetterFactorName(method, annotation);
                if (resolvedName.equals(factorName)) {
                    return Optional.of(method);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Derives a factor name from a getter method name.
     *
     * <p>Removes the "get" prefix and lowercases the first character.
     * For example: {@code getTemperature} → "temperature"
     *
     * @param methodName the method name
     * @return the derived factor name
     * @throws IllegalArgumentException if the method name doesn't start with "get"
     */
    public static String deriveFactorNameFromGetter(String methodName) {
        if (!methodName.startsWith("get") || methodName.length() <= 3) {
            throw new IllegalArgumentException(
                "Cannot derive factor name from '" + methodName +
                "': method must start with 'get' followed by at least one character");
        }
        return lowercaseFirst(methodName.substring(3));
    }

    /**
     * Derives a factor name from a setter method name.
     *
     * <p>Removes the "set" prefix and lowercases the first character.
     * For example: {@code setTemperature} → "temperature"
     *
     * @param methodName the method name
     * @return the derived factor name
     * @throws IllegalArgumentException if the method name doesn't start with "set"
     */
    public static String deriveFactorNameFromSetter(String methodName) {
        if (!methodName.startsWith("set") || methodName.length() <= 3) {
            throw new IllegalArgumentException(
                "Cannot derive factor name from '" + methodName +
                "': method must start with 'set' followed by at least one character");
        }
        return lowercaseFirst(methodName.substring(3));
    }

    private static String lowercaseFirst(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}

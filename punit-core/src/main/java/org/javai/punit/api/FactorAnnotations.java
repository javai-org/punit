package org.javai.punit.api;

import java.lang.reflect.Method;

/**
 * Utilities for working with {@link CovariateSource} annotations and deriving
 * factor names from getter-style method names.
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

    private static String lowercaseFirst(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}

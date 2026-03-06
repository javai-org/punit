package org.javai.punit.experiment.engine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.HashableFactorSource;

/**
 * Resolves factor source methods and wraps them in {@link HashableFactorSource} instances.
 *
 * <p>Factor sources are static methods that return {@code List<FactorArguments>} or
 * {@code Stream<FactorArguments>}. They are referenced by name via {@link FactorSource}
 * annotations on experiment or test methods.
 *
 * <p>The implementation type is determined by the method's return type:
 * <ul>
 *   <li>{@code List} or {@code Collection} → {@link DefaultHashableFactorSource}
 *       (cycling consumption, content hash)</li>
 *   <li>{@code Stream} → {@link StreamingHashableFactorSource}
 *       (sequential consumption, path hash)</li>
 * </ul>
 *
 * <h2>Method Requirements</h2>
 * <ul>
 *   <li>Must be static</li>
 *   <li>Must accept no parameters</li>
 *   <li>Must return {@code Stream<FactorArguments>}, {@code List<FactorArguments>},
 *       or {@code Collection<FactorArguments>}</li>
 * </ul>
 *
 * <h2>Reference Formats and Resolution</h2>
 * <ul>
 *   <li><b>Simple name</b> ({@code "methodName"}): Search current class,
 *       then use case class</li>
 *   <li><b>Class#method</b> ({@code "ClassName#methodName"}): Search current
 *       package, then use case's package</li>
 *   <li><b>Fully qualified</b> ({@code "pkg.ClassName#methodName"}): Direct lookup</li>
 * </ul>
 *
 * @see DefaultHashableFactorSource
 * @see StreamingHashableFactorSource
 */
public final class FactorSourceAdapter {

    private FactorSourceAdapter() {
        // Utility class
    }

    /**
     * Creates a HashableFactorSource from a @FactorSource annotation on a method.
     *
     * <p>This overload does not search the use case class for simple names.
     * Prefer {@link #fromAnnotation(FactorSource, Class, Class)} when a use case
     * class is available.
     *
     * @param annotation    the FactorSource annotation
     * @param declaringClass the class containing the annotation
     * @return a HashableFactorSource wrapping the annotated method
     * @throws FactorSourceResolutionException if the method cannot be resolved or invoked
     */
    public static HashableFactorSource fromAnnotation(FactorSource annotation, Class<?> declaringClass) {
        return fromAnnotation(annotation, declaringClass, null);
    }

    /**
     * Creates a HashableFactorSource from a @FactorSource annotation on a method.
     *
     * <p>Resolution order depends on the reference format:
     * <ul>
     *   <li><b>Simple name</b>: Search current class, then use case class</li>
     *   <li><b>Class#method</b>: Search current package, then use case's package</li>
     *   <li><b>Fully qualified</b>: Direct lookup</li>
     * </ul>
     *
     * <p>The implementation type is determined by the method's return type:
     * <ul>
     *   <li>{@code List} or {@code Collection} → {@link DefaultHashableFactorSource}</li>
     *   <li>{@code Stream} → {@link StreamingHashableFactorSource}</li>
     * </ul>
     *
     * @param annotation     the FactorSource annotation
     * @param declaringClass the class containing the annotation
     * @param useCaseClass   the use case class to search (may be null)
     * @return a HashableFactorSource wrapping the annotated method
     * @throws FactorSourceResolutionException if the method cannot be resolved or invoked
     */
    public static HashableFactorSource fromAnnotation(
            FactorSource annotation, Class<?> declaringClass, Class<?> useCaseClass) {
        Objects.requireNonNull(annotation, "annotation must not be null");
        Objects.requireNonNull(declaringClass, "declaringClass must not be null");

        String reference = annotation.value();
        if (reference == null || reference.isEmpty()) {
            throw new FactorSourceResolutionException(
                    "FactorSource value is required in class " + declaringClass.getName());
        }

        return resolveReference(reference, declaringClass, useCaseClass);
    }

    /**
     * Resolves a method reference that may include a class name.
     *
     * <p>This overload does not search the use case class for simple names.
     * Prefer {@link #fromReference(String, Class, Class)} when a use case
     * class is available.
     *
     * @param reference      the method reference from the annotation
     * @param declaringClass the class containing the annotation
     * @return a HashableFactorSource for the referenced method
     * @throws FactorSourceResolutionException if resolution fails
     */
    public static HashableFactorSource fromReference(String reference, Class<?> declaringClass) {
        return fromReference(reference, declaringClass, null);
    }

    /**
     * Resolves a method reference that may include a class name.
     *
     * <p>Resolution order depends on the reference format:
     * <ul>
     *   <li><b>Simple name</b> ({@code "methodName"}): Search current class,
     *       then use case class</li>
     *   <li><b>Class#method</b> ({@code "ClassName#methodName"}): Search current
     *       package, then use case's package</li>
     *   <li><b>Fully qualified</b> ({@code "pkg.ClassName#methodName"}): Direct lookup</li>
     * </ul>
     *
     * @param reference      the method reference from the annotation
     * @param declaringClass the class containing the annotation
     * @param useCaseClass   the use case class to search (may be null)
     * @return a HashableFactorSource for the referenced method
     * @throws FactorSourceResolutionException if resolution fails
     */
    public static HashableFactorSource fromReference(
            String reference, Class<?> declaringClass, Class<?> useCaseClass) {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(declaringClass, "declaringClass must not be null");

        return resolveReference(reference, declaringClass, useCaseClass);
    }

    /**
     * Core resolution logic implementing the three-form algorithm.
     */
    private static HashableFactorSource resolveReference(
            String reference, Class<?> declaringClass, Class<?> useCaseClass) {

        if (!reference.contains("#")) {
            // Simple name - search current class, then use case class
            return resolveSimpleName(reference, declaringClass, useCaseClass);
        }

        String[] parts = reference.split("#", 2);
        String classRef = parts[0];
        String methodName = parts[1];

        if (classRef.contains(".")) {
            // Fully qualified - direct lookup
            return resolveFullyQualified(classRef, methodName);
        } else {
            // Class#method - search current package, then use case's package
            return resolveClassMethod(classRef, methodName, declaringClass, useCaseClass);
        }
    }

    /**
     * Resolves a simple method name by searching current class, then use case class.
     */
    private static HashableFactorSource resolveSimpleName(
            String methodName, Class<?> currentClass, Class<?> useCaseClass) {

        // 1. Try current class
        Method method = tryFindMethod(methodName, currentClass);
        if (method != null) {
            validateMethod(method);
            return createSourceForMethod(method, methodName, currentClass);
        }

        // 2. Try use case class
        if (useCaseClass != null && useCaseClass != Void.class && useCaseClass != currentClass) {
            method = tryFindMethod(methodName, useCaseClass);
            if (method != null) {
                validateMethod(method);
                return createSourceForMethod(method, methodName, useCaseClass);
            }
        }

        // Not found - build helpful error message
        StringBuilder searched = new StringBuilder();
        searched.append("\n  - ").append(currentClass.getName());
        if (useCaseClass != null && useCaseClass != Void.class && useCaseClass != currentClass) {
            searched.append("\n  - ").append(useCaseClass.getName());
        }

        throw new FactorSourceResolutionException(
                "Cannot find factor source method '" + methodName + "'.\n" +
                        "Searched in:" + searched +
                        "\nHint: Use 'ClassName#methodName' or fully qualified 'pkg.ClassName#methodName' for explicit resolution.");
    }

    /**
     * Resolves Class#method by searching current package, then use case's package.
     */
    private static HashableFactorSource resolveClassMethod(
            String className, String methodName, Class<?> currentClass, Class<?> useCaseClass) {

        List<String> searchedLocations = new ArrayList<>();

        // 1. Try current class's package
        String currentPackage = currentClass.getPackageName();
        Class<?> targetClass = tryLoadClass(currentPackage + "." + className);
        if (targetClass != null) {
            Method method = tryFindMethod(methodName, targetClass);
            if (method != null) {
                validateMethod(method);
                return createSourceForMethod(method, className + "#" + methodName, targetClass);
            }
        }
        searchedLocations.add(currentPackage + "." + className);

        // 2. Try use case class's package (if different)
        if (useCaseClass != null && useCaseClass != Void.class) {
            String useCasePackage = useCaseClass.getPackageName();
            if (!useCasePackage.equals(currentPackage)) {
                targetClass = tryLoadClass(useCasePackage + "." + className);
                if (targetClass != null) {
                    Method method = tryFindMethod(methodName, targetClass);
                    if (method != null) {
                        validateMethod(method);
                        return createSourceForMethod(method, className + "#" + methodName, targetClass);
                    }
                }
                searchedLocations.add(useCasePackage + "." + className);
            }
        }

        // Not found - build helpful error message
        StringBuilder searched = new StringBuilder();
        for (String loc : searchedLocations) {
            searched.append("\n  - ").append(loc);
        }

        throw new FactorSourceResolutionException(
                "Cannot find factor source '" + className + "#" + methodName + "'.\n" +
                        "Searched in:" + searched +
                        "\nHint: Use fully qualified 'pkg.ClassName#methodName' for explicit resolution.");
    }

    /**
     * Resolves a fully qualified class#method reference.
     */
    private static HashableFactorSource resolveFullyQualified(
            String fullyQualifiedClassName, String methodName) {

        Class<?> targetClass;
        try {
            targetClass = Class.forName(fullyQualifiedClassName);
        } catch (ClassNotFoundException e) {
            throw new FactorSourceResolutionException(
                    "Cannot find class '" + fullyQualifiedClassName + "' for factor source.");
        }

        Method method = tryFindMethod(methodName, targetClass);
        if (method == null) {
            throw new FactorSourceResolutionException(
                    "Cannot find method '" + methodName + "' in class " + fullyQualifiedClassName);
        }

        validateMethod(method);
        return createSourceForMethod(method, fullyQualifiedClassName + "#" + methodName, targetClass);
    }

    /**
     * Attempts to find a method in a class, returning null if not found.
     */
    private static Method tryFindMethod(String methodName, Class<?> clazz) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Attempts to load a class, returning null if not found.
     */
    private static Class<?> tryLoadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Creates the appropriate HashableFactorSource based on the method's return type.
     *
     * @param method       the factor source method
     * @param sourceName   the name to use for the source
     * @param targetClass  the class containing the method
     * @return the appropriate HashableFactorSource implementation
     */
    private static HashableFactorSource createSourceForMethod(Method method, String sourceName, Class<?> targetClass) {
        Class<?> returnType = method.getReturnType();

        if (Stream.class.isAssignableFrom(returnType)) {
            // Stream return type → StreamingHashableFactorSource (sequential, path hash)
            String sourcePath = targetClass.getSimpleName() + "#" + method.getName();
            return new StreamingHashableFactorSource(sourceName, sourcePath, () -> invokeMethodAsStream(method));
        } else {
            // Collection/List return type → DefaultHashableFactorSource (cycling, content hash)
            return new DefaultHashableFactorSource(sourceName, () -> invokeMethod(method));
        }
    }

    /**
     * Invokes a method and returns the result as a Stream (for streaming sources).
     * Unlike invokeMethod, this does NOT convert Collections to streams—it expects
     * the method to return a Stream directly.
     */
    @SuppressWarnings("unchecked")
    private static Stream<FactorArguments> invokeMethodAsStream(Method method) {
        try {
            method.setAccessible(true);
            Object result = method.invoke(null);

            if (result instanceof Stream) {
                return (Stream<FactorArguments>) result;
            } else {
                throw new FactorSourceResolutionException(
                        "Factor source method '" + method.getName() +
                                "' expected to return Stream but returned: " + result.getClass().getName());
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new FactorSourceResolutionException(
                    "Failed to invoke factor source method '" + method.getName() + "': " + e.getMessage(), e);
        }
    }

    private static void validateMethod(Method method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new FactorSourceResolutionException(
                    "Factor source method '" + method.getName() + "' must be static");
        }

        if (method.getParameterCount() > 0) {
            throw new FactorSourceResolutionException(
                    "Factor source method '" + method.getName() + "' must not accept parameters");
        }

        Class<?> returnType = method.getReturnType();
        if (!Stream.class.isAssignableFrom(returnType) &&
                !Collection.class.isAssignableFrom(returnType)) {
            throw new FactorSourceResolutionException(
                    "Factor source method '" + method.getName() +
                            "' must return Stream<FactorArguments> or Collection<FactorArguments>, " +
                            "but returns " + returnType.getName());
        }
    }

    @SuppressWarnings("unchecked")
    private static Stream<FactorArguments> invokeMethod(Method method) {
        try {
            method.setAccessible(true);
            Object result = method.invoke(null);

            if (result instanceof Stream) {
                return (Stream<FactorArguments>) result;
            } else if (result instanceof Collection) {
                return ((Collection<FactorArguments>) result).stream();
            } else {
                throw new FactorSourceResolutionException(
                        "Factor source method '" + method.getName() +
                                "' returned unexpected type: " + result.getClass().getName());
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new FactorSourceResolutionException(
                    "Failed to invoke factor source method '" + method.getName() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Exception thrown when a factor source cannot be resolved or invoked.
     */
    public static class FactorSourceResolutionException extends RuntimeException {
        public FactorSourceResolutionException(String message) {
            super(message);
        }

        public FactorSourceResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


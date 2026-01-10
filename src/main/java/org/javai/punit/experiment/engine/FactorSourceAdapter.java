package org.javai.punit.experiment.engine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
import org.javai.punit.experiment.api.FactorArguments;
import org.javai.punit.experiment.api.FactorSource;
import org.javai.punit.experiment.api.HashableFactorSource;

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
 * <h2>Reference Formats</h2>
 * <ul>
 *   <li>{@code "methodName"} - method in the same class</li>
 *   <li>{@code "ClassName#methodName"} - method in another class</li>
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
     * <p>The implementation type is determined by the method's return type:
     * <ul>
     *   <li>{@code List} or {@code Collection} → {@link DefaultHashableFactorSource}</li>
     *   <li>{@code Stream} → {@link StreamingHashableFactorSource}</li>
     * </ul>
     *
     * @param annotation    the FactorSource annotation
     * @param declaringClass the class containing the factor source method
     * @return a HashableFactorSource wrapping the annotated method
     * @throws FactorSourceResolutionException if the method cannot be resolved or invoked
     */
    public static HashableFactorSource fromAnnotation(FactorSource annotation, Class<?> declaringClass) {
        Objects.requireNonNull(annotation, "annotation must not be null");
        Objects.requireNonNull(declaringClass, "declaringClass must not be null");

        String methodName = resolveMethodName(annotation.value(), declaringClass);
        Method method = findMethod(methodName, declaringClass);
        validateMethod(method);

        return createSourceForMethod(method, methodName, declaringClass);
    }

    /**
     * Resolves a method reference that may include a class name.
     *
     * <p>Supports two formats:
     * <ul>
     *   <li>{@code "methodName"} - method in the declaring class</li>
     *   <li>{@code "ClassName#methodName"} - method in the specified class</li>
     * </ul>
     *
     * <p>The implementation type is determined by the method's return type:
     * <ul>
     *   <li>{@code List} or {@code Collection} → {@link DefaultHashableFactorSource}</li>
     *   <li>{@code Stream} → {@link StreamingHashableFactorSource}</li>
     * </ul>
     *
     * @param reference      the method reference from the annotation
     * @param declaringClass the class containing the annotation
     * @return a HashableFactorSource for the referenced method
     * @throws FactorSourceResolutionException if resolution fails
     */
    public static HashableFactorSource fromReference(String reference, Class<?> declaringClass) {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(declaringClass, "declaringClass must not be null");

        if (reference.contains("#")) {
            // Cross-class reference: "ClassName#methodName"
            String[] parts = reference.split("#", 2);
            String className = parts[0];
            String methodName = parts[1];

            Class<?> targetClass = resolveClass(className, declaringClass);
            Method method = findMethod(methodName, targetClass);
            validateMethod(method);

            return createSourceForMethod(method, reference, targetClass);
        } else {
            // Same-class reference: "methodName"
            Method method = findMethod(reference, declaringClass);
            validateMethod(method);

            return createSourceForMethod(method, reference, declaringClass);
        }
    }

    private static String resolveMethodName(String value, Class<?> declaringClass) {
        if (value == null || value.isEmpty()) {
            throw new FactorSourceResolutionException(
                    "FactorSource value is required in class " + declaringClass.getName());
        }
        return value;
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

    private static Method findMethod(String methodName, Class<?> declaringClass) {
        // Handle cross-class reference if present
        if (methodName.contains("#")) {
            String[] parts = methodName.split("#", 2);
            declaringClass = resolveClass(parts[0], declaringClass);
            methodName = parts[1];
        }

        try {
            return declaringClass.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            throw new FactorSourceResolutionException(
                    "Factor source method '" + methodName + "' not found in class " +
                            declaringClass.getName() + ". Ensure the method exists and takes no parameters.");
        }
    }

    private static Class<?> resolveClass(String className, Class<?> contextClass) {
        String packageName = contextClass.getPackageName();
        
        // 1. Try same package
        try {
            return Class.forName(packageName + "." + className);
        } catch (ClassNotFoundException ignored) {
        }
        
        // 2. Try fully qualified name (className might already be FQN)
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
        }
        
        // 3. Try sibling packages (e.g., from .experiment to .usecase)
        // This handles common project structures where related classes are in sibling packages
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot > 0) {
            String parentPackage = packageName.substring(0, lastDot);
            
            // Try common sibling package names
            for (String sibling : new String[]{"usecase", "model", "domain", "service", "api", "core"}) {
                try {
                    return Class.forName(parentPackage + "." + sibling + "." + className);
                } catch (ClassNotFoundException ignored) {
                }
            }
            
            // Try parent package directly
            try {
                return Class.forName(parentPackage + "." + className);
            } catch (ClassNotFoundException ignored) {
            }
        }
        
        throw new FactorSourceResolutionException(
                "Cannot resolve class '" + className + "' from context " + contextClass.getName() +
                ". Try using the fully qualified class name.");
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


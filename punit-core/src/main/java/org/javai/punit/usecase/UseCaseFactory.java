package org.javai.punit.usecase;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.javai.punit.api.FactorAnnotations;
import org.javai.punit.api.FactorSetter;
import org.javai.punit.api.FactorValues;
import org.javai.punit.api.UseCase;
import org.javai.punit.model.UseCaseAttributes;

/**
 * JUnit-free factory for creating and managing use case instances.
 *
 * <p>This class contains all the factory registration, instance resolution,
 * and factor injection logic that was previously in {@code UseCaseProvider}.
 * It has no JUnit dependencies and can be used by both the JUnit 5 extension
 * layer and the Sentinel runtime.
 *
 * <p>In the JUnit 5 layer, {@code UseCaseProvider} delegates to this class
 * and additionally implements {@code ParameterResolver}.
 */
public class UseCaseFactory {

    private final Map<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Function<FactorValues, ?>> factorFactories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Supplier<?>> autoWiredFactories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> lastCreatedInstances = new ConcurrentHashMap<>();
    private boolean useSingletons = false;

    // Current factor values for EXPLORE mode
    private FactorValues currentFactorValues = null;

    /**
     * Creates a new factory with per-invocation instance creation.
     */
    public UseCaseFactory() {
    }

    /**
     * Creates a new factory with optional singleton behavior.
     *
     * @param useSingletons if true, each use case class gets one instance per lifecycle
     */
    public UseCaseFactory(boolean useSingletons) {
        this.useSingletons = useSingletons;
    }

    /**
     * Registers a factory for creating instances of a use case class.
     *
     * @param useCaseClass the use case class
     * @param factory      a supplier that creates instances
     * @param <T>          the use case type
     */
    public <T> UseCaseFactory register(Class<T> useCaseClass, Supplier<T> factory) {
        factories.put(useCaseClass, factory);
        singletons.remove(useCaseClass);
        return this;
    }

    /**
     * Registers a factor-aware factory for EXPLORE mode experiments.
     *
     * @param useCaseClass  the use case class
     * @param factorFactory a function that takes FactorValues and creates an instance
     * @param <T>           the use case type
     */
    public <T> UseCaseFactory registerWithFactors(Class<T> useCaseClass,
                                         Function<FactorValues, T> factorFactory) {
        factorFactories.put(useCaseClass, factorFactory);
        singletons.remove(useCaseClass);
        return this;
    }

    /**
     * Registers a use case for automatic factor injection via {@link FactorSetter} annotations.
     *
     * @param useCaseClass the use case class
     * @param factory      a supplier that creates base instances
     * @param <T>          the use case type
     */
    public <T> UseCaseFactory registerAutoWired(Class<T> useCaseClass, Supplier<T> factory) {
        autoWiredFactories.put(useCaseClass, factory);
        singletons.remove(useCaseClass);
        return this;
    }

    /**
     * Sets the current factor values for EXPLORE mode.
     *
     * @param factorValues the current factor values with names
     */
    public void setCurrentFactorValues(FactorValues factorValues) {
        this.currentFactorValues = factorValues;
    }

    /**
     * Sets the current factor values for EXPLORE mode.
     *
     * @param values the factor values
     * @param names  the factor names
     */
    public void setCurrentFactorValues(Object[] values, List<String> names) {
        this.currentFactorValues = new FactorValues(values, names);
    }

    /**
     * Clears the current factor values.
     */
    public void clearCurrentFactorValues() {
        this.currentFactorValues = null;
    }

    /**
     * Returns the current factor values, or null if not in EXPLORE mode.
     */
    public FactorValues getCurrentFactorValues() {
        return currentFactorValues;
    }

    /**
     * Gets an instance of the specified use case class.
     *
     * <p>Resolution order (when factor values are set):
     * <ol>
     *   <li>Factor factory ({@link #registerWithFactors})</li>
     *   <li>Auto-wired factory ({@link #registerAutoWired})</li>
     *   <li>Regular factory ({@link #register})</li>
     * </ol>
     *
     * @param useCaseClass the use case class
     * @param <T>          the use case type
     * @return an instance of the use case
     * @throws IllegalStateException if no factory is registered for the class
     */
    @SuppressWarnings("unchecked")
    public <T> T getInstance(Class<T> useCaseClass) {
        T instance;

        // 1. Check for factor factory (EXPLORE mode with custom factory)
        Function<FactorValues, ?> factorFactory = factorFactories.get(useCaseClass);
        if (factorFactory != null && currentFactorValues != null) {
            instance = (T) factorFactory.apply(currentFactorValues);
            lastCreatedInstances.put(useCaseClass, instance);
            return instance;
        }

        // 2. Check for auto-wired factory (EXPLORE mode with @FactorSetter)
        Supplier<?> autoWiredFactory = autoWiredFactories.get(useCaseClass);
        if (autoWiredFactory != null && currentFactorValues != null) {
            instance = (T) autoWiredFactory.get();
            injectFactorValues(instance, useCaseClass, currentFactorValues, true);
            lastCreatedInstances.put(useCaseClass, instance);
            return instance;
        }

        // 3. Fall back to regular factory
        Supplier<?> factory = factories.get(useCaseClass);

        if (factory == null && factorFactory == null && autoWiredFactory == null) {
            throw new IllegalStateException(
                    "No factory registered for use case: " + useCaseClass.getName() + ". " +
                    "Register one during initialization: factory.register(" + useCaseClass.getSimpleName() +
                    ".class, () -> new " + useCaseClass.getSimpleName() + "(...))");
        }

        if (factory == null) {
            throw new IllegalStateException(
                    "Factor-aware factory registered for " + useCaseClass.getName() +
                    " but no factor values set. " +
                    "Either use EXPLORE mode or register a regular factory.");
        }

        if (useSingletons) {
            instance = (T) singletons.computeIfAbsent(useCaseClass, k -> factory.get());
        } else {
            instance = (T) factory.get();
        }

        // Inject factors if available (e.g., from OPTIMIZE mode)
        // Use lenient mode: skip setters for factors that aren't provided
        if (currentFactorValues != null) {
            injectFactorValues(instance, useCaseClass, currentFactorValues, false);
        }

        lastCreatedInstances.put(useCaseClass, instance);
        return instance;
    }

    /**
     * Injects factor values into methods annotated with @FactorSetter.
     */
    private void injectFactorValues(Object instance, Class<?> useCaseClass, FactorValues factors, boolean strict) {
        for (Method method : useCaseClass.getMethods()) {
            FactorSetter annotation = method.getAnnotation(FactorSetter.class);
            if (annotation != null) {
                String factorName = FactorAnnotations.resolveSetterFactorName(method, annotation);

                if (!factors.has(factorName)) {
                    if (strict) {
                        throw new IllegalStateException(
                            "Use case " + useCaseClass.getSimpleName() + " has @FactorSetter for \"" +
                            factorName + "\" but no such factor exists. Available: " + factors.names());
                    }
                    continue;
                }

                Object value = factors.get(factorName);
                try {
                    Class<?> paramType = method.getParameterTypes()[0];
                    Object convertedValue = convertValue(value, paramType);
                    method.setAccessible(true);
                    method.invoke(instance, convertedValue);
                } catch (Exception e) {
                    throw new IllegalStateException(
                        "Failed to inject @FactorSetter for \"" + factorName + "\" into " +
                        useCaseClass.getSimpleName() + "." + method.getName() + "(): " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Converts a factor value to the target type.
     */
    static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }

        if (targetType == double.class || targetType == Double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        }
        if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            return Boolean.parseBoolean(value.toString());
        }
        if (targetType == String.class) {
            return value.toString();
        }

        return value;
    }

    /**
     * Returns the last created instance of a use case class.
     *
     * @param useCaseClass the use case class
     * @return the last created instance, or null if none exists
     */
    @SuppressWarnings("unchecked")
    public <T> T getCurrentInstance(Class<T> useCaseClass) {
        return (T) lastCreatedInstances.get(useCaseClass);
    }

    /**
     * Checks if any factory is registered for the class.
     */
    public boolean isRegistered(Class<?> useCaseClass) {
        return factories.containsKey(useCaseClass)
            || factorFactories.containsKey(useCaseClass)
            || autoWiredFactories.containsKey(useCaseClass);
    }

    /**
     * Checks if a factor-aware factory is registered for the class.
     */
    public boolean hasFactorFactory(Class<?> useCaseClass) {
        return factorFactories.containsKey(useCaseClass)
            || autoWiredFactories.containsKey(useCaseClass);
    }

    /**
     * Clears all registered factories and cached singletons.
     */
    public void clear() {
        factories.clear();
        factorFactories.clear();
        autoWiredFactories.clear();
        singletons.clear();
        lastCreatedInstances.clear();
        currentFactorValues = null;
    }

    /**
     * Resolves all use-case-level attributes from a use case class.
     *
     * <p>Reads the {@code @UseCase} annotation and bundles its attributes
     * into a single {@link UseCaseAttributes} carrier.
     *
     * @param useCaseClass the use case class
     * @return the resolved attributes (never null)
     */
    public static UseCaseAttributes resolveAttributes(Class<?> useCaseClass) {
        UseCase annotation = useCaseClass.getAnnotation(UseCase.class);
        if (annotation == null) {
            return UseCaseAttributes.DEFAULT;
        }
        return new UseCaseAttributes(annotation.warmup(), annotation.maxConcurrent());
    }

    /**
     * Resolves the warmup count from a use case class.
     *
     * <p>Reads the {@code warmup} attribute from the {@code @UseCase} annotation
     * on the class. Returns 0 if the annotation is absent or warmup is not specified.
     *
     * @param useCaseClass the use case class
     * @return the warmup count (>= 0)
     * @throws IllegalArgumentException if warmup is negative
     */
    public static int resolveWarmup(Class<?> useCaseClass) {
        return resolveAttributes(useCaseClass).warmup();
    }

    /**
     * Resolves the use case ID from a class.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If {@code @UseCase} annotation present with non-empty value, use that</li>
     *   <li>Otherwise, use the simple class name</li>
     * </ol>
     *
     * @param useCaseClass the use case class
     * @return the resolved ID
     */
    public static String resolveId(Class<?> useCaseClass) {
        UseCase annotation = useCaseClass.getAnnotation(UseCase.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return useCaseClass.getSimpleName();
    }
}

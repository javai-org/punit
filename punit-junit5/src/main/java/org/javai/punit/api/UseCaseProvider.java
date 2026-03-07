package org.javai.punit.api;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 extension for creating and managing use case instances.
 *
 * <p>{@code UseCaseProvider} is the JUnit-native way to register use case factories
 * and inject use case instances into test method parameters. It is used via
 * {@code @RegisterExtension} in JUnit test classes.
 *
 * <h2>Basic Usage</h2>
 * <pre>{@code
 * class ShoppingTest {
 *
 *     @RegisterExtension
 *     UseCaseProvider provider = new UseCaseProvider();
 *
 *     @BeforeEach
 *     void setUp() {
 *         provider.register(ShoppingUseCase.class, () ->
 *             new ShoppingUseCase(new MockShoppingAssistant())
 *         );
 *     }
 *
 *     @ProbabilisticTest(useCase = ShoppingUseCase.class, samples = 30)
 *     void testJsonValidity(ShoppingUseCase useCase) {
 *         // useCase is injected by the provider
 *     }
 * }
 * }</pre>
 *
 * <h2>Relationship to UseCaseFactory</h2>
 * <p>{@code UseCaseProvider} and {@link org.javai.punit.usecase.UseCaseFactory UseCaseFactory}
 * serve independent deployment contexts:
 * <ul>
 *   <li>{@code UseCaseProvider} — JUnit extension for developer workstations and CI</li>
 *   <li>{@code UseCaseFactory} — JUnit-free factory for Sentinel production monitoring</li>
 * </ul>
 * <p>They are independent classes with parallel APIs. Neither inherits from the other.
 *
 * @see UseCase
 * @see ProbabilisticTest#useCase()
 */
public class UseCaseProvider implements ParameterResolver {

    private final Map<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Function<FactorValues, ?>> factorFactories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Supplier<?>> autoWiredFactories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> lastCreatedInstances = new ConcurrentHashMap<>();
    private boolean useSingletons = false;

    private FactorValues currentFactorValues = null;

    /**
     * Creates a new use case provider with per-invocation instance creation.
     */
    public UseCaseProvider() {
    }

    /**
     * Creates a new use case provider with optional singleton behavior.
     *
     * @param useSingletons if true, each use case class gets one instance per test class
     */
    public UseCaseProvider(boolean useSingletons) {
        this.useSingletons = useSingletons;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory registration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registers a factory for creating instances of a use case class.
     *
     * @param useCaseClass the use case class
     * @param factory      a supplier that creates instances
     * @param <T>          the use case type
     */
    public <T> UseCaseProvider register(Class<T> useCaseClass, Supplier<T> factory) {
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
    public <T> UseCaseProvider registerWithFactors(Class<T> useCaseClass,
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
    public <T> UseCaseProvider registerAutoWired(Class<T> useCaseClass, Supplier<T> factory) {
        autoWiredFactories.put(useCaseClass, factory);
        singletons.remove(useCaseClass);
        return this;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Factor values (EXPLORE/OPTIMIZE mode)
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance resolution
    // ═══════════════════════════════════════════════════════════════════════════

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
                    "Register one in @BeforeEach: provider.register(" + useCaseClass.getSimpleName() +
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

    // ═══════════════════════════════════════════════════════════════════════════
    // JUnit 5 ParameterResolver implementation
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        return isRegistered(paramType);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        return getInstance(paramType);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Factor injection
    // ═══════════════════════════════════════════════════════════════════════════

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
}

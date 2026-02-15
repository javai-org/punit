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
 * Provides use case instances for experiments and probabilistic tests.
 *
 * <p>The {@code UseCaseProvider} is the bridge between dependency injection
 * (Spring, Guice, CDI, or manual construction) and PUnit's use case injection.
 * Configure it in {@code @BeforeEach} or {@code @BeforeAll} to specify how
 * use cases should be constructed.
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
 * <h2>Spring Boot Integration</h2>
 * <pre>{@code
 * @SpringBootTest
 * class ShoppingIntegrationTest {
 *
 *     @Autowired
 *     private OpenAIClient openAIClient;
 *
 *     @RegisterExtension
 *     UseCaseProvider provider = new UseCaseProvider();
 *
 *     @BeforeEach
 *     void setUp() {
 *         // Bridge Spring dependencies to PUnit
 *         provider.register(ShoppingUseCase.class, () ->
 *             new ShoppingUseCase(new OpenAIShoppingAssistant(openAIClient))
 *         );
 *     }
 *
 *     @ProbabilisticTest(useCase = ShoppingUseCase.class, samples = 30)
 *     void testWithRealLLM(ShoppingUseCase useCase) {
 *         // Uses real OpenAI client!
 *     }
 * }
 * }</pre>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>Register factories in {@code @BeforeEach}: {@code provider.register(Class, Supplier)}</li>
 *   <li>PUnit reads {@code useCase = Foo.class} from annotation</li>
 *   <li>PUnit asks provider for an instance: {@code provider.getInstance(Foo.class)}</li>
 *   <li>Provider invokes the registered factory</li>
 *   <li>Instance is injected into the test/experiment method</li>
 * </ol>
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
    
    // Current factor values for EXPLORE mode - set by ExperimentExtension
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

    /**
     * Registers a factory for creating instances of a use case class.
     *
     * <p>Call this in {@code @BeforeEach} or {@code @BeforeAll} to configure
     * how use cases should be constructed.
     *
     * @param useCaseClass the use case class
     * @param factory      a supplier that creates instances
     * @param <T>          the use case type
     * @return this provider for fluent chaining
     */
    public <T> UseCaseProvider register(Class<T> useCaseClass, Supplier<T> factory) {
        factories.put(useCaseClass, factory);
        // Clear any cached singleton when re-registering
        singletons.remove(useCaseClass);
        return this;
    }

    /**
     * Registers a factor-aware factory for EXPLORE mode experiments.
     *
     * <p>In EXPLORE mode, the experiment extension calls this factory with
     * the current factor values. The factory uses these to create an
     * appropriately configured use case instance.
     *
     * <h2>Example</h2>
     * <pre>{@code
     * @BeforeAll
     * static void configureFactors() {
     *     provider.registerWithFactors(ShoppingUseCase.class, factors -> {
     *         String model = factors.getString("model");
     *         double temp = factors.getDouble("temp");
     *         MockConfiguration config = selectConfig(model, temp);
     *         return new ShoppingUseCase(new MockShoppingAssistant(config), model, temp);
     *     });
     * }
     * }</pre>
     *
     * @param useCaseClass the use case class
     * @param factorFactory a function that takes FactorValues and creates an instance
     * @param <T> the use case type
     * @return this provider for fluent chaining
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
     * <p>The provider will:
     * <ol>
     *   <li>Create an instance using the supplied factory</li>
     *   <li>Find all methods annotated with {@code @FactorSetter}</li>
     *   <li>Invoke each setter with the corresponding factor value</li>
     * </ol>
     *
     * <h2>Use Case Definition</h2>
     * <pre>{@code
     * @UseCase
     * public class ShoppingUseCase {
     *     private String model;
     *     private double temperature;
     *
     *     @FactorSetter("model")
     *     public void setModel(String model) { this.model = model; }
     *
     *     @FactorSetter("temp")
     *     public void setTemperature(double temp) { this.temperature = temp; }
     * }
     * }</pre>
     *
     * <h2>Registration</h2>
     * <pre>{@code
     * provider.registerAutoWired(ShoppingUseCase.class, 
     *     () -> new ShoppingUseCase(new MockShoppingAssistant()));
     * }</pre>
     *
     * @param useCaseClass the use case class
     * @param factory a supplier that creates base instances
     * @param <T> the use case type
     * @return this provider for fluent chaining
     */
    public <T> UseCaseProvider registerAutoWired(Class<T> useCaseClass, Supplier<T> factory) {
        autoWiredFactories.put(useCaseClass, factory);
        singletons.remove(useCaseClass);
        return this;
    }

    /**
     * Sets the current factor values for EXPLORE mode.
     *
     * <p>Called by ExperimentExtension before invoking the experiment method.
     * The factor values are used when {@link #getInstance(Class)} is called
     * for a use case registered with {@link #registerWithFactors} or
     * {@link #registerAutoWired}.
     *
     * @param factorValues the current factor values with names
     */
    public void setCurrentFactorValues(FactorValues factorValues) {
        this.currentFactorValues = factorValues;
    }
    
    /**
     * Sets the current factor values for EXPLORE mode.
     *
     * <p>Convenience overload that creates a FactorValues from arrays.
     *
     * @param values the factor values
     * @param names the factor names
     */
    public void setCurrentFactorValues(Object[] values, List<String> names) {
        this.currentFactorValues = new FactorValues(values, names);
    }

    /**
     * Clears the current factor values.
     *
     * <p>Called by ExperimentExtension after the experiment method completes.
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
     * <p>If singleton mode is enabled, returns the same instance for repeated calls
     * (only for regular factories; factor factories always create new instances).
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
            injectFactorValues(instance, useCaseClass, currentFactorValues, true);  // strict mode
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
     * Injects factor values into methods annotated with @FactorSetter.
     *
     * @param instance the use case instance
     * @param useCaseClass the use case class
     * @param factors the factor values to inject
     * @param strict if true, throws when a setter references a missing factor;
     *               if false, skips setters for missing factors
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
                    continue;  // Skip in lenient mode
                }

                Object value = factors.get(factorName);
                try {
                    // Convert value to parameter type if needed
                    Class<?> paramType = method.getParameterTypes()[0];
                    Object convertedValue = convertValue(value, paramType);
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
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        
        // Handle primitive conversions
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
        
        // No conversion possible
        return value;
    }

    /**
     * Returns the last created instance of a use case class.
     *
     * <p>This is useful for checking if the instance implements specific interfaces
     * without creating a new instance.
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
     *
     * @param useCaseClass the use case class
     * @return true if a factory is registered
     */
    public boolean isRegistered(Class<?> useCaseClass) {
        return factories.containsKey(useCaseClass) 
            || factorFactories.containsKey(useCaseClass)
            || autoWiredFactories.containsKey(useCaseClass);
    }
    
    /**
     * Checks if a factor-aware factory is registered for the class.
     *
     * @param useCaseClass the use case class
     * @return true if a factor factory or auto-wired factory is registered
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
}


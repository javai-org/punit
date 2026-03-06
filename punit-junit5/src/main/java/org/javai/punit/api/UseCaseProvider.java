package org.javai.punit.api;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.javai.punit.usecase.UseCaseFactory;
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
 * <p>This class is a thin JUnit 5 {@link ParameterResolver} adapter that
 * delegates all factory logic to {@link UseCaseFactory}.
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
 * @see UseCase
 * @see ProbabilisticTest#useCase()
 * @see UseCaseFactory
 */
public class UseCaseProvider implements ParameterResolver {

    private final UseCaseFactory factory;

    /**
     * Creates a new use case provider with per-invocation instance creation.
     */
    public UseCaseProvider() {
        this.factory = new UseCaseFactory();
    }

    /**
     * Creates a new use case provider with optional singleton behavior.
     *
     * @param useSingletons if true, each use case class gets one instance per test class
     */
    public UseCaseProvider(boolean useSingletons) {
        this.factory = new UseCaseFactory(useSingletons);
    }

    /**
     * Returns the underlying {@link UseCaseFactory} for direct access.
     */
    public UseCaseFactory getFactory() {
        return factory;
    }

    /**
     * Registers a factory for creating instances of a use case class.
     *
     * @param useCaseClass the use case class
     * @param supplier     a supplier that creates instances
     * @param <T>          the use case type
     * @return this provider for fluent chaining
     */
    public <T> UseCaseProvider register(Class<T> useCaseClass, Supplier<T> supplier) {
        factory.register(useCaseClass, supplier);
        return this;
    }

    /**
     * Registers a factor-aware factory for EXPLORE mode experiments.
     *
     * @param useCaseClass  the use case class
     * @param factorFactory a function that takes FactorValues and creates an instance
     * @param <T>           the use case type
     * @return this provider for fluent chaining
     */
    public <T> UseCaseProvider registerWithFactors(Class<T> useCaseClass,
                                                    Function<FactorValues, T> factorFactory) {
        factory.registerWithFactors(useCaseClass, factorFactory);
        return this;
    }

    /**
     * Registers a use case for automatic factor injection via {@link FactorSetter} annotations.
     *
     * @param useCaseClass the use case class
     * @param supplier     a supplier that creates base instances
     * @param <T>          the use case type
     * @return this provider for fluent chaining
     */
    public <T> UseCaseProvider registerAutoWired(Class<T> useCaseClass, Supplier<T> supplier) {
        factory.registerAutoWired(useCaseClass, supplier);
        return this;
    }

    /**
     * Sets the current factor values for EXPLORE mode.
     *
     * @param factorValues the current factor values with names
     */
    public void setCurrentFactorValues(FactorValues factorValues) {
        factory.setCurrentFactorValues(factorValues);
    }

    /**
     * Sets the current factor values for EXPLORE mode.
     *
     * @param values the factor values
     * @param names  the factor names
     */
    public void setCurrentFactorValues(Object[] values, List<String> names) {
        factory.setCurrentFactorValues(values, names);
    }

    /**
     * Clears the current factor values.
     */
    public void clearCurrentFactorValues() {
        factory.clearCurrentFactorValues();
    }

    /**
     * Returns the current factor values, or null if not in EXPLORE mode.
     */
    public FactorValues getCurrentFactorValues() {
        return factory.getCurrentFactorValues();
    }

    /**
     * Gets an instance of the specified use case class.
     *
     * @param useCaseClass the use case class
     * @param <T>          the use case type
     * @return an instance of the use case
     * @throws IllegalStateException if no factory is registered for the class
     */
    public <T> T getInstance(Class<T> useCaseClass) {
        return factory.getInstance(useCaseClass);
    }

    /**
     * Returns the last created instance of a use case class.
     *
     * @param useCaseClass the use case class
     * @return the last created instance, or null if none exists
     */
    public <T> T getCurrentInstance(Class<T> useCaseClass) {
        return factory.getCurrentInstance(useCaseClass);
    }

    /**
     * Checks if any factory is registered for the class.
     */
    public boolean isRegistered(Class<?> useCaseClass) {
        return factory.isRegistered(useCaseClass);
    }

    /**
     * Checks if a factor-aware factory is registered for the class.
     */
    public boolean hasFactorFactory(Class<?> useCaseClass) {
        return factory.hasFactorFactory(useCaseClass);
    }

    /**
     * Clears all registered factories and cached singletons.
     */
    public void clear() {
        factory.clear();
    }

    /**
     * Resolves the use case ID from a class.
     *
     * @param useCaseClass the use case class
     * @return the resolved ID
     */
    public static String resolveId(Class<?> useCaseClass) {
        return UseCaseFactory.resolveId(useCaseClass);
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

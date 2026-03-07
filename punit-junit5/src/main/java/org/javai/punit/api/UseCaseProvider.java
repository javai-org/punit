package org.javai.punit.api;

import org.javai.punit.usecase.UseCaseFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 adapter for {@link UseCaseFactory} that adds {@link ParameterResolver}
 * integration.
 *
 * <p>The {@code UseCaseProvider} extends {@link UseCaseFactory} and adds JUnit 5
 * parameter resolution, enabling use case instances to be injected as test method
 * parameters. All factory registration and instance management methods are inherited
 * from {@link UseCaseFactory}.
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
 * <h2>Sentinel Pattern</h2>
 * <p>For Sentinel-eligible reliability specifications, the {@code @Sentinel} class
 * uses {@link UseCaseFactory} directly (from {@code punit-core}). JUnit test classes
 * inherit from the specification and use this provider via {@code @RegisterExtension}
 * only when additional JUnit lifecycle integration is needed.
 *
 * @see UseCaseFactory
 * @see UseCase
 * @see ProbabilisticTest#useCase()
 */
public class UseCaseProvider extends UseCaseFactory implements ParameterResolver {

    /**
     * Creates a new use case provider with per-invocation instance creation.
     */
    public UseCaseProvider() {
        super();
    }

    /**
     * Creates a new use case provider with optional singleton behavior.
     *
     * @param useSingletons if true, each use case class gets one instance per test class
     */
    public UseCaseProvider(boolean useSingletons) {
        super(useSingletons);
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

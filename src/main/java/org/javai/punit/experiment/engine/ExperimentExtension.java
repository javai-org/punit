package org.javai.punit.experiment.engine;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.javai.punit.controls.pacing.PacingConfiguration;
import org.javai.punit.controls.pacing.PacingReporter;
import org.javai.punit.controls.pacing.PacingResolver;
import org.javai.punit.experiment.explore.ExploreStrategy;
import org.javai.punit.experiment.measure.MeasureStrategy;
import org.javai.punit.experiment.optimize.OptimizeStrategy;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * JUnit 5 extension that coordinates experiment execution.
 *
 * <p>This class is intentionally thin - it detects which mode annotation is present,
 * delegates to the corresponding strategy, and manages shared infrastructure (pacing).
 * Mode-specific logic lives in the strategy implementations:
 * <ul>
 *   <li>{@link MeasureStrategy} - Handles @MeasureExperiment</li>
 *   <li>{@link ExploreStrategy} - Handles @ExploreExperiment</li>
 *   <li>{@link OptimizeStrategy} - Handles @OptimizeExperiment</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>The extension uses the Strategy pattern to keep mode-specific logic encapsulated.
 * Each strategy knows how to:
 * <ol>
 *   <li>Parse its annotation into a configuration</li>
 *   <li>Generate invocation contexts (the sample stream)</li>
 *   <li>Intercept and execute each sample</li>
 *   <li>Generate mode-specific output</li>
 * </ol>
 */
public class ExperimentExtension implements TestTemplateInvocationContextProvider,
        InvocationInterceptor {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create("org.javai.punit.experiment");

    private static final List<ExperimentModeStrategy> STRATEGIES = List.of(
            new MeasureStrategy(),
            new ExploreStrategy(),
            new OptimizeStrategy()
    );

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(m -> STRATEGIES.stream().anyMatch(s -> s.supports(m)))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {

        Method testMethod = context.getRequiredTestMethod();
        ExperimentModeStrategy strategy = findStrategy(testMethod);

        ExtensionContext.Store store = context.getStore(NAMESPACE);
        ExperimentConfig config = strategy.parseConfig(testMethod);

        // Store shared state
        store.put("strategy", strategy);
        store.put("config", config);
        store.put("testMethod", testMethod);
        store.put("startTimeMs", System.currentTimeMillis());
        store.put("useCaseId", config.useCaseId());
        store.put("useCaseClass", config.useCaseClass());

        // Setup pacing (shared infrastructure)
        int totalSamples = strategy.computeTotalSamples(config, testMethod);
        setupPacing(testMethod, totalSamples, config, store);

        return strategy.provideInvocationContexts(config, context, store);
    }

    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {

        // Use parent context's store since provideTestTemplateInvocationContexts stored data there
        ExtensionContext parentContext = extensionContext.getParent().orElse(extensionContext);
        ExtensionContext.Store store = parentContext.getStore(NAMESPACE);

        ExperimentModeStrategy strategy = store.get("strategy", ExperimentModeStrategy.class);

        // Apply pacing delay (shared infrastructure)
        applyPacingDelay(store);

        strategy.intercept(invocation, invocationContext, extensionContext, store);
    }

    /**
     * Finds the strategy that supports the given test method.
     */
    private ExperimentModeStrategy findStrategy(Method testMethod) {
        return STRATEGIES.stream()
                .filter(s -> s.supports(testMethod))
                .findFirst()
                .orElseThrow(() -> new ExtensionConfigurationException(
                        "No strategy found for method: " + testMethod.getName() +
                                ". Method must be annotated with @MeasureExperiment, " +
                                "@ExploreExperiment, or @OptimizeExperiment."));
    }

    /**
     * Sets up pacing configuration from @Pacing annotation.
     */
    private void setupPacing(Method testMethod, int totalSamples,
                             ExperimentConfig config, ExtensionContext.Store store) {

        PacingResolver resolver = new PacingResolver();
        PacingConfiguration pacing = resolver.resolve(testMethod, totalSamples);
        store.put("pacing", pacing);
        store.put("globalSampleCounter", new AtomicInteger(0));

        // Report pacing configuration if enabled
        if (pacing.hasPacing()) {
            String testName = testMethod.getDeclaringClass().getSimpleName() + "." + testMethod.getName();
            PacingReporter pacingReporter = new PacingReporter();
            pacingReporter.printPreFlightReport(testName, totalSamples, pacing, Instant.now());
            pacingReporter.printFeasibilityWarning(pacing, config.timeBudgetMs(), totalSamples);
        }
    }

    /**
     * Applies pacing delay between samples.
     *
     * <p>Uses a global sample counter to ensure continuous pacing across all samples,
     * including across configuration boundaries in @ExploreExperiment.
     */
    private void applyPacingDelay(ExtensionContext.Store store) {
        PacingConfiguration pacing = store.get("pacing", PacingConfiguration.class);
        if (pacing == null || !pacing.hasPacing()) {
            return;
        }

        AtomicInteger globalSampleCounter = store.get("globalSampleCounter", AtomicInteger.class);
        if (globalSampleCounter == null) {
            return;
        }

        int globalSample = globalSampleCounter.incrementAndGet();

        // Skip delay for first sample
        if (globalSample <= 1) {
            return;
        }

        long delayMs = pacing.effectiveMinDelayMs();
        if (delayMs <= 0) {
            return;
        }

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Don't fail the experiment, just continue
        }
    }
}

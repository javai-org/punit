package org.javai.punit.experiment.engine;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Strategy interface for handling a specific experiment mode.
 *
 * <p>Each experiment mode (MEASURE, EXPLORE, OPTIMIZE) provides an implementation
 * that encapsulates all mode-specific behavior:
 * <ul>
 *   <li>Parsing its annotation into a configuration</li>
 *   <li>Generating invocation contexts (the sample stream)</li>
 *   <li>Intercepting and executing each sample</li>
 *   <li>Generating mode-specific output (specs, optimization history, etc.)</li>
 * </ul>
 *
 * <p>This pattern keeps {@link ExperimentExtension} thin and mode-agnostic.
 * The extension simply detects which annotation is present, finds the corresponding
 * strategy, and delegates all mode-specific work.
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@code MeasureStrategy} - Handles @MeasureExperiment</li>
 *   <li>{@code ExploreStrategy} - Handles @ExploreExperiment</li>
 *   <li>{@code OptimizeStrategy} - Handles @OptimizeExperiment</li>
 * </ul>
 */
public interface ExperimentModeStrategy {

    /**
     * Check if this strategy handles the given test method.
     *
     * <p>Typically checks for the presence of the mode's annotation
     * (e.g., @MeasureExperiment for MeasureStrategy).
     *
     * @param testMethod the test method to check
     * @return true if this strategy's annotation is present
     */
    boolean supports(Method testMethod);

    /**
     * Parse the experiment annotation into a mode-specific configuration.
     *
     * @param testMethod the annotated test method
     * @return the parsed configuration (a subtype of {@link ExperimentConfig})
     * @throws org.junit.jupiter.api.extension.ExtensionConfigurationException
     *         if the annotation is invalid or missing required attributes
     */
    ExperimentConfig parseConfig(Method testMethod);

    /**
     * Provide the stream of invocation contexts for this experiment.
     *
     * <p>Each invocation context represents one sample execution. The stream
     * may be finite (e.g., MEASURE with fixed sample count) or lazy
     * (e.g., OPTIMIZE with dynamic iteration).
     *
     * @param config the parsed configuration
     * @param context the JUnit extension context
     * @param store the extension store for shared state
     * @return stream of invocation contexts (one per sample)
     */
    Stream<TestTemplateInvocationContext> provideInvocationContexts(
            ExperimentConfig config,
            ExtensionContext context,
            ExtensionContext.Store store);

    /**
     * Intercept and execute a single sample.
     *
     * <p>Called for each invocation context. Responsible for:
     * <ul>
     *   <li>Checking budget constraints (time, tokens)</li>
     *   <li>Proceeding with or skipping the invocation</li>
     *   <li>Recording results to aggregators</li>
     *   <li>Generating output when complete</li>
     * </ul>
     *
     * @param invocation the JUnit invocation to proceed or skip
     * @param invocationContext reflective context for the method
     * @param extensionContext the JUnit extension context
     * @param store the extension store for shared state
     * @throws Throwable if the test method throws
     */
    void intercept(
            InvocationInterceptor.Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) throws Throwable;

    /**
     * Compute the total number of samples for pacing calculation.
     *
     * <p>Used by the extension to configure pacing before samples execute.
     * For OPTIMIZE mode, this may be an estimate (samplesPerIteration × maxIterations).
     *
     * @param config the parsed configuration
     * @param testMethod the test method (may have @FactorSource for EXPLORE)
     * @return estimated total samples
     */
    int computeTotalSamples(ExperimentConfig config, Method testMethod);
}

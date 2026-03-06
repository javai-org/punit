package org.javai.punit.ptest.strategy;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;
import org.javai.punit.ptest.engine.ConfigurationResolver;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Strategy interface for probabilistic test execution.
 *
 * <p>This interface defines the contract for different types of probabilistic
 * testing strategies. Each strategy encapsulates:
 * <ul>
 *   <li>Configuration parsing and validation</li>
 *   <li>Sample stream generation</li>
 *   <li>Individual sample execution (interception)</li>
 *   <li>Final verdict determination</li>
 * </ul>
 *
 * <p>The default implementation is {@link org.javai.punit.ptest.bernoulli.BernoulliTrialsStrategy}
 * which models each sample as a Bernoulli trial (success/failure) and uses
 * statistical inference to determine pass/fail based on observed pass rate.
 *
 * <p>Future strategies might include Statistical Process Control (SPC) or
 * other statistical approaches.
 *
 * @see ProbabilisticTestConfig
 */
public interface ProbabilisticTestStrategy {

    /**
     * Determines if this strategy supports the given annotation configuration.
     *
     * <p>The extension uses this method to select the appropriate strategy
     * for each test method.
     *
     * @param annotation the @ProbabilisticTest annotation
     * @return true if this strategy can handle the given configuration
     */
    boolean supports(ProbabilisticTest annotation);

    /**
     * Parses the annotation and resolves configuration for this strategy.
     *
     * <p>This method is called once per test method, before any samples
     * are executed. It should:
     * <ul>
     *   <li>Resolve configuration with precedence (system prop &gt; env var &gt; annotation)</li>
     *   <li>Validate configuration values</li>
     *   <li>Determine token mode</li>
     *   <li>Create any strategy-specific configuration</li>
     * </ul>
     *
     * @param annotation the @ProbabilisticTest annotation
     * @param testMethod the test method being executed
     * @param resolver the configuration resolver for resolving overrides
     * @return the parsed configuration
     */
    ProbabilisticTestConfig parseConfig(
            ProbabilisticTest annotation,
            Method testMethod,
            ConfigurationResolver resolver);

    /**
     * Provides the stream of invocation contexts for sample execution.
     *
     * <p>This method generates the sample invocation contexts that JUnit 5
     * will execute. The stream should:
     * <ul>
     *   <li>Generate the appropriate number of samples based on configuration</li>
     *   <li>Support early termination via the terminated flag in the store</li>
     *   <li>Include any strategy-specific context in the invocation contexts</li>
     * </ul>
     *
     * @param config the parsed configuration
     * @param context the JUnit extension context
     * @param store the extension context store for sharing state
     * @return stream of invocation contexts for each sample
     */
    Stream<TestTemplateInvocationContext> provideInvocationContexts(
            ProbabilisticTestConfig config,
            ExtensionContext context,
            ExtensionContext.Store store);

    /**
     * Intercepts and executes a single sample.
     *
     * <p>This method is called for each sample execution and should:
     * <ul>
     *   <li>Execute the sample via the invocation</li>
     *   <li>Handle exceptions according to configuration</li>
     *   <li>Record results in the aggregator</li>
     *   <li>Evaluate early termination conditions</li>
     * </ul>
     *
     * @param invocation the JUnit invocation to proceed or skip
     * @param executionContext context information for this sample execution
     * @return the result of the interception, including early termination status
     * @throws Throwable if the sample execution fails in a way that should abort
     */
    InterceptResult intercept(
            Invocation<Void> invocation,
            SampleExecutionContext executionContext) throws Throwable;

    /**
     * Computes the total number of samples this strategy will execute.
     *
     * <p>Used for display purposes and progress calculation.
     *
     * @param config the parsed configuration
     * @return the total number of samples
     */
    int computeTotalSamples(ProbabilisticTestConfig config);

    /**
     * Determines the final verdict after all samples have executed.
     *
     * <p>This method is called after all samples have been executed (or
     * early termination has occurred) to determine whether the test passed.
     *
     * @param aggregator the sample result aggregator with execution results
     * @param config the test configuration
     * @return true if the test passed, false otherwise
     */
    boolean computeVerdict(SampleResultAggregator aggregator, ProbabilisticTestConfig config);

    /**
     * Builds a failure message when the test fails.
     *
     * <p>This method is called when {@link #computeVerdict} returns false
     * to build an appropriate failure message with statistical context.
     *
     * @param aggregator the sample result aggregator with execution results
     * @param config the test configuration
     * @return the failure message
     */
    String buildFailureMessage(SampleResultAggregator aggregator, ProbabilisticTestConfig config);
}

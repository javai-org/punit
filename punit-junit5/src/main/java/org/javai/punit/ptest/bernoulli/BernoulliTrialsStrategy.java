package org.javai.punit.ptest.bernoulli;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.HashableFactorSource;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.experiment.engine.input.InputParameterDetector;
import org.javai.punit.experiment.engine.input.InputSourceResolver;
import org.javai.punit.controls.budget.BudgetOrchestrator;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.DefaultTokenChargeRecorder;
import org.javai.punit.controls.pacing.PacingConfiguration;
import org.javai.punit.controls.pacing.PacingResolver;
import org.javai.punit.experiment.engine.FactorSourceAdapter;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.ptest.engine.ConfigurationResolver;
import org.javai.punit.ptest.engine.FactorConsistencyValidator;
import org.javai.punit.ptest.engine.ProbabilisticTestConfigurationException;
import org.javai.punit.ptest.engine.ProbabilisticTestInvocationContext;
import org.javai.punit.ptest.engine.SampleExecutor;
import org.javai.punit.ptest.strategy.InterceptResult;
import org.javai.punit.ptest.strategy.ProbabilisticTestConfig;
import org.javai.punit.ptest.strategy.ProbabilisticTestStrategy;
import org.javai.punit.ptest.strategy.SampleExecutionContext;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.statistics.transparent.TransparentStatsConfig;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Strategy for one-sided inference testing based on Bernoulli trials.
 *
 * <p>This strategy models each sample as a Bernoulli trial (success/failure)
 * and uses statistical inference to determine pass/fail based on:
 * <ul>
 *   <li>Observed pass rate vs. configured threshold</li>
 *   <li>Early termination when success becomes impossible or guaranteed</li>
 *   <li>Statistical context from baseline measurements (when available)</li>
 * </ul>
 *
 * <p>This is the default and currently only strategy for @ProbabilisticTest.
 */
public class BernoulliTrialsStrategy implements ProbabilisticTestStrategy {

    private static final Logger logger = LogManager.getLogger(BernoulliTrialsStrategy.class);

    private final PacingResolver pacingResolver;
    private final SampleExecutor sampleExecutor;
    private final BudgetOrchestrator budgetOrchestrator;
    private final FinalVerdictDecider verdictDecider;

    /**
     * Creates a new BernoulliTrialsStrategy with default dependencies.
     */
    public BernoulliTrialsStrategy() {
        this(new PacingResolver(), new SampleExecutor(), new BudgetOrchestrator(), new FinalVerdictDecider());
    }

    /**
     * Creates a new BernoulliTrialsStrategy with custom dependencies (for testing).
     */
    BernoulliTrialsStrategy(
            PacingResolver pacingResolver,
            SampleExecutor sampleExecutor,
            BudgetOrchestrator budgetOrchestrator,
            FinalVerdictDecider verdictDecider) {
        this.pacingResolver = pacingResolver;
        this.sampleExecutor = sampleExecutor;
        this.budgetOrchestrator = budgetOrchestrator;
        this.verdictDecider = verdictDecider;
    }

    @Override
    public boolean supports(ProbabilisticTest annotation) {
        // Currently the only strategy - supports all @ProbabilisticTest annotations
        return true;
    }

    @Override
    public BernoulliTrialsConfig parseConfig(
            ProbabilisticTest annotation,
            Method testMethod,
            ConfigurationResolver resolver) {

        // Resolve configuration with precedence: system prop > env var > annotation
        ConfigurationResolver.ResolvedConfiguration resolved;
        try {
            resolved = resolver.resolve(annotation, testMethod.getName());
        } catch (ProbabilisticTestConfigurationException | IllegalArgumentException e) {
            throw new org.junit.jupiter.api.extension.ExtensionConfigurationException(e.getMessage(), e);
        }

        // Detect token charging mode
        boolean hasTokenRecorderParam = hasTokenChargeRecorderParameter(testMethod);
        CostBudgetMonitor.TokenMode tokenMode = determineTokenMode(resolved, hasTokenRecorderParam);

        // Resolve pacing configuration
        PacingConfiguration pacing = pacingResolver.resolve(testMethod, resolved.samples());

        // Resolve transparent stats config
        TransparentStatsConfig transparentStats = TransparentStatsConfig.resolve(
                annotation.transparentStats() ? Boolean.TRUE : null);

        return new BernoulliTrialsConfig(
                resolved.samples(),
                resolved.minPassRate(),
                resolved.appliedMultiplier(),
                resolved.timeBudgetMs(),
                resolved.tokenCharge(),
                (int) resolved.tokenBudget(),
                tokenMode,
                resolved.onBudgetExhausted(),
                resolved.onException(),
                resolved.maxExampleFailures(),
                resolved.confidence(),
                resolved.baselineRate(),
                resolved.baselineSamples(),
                resolved.specId(),
                pacing,
                transparentStats,
                resolved.thresholdOrigin(),
                resolved.contractRef(),
                resolved.intent(),
                resolved.resolvedConfidence()
        );
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideInvocationContexts(
            ProbabilisticTestConfig config,
            ExtensionContext context,
            ExtensionContext.Store store) {

        BernoulliTrialsConfig bernoulliConfig = (BernoulliTrialsConfig) config;
        int samples = bernoulliConfig.samples();

        // Get the terminated flag from the store
        AtomicBoolean terminated = store.get("terminated", AtomicBoolean.class);
        if (terminated == null) {
            terminated = new AtomicBoolean(false);
            store.put("terminated", terminated);
        }

        // Get token recorder if present
        DefaultTokenChargeRecorder tokenRecorder = store.get("tokenRecorder", DefaultTokenChargeRecorder.class);

        // Check for @InputSource annotation
        Method testMethod = context.getRequiredTestMethod();
        InputSource inputSource = testMethod.getAnnotation(InputSource.class);

        if (inputSource != null) {
            return provideWithInputsInvocationContexts(
                    testMethod, inputSource, context.getRequiredTestClass(),
                    samples, store, terminated, tokenRecorder);
        }

        AtomicBoolean terminatedFinal = terminated;
        return Stream.iterate(1, i -> i + 1)
                .limit(samples)
                .takeWhile(i -> !terminatedFinal.get())
                .map(sampleIndex -> new ProbabilisticTestInvocationContext(
                        sampleIndex, samples, tokenRecorder));
    }

    private Stream<TestTemplateInvocationContext> provideWithInputsInvocationContexts(
            Method testMethod,
            InputSource inputSource,
            Class<?> testClass,
            int samples,
            ExtensionContext.Store store,
            AtomicBoolean terminated,
            DefaultTokenChargeRecorder tokenRecorder) {

        // Determine input type from method parameters
        Class<?> inputType = findInputParameterType(testMethod);

        // Resolve inputs
        InputSourceResolver resolver = new InputSourceResolver();
        List<Object> inputs = resolver.resolve(inputSource, testClass, inputType);

        if (inputs.isEmpty()) {
            throw new org.junit.jupiter.api.extension.ExtensionConfigurationException(
                    "@InputSource resolved to empty list");
        }

        store.put("inputs", inputs);
        store.put("inputType", inputType);

        // Generate sample stream with cycling inputs
        int totalInputs = inputs.size();
        return Stream.iterate(1, i -> i + 1)
                .limit(samples)
                .takeWhile(i -> !terminated.get())
                .map(i -> {
                    int inputIndex = (i - 1) % totalInputs;
                    Object inputValue = inputs.get(inputIndex);
                    return new ProbabilisticTestWithInputsInvocationContext(
                            i, samples, tokenRecorder, inputValue, inputType, inputIndex, totalInputs);
                });
    }

    /**
     * Finds the input parameter type from method parameters.
     */
    private Class<?> findInputParameterType(Method method) {
        return InputParameterDetector.findInputParameterType(method);
    }

    @Override
    public InterceptResult intercept(
            Invocation<Void> invocation,
            SampleExecutionContext executionContext) throws Throwable {

        BernoulliTrialsConfig config = (BernoulliTrialsConfig) executionContext.config();
        SampleResultAggregator aggregator = executionContext.aggregator();
        EarlyTerminationEvaluator evaluator = executionContext.evaluator();
        AtomicBoolean terminated = executionContext.terminated();

        // Pre-sample budget check
        BudgetOrchestrator.BudgetCheckResult preSampleCheck = budgetOrchestrator.checkBeforeSample(
                executionContext.suiteBudget(),
                executionContext.classBudget(),
                executionContext.methodBudget());

        Optional<InterceptResult> preSampleResult = handleBudgetExhaustion(
                preSampleCheck, executionContext, config, aggregator, terminated);
        if (preSampleResult.isPresent()) {
            invocation.skip();
            return preSampleResult.get();
        }

        // Reset token recorder for new sample
        if (executionContext.tokenRecorder() != null) {
            executionContext.tokenRecorder().resetForNextSample();
        }

        // Execute the sample
        SampleExecutor.SampleResult sampleResult = sampleExecutor.execute(
                invocation, aggregator, config.onException());

        // Handle abort
        if (sampleResult.shouldAbort()) {
            sampleExecutor.prepareForAbort(aggregator);
            terminated.set(true);
            return InterceptResult.abort(sampleResult.abortException());
        }

        // Post-sample token recording
        budgetOrchestrator.recordAndPropagateTokens(
                executionContext.tokenRecorder(),
                executionContext.methodBudget(),
                config.tokenMode(),
                config.tokenCharge(),
                executionContext.classBudget(),
                executionContext.suiteBudget());

        // Post-sample budget check
        BudgetOrchestrator.BudgetCheckResult postSampleCheck = budgetOrchestrator.checkAfterSample(
                executionContext.suiteBudget(),
                executionContext.classBudget(),
                executionContext.methodBudget());

        Optional<InterceptResult> postSampleResult = handleBudgetExhaustion(
                postSampleCheck, executionContext, config, aggregator, terminated);
        if (postSampleResult.isPresent()) {
            return postSampleResult.get();
        }

        // Check for early termination (impossibility or success guaranteed)
        Optional<TerminationReason> earlyTermination = evaluator.shouldTerminate(
                aggregator.getSuccesses(), aggregator.getSamplesExecuted());

        if (earlyTermination.isPresent()) {
            TerminationReason reason = earlyTermination.get();
            String details = EarlyTerminationMessages.buildExplanation(
                    reason,
                    aggregator.getSuccesses(),
                    aggregator.getSamplesExecuted(),
                    evaluator.getTotalSamples(),
                    evaluator.getRequiredSuccesses());

            aggregator.setTerminated(reason, details);
            terminated.set(true);

            if (sampleResult.hasSampleFailure()) {
                return InterceptResult.terminateWithFailure(reason, details, sampleResult.failure());
            }
            return InterceptResult.terminate(reason, details);
        }

        // Check if all samples completed
        if (aggregator.getSamplesExecuted() >= config.samples()) {
            aggregator.setCompleted();
            terminated.set(true);
            return InterceptResult.terminate(TerminationReason.COMPLETED, "All samples completed");
        }

        // Continue execution
        if (sampleResult.hasSampleFailure()) {
            return InterceptResult.continueWithFailure(sampleResult.failure());
        }
        return InterceptResult.continueExecution();
    }

    @Override
    public int computeTotalSamples(ProbabilisticTestConfig config) {
        return config.samples();
    }

    @Override
    public boolean computeVerdict(SampleResultAggregator aggregator, ProbabilisticTestConfig config) {
        BernoulliTrialsConfig bernoulliConfig = (BernoulliTrialsConfig) config;
        if (aggregator.isForcedFailure()) {
            return false;
        }
        return verdictDecider.isPassing(aggregator, bernoulliConfig.minPassRate());
    }

    @Override
    public String buildFailureMessage(SampleResultAggregator aggregator, ProbabilisticTestConfig config) {
        BernoulliTrialsConfig bernoulliConfig = (BernoulliTrialsConfig) config;

        if (aggregator.isForcedFailure()) {
            return budgetOrchestrator.buildExhaustionFailureMessage(
                    aggregator.getTerminationReason().orElse(null),
                    aggregator.getTerminationDetails(),
                    aggregator.getSamplesExecuted(),
                    config.samples(),
                    aggregator.getObservedPassRate(),
                    aggregator.getSuccesses(),
                    bernoulliConfig.minPassRate(),
                    aggregator.getElapsedMs());
        }

        BernoulliFailureMessages.StatisticalContext statisticalContext = bernoulliConfig.buildStatisticalContext(
                aggregator.getObservedPassRate(),
                aggregator.getSuccesses(),
                aggregator.getSamplesExecuted()
        );

        return verdictDecider.buildFailureMessage(aggregator, statisticalContext);
    }

    /**
     * Handles budget exhaustion by recording termination and determining the forced-failure behavior.
     *
     * @return the termination result, or empty if the budget is not exhausted
     */
    private Optional<InterceptResult> handleBudgetExhaustion(
            BudgetOrchestrator.BudgetCheckResult checkResult,
            SampleExecutionContext executionContext,
            BernoulliTrialsConfig config,
            SampleResultAggregator aggregator,
            AtomicBoolean terminated) {

        if (!checkResult.shouldTerminate()) {
            return Optional.empty();
        }

        TerminationReason reason = checkResult.terminationReason().get();
        BudgetExhaustedBehavior behavior = budgetOrchestrator.determineBehavior(
                reason,
                executionContext.suiteBudget(),
                executionContext.classBudget(),
                config.onBudgetExhausted());

        String details = budgetOrchestrator.buildExhaustionMessage(
                reason,
                executionContext.methodBudget(),
                executionContext.classBudget(),
                executionContext.suiteBudget());

        aggregator.setTerminated(reason, details);
        terminated.set(true);

        if (behavior == BudgetExhaustedBehavior.FAIL) {
            aggregator.setForcedFailure(true);
        }

        return Optional.of(InterceptResult.terminate(reason, details));
    }

    /**
     * Validates factor source consistency with baseline spec.
     */
    public Optional<FactorConsistencyValidator.ValidationResult> validateFactorConsistency(
            Method testMethod,
            ProbabilisticTest annotation,
            int testSamples,
            ConfigurationResolver configResolver) {

        FactorSource factorSourceAnnotation = testMethod.getAnnotation(FactorSource.class);
        if (factorSourceAnnotation == null) {
            return Optional.empty();
        }

        HashableFactorSource testFactorSource;
        try {
            Class<?> useCaseClass = annotation.useCase();
            testFactorSource = FactorSourceAdapter.fromAnnotation(
                    factorSourceAnnotation, testMethod.getDeclaringClass(), useCaseClass);
        } catch (Exception e) {
            logger.warn("Could not resolve factor source for consistency check: {}", e.getMessage());
            return Optional.empty();
        }

        Optional<String> specIdOpt = configResolver.resolveSpecIdFromAnnotation(annotation);
        if (specIdOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<ExecutionSpecification> optionalSpec = configResolver.loadSpec(specIdOpt.get());
        if (optionalSpec.isEmpty()) {
            return Optional.empty();
        }

        FactorConsistencyValidator.ValidationResult result = FactorConsistencyValidator.validateWithSampleCount(
                testFactorSource, optionalSpec.get(), testSamples);

        return result.shouldWarn() ? Optional.of(result) : Optional.empty();
    }

    private boolean hasTokenChargeRecorderParameter(Method method) {
        for (Parameter param : method.getParameters()) {
            if (TokenChargeRecorder.class.isAssignableFrom(param.getType())) {
                return true;
            }
        }
        return false;
    }

    private CostBudgetMonitor.TokenMode determineTokenMode(
            ConfigurationResolver.ResolvedConfiguration config,
            boolean hasTokenRecorderParam) {

        if (hasTokenRecorderParam) {
            return CostBudgetMonitor.TokenMode.DYNAMIC;
        } else if (config.tokenCharge() > 0) {
            return CostBudgetMonitor.TokenMode.STATIC;
        } else {
            return CostBudgetMonitor.TokenMode.NONE;
        }
    }
}

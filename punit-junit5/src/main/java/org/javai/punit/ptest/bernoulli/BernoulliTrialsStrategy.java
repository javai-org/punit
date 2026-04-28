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
import org.javai.punit.api.legacy.ProbabilisticTest;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.controls.budget.BudgetOrchestrator;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.DefaultTokenChargeRecorder;
import org.javai.punit.controls.pacing.PacingConfiguration;
import org.javai.punit.controls.pacing.PacingResolver;
import org.javai.punit.experiment.engine.FactorSourceAdapter;
import org.javai.punit.experiment.engine.WarmupInvocationContext;
import org.javai.punit.experiment.engine.input.InputParameterDetector;
import org.javai.punit.experiment.engine.input.InputParameterResolver;
import org.javai.punit.experiment.engine.input.InputSourceResolver;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.model.UseCaseAttributes;
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
import org.javai.punit.usecase.UseCaseFactory;
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

        // Resolve use case attributes from @UseCase on the use case class
        UseCaseAttributes useCaseAttributes = UseCaseAttributes.DEFAULT;
        Class<?> useCaseClass = annotation.useCase();
        if (useCaseClass != Void.class) {
            useCaseAttributes = UseCaseFactory.resolveAttributes(useCaseClass);
        }

        return new BernoulliTrialsConfig(
                useCaseAttributes,
                resolved.samples(),
                resolved.minPassRate(),
                resolved.appliedMultiplier(),
                resolved.timeBudgetMs(),
                resolved.tokenCharge(),
                (int) resolved.tokenBudget(),
                resolved.maxExampleFailures(), resolved.confidence(), resolved.baselineRate(), resolved.baselineSamples(), resolved.resolvedConfidence(), tokenMode,
                resolved.onBudgetExhausted(),
                resolved.onException(),
                resolved.specId(),
                pacing,
                transparentStats,
                resolved.thresholdOrigin(),
                resolved.contractRef(),
                resolved.intent()
        );
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideInvocationContexts(
            ProbabilisticTestConfig config,
            ExtensionContext context,
            ExtensionContext.Store store) {

        BernoulliTrialsConfig bernoulliConfig = (BernoulliTrialsConfig) config;
        int warmup = bernoulliConfig.warmup();
        int samples = bernoulliConfig.samples();
        int totalInvocations = warmup + samples;

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
                    warmup, samples, store, terminated, tokenRecorder);
        }

        AtomicBoolean terminatedFinal = terminated;
        return Stream.iterate(1, i -> i + 1)
                .limit(totalInvocations)
                .takeWhile(i -> !terminatedFinal.get())
                .map(i -> {
                    if (i <= warmup) {
                        return new WarmupInvocationContext(i, warmup);
                    }
                    int sampleIndex = i - warmup;
                    return new ProbabilisticTestInvocationContext(
                            sampleIndex, samples, tokenRecorder);
                });
    }

    private Stream<TestTemplateInvocationContext> provideWithInputsInvocationContexts(
            Method testMethod,
            InputSource inputSource,
            Class<?> testClass,
            int warmup,
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

        // Generate warmup + sample stream with cycling inputs
        int totalInvocations = warmup + samples;
        int totalInputs = inputs.size();
        Object firstInput = inputs.get(0);
        return Stream.iterate(1, i -> i + 1)
                .limit(totalInvocations)
                .takeWhile(i -> !terminated.get())
                .map(i -> {
                    if (i <= warmup) {
                        return new WarmupInvocationContext(i, warmup,
                                List.of(new InputParameterResolver(firstInput, inputType)));
                    }
                    int sampleIndex = i - warmup;
                    int inputIndex = (sampleIndex - 1) % totalInputs;
                    Object inputValue = inputs.get(inputIndex);
                    return new ProbabilisticTestWithInputsInvocationContext(
                            sampleIndex, samples, tokenRecorder, inputValue, inputType, inputIndex, totalInputs);
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

        // 1. Warmup gate
        Optional<InterceptResult> warmupResult = handleWarmupPhase(invocation, executionContext);
        if (warmupResult.isPresent()) return warmupResult.get();

        // 2. Pre-sample budget check
        Optional<InterceptResult> preBudgetResult = checkPreSampleBudget(
                executionContext, config, aggregator, terminated);
        if (preBudgetResult.isPresent()) {
            invocation.skip();
            return preBudgetResult.get();
        }

        // 3. Reset token recorder
        if (executionContext.tokenRecorder() != null) {
            executionContext.tokenRecorder().resetForNextSample();
        }

        // 4. Execute sample
        SampleExecutor.SampleResult sampleResult = sampleExecutor.execute(
                invocation, aggregator, config.onException());
        if (sampleResult.shouldAbort()) {
            sampleExecutor.prepareForAbort(aggregator);
            terminated.set(true);
            return InterceptResult.abort(sampleResult.abortException());
        }

        // 5. Post-sample budgets
        Optional<InterceptResult> postBudgetResult = processPostSampleBudgets(
                executionContext, config, aggregator, terminated);
        if (postBudgetResult.isPresent()) return postBudgetResult.get();

        // 6. Early termination
        Optional<InterceptResult> terminationResult = evaluateEarlyTermination(
                aggregator, evaluator, terminated, sampleResult);
		return terminationResult.orElseGet(() -> evaluateCompletion(aggregator, config, terminated, sampleResult));

        // 7. Completion or continue
	}

    /**
     * Handles the warmup phase: executes warmup invocations without recording results,
     * checking budgets before and after each warmup sample.
     *
     * @return the result if warmup is active and was handled, or empty if warmup is complete
     *         and normal sample execution should proceed
     */
    Optional<InterceptResult> handleWarmupPhase(
            Invocation<Void> invocation,
            SampleExecutionContext executionContext) throws Throwable {

        BernoulliTrialsConfig config = (BernoulliTrialsConfig) executionContext.config();
        SampleResultAggregator aggregator = executionContext.aggregator();
        AtomicBoolean terminated = executionContext.terminated();

        int warmup = config.warmup();
        if (warmup <= 0) {
            return Optional.empty();
        }

        int warmupCompleted = aggregator.getSamplesExecuted() == 0
                ? executionContext.warmupCounter().get() : warmup;
        if (warmupCompleted >= warmup) {
            return Optional.empty();
        }

        // Pre-sample budget check during warmup
        BudgetOrchestrator.BudgetCheckResult preSampleCheck = budgetOrchestrator.checkBeforeSample(
                executionContext.suiteBudget(),
                executionContext.classBudget(),
                executionContext.methodBudget());
        if (preSampleCheck.shouldTerminate()) {
            Optional<InterceptResult> result = handleBudgetExhaustion(
                    preSampleCheck, executionContext, config, aggregator, terminated);
            if (result.isPresent()) {
                invocation.skip();
                return result;
            }
        }

        // Execute warmup invocation — results discarded
        sampleExecutor.executeWarmup(invocation);
        executionContext.warmupCounter().incrementAndGet();

        // Post-sample budget check during warmup
        BudgetOrchestrator.BudgetCheckResult postSampleCheck = budgetOrchestrator.checkAfterSample(
                executionContext.suiteBudget(),
                executionContext.classBudget(),
                executionContext.methodBudget());
        if (postSampleCheck.shouldTerminate()) {
            Optional<InterceptResult> result = handleBudgetExhaustion(
                    postSampleCheck, executionContext, config, aggregator, terminated);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.of(InterceptResult.continueExecution());
    }

    /**
     * Checks budget constraints before executing a sample.
     *
     * @return the termination result if budget is exhausted, or empty to proceed
     */
    Optional<InterceptResult> checkPreSampleBudget(
            SampleExecutionContext executionContext,
            BernoulliTrialsConfig config,
            SampleResultAggregator aggregator,
            AtomicBoolean terminated) {

        BudgetOrchestrator.BudgetCheckResult preSampleCheck = budgetOrchestrator.checkBeforeSample(
                executionContext.suiteBudget(),
                executionContext.classBudget(),
                executionContext.methodBudget());

        return handleBudgetExhaustion(preSampleCheck, executionContext, config, aggregator, terminated);
    }

    /**
     * Records token usage and checks budget constraints after executing a sample.
     *
     * @return the termination result if budget is exhausted, or empty to proceed
     */
    Optional<InterceptResult> processPostSampleBudgets(
            SampleExecutionContext executionContext,
            BernoulliTrialsConfig config,
            SampleResultAggregator aggregator,
            AtomicBoolean terminated) {

        budgetOrchestrator.recordAndPropagateTokens(
                executionContext.tokenRecorder(),
                executionContext.methodBudget(),
                config.tokenMode(),
                config.tokenCharge(),
                executionContext.classBudget(),
                executionContext.suiteBudget());

        BudgetOrchestrator.BudgetCheckResult postSampleCheck = budgetOrchestrator.checkAfterSample(
                executionContext.suiteBudget(),
                executionContext.classBudget(),
                executionContext.methodBudget());

        return handleBudgetExhaustion(postSampleCheck, executionContext, config, aggregator, terminated);
    }

    /**
     * Evaluates whether early termination should occur based on impossibility or guaranteed success.
     *
     * @return the termination result if early termination is warranted, or empty to proceed
     */
    Optional<InterceptResult> evaluateEarlyTermination(
            SampleResultAggregator aggregator,
            EarlyTerminationEvaluator evaluator,
            AtomicBoolean terminated,
            SampleExecutor.SampleResult sampleResult) {

        Optional<TerminationReason> earlyTermination = evaluator.shouldTerminate(
                aggregator.getSuccesses(), aggregator.getSamplesExecuted());

        if (earlyTermination.isEmpty()) {
            return Optional.empty();
        }

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
            return Optional.of(InterceptResult.terminateWithFailure(reason, details, sampleResult.failure()));
        }
        return Optional.of(InterceptResult.terminate(reason, details));
    }

    /**
     * Evaluates whether all samples have completed, or signals continuation.
     *
     * @return the appropriate result: termination if complete, or continue (with or without failure)
     */
    InterceptResult evaluateCompletion(
            SampleResultAggregator aggregator,
            BernoulliTrialsConfig config,
            AtomicBoolean terminated,
            SampleExecutor.SampleResult sampleResult) {

        if (aggregator.getSamplesExecuted() >= config.samples()) {
            aggregator.setCompleted();
            terminated.set(true);
            return InterceptResult.terminate(TerminationReason.COMPLETED, "All samples completed");
        }

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

        TerminationReason reason = checkResult.terminationReason()
                .orElseThrow(() -> new IllegalStateException("Reason must be present when shouldTerminate is true"));
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

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
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.HashableFactorSource;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.contract.AssertionScope;
import org.javai.punit.controls.budget.BudgetOrchestrator;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.DefaultTokenChargeRecorder;
import org.javai.punit.controls.pacing.PacingConfiguration;
import org.javai.punit.controls.pacing.PacingResolver;
import org.javai.punit.experiment.engine.FactorSourceAdapter;
import org.javai.punit.experiment.engine.input.InputParameterDetector;
import org.javai.punit.experiment.engine.input.InputSourceResolver;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.ptest.concurrent.BudgetExhaustionHandler;
import org.javai.punit.ptest.concurrent.ConcurrentSampleOrchestrator;
import org.javai.punit.ptest.concurrent.ReflectiveSampleInvoker;
import org.javai.punit.ptest.concurrent.SampleInvoker;
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
 * <p>All sample execution uses the concurrent infrastructure via
 * {@link ConcurrentSampleOrchestrator}, which handles both sequential
 * ({@code maxConcurrent = 1}) and concurrent ({@code maxConcurrent > 1})
 * execution through a single code path.
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
        int samples = bernoulliConfig.samples();

        // Get the terminated flag from the store
        AtomicBoolean terminated = store.get("terminated", AtomicBoolean.class);
        if (terminated == null) {
            terminated = new AtomicBoolean(false);
            store.put("terminated", terminated);
        }

        // Resolve @InputSource and store inputs for use in intercept()
        Method testMethod = context.getRequiredTestMethod();
        InputSource inputSource = testMethod.getAnnotation(InputSource.class);
        if (inputSource != null) {
            Class<?> inputType = InputParameterDetector.findInputParameterType(testMethod);
            InputSourceResolver resolver = new InputSourceResolver();
            List<Object> inputs = resolver.resolve(inputSource, context.getRequiredTestClass(), inputType);
            if (inputs.isEmpty()) {
                throw new org.junit.jupiter.api.extension.ExtensionConfigurationException(
                        "@InputSource resolved to empty list");
            }
            store.put("inputs", inputs);
            store.put("inputType", inputType);
        }

        // Return a single invocation context — the interceptor handles all execution internally
        return Stream.of(new ProbabilisticTestInvocationContext(1, samples, null));
    }

    @Override
    public InterceptResult intercept(
            Invocation<Void> invocation,
            SampleExecutionContext executionContext) throws Throwable {

        // Skip the JUnit invocation — we handle execution ourselves
        invocation.skip();

        BernoulliTrialsConfig config = (BernoulliTrialsConfig) executionContext.config();
        SampleResultAggregator aggregator = executionContext.aggregator();
        EarlyTerminationEvaluator evaluator = executionContext.evaluator();
        AtomicBoolean terminated = executionContext.terminated();
        ExtensionContext extensionContext = executionContext.extensionContext();

        // Resolve test instance and method for reflective invocation
        Object testInstance = extensionContext.getRequiredTestInstance();
        Method testMethod = extensionContext.getRequiredTestMethod();
        testMethod.setAccessible(true);

        // Resolve inputs from store
        ExtensionContext.Store store = extensionContext.getStore(
                ExtensionContext.Namespace.create(
                        org.javai.punit.ptest.engine.ProbabilisticTestExtension.class));
        @SuppressWarnings("unchecked")
        List<Object> inputs = (List<Object>) store.get("inputs");
        Class<?> inputType = store.get("inputType", Class.class);
        int inputCount = inputs != null ? inputs.size() : 0;

        boolean hasTokenRecorderParam = hasTokenChargeRecorderParameter(testMethod);
        int maxConcurrent = config.useCaseAttributes().maxConcurrent();

        // Warmup phase — sequential, before concurrent execution
        int warmup = config.warmup();
        if (warmup > 0) {
            Optional<InterceptResult> warmupResult = executeWarmupPhase(
                    warmup, testInstance, testMethod, inputs, inputType,
                    hasTokenRecorderParam, config, executionContext);
            if (warmupResult.isPresent()) {
                terminated.set(true);
                return warmupResult.get();
            }
        }

        // Create the sample invoker
        SampleInvoker invoker = new ReflectiveSampleInvoker(
                testInstance, testMethod, config.onException(),
                inputs, inputType, hasTokenRecorderParam, config.tokenBudget());

        // Create budget exhaustion handler
        BudgetExhaustionHandler budgetExhaustionHandler = createBudgetExhaustionHandler(
                config, executionContext);

        // Create and run the concurrent orchestrator
        ConcurrentSampleOrchestrator orchestrator = new ConcurrentSampleOrchestrator(
                maxConcurrent, aggregator, evaluator, budgetOrchestrator,
                executionContext.methodBudget(), executionContext.classBudget(),
                executionContext.suiteBudget(), invoker, budgetExhaustionHandler, null);

        ConcurrentSampleOrchestrator.ExecutionResult result =
                orchestrator.execute(config.samples(), inputCount);

        terminated.set(true);

        // Convert orchestrator result to InterceptResult
        if (result.wasAborted()) {
            sampleExecutor.prepareForAbort(aggregator);
            return InterceptResult.abort(result.abortResult().abortException());
        }

        TerminationReason reason = aggregator.getTerminationReason().orElse(TerminationReason.COMPLETED);
        return InterceptResult.terminate(reason, aggregator.getTerminationDetails());
    }

    /**
     * Executes warmup invocations sequentially before the concurrent phase.
     *
     * @return the termination result if budget is exhausted during warmup, or empty to proceed
     */
    private Optional<InterceptResult> executeWarmupPhase(
            int warmupCount,
            Object testInstance,
            Method testMethod,
            List<Object> inputs,
            Class<?> inputType,
            boolean hasTokenRecorderParam,
            BernoulliTrialsConfig config,
            SampleExecutionContext executionContext) {

        for (int i = 0; i < warmupCount; i++) {
            // Pre-warmup budget check
            BudgetOrchestrator.BudgetCheckResult preSampleCheck = budgetOrchestrator.checkBeforeSample(
                    executionContext.suiteBudget(),
                    executionContext.classBudget(),
                    executionContext.methodBudget());
            if (preSampleCheck.shouldTerminate()) {
                return handleBudgetExhaustion(preSampleCheck, executionContext, config,
                        executionContext.aggregator(), executionContext.terminated());
            }

            // Execute warmup invocation reflectively — results discarded
            Object input = inputs != null && !inputs.isEmpty() ? inputs.get(0) : null;
            executeWarmupReflectively(testInstance, testMethod, input, inputType, hasTokenRecorderParam);

            // Post-warmup budget check
            BudgetOrchestrator.BudgetCheckResult postSampleCheck = budgetOrchestrator.checkAfterSample(
                    executionContext.suiteBudget(),
                    executionContext.classBudget(),
                    executionContext.methodBudget());
            if (postSampleCheck.shouldTerminate()) {
                return handleBudgetExhaustion(postSampleCheck, executionContext, config,
                        executionContext.aggregator(), executionContext.terminated());
            }
        }
        return Optional.empty();
    }

    /**
     * Executes a single warmup invocation reflectively, discarding results.
     */
    private void executeWarmupReflectively(
            Object testInstance, Method testMethod,
            Object input, Class<?> inputType, boolean hasTokenRecorderParam) {
        AssertionScope.begin();
        try {
            Object[] args = buildWarmupArguments(testMethod, input, inputType, hasTokenRecorderParam);
            if (args.length == 0) {
                testMethod.invoke(testInstance);
            } else {
                testMethod.invoke(testInstance, args);
            }
        } catch (Throwable t) {
            // Silently discard — warmup failures are not recorded
        } finally {
            AssertionScope.end();
        }
    }

    private Object[] buildWarmupArguments(Method testMethod, Object input,
                                          Class<?> inputType, boolean hasTokenRecorderParam) {
        Parameter[] params = testMethod.getParameters();
        if (params.length == 0) {
            return new Object[0];
        }
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> paramType = params[i].getType();
            if (TokenChargeRecorder.class.isAssignableFrom(paramType) && hasTokenRecorderParam) {
                args[i] = new DefaultTokenChargeRecorder(0);
            } else if (inputType != null && paramType.isAssignableFrom(inputType) && input != null) {
                args[i] = input;
            }
        }
        return args;
    }

    /**
     * Creates a BudgetExhaustionHandler that delegates to the orchestrator's budget policy.
     */
    private BudgetExhaustionHandler createBudgetExhaustionHandler(
            BernoulliTrialsConfig config,
            SampleExecutionContext executionContext) {
        return (checkResult, aggregator) -> {
            TerminationReason reason = checkResult.terminationReason()
                    .orElseThrow(() -> new IllegalStateException(
                            "Reason must be present when shouldTerminate is true"));
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
            if (behavior == BudgetExhaustedBehavior.FAIL) {
                aggregator.setForcedFailure(true);
            }
        };
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

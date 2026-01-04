package org.javai.punit.engine;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.model.TerminationReason;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * JUnit 5 extension that implements probabilistic test execution.
 *
 * <p>This extension:
 * <ul>
 *   <li>Generates N sample invocations based on resolved configuration</li>
 *   <li>Catches assertion failures and records them without failing the individual sample</li>
 *   <li>Aggregates results and determines final pass/fail based on observed pass rate</li>
 *   <li>Terminates early when success becomes mathematically impossible</li>
 *   <li>Monitors and enforces time and token budgets at method, class, and suite levels</li>
 *   <li>Supports dynamic token charging via TokenChargeRecorder injection</li>
 *   <li>Supports configuration overrides via system properties and environment variables</li>
 *   <li>Publishes structured statistics via {@link org.junit.jupiter.api.TestReporter}</li>
 * </ul>
 * 
 * <h2>Budget Scope Precedence</h2>
 * <p>Budgets are checked in order: suite → class → method. The first exhausted
 * budget triggers termination.
 */
public class ProbabilisticTestExtension implements
        TestTemplateInvocationContextProvider,
        InvocationInterceptor {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ProbabilisticTestExtension.class);

    private static final String AGGREGATOR_KEY = "aggregator";
    private static final String CONFIG_KEY = "config";
    private static final String EVALUATOR_KEY = "evaluator";
    private static final String BUDGET_MONITOR_KEY = "budgetMonitor";
    private static final String TOKEN_RECORDER_KEY = "tokenRecorder";
    private static final String TERMINATED_KEY = "terminated";

    private final ConfigurationResolver configResolver;

    /**
     * Default constructor using standard configuration resolver.
     */
    public ProbabilisticTestExtension() {
        this(new ConfigurationResolver());
    }

    /**
     * Constructor for testing with custom configuration resolver.
     */
    ProbabilisticTestExtension(ConfigurationResolver configResolver) {
        this.configResolver = configResolver;
    }

    // ========== TestTemplateInvocationContextProvider ==========

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(m -> AnnotationSupport.isAnnotated(m, ProbabilisticTest.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {
        
        Method testMethod = context.getRequiredTestMethod();
        ProbabilisticTest annotation = testMethod.getAnnotation(ProbabilisticTest.class);
        
        // Resolve configuration with precedence: system prop > env var > annotation
        ConfigurationResolver.ResolvedConfiguration resolved;
        try {
            resolved = configResolver.resolve(annotation, testMethod.getName());
        } catch (IllegalArgumentException e) {
            throw new ExtensionConfigurationException(e.getMessage(), e);
        }
        
        // Detect token charging mode
        boolean hasTokenRecorderParam = hasTokenChargeRecorderParameter(testMethod);
        CostBudgetMonitor.TokenMode tokenMode = determineTokenMode(resolved, hasTokenRecorderParam);
        
        // Create method-level budget monitor
        CostBudgetMonitor budgetMonitor = new CostBudgetMonitor(
                resolved.timeBudgetMs(),
                resolved.tokenBudget(),
                resolved.tokenCharge(),
                tokenMode,
                resolved.onBudgetExhausted()
        );
        
        // Create token recorder if dynamic mode
        DefaultTokenChargeRecorder tokenRecorder = tokenMode == CostBudgetMonitor.TokenMode.DYNAMIC
                ? new DefaultTokenChargeRecorder(resolved.tokenBudget())
                : null;
        
        // Store configuration and create components
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        TestConfiguration config = createTestConfiguration(resolved, tokenMode);
        SampleResultAggregator aggregator = new SampleResultAggregator(resolved.samples(), resolved.maxExampleFailures());
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(resolved.samples(), resolved.minPassRate());
        AtomicBoolean terminated = new AtomicBoolean(false);
        
        store.put(CONFIG_KEY, config);
        store.put(AGGREGATOR_KEY, aggregator);
        store.put(EVALUATOR_KEY, evaluator);
        store.put(BUDGET_MONITOR_KEY, budgetMonitor);
        store.put(TERMINATED_KEY, terminated);
        if (tokenRecorder != null) {
            store.put(TOKEN_RECORDER_KEY, tokenRecorder);
        }
        
        // Generate stream of invocation contexts with early termination support
        return createSampleStream(resolved.samples(), terminated, tokenRecorder);
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

    private TestConfiguration createTestConfiguration(
            ConfigurationResolver.ResolvedConfiguration resolved,
            CostBudgetMonitor.TokenMode tokenMode) {
        
        return new TestConfiguration(
                resolved.samples(),
                resolved.minPassRate(),
                resolved.appliedMultiplier(),
                resolved.timeBudgetMs(),
                resolved.tokenCharge(),
                resolved.tokenBudget(),
                tokenMode,
                resolved.onBudgetExhausted(),
                resolved.onException(),
                resolved.maxExampleFailures()
        );
    }

    /**
     * Creates a stream of sample invocation contexts that can be short-circuited
     * when early termination is triggered.
     */
    private Stream<TestTemplateInvocationContext> createSampleStream(
            int samples, AtomicBoolean terminated, DefaultTokenChargeRecorder tokenRecorder) {
        
        return Stream.iterate(1, i -> i + 1)
                .limit(samples)
                .takeWhile(i -> !terminated.get())
                .map(sampleIndex -> new ProbabilisticTestInvocationContext(sampleIndex, samples, tokenRecorder));
    }

    // ========== InvocationInterceptor ==========

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                             ReflectiveInvocationContext<Method> invocationContext,
                                             ExtensionContext extensionContext) throws Throwable {
        
        SampleResultAggregator aggregator = getAggregator(extensionContext);
        TestConfiguration config = getConfiguration(extensionContext);
        EarlyTerminationEvaluator evaluator = getEvaluator(extensionContext);
        CostBudgetMonitor budgetMonitor = getBudgetMonitor(extensionContext);
        DefaultTokenChargeRecorder tokenRecorder = getTokenRecorder(extensionContext);
        AtomicBoolean terminated = getTerminatedFlag(extensionContext);
        
        // Get class and suite budget monitors
        SharedBudgetMonitor classBudgetMonitor = ProbabilisticTestBudgetExtension.getClassBudgetMonitor(extensionContext);
        SharedBudgetMonitor suiteBudgetMonitor = SuiteBudgetManager.getMonitor();
        
        // If already terminated, skip execution
        if (terminated.get()) {
            invocation.skip();
            return;
        }
        
        // Pre-sample budget checks (suite → class → method precedence)
        Optional<TerminationReason> preSampleTermination = checkAllBudgetsBeforeSample(
                suiteBudgetMonitor, classBudgetMonitor, budgetMonitor);
        if (preSampleTermination.isPresent()) {
            BudgetExhaustedBehavior behavior = determineBehavior(
                    preSampleTermination.get(), suiteBudgetMonitor, classBudgetMonitor, config);
            handleBudgetExhaustion(extensionContext, aggregator, config, budgetMonitor,
                    classBudgetMonitor, suiteBudgetMonitor, preSampleTermination.get(), behavior, terminated);
            invocation.skip();
            return;
        }
        
        // Reset token recorder for new sample
        if (tokenRecorder != null) {
            tokenRecorder.resetForNextSample();
        }
        
        // Track sample failure for IDE display purposes
        Throwable sampleFailure = null;
        
        // Execute the sample
        try {
            invocation.proceed();
            aggregator.recordSuccess();
        } catch (AssertionError e) {
            aggregator.recordFailure(e);
            sampleFailure = e;
        } catch (Throwable t) {
            if (config.onException() == ExceptionHandling.ABORT_TEST) {
                // Immediate abort
                aggregator.recordFailure(t);
                aggregator.setTerminated(TerminationReason.COMPLETED, "Test aborted due to exception");
                terminated.set(true);
                finalizeProbabilisticTest(extensionContext, aggregator, config, budgetMonitor,
                        classBudgetMonitor, suiteBudgetMonitor);
                throw t;
            }
            // FAIL_SAMPLE: record and continue
            aggregator.recordFailure(t);
            sampleFailure = t;
        }
        
        // Post-sample processing: record tokens and propagate to all scopes
        recordAndPropagateTokens(tokenRecorder, budgetMonitor, config,
                classBudgetMonitor, suiteBudgetMonitor);
        
        // Post-sample budget checks (dynamic mode)
        Optional<TerminationReason> postSampleTermination = checkAllBudgetsAfterSample(
                suiteBudgetMonitor, classBudgetMonitor, budgetMonitor);
        if (postSampleTermination.isPresent()) {
            BudgetExhaustedBehavior behavior = determineBehavior(
                    postSampleTermination.get(), suiteBudgetMonitor, classBudgetMonitor, config);
            handleBudgetExhaustion(extensionContext, aggregator, config, budgetMonitor,
                    classBudgetMonitor, suiteBudgetMonitor, postSampleTermination.get(), behavior, terminated);
            return;
        }
        
        // Check for early termination (impossibility or success guaranteed)
        Optional<TerminationReason> earlyTerminationReason = evaluator.shouldTerminate(
                aggregator.getSuccesses(), aggregator.getSamplesExecuted());
        
        if (earlyTerminationReason.isPresent()) {
            TerminationReason reason = earlyTerminationReason.get();
            String details = evaluator.buildExplanation(reason,
                    aggregator.getSuccesses(), aggregator.getSamplesExecuted());
            
            aggregator.setTerminated(reason, details);
            terminated.set(true);
            finalizeProbabilisticTest(extensionContext, aggregator, config, budgetMonitor,
                    classBudgetMonitor, suiteBudgetMonitor);
            // finalizeProbabilisticTest throws if test failed, so we won't reach here unless test passed
        } else if (aggregator.getSamplesExecuted() >= config.samples()) {
            // All samples completed normally
            aggregator.setCompleted();
            finalizeProbabilisticTest(extensionContext, aggregator, config, budgetMonitor,
                    classBudgetMonitor, suiteBudgetMonitor);
            // finalizeProbabilisticTest throws if test failed, so we won't reach here unless test passed
        }
        
        // If this sample failed but we're not terminating the test yet,
        // re-throw the original error to show this sample as "failed" (❌) in the IDE
        // rather than "passed" (✅). This provides accurate visual feedback that the
        // sample failed, while subsequent samples will still execute (JUnit TestTemplate
        // invocations are independent).
        if (sampleFailure != null && !terminated.get()) {
            throw sampleFailure;
        }
    }

    /**
     * Checks all budget scopes before a sample (suite → class → method).
     */
    private Optional<TerminationReason> checkAllBudgetsBeforeSample(
            SharedBudgetMonitor suiteBudget,
            SharedBudgetMonitor classBudget,
            CostBudgetMonitor methodBudget) {
        
        // 1. Suite-level time budget
        if (suiteBudget != null) {
            Optional<TerminationReason> reason = suiteBudget.checkTimeBudget();
            if (reason.isPresent()) return reason;
            
            reason = suiteBudget.checkTokenBudget();
            if (reason.isPresent()) return reason;
        }
        
        // 2. Class-level time budget
        if (classBudget != null) {
            Optional<TerminationReason> reason = classBudget.checkTimeBudget();
            if (reason.isPresent()) return reason;
            
            reason = classBudget.checkTokenBudget();
            if (reason.isPresent()) return reason;
        }
        
        // 3. Method-level budgets
        Optional<TerminationReason> reason = methodBudget.checkTimeBudget();
        if (reason.isPresent()) return reason;
        
        return methodBudget.checkTokenBudgetBeforeSample();
    }

    /**
     * Checks all budget scopes after a sample (for dynamic token mode).
     */
    private Optional<TerminationReason> checkAllBudgetsAfterSample(
            SharedBudgetMonitor suiteBudget,
            SharedBudgetMonitor classBudget,
            CostBudgetMonitor methodBudget) {
        
        // Check in order: suite → class → method
        if (suiteBudget != null) {
            Optional<TerminationReason> reason = suiteBudget.checkTokenBudget();
            if (reason.isPresent()) return reason;
        }
        
        if (classBudget != null) {
            Optional<TerminationReason> reason = classBudget.checkTokenBudget();
            if (reason.isPresent()) return reason;
        }
        
        return methodBudget.checkTokenBudgetAfterSample();
    }

    /**
     * Records tokens and propagates consumption to all active scopes.
     */
    private long recordAndPropagateTokens(DefaultTokenChargeRecorder tokenRecorder,
                                          CostBudgetMonitor methodBudget,
                                          TestConfiguration config,
                                          SharedBudgetMonitor classBudget,
                                          SharedBudgetMonitor suiteBudget) {
        long sampleTokens = 0;
        
        if (tokenRecorder != null) {
            sampleTokens = tokenRecorder.finalizeSample();
            methodBudget.recordDynamicTokens(sampleTokens);
        } else if (config.tokenMode() == CostBudgetMonitor.TokenMode.STATIC) {
            sampleTokens = config.tokenCharge();
            methodBudget.recordStaticTokenCharge();
        }
        
        // Propagate to class and suite scopes
        if (sampleTokens > 0) {
            if (classBudget != null) {
                classBudget.addTokens(sampleTokens);
            }
            if (suiteBudget != null) {
                suiteBudget.addTokens(sampleTokens);
            }
        }
        
        return sampleTokens;
    }

    /**
     * Determines the budget exhaustion behavior based on the scope that triggered it.
     */
    private BudgetExhaustedBehavior determineBehavior(TerminationReason reason,
                                                       SharedBudgetMonitor suiteBudget,
                                                       SharedBudgetMonitor classBudget,
                                                       TestConfiguration config) {
        if (reason.name().startsWith("SUITE_") && suiteBudget != null) {
            return suiteBudget.getOnBudgetExhausted();
        } else if (reason.name().startsWith("CLASS_") && classBudget != null) {
            return classBudget.getOnBudgetExhausted();
        } else {
            return config.onBudgetExhausted();
        }
    }

    private void handleBudgetExhaustion(ExtensionContext context,
                                        SampleResultAggregator aggregator,
                                        TestConfiguration config,
                                        CostBudgetMonitor methodBudget,
                                        SharedBudgetMonitor classBudget,
                                        SharedBudgetMonitor suiteBudget,
                                        TerminationReason reason,
                                        BudgetExhaustedBehavior behavior,
                                        AtomicBoolean terminated) {
        
        String details = buildBudgetExhaustionMessage(reason, methodBudget, classBudget, suiteBudget);
        aggregator.setTerminated(reason, details);
        terminated.set(true);
        
        // If FAIL behavior, force a failure regardless of pass rate
        if (behavior == BudgetExhaustedBehavior.FAIL) {
            aggregator.setForcedFailure(true);
        }
        
        finalizeProbabilisticTest(context, aggregator, config, methodBudget, classBudget, suiteBudget);
    }

    private String buildBudgetExhaustionMessage(TerminationReason reason,
                                                 CostBudgetMonitor methodBudget,
                                                 SharedBudgetMonitor classBudget,
                                                 SharedBudgetMonitor suiteBudget) {
        switch (reason) {
            case SUITE_TIME_BUDGET_EXHAUSTED:
                return suiteBudget != null
                        ? String.format("Suite time budget exhausted: %dms elapsed >= %dms budget",
                                suiteBudget.getElapsedMs(), suiteBudget.getTimeBudgetMs())
                        : reason.getDescription();
            case SUITE_TOKEN_BUDGET_EXHAUSTED:
                return suiteBudget != null
                        ? String.format("Suite token budget exhausted: %d tokens >= %d budget",
                                suiteBudget.getTokensConsumed(), suiteBudget.getTokenBudget())
                        : reason.getDescription();
            case CLASS_TIME_BUDGET_EXHAUSTED:
                return classBudget != null
                        ? String.format("Class time budget exhausted: %dms elapsed >= %dms budget",
                                classBudget.getElapsedMs(), classBudget.getTimeBudgetMs())
                        : reason.getDescription();
            case CLASS_TOKEN_BUDGET_EXHAUSTED:
                return classBudget != null
                        ? String.format("Class token budget exhausted: %d tokens >= %d budget",
                                classBudget.getTokensConsumed(), classBudget.getTokenBudget())
                        : reason.getDescription();
            case METHOD_TIME_BUDGET_EXHAUSTED:
                return String.format("Method time budget exhausted: %dms elapsed >= %dms budget",
                        methodBudget.getElapsedMs(), methodBudget.getTimeBudgetMs());
            case METHOD_TOKEN_BUDGET_EXHAUSTED:
                return String.format("Method token budget exhausted: %d tokens >= %d budget",
                        methodBudget.getTokensConsumed(), methodBudget.getTokenBudget());
            default:
                return reason.getDescription();
        }
    }

    private void finalizeProbabilisticTest(ExtensionContext context,
                                           SampleResultAggregator aggregator,
                                           TestConfiguration config,
                                           CostBudgetMonitor methodBudget,
                                           SharedBudgetMonitor classBudget,
                                           SharedBudgetMonitor suiteBudget) {
        
        FinalVerdictDecider decider = new FinalVerdictDecider();
        boolean passed = !aggregator.isForcedFailure() && decider.isPassing(aggregator, config.minPassRate());
        
        // Publish structured results via TestReporter
        publishResults(context, aggregator, config, methodBudget, classBudget, suiteBudget, passed);
        
        // Throw assertion error if test failed
        if (!passed) {
            String message = decider.buildFailureMessage(aggregator, config.minPassRate());
            AssertionError error = new AssertionError(message);
            
            // Add example failures as suppressed exceptions
            for (Throwable failure : aggregator.getExampleFailures()) {
                error.addSuppressed(failure);
            }
            
            throw error;
        }
    }

    private void publishResults(ExtensionContext context,
                                SampleResultAggregator aggregator,
                                TestConfiguration config,
                                CostBudgetMonitor methodBudget,
                                SharedBudgetMonitor classBudget,
                                SharedBudgetMonitor suiteBudget,
                                boolean passed) {
        
        TerminationReason reason = aggregator.getTerminationReason();
        String terminationReasonStr = reason != null ? reason.name() : TerminationReason.COMPLETED.name();
        
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("punit.samples", String.valueOf(config.samples()));
        entries.put("punit.samplesExecuted", String.valueOf(aggregator.getSamplesExecuted()));
        entries.put("punit.successes", String.valueOf(aggregator.getSuccesses()));
        entries.put("punit.failures", String.valueOf(aggregator.getFailures()));
        entries.put("punit.minPassRate", String.format("%.4f", config.minPassRate()));
        entries.put("punit.observedPassRate", String.format("%.4f", aggregator.getObservedPassRate()));
        entries.put("punit.verdict", passed ? "PASS" : "FAIL");
        entries.put("punit.terminationReason", terminationReasonStr);
        entries.put("punit.elapsedMs", String.valueOf(aggregator.getElapsedMs()));
        
        // Include multiplier info if one was applied
        if (config.hasMultiplier()) {
            entries.put("punit.samplesMultiplier", String.format("%.2f", config.appliedMultiplier()));
        }
        
        // Include method-level budget info
        if (config.hasTimeBudget()) {
            entries.put("punit.method.timeBudgetMs", String.valueOf(config.timeBudgetMs()));
        }
        if (config.hasTokenBudget()) {
            entries.put("punit.method.tokenBudget", String.valueOf(config.tokenBudget()));
        }
        entries.put("punit.method.tokensConsumed", String.valueOf(methodBudget.getTokensConsumed()));
        
        if (config.tokenMode() != CostBudgetMonitor.TokenMode.NONE) {
            entries.put("punit.tokenMode", config.tokenMode().name());
        }
        
        // Include class-level budget info
        if (classBudget != null) {
            if (classBudget.hasTimeBudget()) {
                entries.put("punit.class.timeBudgetMs", String.valueOf(classBudget.getTimeBudgetMs()));
                entries.put("punit.class.elapsedMs", String.valueOf(classBudget.getElapsedMs()));
            }
            if (classBudget.hasTokenBudget()) {
                entries.put("punit.class.tokenBudget", String.valueOf(classBudget.getTokenBudget()));
            }
            entries.put("punit.class.tokensConsumed", String.valueOf(classBudget.getTokensConsumed()));
        }
        
        // Include suite-level budget info
        if (suiteBudget != null) {
            if (suiteBudget.hasTimeBudget()) {
                entries.put("punit.suite.timeBudgetMs", String.valueOf(suiteBudget.getTimeBudgetMs()));
                entries.put("punit.suite.elapsedMs", String.valueOf(suiteBudget.getElapsedMs()));
            }
            if (suiteBudget.hasTokenBudget()) {
                entries.put("punit.suite.tokenBudget", String.valueOf(suiteBudget.getTokenBudget()));
            }
            entries.put("punit.suite.tokensConsumed", String.valueOf(suiteBudget.getTokensConsumed()));
        }
        
        context.publishReportEntry(entries);
    }

    // ========== Store Access Helpers ==========

    private SampleResultAggregator getAggregator(ExtensionContext context) {
        return getFromStoreOrParent(context, AGGREGATOR_KEY, SampleResultAggregator.class);
    }

    private TestConfiguration getConfiguration(ExtensionContext context) {
        return getFromStoreOrParent(context, CONFIG_KEY, TestConfiguration.class);
    }

    private EarlyTerminationEvaluator getEvaluator(ExtensionContext context) {
        return getFromStoreOrParent(context, EVALUATOR_KEY, EarlyTerminationEvaluator.class);
    }

    private CostBudgetMonitor getBudgetMonitor(ExtensionContext context) {
        return getFromStoreOrParent(context, BUDGET_MONITOR_KEY, CostBudgetMonitor.class);
    }

    private DefaultTokenChargeRecorder getTokenRecorder(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        DefaultTokenChargeRecorder recorder = store.get(TOKEN_RECORDER_KEY, DefaultTokenChargeRecorder.class);
        if (recorder != null) {
            return recorder;
        }
        return context.getParent()
                .map(parent -> parent.getStore(NAMESPACE).get(TOKEN_RECORDER_KEY, DefaultTokenChargeRecorder.class))
                .orElse(null);
    }

    private AtomicBoolean getTerminatedFlag(ExtensionContext context) {
        return getFromStoreOrParent(context, TERMINATED_KEY, AtomicBoolean.class);
    }

    private <T> T getFromStoreOrParent(ExtensionContext context, String key, Class<T> type) {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        T value = store.get(key, type);
        if (value != null) {
            return value;
        }
        
        // Try parent context (template context stores data at parent level)
        return context.getParent()
                .map(parent -> parent.getStore(NAMESPACE).get(key, type))
                .orElseThrow(() -> new IllegalStateException(
                        "Could not find " + key + " in extension context store"));
    }

    // ========== Inner Classes ==========

    /**
     * Holds the resolved test configuration.
     */
    private record TestConfiguration(
            int samples,
            double minPassRate,
            double appliedMultiplier,
            long timeBudgetMs,
            int tokenCharge,
            long tokenBudget,
            CostBudgetMonitor.TokenMode tokenMode,
            BudgetExhaustedBehavior onBudgetExhausted,
            ExceptionHandling onException,
            int maxExampleFailures
    ) {
        boolean hasMultiplier() {
            return appliedMultiplier != 1.0;
        }
        
        boolean hasTimeBudget() {
            return timeBudgetMs > 0;
        }
        
        boolean hasTokenBudget() {
            return tokenBudget > 0;
        }
    }

	/**
	 * Invocation context for a single sample execution.
	 * Provides ParameterResolver for TokenChargeRecorder injection.
	 */
	private record ProbabilisticTestInvocationContext(int sampleIndex, int totalSamples,
													  DefaultTokenChargeRecorder tokenRecorder) implements TestTemplateInvocationContext {

		@Override
		public String getDisplayName(int invocationIndex) {
			return String.format("Sample %d/%d", sampleIndex, totalSamples);
		}

		@Override
		public List<Extension> getAdditionalExtensions() {
			if (tokenRecorder != null) {
				return List.of(new TokenChargeRecorderParameterResolver(tokenRecorder));
			}
			return Collections.emptyList();
		}
	}

	/**
	 * ParameterResolver for injecting TokenChargeRecorder into test methods.
	 */
	private record TokenChargeRecorderParameterResolver(TokenChargeRecorder recorder) implements ParameterResolver {

		@Override
		public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
			return TokenChargeRecorder.class.isAssignableFrom(parameterContext.getParameter().getType());
		}

		@Override
		public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
			return recorder;
		}
	}
}

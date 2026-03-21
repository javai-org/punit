package org.javai.punit.ptest.engine;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.controls.budget.DefaultTokenChargeRecorder;
import org.javai.punit.controls.budget.ProbabilisticTestBudgetExtension;
import org.javai.punit.controls.budget.SharedBudgetMonitor;
import org.javai.punit.controls.budget.SuiteBudgetManager;
import org.javai.punit.controls.pacing.PacingConfiguration;
import org.javai.punit.controls.pacing.PacingReporter;
import org.javai.punit.controls.pacing.PacingResolver;
import org.javai.punit.ptest.bernoulli.BernoulliTrialsConfig;
import org.javai.punit.ptest.bernoulli.BernoulliTrialsStrategy;
import org.javai.punit.ptest.bernoulli.EarlyTerminationEvaluator;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;
import org.javai.punit.statistics.LatencyDistribution;
import org.javai.punit.ptest.strategy.InterceptResult;
import org.javai.punit.ptest.strategy.ProbabilisticTestStrategy;
import org.javai.punit.ptest.strategy.SampleExecutionContext;
import org.opentest4j.TestAbortedException;
import org.javai.punit.report.ReportConfiguration;
import org.javai.punit.report.VerdictXmlSink;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdictBuilder;
import org.javai.punit.spec.baseline.BaselineRepository;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.BaselineSource;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.SelectionResult;
import org.javai.punit.spec.baseline.BaselineSelector;
import org.javai.punit.spec.baseline.FootprintComputer;
import org.javai.punit.spec.baseline.covariate.CovariateProfileResolver;
import org.javai.punit.spec.baseline.covariate.UseCaseCovariateExtractor;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.statistics.VerificationFeasibilityEvaluator;
import org.javai.punit.statistics.transparent.BaselineData;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.AnnotationSupport;

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

	private static final Logger logger = LogManager.getLogger(ProbabilisticTestExtension.class);
	private static final PUnitReporter reporter = new PUnitReporter();
	private static final ProbabilisticTestValidator testValidator = new ProbabilisticTestValidator();
	private static final FinalConfigurationLogger configurationLogger = new FinalConfigurationLogger(reporter);
	private static final SampleFailureFormatter sampleFailureFormatter = new SampleFailureFormatter();
	private static final ResultPublisher resultPublisher = new ResultPublisher(reporter);
	private static final VerdictXmlSink xmlSink = new VerdictXmlSink(ReportConfiguration.resolve());

	private static final String AGGREGATOR_KEY = "aggregator";
	private static final String CONFIG_KEY = "config";
	private static final String EVALUATOR_KEY = "evaluator";
	private static final String BUDGET_MONITOR_KEY = "budgetMonitor";
	private static final String TOKEN_RECORDER_KEY = "tokenRecorder";
	private static final String TERMINATED_KEY = "terminated";
	private static final String PACING_KEY = "pacing";
	private static final String SAMPLE_COUNTER_KEY = "sampleCounter";
	private static final String LAST_SAMPLE_TIME_KEY = "lastSampleTime";
	private static final String SPEC_KEY = "spec";
	private static final String SELECTION_RESULT_KEY = "selectionResult";
	private static final String WARMUP_COUNTER_KEY = "warmupCounter";
	private static final String PENDING_SELECTION_KEY = "pendingSelection";
	private static final String STRATEGY_CONFIG_KEY = "strategyConfig";
	private static final String THRESHOLD_DERIVED_KEY = "thresholdDerived";
	private static final String LATENCY_CONFIG_KEY = "latencyConfig";
	private static final String RESOLVED_LATENCY_THRESHOLDS_KEY = "resolvedLatencyThresholds";

	// Strategy for test execution (currently only Bernoulli trials supported)
	private final ProbabilisticTestStrategy strategy;

	private final ConfigurationResolver configResolver;
	private final BaselineRepository baselineRepository;
	private final BaselineSelector baselineSelector;
	private final CovariateProfileResolver covariateProfileResolver;
	private final FootprintComputer footprintComputer;
	private final UseCaseCovariateExtractor covariateExtractor;
	private final PacingResolver pacingResolver;
	private final PacingReporter pacingReporter;
	private final BaselineSelectionOrchestrator baselineOrchestrator;

	/**
	 * Default constructor using standard configuration resolver.
	 */
	public ProbabilisticTestExtension() {
		this(new ConfigurationResolver(), new PacingResolver(), new PacingReporter(),
			 new BaselineRepository(), new BaselineSelector(), new CovariateProfileResolver(),
			 new FootprintComputer(), new UseCaseCovariateExtractor(),
			 new BernoulliTrialsStrategy());
	}

	/**
	 * Constructor for testing with custom resolvers.
	 */
	ProbabilisticTestExtension(ConfigurationResolver configResolver) {
		this(configResolver, new PacingResolver(), new PacingReporter(),
			 new BaselineRepository(), new BaselineSelector(), new CovariateProfileResolver(),
			 new FootprintComputer(), new UseCaseCovariateExtractor(),
			 new BernoulliTrialsStrategy());
	}

	/**
	 * Constructor for testing with custom resolvers and reporter.
	 */
	ProbabilisticTestExtension(ConfigurationResolver configResolver,
							   PacingResolver pacingResolver,
							   PacingReporter pacingReporter) {
		this(configResolver, pacingResolver, pacingReporter,
			 new BaselineRepository(), new BaselineSelector(), new CovariateProfileResolver(),
			 new FootprintComputer(), new UseCaseCovariateExtractor(),
			 new BernoulliTrialsStrategy());
	}

	/**
	 * Full constructor for testing with all dependencies injectable.
	 */
	ProbabilisticTestExtension(ConfigurationResolver configResolver,
							   PacingResolver pacingResolver,
							   PacingReporter pacingReporter,
							   BaselineRepository baselineRepository,
							   BaselineSelector baselineSelector,
							   CovariateProfileResolver covariateProfileResolver,
							   FootprintComputer footprintComputer,
							   UseCaseCovariateExtractor covariateExtractor,
							   ProbabilisticTestStrategy strategy) {
		this.strategy = strategy;
		this.configResolver = configResolver;
		this.pacingResolver = pacingResolver;
		this.pacingReporter = pacingReporter;
		this.baselineRepository = baselineRepository;
		this.baselineSelector = baselineSelector;
		this.covariateProfileResolver = covariateProfileResolver;
		this.footprintComputer = footprintComputer;
		this.covariateExtractor = covariateExtractor;
		this.baselineOrchestrator = new BaselineSelectionOrchestrator(
				configResolver, baselineRepository, baselineSelector, covariateProfileResolver,
				footprintComputer, covariateExtractor, reporter);
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

		// Delegate configuration parsing to strategy
		BernoulliTrialsConfig strategyConfig = (BernoulliTrialsConfig) strategy.parseConfig(
				annotation, testMethod, configResolver);

		// Concurrency guards
		int maxConcurrent = strategyConfig.useCaseAttributes().maxConcurrent();
		if (maxConcurrent > 1) {
			String parallelEnabled = System.getProperty("junit.jupiter.execution.parallel.enabled");
			if ("true".equalsIgnoreCase(parallelEnabled)) {
				throw new ExtensionConfigurationException(
						"maxConcurrent > 1 is incompatible with JUnit 5 parallel test execution "
						+ "(junit.jupiter.execution.parallel.enabled=true). "
						+ "Disable JUnit parallelism or set maxConcurrent = 1.");
			}

			if (strategyConfig.hasPacing()) {
				throw new ExtensionConfigurationException(
						"Pacing and maxConcurrent > 1 are mutually exclusive. "
						+ "Use pacing to limit dispatch rate (sequential execution), "
						+ "or use maxConcurrent to limit concurrent invocations, but not both.");
			}
		}

		// @Timeout warning
		if (AnnotationSupport.isAnnotated(testMethod, org.junit.jupiter.api.Timeout.class)
				|| AnnotationSupport.isAnnotated(context.getRequiredTestClass(), org.junit.jupiter.api.Timeout.class)) {
			logger.warn("JUnit @Timeout is active on this probabilistic test. "
					+ "If the timeout fires, the statistical verdict will be incomplete. "
					+ "PUnit's budget mechanism is the recommended alternative.");
		}

		// Create method-level budget monitor
		CostBudgetMonitor budgetMonitor = new CostBudgetMonitor(
				strategyConfig.timeBudgetMs(),
				strategyConfig.tokenBudget(),
				strategyConfig.tokenCharge(),
				strategyConfig.tokenMode(),
				strategyConfig.onBudgetExhausted()
		);

		// Create token recorder if dynamic mode
		DefaultTokenChargeRecorder tokenRecorder = strategyConfig.tokenMode() == CostBudgetMonitor.TokenMode.DYNAMIC
				? new DefaultTokenChargeRecorder(strategyConfig.tokenBudget())
				: null;

		// Store configuration and create components
		ExtensionContext.Store store = context.getStore(NAMESPACE);
		SampleResultAggregator aggregator = new SampleResultAggregator(
				strategyConfig.samples(), strategyConfig.maxExampleFailures());
		EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(
				strategyConfig.samples(), strategyConfig.minPassRate());
		AtomicBoolean terminated = new AtomicBoolean(false);
		AtomicInteger sampleCounter = new AtomicInteger(0);
		AtomicInteger warmupCounter = new AtomicInteger(0);

		// Store strategy config and create TestConfiguration for backward compatibility
		store.put(STRATEGY_CONFIG_KEY, strategyConfig);
		TestConfiguration config = createTestConfigurationFromStrategy(strategyConfig);
		store.put(CONFIG_KEY, config);

		// Resolve latency assertion config
		LatencyAssertionConfig latencyConfig = LatencyAssertionConfig.fromAnnotation(
				annotation.latency());
		store.put(LATENCY_CONFIG_KEY, latencyConfig);
		store.put(AGGREGATOR_KEY, aggregator);
		store.put(EVALUATOR_KEY, evaluator);
		store.put(BUDGET_MONITOR_KEY, budgetMonitor);
		store.put(TERMINATED_KEY, terminated);
		store.put(PACING_KEY, strategyConfig.pacing());
		store.put(SAMPLE_COUNTER_KEY, sampleCounter);
		store.put(WARMUP_COUNTER_KEY, warmupCounter);
		if (tokenRecorder != null) {
			store.put(TOKEN_RECORDER_KEY, tokenRecorder);
		}

		// Prepare baseline selection data (selection is resolved lazily during first sample)
		prepareBaselineSelection(annotation, strategyConfig.specId(), store, context);

		// Print pre-flight report if pacing is configured
		if (strategyConfig.hasPacing()) {
			Instant startTime = Instant.now();
			store.put(LAST_SAMPLE_TIME_KEY, startTime);
			pacingReporter.printPreFlightReport(testMethod.getName(), strategyConfig.samples(),
					strategyConfig.pacing(), startTime);
			pacingReporter.printFeasibilityWarning(strategyConfig.pacing(),
					strategyConfig.timeBudgetMs(), strategyConfig.samples());
		}

		// Validate factor source consistency if applicable
		if (strategy instanceof BernoulliTrialsStrategy bernoulliStrategy) {
			bernoulliStrategy.validateFactorConsistency(testMethod, annotation,
					strategyConfig.samples(), configResolver)
				.ifPresent(result -> {
					var content = result.toWarningContent();
					reporter.reportWarn(content.title(), content.body());
				});
		}

		// Delegate sample stream generation to strategy
		return strategy.provideInvocationContexts(strategyConfig, context, store);
	}

	/**
	 * Creates a TestConfiguration from a BernoulliTrialsConfig for backward compatibility.
	 */
	private TestConfiguration createTestConfigurationFromStrategy(BernoulliTrialsConfig strategyConfig) {
		return new TestConfiguration(
				strategyConfig.samples(),
				strategyConfig.minPassRate(),
				strategyConfig.appliedMultiplier(),
				strategyConfig.timeBudgetMs(),
				strategyConfig.tokenCharge(),
				strategyConfig.tokenBudget(),
				strategyConfig.tokenMode(),
				strategyConfig.onBudgetExhausted(),
				strategyConfig.onException(),
				strategyConfig.maxExampleFailures(),
				strategyConfig.confidence(),
				strategyConfig.baselineRate(),
				strategyConfig.baselineSamples(),
				strategyConfig.specId(),
				strategyConfig.pacing(),
				strategyConfig.transparentStats(),
				strategyConfig.thresholdOrigin(),
				strategyConfig.contractRef(),
				strategyConfig.intent(),
				strategyConfig.resolvedConfidence()
		);
	}

	// ========== InvocationInterceptor ==========

	@Override
	public void interceptTestTemplateMethod(Invocation<Void> invocation,
											ReflectiveInvocationContext<Method> invocationContext,
											ExtensionContext extensionContext) throws Throwable {

		// Guard: when registered globally via auto-detection, this interceptor fires for
		// all test template methods including @MeasureExperiment etc. Delegate to the next
		// interceptor in the chain if this invocation doesn't belong to a probabilistic test.
		Method method = extensionContext.getRequiredTestMethod();
		if (!AnnotationSupport.isAnnotated(method, ProbabilisticTest.class)) {
			invocation.proceed();
			return;
		}

		// Ensure baseline selection is resolved lazily before first sample.
		// This must happen BEFORE getting config, as it may derive minPassRate from baseline.
		ensureBaselineSelected(extensionContext);

		// Get components from store
		BernoulliTrialsConfig strategyConfig = getStrategyConfig(extensionContext);
		SampleResultAggregator aggregator = getAggregator(extensionContext);
		TestConfiguration config = getConfiguration(extensionContext);
		EarlyTerminationEvaluator evaluator = getEvaluator(extensionContext);
		CostBudgetMonitor budgetMonitor = getBudgetMonitor(extensionContext);
		DefaultTokenChargeRecorder tokenRecorder = getTokenRecorder(extensionContext);
		AtomicBoolean terminated = getTerminatedFlag(extensionContext);

		// Get class and suite budget monitors
		SharedBudgetMonitor classBudgetMonitor = ProbabilisticTestBudgetExtension.getClassBudgetMonitor(extensionContext).orElse(null);
		SharedBudgetMonitor suiteBudgetMonitor = SuiteBudgetManager.getMonitor().orElse(null);

		// Note: SampleExecutionCondition handles skipping for terminated tests.
		// This check is kept as a safety net for edge cases.
		if (terminated.get()) {
			invocation.skip();
			return;
		}

		// Apply pacing delay if configured (skip for first sample)
		applyPacingDelay(extensionContext, config);

		// Get warmup counter
		AtomicInteger warmupCounter = getWarmupCounter(extensionContext);

		// Build execution context and delegate to strategy
		SampleExecutionContext executionContext = new SampleExecutionContext(
				strategyConfig, aggregator, evaluator, budgetMonitor,
				classBudgetMonitor, suiteBudgetMonitor, tokenRecorder,
				terminated, extensionContext, warmupCounter);

		InterceptResult result = strategy.intercept(invocation, executionContext);

		// Handle the result
		if (result.shouldAbort()) {
			// Abort - finalize and throw
			finalizeProbabilisticTest(extensionContext, aggregator, config, budgetMonitor,
					classBudgetMonitor, suiteBudgetMonitor);
			throw result.abortException();
		} else if (result.shouldTerminate()) {
			// Terminate - finalize the test
			// finalizeProbabilisticTest throws if test failed, so we won't reach after this unless test passed.
			// When test passes (e.g., SUCCESS_GUARANTEED), we don't rethrow sample failures because
			// the overall verdict is PASS - individual sample failures don't matter.
			finalizeProbabilisticTest(extensionContext, aggregator, config, budgetMonitor,
					classBudgetMonitor, suiteBudgetMonitor);
		} else {
			if (result.hasSampleFailure()) {
				// Mark failed samples as aborted so JUnit counts them as "skipped"
				// rather than "failed". This prevents CI from breaking on individual
				// sample failures — only the aggregate verdict (from finalizeProbabilisticTest)
				// determines pass/fail. The punitVerify task provides a second enforcement
				// point that reads verdict XMLs post-execution.
				String message = sampleFailureFormatter.formatSampleFailure(
						result.sampleFailure(),
						aggregator.getSuccesses(),
						aggregator.getSamplesExecuted(),
						config.samples(),
						config.minPassRate()
				);
				throw new TestAbortedException(message);
			}
		}
	}

	private void finalizeProbabilisticTest(ExtensionContext context,
										   SampleResultAggregator aggregator,
										   TestConfiguration config,
										   CostBudgetMonitor methodBudget,
										   SharedBudgetMonitor classBudget,
										   SharedBudgetMonitor suiteBudget) {

		// Delegate verdict computation to strategy (pass rate)
		BernoulliTrialsConfig strategyConfig = getStrategyConfig(context);

		// Guard: detect config sync failures between TestConfiguration and BernoulliTrialsConfig
		if (!Double.isNaN(config.minPassRate()) && Double.isNaN(strategyConfig.minPassRate())) {
			throw new IllegalStateException(
					"BUG: TestConfiguration.minPassRate was updated to " + config.minPassRate()
					+ " but BernoulliTrialsConfig.minPassRate is still NaN. "
					+ "This indicates a config sync failure in baseline derivation.");
		}

		boolean passRatePassed = strategy.computeVerdict(aggregator, strategyConfig);

		// Evaluate latency assertions
		LatencyAssertionResult latencyResult = evaluateLatency(context, aggregator);
		boolean latencyPassed = latencyResult.passed();
		LatencyAssertionConfig latencyConfig = getLatencyConfig(context);
		boolean hasExplicitThresholds = latencyConfig != null && latencyConfig.hasExplicitThresholds();
		boolean latencyEnforced = LatencyAssertionConfig.isEffectivelyEnforced(hasExplicitThresholds);
		boolean passed = passRatePassed && (latencyPassed || !latencyEnforced);

		// Publish structured results via TestReporter
		publishResults(context, aggregator, config, methodBudget, classBudget, suiteBudget,
				passed, latencyResult);

		// Throw assertion error if test failed
		if (!passed) {
			String message;
			if (!passRatePassed) {
				// Delegate failure message building to strategy
				message = strategy.buildFailureMessage(aggregator, strategyConfig);
			} else {
				// Pass rate passed but latency failed
				message = buildLatencyFailureMessage(latencyResult);
			}

			AssertionError error = new AssertionError(message);

			// Add example failures as suppressed exceptions
			for (Throwable failure : aggregator.getExampleFailures()) {
				error.addSuppressed(failure);
			}

			throw error;
		}
	}

	/**
	 * Resolves latency thresholds using the annotation config and baseline data.
	 * Stores the resolved thresholds in the extension store.
	 *
	 * <p>Resolution is triggered when explicit thresholds are present OR
	 * when the baseline contains latency data (automatic derivation).
	 * Skipped when latency is disabled or when neither source is available.
	 *
	 * <p>Per-dimension spec resolution: if the provided baseline does not contain
	 * latency data, attempts to load a dedicated latency spec via
	 * {@link ConfigurationResolver#loadLatencySpec(String)}.
	 */
	private void resolveLatencyThresholds(ExtensionContext.Store store,
										  ExecutionSpecification baseline,
										  ExtensionContext context) {
		LatencyAssertionConfig latencyConfig = store.get(LATENCY_CONFIG_KEY, LatencyAssertionConfig.class);
		if (latencyConfig == null) {
			// Try parent store
			latencyConfig = context.getParent()
					.map(parent -> parent.getStore(NAMESPACE).get(LATENCY_CONFIG_KEY, LatencyAssertionConfig.class))
					.orElse(null);
		}
		if (latencyConfig == null || latencyConfig.disabled()) {
			return;
		}

		// Attempt per-dimension latency spec resolution
		ExecutionSpecification latencySource = baseline;
		if ((latencySource == null || !latencySource.hasLatencyBaseline())) {
			TestConfiguration config = store.get(CONFIG_KEY, TestConfiguration.class);
			if (config != null && config.specId() != null) {
				Optional<ExecutionSpecification> latencySpec = configResolver.loadLatencySpec(config.specId());
				if (latencySpec.isPresent()) {
					latencySource = latencySpec.get();
				}
			}
		}

		boolean hasBaselineLatency = latencySource != null && latencySource.hasLatencyBaseline();
		if (!latencyConfig.hasExplicitThresholds() && !hasBaselineLatency) {
			return;
		}

		org.javai.punit.spec.model.LatencyBaseline latencyBaseline =
				hasBaselineLatency ? latencySource.getLatencyBaseline() : null;

		TestConfiguration config = store.get(CONFIG_KEY, TestConfiguration.class);
		double confidence = config != null ? config.resolvedConfidence() : 0.95;

		LatencyThresholdResolver resolver = new LatencyThresholdResolver();
		LatencyThresholdResolver.ResolvedThresholds resolved =
				resolver.resolve(latencyConfig, latencyBaseline, confidence);

		store.put(RESOLVED_LATENCY_THRESHOLDS_KEY, resolved);
	}

	/**
	 * Evaluates latency assertions using the aggregator's collected latencies.
	 */
	private LatencyAssertionResult evaluateLatency(ExtensionContext context,
												   SampleResultAggregator aggregator) {
		LatencyAssertionConfig latencyConfig = getLatencyConfig(context);
		if (latencyConfig == null || latencyConfig.disabled()) {
			return LatencyAssertionResult.notRequested();
		}

		// Use resolved thresholds if available (covers both explicit and baseline-derived),
		// fall back to raw config for explicit-only without baseline
		LatencyThresholdResolver.ResolvedThresholds resolvedThresholds = getResolvedLatencyThresholds(context);
		if (resolvedThresholds == null && !latencyConfig.hasExplicitThresholds()) {
			return LatencyAssertionResult.notRequested();
		}

		List<Long> latenciesMs = aggregator.getSuccessfulLatenciesMs();
		org.javai.punit.statistics.LatencyDistribution distribution = null;
		if (!latenciesMs.isEmpty()) {
			long[] millis = latenciesMs.stream().mapToLong(Long::longValue).toArray();
			distribution = org.javai.punit.statistics.LatencyDistribution.fromMillis(millis);
		}

		LatencyAssertionEvaluator evaluator = new LatencyAssertionEvaluator();
		if (resolvedThresholds != null) {
			return evaluator.evaluate(resolvedThresholds, distribution, latenciesMs.size());
		}
		return evaluator.evaluate(latencyConfig, distribution, latenciesMs.size());
	}

	private LatencyThresholdResolver.ResolvedThresholds getResolvedLatencyThresholds(ExtensionContext context) {
		ExtensionContext.Store store = context.getStore(NAMESPACE);
		var resolved = store.get(RESOLVED_LATENCY_THRESHOLDS_KEY, LatencyThresholdResolver.ResolvedThresholds.class);
		if (resolved != null) {
			return resolved;
		}
		return context.getParent()
				.map(parent -> parent.getStore(NAMESPACE).get(
						RESOLVED_LATENCY_THRESHOLDS_KEY, LatencyThresholdResolver.ResolvedThresholds.class))
				.orElse(null);
	}

	/**
	 * Builds a failure message when pass rate passed but latency failed.
	 */
	private String buildLatencyFailureMessage(LatencyAssertionResult result) {
		StringBuilder sb = new StringBuilder();
		sb.append("Latency assertion failed.\n");
		for (var pr : result.percentileResults()) {
			if (!pr.passed()) {
				sb.append(String.format("  %s: %dms > %dms (threshold exceeded)\n",
						pr.label(), pr.observedMs(), pr.thresholdMs()));
			}
		}
		return sb.toString();
	}

	private LatencyAssertionConfig getLatencyConfig(ExtensionContext context) {
		ExtensionContext.Store store = context.getStore(NAMESPACE);
		LatencyAssertionConfig config = store.get(LATENCY_CONFIG_KEY, LatencyAssertionConfig.class);
		if (config != null) {
			return config;
		}
		return context.getParent()
				.map(parent -> parent.getStore(NAMESPACE).get(LATENCY_CONFIG_KEY, LatencyAssertionConfig.class))
				.orElse(null);
	}

	private void publishResults(ExtensionContext context,
								SampleResultAggregator aggregator,
								TestConfiguration config,
								CostBudgetMonitor methodBudget,
								SharedBudgetMonitor classBudget,
								SharedBudgetMonitor suiteBudget,
								boolean passed,
								LatencyAssertionResult latencyResult) {

		ExecutionSpecification spec = getSpec(context);
		List<ProbabilisticTestVerdictBuilder.MisalignmentInput> misalignments = extractMisalignments(context);
		boolean hasSelectedBaseline = spec != null;
		BaselineData baseline = hasSelectedBaseline ? loadBaselineDataFromContext(context) : BaselineData.empty();
		SelectionResult selectionResult = getSelectionResult(context);
		String baselineFilename = selectionResult != null ? selectionResult.selected().filename() : null;

		// Build structured verdict model — the single source of truth
		ProbabilisticTestVerdict verdict = buildProbabilisticTestVerdict(
				context, aggregator, config, methodBudget, classBudget, suiteBudget,
				passed, latencyResult, spec, misalignments, baseline, baselineFilename, selectionResult);
		logger.debug("Verdict [{}]: junit={}, punit={}",
				verdict.correlationId(), verdict.junitPassed(), verdict.punitVerdict());

		// Print console summary from verdict model
		resultPublisher.printConsoleSummary(verdict, config.transparentStats(), spec);

		// Write XML report from verdict model
		xmlSink.accept(verdict);

		// Build and publish report entries from verdict model
		Map<String, String> entries = resultPublisher.buildReportEntries(verdict, spec);
		context.publishReportEntry(entries);
	}

	/**
	 * Builds a {@link ProbabilisticTestVerdict} from the available execution data.
	 *
	 * <p>This is the single source of truth for all verdict rendering — both console
	 * summary and TestReporter entries are derived from this model.
	 */
	private ProbabilisticTestVerdict buildProbabilisticTestVerdict(
			ExtensionContext context,
			SampleResultAggregator aggregator,
			TestConfiguration config,
			CostBudgetMonitor methodBudget,
			SharedBudgetMonitor classBudget,
			SharedBudgetMonitor suiteBudget,
			boolean passed,
			LatencyAssertionResult latencyResult,
			ExecutionSpecification spec,
			List<ProbabilisticTestVerdictBuilder.MisalignmentInput> misalignments,
			BaselineData baseline,
			String baselineFilename,
			SelectionResult selectionResult) {

		// Identity: class name and method name from the parent context (test method level)
		String className = context.getParent()
				.flatMap(ExtensionContext::getTestClass)
				.map(Class::getName)
				.orElse("unknown");
		String methodName = context.getParent()
				.flatMap(ExtensionContext::getTestMethod)
				.map(java.lang.reflect.Method::getName)
				.orElse(context.getDisplayName());

		BernoulliTrialsConfig stratConfig = getStrategyConfig(context);

		ProbabilisticTestVerdictBuilder builder = new ProbabilisticTestVerdictBuilder()
				.identity(className, methodName, config.specId())
				.execution(
						config.samples(),
						aggregator.getSamplesExecuted(),
						aggregator.getSuccesses(),
						aggregator.getFailures(),
						config.minPassRate(),
						aggregator.getObservedPassRate(),
						aggregator.getElapsedMs())
				.intent(config.intent(), config.resolvedConfidence())
				.useCaseAttributes(stratConfig.useCaseAttributes())
				.termination(
						aggregator.getTerminationReason().orElse(TerminationReason.COMPLETED),
						aggregator.getTerminationDetails())
				.junitPassed(aggregator.getFailures() == 0)
				.passedStatistically(passed)
				.cost(
						methodBudget.getTokensConsumed(),
						config.timeBudgetMs(),
						config.tokenBudget(),
						config.tokenMode())
				.sharedBudgets(classBudget, suiteBudget);

		// Applied multiplier
		if (config.hasMultiplier()) {
			builder.appliedMultiplier(config.appliedMultiplier());
		}

		// Functional dimension
		if (aggregator.isFunctionalAsserted()) {
			builder.functionalDimension(
					aggregator.functionalSuccesses().orElse(0),
					aggregator.functionalFailures().orElse(0));
		}

		// Latency dimension
		if (latencyResult.wasEvaluated()) {
			List<ProbabilisticTestVerdictBuilder.PercentileAssertionInput> assertions =
					latencyResult.percentileResults().stream()
							.map(pr -> new ProbabilisticTestVerdictBuilder.PercentileAssertionInput(
									pr.label(), pr.observedMs(), pr.thresholdMs(),
									pr.passed(), pr.indicative(), pr.source()))
							.toList();

			builder.latencyDimension(new ProbabilisticTestVerdictBuilder.LatencyInput(
					latencyResult.successfulSampleCount(),
					aggregator.getSamplesExecuted(),
					latencyResult.skipped(),
					latencyResult.skipped() && !latencyResult.caveats().isEmpty()
							? latencyResult.caveats().getFirst() : null,
					latencyResult.observedP50Ms(),
					latencyResult.observedP90Ms(),
					latencyResult.observedP95Ms(),
					latencyResult.observedP99Ms(),
					latencyResult.maxLatencyMs(),
					assertions,
					latencyResult.caveats(),
					aggregator.latencySuccesses().orElse(0),
					aggregator.latencyFailures().orElse(0)));
		} else if (!aggregator.getSuccessfulLatenciesMs().isEmpty()) {
			// Observational latency: distribution only, no threshold assertions
			long[] millis = aggregator.getSuccessfulLatenciesMs().stream()
					.mapToLong(Long::longValue).toArray();
			LatencyDistribution dist = LatencyDistribution.fromMillis(millis);
			builder.latencyDimension(new ProbabilisticTestVerdictBuilder.LatencyInput(
					millis.length,
					aggregator.getSamplesExecuted(),
					false, null,
					dist.p50Ms(), dist.p90Ms(), dist.p95Ms(), dist.p99Ms(), dist.maxMs(),
					List.of(), List.of(), 0, 0));
		}

		// Covariates
		if (!misalignments.isEmpty()) {
			builder.misalignments(misalignments);
		}

		// Pacing
		if (config.hasPacing()) {
			builder.pacing(config.pacing());
		}

		// Provenance
		if (config.thresholdOrigin() != null || (config.contractRef() != null && !config.contractRef().isEmpty())) {
			String sourceLabel = deriveBaselineSourceLabel(selectionResult);
			builder.provenance(config.thresholdOrigin(), config.contractRef(), baselineFilename, sourceLabel);
		}

		// Spec and baseline
		if (spec != null) {
			builder.spec(spec);
		}
		if (baseline != null) {
			builder.baseline(baseline);
		}

		return builder.build();
	}

	/**
	 * Extracts covariate misalignments from the selection result, if present.
	 */
	private List<ProbabilisticTestVerdictBuilder.MisalignmentInput> extractMisalignments(ExtensionContext context) {
		SelectionResult result = getSelectionResult(context);
		if (result == null || !result.hasNonConformance()) {
			return List.of();
		}
		return result.nonConformingDetails().stream()
				.map(d -> new ProbabilisticTestVerdictBuilder.MisalignmentInput(
						d.covariateKey(),
						d.baselineValue().toCanonicalString(),
						d.testValue().toCanonicalString()))
				.toList();
	}

	/**
	 * Derives a human-readable label for the baseline source.
	 */
	private String deriveBaselineSourceLabel(SelectionResult selectionResult) {
		if (selectionResult == null || selectionResult.selected() == null) {
			return null;
		}
		return switch (selectionResult.selected().source()) {
			case ENVIRONMENT_LOCAL -> {
				String specDir = System.getProperty("punit.spec.dir");
				if (specDir == null || specDir.isBlank()) {
					specDir = System.getenv("PUNIT_SPEC_DIR");
				}
				yield specDir != null ? specDir : "(env-local)";
			}
			case BUNDLED -> "(bundled)";
		};
	}

	/**
	 * Loads baseline data from the already-selected baseline in the context.
	 *
	 * <p>This uses the baseline that was selected during covariate-aware matching,
	 * not a fresh lookup from the spec registry.
	 */
	private BaselineData loadBaselineDataFromContext(ExtensionContext context) {
		ExecutionSpecification spec = getSpec(context);
		if (spec == null) {
			return BaselineData.empty();
		}
		
		// Get the actual filename from the selection result if available
		SelectionResult result = getSelectionResult(context);
		String filename = result != null ? result.selected().filename() : spec.getUseCaseId() + ".yaml";
		
		// Use fromSpec to indicate this data comes from a selected baseline
		return BaselineData.fromSpec(
				filename,
				spec.getEmpiricalBasis() != null && spec.getEmpiricalBasis().generatedAt() != null
						? spec.getEmpiricalBasis().generatedAt()
						: spec.getGeneratedAt(),
				spec.getBaselineSamples(),
				spec.getBaselineSuccesses()
		);
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

	private BernoulliTrialsConfig getStrategyConfig(ExtensionContext context) {
		return getFromStoreOrParent(context, STRATEGY_CONFIG_KEY, BernoulliTrialsConfig.class);
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

	private AtomicInteger getSampleCounter(ExtensionContext context) {
		return getFromStoreOrParent(context, SAMPLE_COUNTER_KEY, AtomicInteger.class);
	}

	private AtomicInteger getWarmupCounter(ExtensionContext context) {
		return getFromStoreOrParent(context, WARMUP_COUNTER_KEY, AtomicInteger.class);
	}

	private ExecutionSpecification getSpec(ExtensionContext context) {
		ExtensionContext.Store store = context.getStore(NAMESPACE);
		ExecutionSpecification spec = store.get(SPEC_KEY, ExecutionSpecification.class);
		if (spec != null) {
			return spec;
		}
		return context.getParent()
				.map(parent -> parent.getStore(NAMESPACE).get(SPEC_KEY, ExecutionSpecification.class))
				.orElse(null);
	}

	/**
	 * Gets the baseline selection result if available.
	 */
	private SelectionResult getSelectionResult(ExtensionContext context) {
		ExtensionContext.Store store = context.getStore(NAMESPACE);
		SelectionResult result = store.get(SELECTION_RESULT_KEY, SelectionResult.class);
		if (result != null) {
			return result;
		}
		return context.getParent()
				.map(parent -> parent.getStore(NAMESPACE).get(SELECTION_RESULT_KEY, SelectionResult.class))
				.orElse(null);
	}

	private ExtensionContext.Store getMethodStore(ExtensionContext context) {
		return context.getParent()
				.map(parent -> parent.getStore(NAMESPACE))
				.orElse(context.getStore(NAMESPACE));
	}

	/**
	 * Prepares baseline selection data for lazy covariate-aware selection.
	 *
	 * <p>If the use case has covariates declared:
	 * <ol>
	 *   <li>Extract covariate declaration from use case class</li>
	 *   <li>Compute footprint for the test</li>
	 *   <li>Find baseline candidates with matching footprint</li>
	 *   <li>Store pending selection data in the extension store</li>
	 * </ol>
	 *
	 * <p>Actual baseline selection is performed lazily during the first sample
	 * invocation, when a test instance (and therefore a use case instance) exists.
	 *
	 * <p>If no covariates are declared, falls back to simple spec loading.
	 *
	 * @param annotation the test annotation
	 * @param specId the resolved spec ID (may be null)
	 * @param store the extension context store
	 * @param context the extension context (for accessing UseCaseProvider)
	 */
	private void prepareBaselineSelection(
			ProbabilisticTest annotation,
			String specId,
			ExtensionContext.Store store,
			ExtensionContext context) {

		BaselineSelectionOrchestrator.PreparationResult result = 
				baselineOrchestrator.prepareSelection(annotation, specId);

		if (result.hasSpec()) {
			store.put(SPEC_KEY, result.spec());
		} else if (result.hasPending()) {
			store.put(PENDING_SELECTION_KEY, result.pending());
		}
	}

	private static final String BASELINE_RESOLVED_KEY = "baselineResolved";

	/**
	 * Resolves baseline selection lazily during the first sample invocation.
	 * Also validates the test configuration after baseline selection and derives
	 * minPassRate from baseline if not explicitly specified.
	 *
	 * <p>If any step throws (validation failure, infeasibility gate, etc.), the
	 * error is unrecoverable — the configuration won't change between invocations.
	 * To avoid repeating the same failure N times, we mark resolution complete and
	 * signal termination so remaining invocations are skipped.
	 */
	private void ensureBaselineSelected(ExtensionContext context) {
		ExtensionContext.Store store = getMethodStore(context);

		// Check if we've already processed baseline selection (with or without a baseline)
		Boolean alreadyResolved = store.get(BASELINE_RESOLVED_KEY, Boolean.class);
		if (Boolean.TRUE.equals(alreadyResolved)) {
			return;
		}

		try {
			resolveBaseline(context, store);
		} catch (RuntimeException ex) {
			// Configuration errors are unrecoverable: mark resolved to prevent
			// re-entry, and signal termination so the stream stops producing
			// invocations and in-flight samples are skipped.
			store.put(BASELINE_RESOLVED_KEY, Boolean.TRUE);
			AtomicBoolean terminated = store.get(TERMINATED_KEY, AtomicBoolean.class);
			if (terminated != null) {
				terminated.set(true);
			}
			throw ex;
		}
	}

	private void resolveBaseline(ExtensionContext context, ExtensionContext.Store store) {
		BaselineSelectionOrchestrator.PendingSelection pending =
				store.get(PENDING_SELECTION_KEY, BaselineSelectionOrchestrator.PendingSelection.class);
		if (pending == null) {
			// No pending covariate-aware selection.
			// A pre-loaded spec may exist (from a useCase without covariates).
			ExecutionSpecification preLoadedSpec = store.get(SPEC_KEY, ExecutionSpecification.class);

			// Use the pre-loaded spec as the selected baseline for validation when
			// minPassRate is not explicitly specified — this is the baseline-derived path.
			TestConfiguration config = store.get(CONFIG_KEY, TestConfiguration.class);
			boolean needsBaselineDerivation = config != null && Double.isNaN(config.minPassRate());
			ExecutionSpecification baselineForValidation = needsBaselineDerivation ? preLoadedSpec : null;

			validateTestConfiguration(context, baselineForValidation);

			// Derive minPassRate from pre-loaded spec if not explicitly specified
			if (needsBaselineDerivation && preLoadedSpec != null) {
				deriveMinPassRateFromBaseline(store, preLoadedSpec);
			}

			// Resolve latency thresholds using pre-loaded spec if available (may contain latency data)
			resolveLatencyThresholds(store, preLoadedSpec, context);
			// Log configuration
			logFinalConfiguration(context);
			// Enforce verification feasibility gate (Req 5)
			enforceVerificationFeasibility(context);
			enforceLatencyFeasibility(context);
			// Mark as resolved to prevent repeated logging
			store.put(BASELINE_RESOLVED_KEY, Boolean.TRUE);
			return;
		}

		store.getOrComputeIfAbsent(SELECTION_RESULT_KEY, key -> {
			// Resolve use case instance for covariate resolution
			Optional<UseCaseProvider> providerOpt = baselineOrchestrator.findUseCaseFactory(
					context.getTestInstance().orElse(null),
					context.getTestClass().orElse(null));
			Object useCaseInstance = providerOpt
					.map(p -> baselineOrchestrator.resolveUseCaseInstance(p, pending.useCaseClass()))
					.orElse(null);

			// Perform baseline selection
			SelectionResult result = baselineOrchestrator.performSelection(pending, useCaseInstance);
			ExecutionSpecification baseline = result.selected().spec();

			// Store the selected spec and selection result
			store.put(SPEC_KEY, baseline);

			// Validate test configuration now that we have the selected baseline
			validateTestConfiguration(context, baseline);

			// Derive minPassRate from baseline if not explicitly specified
			deriveMinPassRateFromBaseline(store, baseline);

			// Resolve latency thresholds using baseline latency data
			resolveLatencyThresholds(store, baseline, context);

			// Log baseline selection result first (so user sees what baseline was used)
			baselineOrchestrator.logSelectionResult(result, pending.specId());

			// Then log configuration (now that minPassRate is known)
			logFinalConfiguration(context);

			// Enforce verification feasibility gate (Req 5)
			enforceVerificationFeasibility(context);
			enforceLatencyFeasibility(context);

			// Mark as resolved
			store.put(BASELINE_RESOLVED_KEY, Boolean.TRUE);

			return result;
		}, SelectionResult.class);
	}

	/**
	 * Logs the final test configuration after baseline selection and minPassRate derivation.
	 */
	private void logFinalConfiguration(ExtensionContext context) {
		TestConfiguration config = getConfiguration(context);
		if (config == null) {
			return;
		}

		String testName = context.getParent()
				.flatMap(ExtensionContext::getTestMethod)
				.map(java.lang.reflect.Method::getName)
				.orElse(context.getDisplayName());

		ExtensionContext.Store store = getMethodStore(context);
		boolean thresholdDerived = Boolean.TRUE.equals(
				store.get(THRESHOLD_DERIVED_KEY, Boolean.class));

		FinalConfigurationLogger.ConfigurationData configData = new FinalConfigurationLogger.ConfigurationData(
				config.samples(),
				config.minPassRate(),
				config.specId(),
				config.thresholdOrigin(),
				config.contractRef(),
				config.intent(),
				thresholdDerived
		);

		configurationLogger.log(testName, configData);
	}

	/**
	 * Derives minPassRate from baseline and updates stored configuration if needed.
	 *
	 * <p>If the current TestConfiguration has a NaN minPassRate (meaning it wasn't
	 * explicitly specified), this method derives it from the baseline's empirical data
	 * and updates both the TestConfiguration and EarlyTerminationEvaluator.
	 */
	private void deriveMinPassRateFromBaseline(ExtensionContext.Store store, ExecutionSpecification baseline) {
		TestConfiguration config = store.get(CONFIG_KEY, TestConfiguration.class);
		if (config == null || !Double.isNaN(config.minPassRate())) {
			return; // Config missing or minPassRate already set
		}

		// Derive minPassRate from baseline
		double derivedMinPassRate = baseline.getMinPassRate();
		if (Double.isNaN(derivedMinPassRate) || derivedMinPassRate <= 0) {
			// Baseline doesn't have a valid minPassRate - this shouldn't happen if validation passed
			throw new ExtensionConfigurationException(
					"Baseline for use case '" + baseline.getUseCaseId() + "' does not contain a valid minPassRate. " +
					"Run a MEASURE experiment to establish baseline data.");
		}

		// Update TestConfiguration with derived minPassRate
		TestConfiguration updatedConfig = config.withMinPassRate(derivedMinPassRate);
		store.put(CONFIG_KEY, updatedConfig);
		store.put(THRESHOLD_DERIVED_KEY, Boolean.TRUE);

		// Update BernoulliTrialsConfig with derived minPassRate (must stay in sync with TestConfiguration)
		BernoulliTrialsConfig oldStrategyConfig = store.get(STRATEGY_CONFIG_KEY, BernoulliTrialsConfig.class);
		if (oldStrategyConfig != null) {
			store.put(STRATEGY_CONFIG_KEY, oldStrategyConfig.withMinPassRate(derivedMinPassRate));
		}

		// Update EarlyTerminationEvaluator with derived minPassRate
		EarlyTerminationEvaluator oldEvaluator = store.get(EVALUATOR_KEY, EarlyTerminationEvaluator.class);
		if (oldEvaluator != null) {
			EarlyTerminationEvaluator updatedEvaluator = new EarlyTerminationEvaluator(
					config.samples(), derivedMinPassRate);
			store.put(EVALUATOR_KEY, updatedEvaluator);
		}
	}

	/**
	 * Enforces the verification feasibility gate (Req 5).
	 *
	 * <p>If the test intent is VERIFICATION and the configured sample size is
	 * insufficient for the declared target and confidence, this method throws
	 * {@link ExtensionConfigurationException} to hard-fail the test before
	 * any samples execute. This prevents "verification theatre" where an
	 * undersized test silently passes.
	 *
	 * <p>SMOKE intent tests bypass this check entirely.
	 *
	 * @param context the extension context
	 * @throws ExtensionConfigurationException if VERIFICATION is infeasible
	 */
	private void enforceVerificationFeasibility(ExtensionContext context) {
		TestConfiguration config = getConfiguration(context);
		if (config == null || config.intent() != TestIntent.VERIFICATION) {
			return;
		}

		double minPassRate = config.minPassRate();
		if (Double.isNaN(minPassRate) || minPassRate <= 0.0 || minPassRate >= 1.0) {
			return; // Cannot evaluate feasibility without a valid target
		}

		VerificationFeasibilityEvaluator.FeasibilityResult result =
				VerificationFeasibilityEvaluator.evaluate(
						config.samples(), minPassRate, config.resolvedConfidence());

		if (!result.feasible()) {
			String testName = context.getParent()
					.flatMap(ExtensionContext::getTestMethod)
					.map(java.lang.reflect.Method::getName)
					.orElse(context.getDisplayName());

			throw new ExtensionConfigurationException(
					InfeasibilityMessageRenderer.render(
							testName, result, config.hasTransparentStats()));
		}
	}

	/**
	 * Enforces the latency feasibility gate for VERIFICATION intent.
	 *
	 * <p>Checks that the expected number of successful samples is sufficient
	 * for all asserted latency percentiles. If not, throws
	 * {@link ExtensionConfigurationException} to fail the test before samples execute.
	 *
	 * <p>Uses resolved thresholds when available (covers baseline-derived latency),
	 * since the raw config's {@code isLatencyRequested()} only reflects explicit thresholds.
	 *
	 * @param context the extension context
	 * @throws ExtensionConfigurationException if latency assertions are infeasible
	 */
	private void enforceLatencyFeasibility(ExtensionContext context) {
		LatencyAssertionConfig latencyConfig = getLatencyConfig(context);
		if (latencyConfig == null || latencyConfig.disabled()) {
			return;
		}

		boolean hasExplicitThresholds = latencyConfig.hasExplicitThresholds();
		if (!LatencyAssertionConfig.isEffectivelyEnforced(hasExplicitThresholds)) {
			return;
		}

		TestConfiguration config = getConfiguration(context);
		if (config == null || config.intent() != TestIntent.VERIFICATION) {
			return;
		}

		// Prefer resolved thresholds (which include baseline-derived) over raw config
		LatencyThresholdResolver.ResolvedThresholds resolvedThresholds = getResolvedLatencyThresholds(context);
		LatencyAssertionConfig effectiveConfig = resolvedThresholds != null
				? resolvedThresholds.toConfig()
				: latencyConfig;

		if (!effectiveConfig.isLatencyRequested()) {
			return;
		}

		double expectedSuccessRate = Double.isNaN(config.minPassRate()) ? 1.0 : config.minPassRate();
		LatencyFeasibilityEvaluator.FeasibilityResult result =
				LatencyFeasibilityEvaluator.evaluate(effectiveConfig, config.samples(), expectedSuccessRate);

		if (!result.feasible()) {
			throw new ExtensionConfigurationException(result.message());
		}
	}

	/**
	 * Validates the probabilistic test configuration using the selected baseline.
	 *
	 * @param context the extension context
	 * @param selectedBaseline the selected baseline (null if none)
	 * @throws ExtensionConfigurationException if validation fails
	 */
	private void validateTestConfiguration(ExtensionContext context, ExecutionSpecification selectedBaseline) {
		Method testMethod = context.getRequiredTestMethod();
		ProbabilisticTest annotation = testMethod.getAnnotation(ProbabilisticTest.class);
		
		if (annotation == null) {
			return; // Not a probabilistic test
		}
		
		ProbabilisticTestValidator.ValidationResult validation = 
				testValidator.validate(annotation, selectedBaseline, testMethod.getName());
		
		if (!validation.valid()) {
			String errors = String.join("\n\n", validation.errors());
			throw new ExtensionConfigurationException(errors);
		}
	}

	/**
	 * Applies pacing delay before sample execution if pacing is configured.
	 *
	 * <p>The delay is applied between samples (not before the first sample) to
	 * maintain the configured rate limit.
	 *
	 * @param context the extension context
	 * @param config the test configuration
	 */
	private void applyPacingDelay(ExtensionContext context, TestConfiguration config) {
		if (!config.hasPacing()) {
			return;
		}

		PacingConfiguration pacing = config.pacing();
		long delayMs = pacing.effectiveMinDelayMs();
		if (delayMs <= 0) {
			return;
		}

		AtomicInteger sampleCounter = getSampleCounter(context);
		int currentSample = sampleCounter.incrementAndGet();

		// Skip delay for first sample
		if (currentSample <= 1) {
			return;
		}

		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			// Don't fail the test, just log and continue
			logger.warn("Pacing delay interrupted");
		}
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
}

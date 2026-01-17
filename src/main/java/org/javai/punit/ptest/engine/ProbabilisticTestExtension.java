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
import org.javai.punit.ptest.strategy.InterceptResult;
import org.javai.punit.ptest.strategy.ProbabilisticTestStrategy;
import org.javai.punit.ptest.strategy.SampleExecutionContext;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.spec.baseline.BaselineRepository;
import org.javai.punit.spec.baseline.BaselineSelectionTypes.SelectionResult;
import org.javai.punit.spec.baseline.BaselineSelector;
import org.javai.punit.spec.baseline.FootprintComputer;
import org.javai.punit.spec.baseline.covariate.CovariateProfileResolver;
import org.javai.punit.spec.baseline.covariate.UseCaseCovariateExtractor;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.statistics.transparent.BaselineData;
import org.javai.punit.statistics.transparent.StatisticalExplanationBuilder;
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
	private static final String PENDING_SELECTION_KEY = "pendingSelection";
	private static final String STRATEGY_CONFIG_KEY = "strategyConfig";

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

		// Store strategy config and create TestConfiguration for backward compatibility
		store.put(STRATEGY_CONFIG_KEY, strategyConfig);
		TestConfiguration config = createTestConfigurationFromStrategy(strategyConfig);
		store.put(CONFIG_KEY, config);
		store.put(AGGREGATOR_KEY, aggregator);
		store.put(EVALUATOR_KEY, evaluator);
		store.put(BUDGET_MONITOR_KEY, budgetMonitor);
		store.put(TERMINATED_KEY, terminated);
		store.put(PACING_KEY, strategyConfig.pacing());
		store.put(SAMPLE_COUNTER_KEY, sampleCounter);
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
					strategyConfig.samples(), configResolver);
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
				strategyConfig.contractRef()
		);
	}

	// ========== InvocationInterceptor ==========

	@Override
	public void interceptTestTemplateMethod(Invocation<Void> invocation,
											ReflectiveInvocationContext<Method> invocationContext,
											ExtensionContext extensionContext) throws Throwable {

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

		// Build execution context and delegate to strategy
		SampleExecutionContext executionContext = new SampleExecutionContext(
				strategyConfig, aggregator, evaluator, budgetMonitor,
				classBudgetMonitor, suiteBudgetMonitor, tokenRecorder,
				terminated, extensionContext);

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
			// Continue - rethrow sample failures so they appear as ❌ in the IDE
			if (result.hasSampleFailure()) {
				rethrowSampleFailure(result.sampleFailure(), aggregator, config);
			}
		}
	}

	/**
	 * Re-throws sample failures so they appear as ❌ in the IDE.
	 */
	private void rethrowSampleFailure(Throwable failure, SampleResultAggregator aggregator, TestConfiguration config) {
		String formattedFailure = sampleFailureFormatter.formatSampleFailure(
				failure,
				aggregator.getSuccesses(),
				aggregator.getSamplesExecuted(),
				config.samples(),
				config.minPassRate()
		);
		throw new AssertionError(formattedFailure);
	}

	private void finalizeProbabilisticTest(ExtensionContext context,
										   SampleResultAggregator aggregator,
										   TestConfiguration config,
										   CostBudgetMonitor methodBudget,
										   SharedBudgetMonitor classBudget,
										   SharedBudgetMonitor suiteBudget) {

		// Delegate verdict computation to strategy
		BernoulliTrialsConfig strategyConfig = getStrategyConfig(context);
		boolean passed = strategy.computeVerdict(aggregator, strategyConfig);

		// Publish structured results via TestReporter
		publishResults(context, aggregator, config, methodBudget, classBudget, suiteBudget, passed);

		// Throw assertion error if test failed
		if (!passed) {
			// Delegate failure message building to strategy
			String message = strategy.buildFailureMessage(aggregator, strategyConfig);

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

		String testName = context.getParent()
				.map(ExtensionContext::getDisplayName)
				.orElse(context.getDisplayName());

		ExecutionSpecification spec = getSpec(context);
		List<StatisticalExplanationBuilder.CovariateMisalignment> misalignments = extractMisalignments(context);
		boolean hasSelectedBaseline = spec != null;
		BaselineData baseline = hasSelectedBaseline ? loadBaselineDataFromContext(context) : BaselineData.empty();
		SelectionResult selectionResult = getSelectionResult(context);
		String baselineFilename = selectionResult != null ? selectionResult.selected().filename() : null;

		ResultPublisher.PublishContext publishCtx = new ResultPublisher.PublishContext(
				testName,
				config.samples(),
				aggregator.getSamplesExecuted(),
				aggregator.getSuccesses(),
				aggregator.getFailures(),
				config.minPassRate(),
				aggregator.getObservedPassRate(),
				passed,
				aggregator.getTerminationReason(),
				aggregator.getTerminationDetails(),
				aggregator.getElapsedMs(),
				config.hasMultiplier(),
				config.appliedMultiplier(),
				config.timeBudgetMs(),
				config.tokenBudget(),
				methodBudget.getTokensConsumed(),
				config.tokenMode(),
				classBudget,
				suiteBudget,
				spec,
				config.transparentStats(),
				config.thresholdOrigin(),
				config.contractRef(),
				config.confidence(),
				baseline,
				misalignments,
				baselineFilename
		);

		// Print console summary
		resultPublisher.printConsoleSummary(publishCtx);

		// Build and publish report entries
		Map<String, String> entries = resultPublisher.buildReportEntries(publishCtx);
		context.publishReportEntry(entries);
	}

	/**
	 * Extracts covariate misalignments from the selection result, if present.
	 */
	private List<StatisticalExplanationBuilder.CovariateMisalignment> extractMisalignments(ExtensionContext context) {
		SelectionResult result = getSelectionResult(context);
		if (result == null || !result.hasNonConformance()) {
			return List.of();
		}
		return result.nonConformingDetails().stream()
				.map(d -> new StatisticalExplanationBuilder.CovariateMisalignment(
						d.covariateKey(),
						d.baselineValue().toCanonicalString(),
						d.testValue().toCanonicalString()))
				.toList();
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
				spec.getEmpiricalBasis() != null 
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
	 */
	private void ensureBaselineSelected(ExtensionContext context) {
		ExtensionContext.Store store = getMethodStore(context);
		
		// Check if we've already processed baseline selection (with or without a baseline)
		Boolean alreadyResolved = store.get(BASELINE_RESOLVED_KEY, Boolean.class);
		if (Boolean.TRUE.equals(alreadyResolved)) {
			return;
		}

		BaselineSelectionOrchestrator.PendingSelection pending = 
				store.get(PENDING_SELECTION_KEY, BaselineSelectionOrchestrator.PendingSelection.class);
		if (pending == null) {
			// No pending selection - validate without baseline
			validateTestConfiguration(context, null);
			// Log configuration for explicit threshold mode
			logFinalConfiguration(context);
			// Mark as resolved to prevent repeated logging
			store.put(BASELINE_RESOLVED_KEY, Boolean.TRUE);
			return;
		}

		store.getOrComputeIfAbsent(SELECTION_RESULT_KEY, key -> {
			// Resolve use case instance for covariate resolution
			Optional<UseCaseProvider> providerOpt = baselineOrchestrator.findUseCaseProvider(
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

			// Log baseline selection result first (so user sees what baseline was used)
			baselineOrchestrator.logSelectionResult(result, pending.specId());

			// Then log configuration (now that minPassRate is known)
			logFinalConfiguration(context);

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
		
		FinalConfigurationLogger.ConfigurationData configData = new FinalConfigurationLogger.ConfigurationData(
				config.samples(),
				config.minPassRate(),
				config.specId(),
				config.thresholdOrigin(),
				config.contractRef()
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

		// Update EarlyTerminationEvaluator with derived minPassRate
		EarlyTerminationEvaluator oldEvaluator = store.get(EVALUATOR_KEY, EarlyTerminationEvaluator.class);
		if (oldEvaluator != null) {
			EarlyTerminationEvaluator updatedEvaluator = new EarlyTerminationEvaluator(
					config.samples(), derivedMinPassRate);
			store.put(EVALUATOR_KEY, updatedEvaluator);
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

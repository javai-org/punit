package org.javai.punit.ptest.engine;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.ptest.engine.FactorConsistencyValidator.ValidationResult;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.HashableFactorSource;
import org.javai.punit.experiment.engine.FactorSourceAdapter;
import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.engine.covariate.BaselineRepository;
import org.javai.punit.engine.covariate.BaselineSelectionTypes.BaselineCandidate;
import org.javai.punit.engine.covariate.BaselineSelectionTypes.SelectionResult;
import org.javai.punit.engine.covariate.BaselineSelector;
import org.javai.punit.engine.covariate.CovariateProfileResolver;
import org.javai.punit.engine.covariate.DefaultCovariateResolutionContext;
import org.javai.punit.engine.covariate.FootprintComputer;
import org.javai.punit.engine.covariate.NoCompatibleBaselineException;
import org.javai.punit.engine.covariate.UseCaseCovariateExtractor;
import org.javai.punit.engine.expiration.ExpirationEvaluator;
import org.javai.punit.engine.expiration.ExpirationWarningRenderer;
import org.javai.punit.engine.expiration.ExpirationReportPublisher;
import org.javai.punit.engine.expiration.WarningLevel;
import org.javai.punit.model.ExpirationStatus;
import org.javai.punit.statistics.transparent.BaselineData;
import org.javai.punit.statistics.transparent.ConsoleExplanationRenderer;
import org.javai.punit.statistics.transparent.StatisticalExplanation;
import org.javai.punit.statistics.transparent.StatisticalExplanationBuilder;
import org.javai.punit.statistics.transparent.TransparentStatsConfig;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
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

	private final ConfigurationResolver configResolver;
	private final BaselineRepository baselineRepository;
	private final BaselineSelector baselineSelector;
	private final CovariateProfileResolver covariateProfileResolver;
	private final FootprintComputer footprintComputer;
	private final UseCaseCovariateExtractor covariateExtractor;
	private final PacingResolver pacingResolver;
	private final PacingReporter pacingReporter;

	/**
	 * Default constructor using standard configuration resolver.
	 */
	public ProbabilisticTestExtension() {
		this(new ConfigurationResolver(), new PacingResolver(), new PacingReporter(),
			 new BaselineRepository(), new BaselineSelector(), new CovariateProfileResolver(),
			 new FootprintComputer(), new UseCaseCovariateExtractor());
	}

	/**
	 * Constructor for testing with custom resolvers.
	 */
	ProbabilisticTestExtension(ConfigurationResolver configResolver) {
		this(configResolver, new PacingResolver(), new PacingReporter(),
			 new BaselineRepository(), new BaselineSelector(), new CovariateProfileResolver(),
			 new FootprintComputer(), new UseCaseCovariateExtractor());
	}

	/**
	 * Constructor for testing with custom resolvers and reporter.
	 */
	ProbabilisticTestExtension(ConfigurationResolver configResolver, 
							   PacingResolver pacingResolver,
							   PacingReporter pacingReporter) {
		this(configResolver, pacingResolver, pacingReporter,
			 new BaselineRepository(), new BaselineSelector(), new CovariateProfileResolver(),
			 new FootprintComputer(), new UseCaseCovariateExtractor());
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
							   UseCaseCovariateExtractor covariateExtractor) {
		this.configResolver = configResolver;
		this.pacingResolver = pacingResolver;
		this.pacingReporter = pacingReporter;
		this.baselineRepository = baselineRepository;
		this.baselineSelector = baselineSelector;
		this.covariateProfileResolver = covariateProfileResolver;
		this.footprintComputer = footprintComputer;
		this.covariateExtractor = covariateExtractor;
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

		// Resolve pacing configuration
		PacingConfiguration pacing = pacingResolver.resolve(testMethod, resolved.samples());

		// Store configuration and create components
		ExtensionContext.Store store = context.getStore(NAMESPACE);
		TestConfiguration config = createTestConfiguration(resolved, tokenMode, pacing, annotation.transparentStats());
		SampleResultAggregator aggregator = new SampleResultAggregator(resolved.samples(), resolved.maxExampleFailures());
		EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(resolved.samples(), resolved.minPassRate());
		AtomicBoolean terminated = new AtomicBoolean(false);
		AtomicInteger sampleCounter = new AtomicInteger(0);

		store.put(CONFIG_KEY, config);
		store.put(AGGREGATOR_KEY, aggregator);
		store.put(EVALUATOR_KEY, evaluator);
		store.put(BUDGET_MONITOR_KEY, budgetMonitor);
		store.put(TERMINATED_KEY, terminated);
		store.put(PACING_KEY, pacing);
		store.put(SAMPLE_COUNTER_KEY, sampleCounter);
		if (tokenRecorder != null) {
			store.put(TOKEN_RECORDER_KEY, tokenRecorder);
		}

		// Load and store spec for expiration checking (with covariate-aware baseline selection)
		loadBaselineWithCovariateSelection(annotation, resolved.specId(), store);

		// Print pre-flight report if pacing is configured
		if (pacing.hasPacing()) {
			Instant startTime = Instant.now();
			store.put(LAST_SAMPLE_TIME_KEY, startTime);
			pacingReporter.printPreFlightReport(testMethod.getName(), resolved.samples(), pacing, startTime);
			pacingReporter.printFeasibilityWarning(pacing, resolved.timeBudgetMs(), resolved.samples());
		}

		// Validate factor source consistency if applicable
		validateFactorConsistency(testMethod, annotation, resolved.samples());

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
			CostBudgetMonitor.TokenMode tokenMode,
			PacingConfiguration pacing,
			boolean annotationTransparentStats) {

		// Resolve transparent stats config with annotation override
		TransparentStatsConfig transparentStats = TransparentStatsConfig.resolve(
				annotationTransparentStats ? Boolean.TRUE : null);

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
				resolved.maxExampleFailures(),
				// Statistical context - populated from resolved configuration if available
				resolved.confidence(),
				resolved.baselineRate(),
				resolved.baselineSamples(),
				resolved.specId(),
				// Pacing configuration
				pacing,
				// Transparent stats configuration
				transparentStats,
				// Provenance metadata
				resolved.thresholdOrigin(),
				resolved.contractRef()
		);
	}

	/**
	 * Validates factor source consistency between the test and its baseline spec.
	 *
	 * <p>This check ensures statistical integrity by verifying that the test uses
	 * the same factor source as the experiment that generated the baseline. If a
	 * mismatch is detected, a warning is logged.
	 *
	 * @param testMethod the test method
	 * @param annotation the @ProbabilisticTest annotation
	 * @param testSamples the number of samples the test will use
	 */
	private void validateFactorConsistency(Method testMethod, ProbabilisticTest annotation, int testSamples) {
		// Check if the test method has a @FactorSource annotation
		FactorSource factorSourceAnnotation = testMethod.getAnnotation(FactorSource.class);
		if (factorSourceAnnotation == null) {
			// No factor source - nothing to validate
			return;
		}

		// Resolve the factor source
		HashableFactorSource testFactorSource;
		try {
			testFactorSource = FactorSourceAdapter.fromAnnotation(
					factorSourceAnnotation, testMethod.getDeclaringClass());
		} catch (Exception e) {
			// Could not resolve factor source - log warning and continue
			System.err.println("Warning: Could not resolve factor source for consistency check: " + e.getMessage());
			return;
		}

		// Load the spec
		Optional<String> specIdOpt = configResolver.resolveSpecIdFromAnnotation(annotation);
		if (specIdOpt.isEmpty()) {
			// No spec reference - nothing to validate against
			return;
		}

		Optional<ExecutionSpecification> optionalSpec = configResolver.loadSpec(specIdOpt.get());
		if (optionalSpec.isEmpty()) {
			// Spec not found - nothing to validate against
			return;
		}
		ExecutionSpecification spec = optionalSpec.get();

		// Validate factor consistency
		ValidationResult result = FactorConsistencyValidator.validateWithSampleCount(
				testFactorSource, spec, testSamples);

		// Log the result
		if (result.shouldWarn()) {
			System.err.println(result.formatForLog());
		} else if (result.isMatch()) {
			// Optionally log successful validation (can be verbose, so using debug-level equivalent)
			// System.out.println(result.formatForLog());
		}
		// NOT_APPLICABLE results are silently ignored
	}

	/**
	 * Creates a stream of sample invocation contexts that terminates early
	 * when the test is terminated.
	 *
	 * <p>Uses {@code takeWhile} to stop generating samples once termination
	 * is triggered. This keeps the test output clean - terminated samples
	 * simply don't appear rather than cluttering the output with SKIPPED entries.
	 */
	private Stream<TestTemplateInvocationContext> createSampleStream(
			int samples, AtomicBoolean terminated, DefaultTokenChargeRecorder tokenRecorder) {

		return Stream.iterate(1, i -> i + 1)
				.limit(samples)
				.takeWhile(i -> !terminated.get())
				.map(sampleIndex -> new ProbabilisticTestInvocationContext(
						sampleIndex, samples, tokenRecorder));
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
		SharedBudgetMonitor classBudgetMonitor = ProbabilisticTestBudgetExtension.getClassBudgetMonitor(extensionContext).orElse(null);
		SharedBudgetMonitor suiteBudgetMonitor = SuiteBudgetManager.getMonitor().orElse(null);

		// Note: SampleExecutionCondition handles skipping for terminated tests.
		// This check is kept as a safety net for edge cases.
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

		// Apply pacing delay if configured (skip for first sample)
		applyPacingDelay(extensionContext, config);

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

		// Re-throw sample failures so they appear as ❌ in the IDE.
		// When the user clicks on a failed sample, they'll see the failure reason.
		// We extract just the message (not the full exception) for cleaner output.
		//
		// The exception message includes a verdict hint so users don't panic before
		// checking the PUnit statistical verdict in the console summary.
		if (sampleFailure != null && !terminated.get()) {
			String reason = extractFailureReason(sampleFailure);
			String verdictHint = formatVerdictHint(aggregator, config);
			throw new AssertionError(verdictHint + "\n" + reason);
		}
	}

	/**
	 * Formats a status hint to include in exception messages.
	 * Shows current stats without claiming a verdict (which isn't known until test completes).
	 */
	private String formatVerdictHint(SampleResultAggregator aggregator, TestConfiguration config) {
		int successes = aggregator.getSuccesses();
		int executed = aggregator.getSamplesExecuted();
		int planned = config.samples();
		double threshold = config.minPassRate();
		
		return String.format(
			"[PUnit sample %d/%d: %d successes so far, need %.0f%% - see console for final verdict]",
			executed, planned, successes, threshold * 100);
	}

	/**
	 * Extracts a concise failure reason from an exception.
	 * This provides just the essential message without verbose stack traces.
	 */
	private String extractFailureReason(Throwable failure) {
		String message = failure.getMessage();
		if (message == null || message.isBlank()) {
			return failure.getClass().getSimpleName();
		}
		// Trim and take first line only (AssertJ messages can be multi-line)
		String firstLine = message.lines().findFirst().orElse(message).trim();
		// If the first line is empty (AssertJ often starts with newline), take next non-empty
		if (firstLine.isEmpty()) {
			firstLine = message.lines()
					.filter(line -> !line.isBlank())
					.findFirst()
					.orElse(failure.getClass().getSimpleName())
					.trim();
		}
		return firstLine;
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

	/**
	 * Builds a failure message for budget exhaustion scenarios.
	 * This is used when the test is forced to fail due to budget limits, not pass rate.
	 */
	private String buildBudgetExhaustionFailureMessage(SampleResultAggregator aggregator,
													   TestConfiguration config) {
		StringBuilder sb = new StringBuilder();

		String details = aggregator.getTerminationDetails();

		String failureReason = aggregator.getTerminationReason()
				.map(TerminationReason::getDescription)
				.orElse("Budget exhausted");
		sb.append(String.format("PUnit FAILED: %s.%n", failureReason));

		if (details != null && !details.isEmpty()) {
			sb.append(String.format("  %s%n", details));
		}

		sb.append(String.format("%n  Samples executed: %d of %d%n",
				aggregator.getSamplesExecuted(), config.samples()));
		sb.append(String.format("  Pass rate at termination: %.1f%% (%d/%d)%n",
				aggregator.getObservedPassRate() * 100.0,
				aggregator.getSuccesses(),
				aggregator.getSamplesExecuted()));
		sb.append(String.format("  Required pass rate: %.1f%%%n", config.minPassRate() * 100.0));
		sb.append(String.format("  Elapsed: %dms%n", aggregator.getElapsedMs()));

		return sb.toString();
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
			String message;

			if (aggregator.isForcedFailure()) {
				// Budget exhaustion failure - don't show misleading pass rate comparison
				message = buildBudgetExhaustionFailureMessage(aggregator, config);
			} else {
				// Genuine pass rate failure - show full statistical context
				PunitFailureMessages.StatisticalContext statisticalContext = config.buildStatisticalContext(
						aggregator.getObservedPassRate(),
						aggregator.getSuccesses(),
						aggregator.getSamplesExecuted()
				);
				message = decider.buildFailureMessage(aggregator, statisticalContext);
			}

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

		Optional<TerminationReason> reason = aggregator.getTerminationReason();
		String terminationReasonStr = reason.map(Enum::name).orElse(TerminationReason.COMPLETED.name());

		// Print summary message to console for visibility
		printConsoleSummary(context, aggregator, config, passed, reason);

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

		// Include expiration status
		ExecutionSpecification spec = getSpec(context);
		if (spec != null) {
			ExpirationStatus expirationStatus = ExpirationEvaluator.evaluate(spec);
			entries.putAll(ExpirationReportPublisher.buildProperties(spec, expirationStatus));
		}

		context.publishReportEntry(entries);
	}

	/**
	 * Prints a summary message to the console for visibility.
	 * This ensures the statistical verdict is visible in test output.
	 */
	private void printConsoleSummary(
			ExtensionContext context,
			SampleResultAggregator aggregator,
			TestConfiguration config,
			boolean passed,
			@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<TerminationReason> reason) {

		String testName = context.getParent()
				.map(ExtensionContext::getDisplayName)
				.orElse(context.getDisplayName());

		// If transparent stats mode is enabled, render the full statistical explanation
		if (config.hasTransparentStats()) {
			printTransparentStatsSummary(context, testName, aggregator, config, passed);
			return;
		}

		// Check if termination was due to budget exhaustion (regardless of FAIL vs EVALUATE_PARTIAL behavior)
		boolean isBudgetExhausted = reason.map(TerminationReason::isBudgetExhaustion).orElse(false);

		StringBuilder sb = new StringBuilder();
		sb.append("\n");
		sb.append("═══════════════════════════════════════════════════════════════\n");

		if (passed) {
			sb.append(String.format("PUnit PASSED: %s%n", testName));
			sb.append(String.format("  Observed pass rate: %.1f%% (%d/%d) >= min pass rate: %.1f%%%n",
					aggregator.getObservedPassRate() * 100,
					aggregator.getSuccesses(),
					aggregator.getSamplesExecuted(),
					config.minPassRate() * 100));
		} else if (isBudgetExhausted) {
			sb.append(String.format("PUnit FAILED: %s%n", testName));
			sb.append(String.format("  Samples executed: %d of %d (budget exhausted before completion)%n",
					aggregator.getSamplesExecuted(),
					config.samples()));
			sb.append(String.format("  Pass rate at termination: %.1f%% (%d/%d), required: %.1f%%%n",
					aggregator.getObservedPassRate() * 100,
					aggregator.getSuccesses(),
					aggregator.getSamplesExecuted(),
					config.minPassRate() * 100));
		} else {
			sb.append(String.format("PUnit FAILED: %s%n", testName));
			sb.append(String.format("  Observed pass rate: %.1f%% (%d/%d) < min pass rate: %.1f%%%n",
					aggregator.getObservedPassRate() * 100,
					aggregator.getSuccesses(),
					aggregator.getSamplesExecuted(),
					config.minPassRate() * 100));
		}

		// Append provenance if configured
		appendProvenance(sb, config);

		reason.filter(r -> r != TerminationReason.COMPLETED)
				.ifPresent(r -> {
					sb.append(String.format("  Termination: %s%n", r.getDescription()));
					String details = aggregator.getTerminationDetails();
					if (details != null && !details.isEmpty()) {
						sb.append(String.format("  Details: %s%n", details));
					}
					// For IMPOSSIBILITY, show what was needed
					if (r == TerminationReason.IMPOSSIBILITY) {
						int required = (int) Math.ceil(config.samples() * config.minPassRate());
						int remaining = config.samples() - aggregator.getSamplesExecuted();
						int maxPossible = aggregator.getSuccesses() + remaining;
						sb.append(String.format("  Analysis: Needed %d successes, maximum possible is %d%n",
								required, maxPossible));
					}
				});

		sb.append(String.format("  Elapsed: %dms%n", aggregator.getElapsedMs()));
		sb.append("═══════════════════════════════════════════════════════════════\n");

		// Print to stdout for visibility
		System.out.println(sb);

		// Print expiration warning if applicable
		printExpirationWarning(context, config.hasTransparentStats());
	}

	/**
	 * Prints an expiration warning if the baseline is expired or expiring.
	 *
	 * <p>Warning visibility is controlled by {@link WarningLevel}:
	 * <ul>
	 *   <li>Expired: Always shown</li>
	 *   <li>Expiring imminently: Shown at normal verbosity</li>
	 *   <li>Expiring soon: Shown only at verbose (transparentStats) level</li>
	 * </ul>
	 */
	private void printExpirationWarning(ExtensionContext context, boolean verbose) {
		ExecutionSpecification spec = getSpec(context);
		if (spec == null) {
			return;
		}

		ExpirationStatus status = ExpirationEvaluator.evaluate(spec);
		if (!status.requiresWarning()) {
			return;
		}

		WarningLevel level = WarningLevel.forStatus(status);
		if (level == null || !level.shouldShow(verbose)) {
			return;
		}

		String warning = ExpirationWarningRenderer.render(spec, status);
		if (!warning.isEmpty()) {
			System.out.println(warning);
		}
	}

	/**
	 * Appends provenance information to the verdict output if configured.
	 *
	 * <p>Provenance lines are added in order: thresholdOrigin, then contractRef.
	 * Lines are only added if the respective value is set (not UNSPECIFIED/empty).
	 */
	private void appendProvenance(StringBuilder sb, TestConfiguration config) {
		if (config.hasThresholdOrigin()) {
			sb.append(String.format("  Threshold origin: %s%n", config.thresholdOrigin().name()));
		}
		if (config.hasContractRef()) {
			sb.append(String.format("  Contract ref: %s%n", config.contractRef()));
		}
	}

	/**
	 * Prints a comprehensive statistical explanation for transparent stats mode.
	 *
	 * <p>This method generates and renders a detailed statistical analysis of the
	 * test verdict, suitable for auditors, stakeholders, and educational purposes.
	 */
	private void printTransparentStatsSummary(
			ExtensionContext context,
			String testName,
			SampleResultAggregator aggregator,
			TestConfiguration config,
			boolean passed) {

		StatisticalExplanationBuilder builder = new StatisticalExplanationBuilder();
		
		// Build the explanation based on whether we have statistical context
		StatisticalExplanation explanation;
		String thresholdOriginName = config.thresholdOrigin() != null ? config.thresholdOrigin().name() : "UNSPECIFIED";
		if (config.hasStatisticalContext()) {
			// Convert spec data to BaselineData (avoiding dependency on spec package from statistics)
			BaselineData baseline = loadBaselineData(config.specId());
			
			explanation = builder.build(
					testName,
					aggregator.getSamplesExecuted(),
					aggregator.getSuccesses(),
					baseline,
					config.minPassRate(),
					passed,
				config.confidence() != null ? config.confidence() : 0.95,
					thresholdOriginName,
				config.contractRef()
		);
	} else {
		// Inline threshold mode (no baseline spec)
		explanation = builder.buildWithInlineThreshold(
				testName,
				aggregator.getSamplesExecuted(),
				aggregator.getSuccesses(),
				config.minPassRate(),
				passed,
				thresholdOriginName,
				config.contractRef()
		);
		}

		// Render and print
		ConsoleExplanationRenderer renderer = new ConsoleExplanationRenderer(config.transparentStats());
		String rendered = renderer.render(explanation);
		System.out.println(rendered);

		// Print expiration warning (verbose=true for transparent stats mode)
		printExpirationWarning(context, true);
	}

	/**
	 * Loads baseline data from a spec for transparent stats reporting.
	 */
	private BaselineData loadBaselineData(String specId) {
		if (specId == null) {
			return BaselineData.empty();
		}
		
		return configResolver.loadSpec(specId)
				.map(spec -> new BaselineData(
						specId + ".yaml",
						spec.getEmpiricalBasis() != null 
								? spec.getEmpiricalBasis().generatedAt() 
								: spec.getGeneratedAt(),
						spec.getBaselineSamples(),
						spec.getBaselineSuccesses()
				))
				.orElse(BaselineData.empty());
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

	/**
	 * Loads a baseline with covariate-aware selection.
	 *
	 * <p>If the use case has covariates declared:
	 * <ol>
	 *   <li>Extract covariate declaration from use case class</li>
	 *   <li>Compute footprint for the test</li>
	 *   <li>Find baseline candidates with matching footprint</li>
	 *   <li>Resolve the test's current covariate profile</li>
	 *   <li>Select the best-matching baseline</li>
	 *   <li>Store both the spec and selection result</li>
	 * </ol>
	 *
	 * <p>If no covariates are declared, falls back to simple spec loading.
	 *
	 * @param annotation the test annotation
	 * @param specId the resolved spec ID (may be null)
	 * @param store the extension context store
	 */
	private void loadBaselineWithCovariateSelection(
			ProbabilisticTest annotation,
			String specId,
			ExtensionContext.Store store) {

		if (specId == null) {
			return;
		}

		// Get the use case class from annotation
		Class<?> useCaseClass = annotation.useCase();
		if (useCaseClass == null || useCaseClass == Void.class) {
			// No use case - fall back to simple loading
			configResolver.loadSpec(specId).ifPresent(spec -> store.put(SPEC_KEY, spec));
			return;
		}

		// Extract covariate declaration
		CovariateDeclaration declaration = covariateExtractor.extractDeclaration(useCaseClass);

		if (declaration.isEmpty()) {
			// No covariates declared - fall back to simple loading
			configResolver.loadSpec(specId).ifPresent(spec -> store.put(SPEC_KEY, spec));
			return;
		}

		// Compute the test's footprint
		// For probabilistic tests, we don't have factor source at this point, so we compute
		// footprint based solely on covariate declaration (empty factors)
		String footprint = footprintComputer.computeFootprint(specId, declaration);

		// Find baseline candidates with matching footprint
		List<BaselineCandidate> candidates = baselineRepository.findCandidates(specId, footprint);

		if (candidates.isEmpty()) {
			// No baselines with matching footprint
			List<String> availableFootprints = baselineRepository.findAvailableFootprints(specId);
			throw new NoCompatibleBaselineException(specId, footprint, availableFootprints);
		}

		// Resolve the test's current covariate profile (using current time as test execution time)
		DefaultCovariateResolutionContext resolutionContext = DefaultCovariateResolutionContext.forNow();
		CovariateProfile testProfile = covariateProfileResolver.resolve(declaration, resolutionContext);

		// Select the best-matching baseline
		SelectionResult result = baselineSelector.select(candidates, testProfile);

		if (!result.hasSelection()) {
			// This shouldn't happen if candidates is non-empty, but handle defensively
			List<String> availableFootprints = baselineRepository.findAvailableFootprints(specId);
			throw new NoCompatibleBaselineException(specId, footprint, availableFootprints);
		}

		// Store the selected spec and selection result
		store.put(SPEC_KEY, result.selected().spec());
		store.put(SELECTION_RESULT_KEY, result);

		// Log warning if non-conforming covariates exist
		if (result.hasNonConformance()) {
			logCovariateNonConformanceWarning(result, specId);
		}
	}

	/**
	 * Logs a warning about covariate non-conformance.
	 */
	private void logCovariateNonConformanceWarning(SelectionResult result, String specId) {
		var nonConforming = result.nonConformingDetails();
		if (nonConforming.isEmpty()) {
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("\n┌─ COVARIATE NON-CONFORMANCE WARNING ─────────────────────────────────────┐\n");
		sb.append("│ Baseline: ").append(result.selected().filename()).append("\n");
		sb.append("│ Use case: ").append(specId).append("\n");
		sb.append("├──────────────────────────────────────────────────────────────────────────┤\n");
		sb.append("│ The following covariates do not match the baseline:                      \n");

		for (var detail : nonConforming) {
			sb.append("│   • ").append(detail.covariateKey()).append(":\n");
			sb.append("│       baseline: ").append(detail.baselineValue().toCanonicalString()).append("\n");
			sb.append("│       test:     ").append(detail.testValue().toCanonicalString()).append("\n");
		}

		sb.append("├──────────────────────────────────────────────────────────────────────────┤\n");
		sb.append("│ Statistical comparison may be less reliable when covariates differ.      \n");
		sb.append("│ Consider running a new MEASURE experiment under current conditions.      \n");
		sb.append("└──────────────────────────────────────────────────────────────────────────┘\n");

		System.err.println(sb);
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
			System.err.println("Pacing delay interrupted");
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
			int maxExampleFailures,
			// Statistical context for failure messages (null for legacy/spec-less mode)
			Double confidence,
			Double baselineRate,
			Integer baselineSamples,
			String specId,
			// Pacing configuration
			PacingConfiguration pacing,
		// Transparent stats configuration
		TransparentStatsConfig transparentStats,
		// Provenance metadata
		ThresholdOrigin thresholdOrigin,
		String contractRef
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

		boolean hasPacing() {
			return pacing != null && pacing.hasPacing();
		}

		boolean hasStatisticalContext() {
			return confidence != null && baselineRate != null && baselineSamples != null && specId != null;
		}

		boolean hasTransparentStats() {
			return transparentStats != null && transparentStats.enabled();
		}

		/**
		 * Returns true if any provenance information is specified.
		 */
		boolean hasProvenance() {
			return hasThresholdOrigin() || hasContractRef();
		}

		/**
		 * Returns true if thresholdOrigin is specified (not UNSPECIFIED).
		 */
		boolean hasThresholdOrigin() {
			return thresholdOrigin != null && thresholdOrigin != ThresholdOrigin.UNSPECIFIED;
		}

		/**
		 * Returns true if contractRef is specified (not null or empty).
		 */
		boolean hasContractRef() {
			return contractRef != null && !contractRef.isEmpty();
		}

		/**
		 * Builds the statistical context for failure messages.
		 */
		PunitFailureMessages.StatisticalContext buildStatisticalContext(
				double observedRate, int successes, int samplesExecuted) {
			if (hasStatisticalContext()) {
				return new PunitFailureMessages.StatisticalContext(
						confidence,
						observedRate,
						successes,
						samplesExecuted,
						minPassRate,
						baselineRate,
						baselineSamples,
						specId
				);
			} else {
				return PunitFailureMessages.StatisticalContext.forLegacyMode(
						observedRate,
						successes,
						samplesExecuted,
						minPassRate
				);
			}
		}
	}

	/**
	 * Invocation context for a single sample execution.
	 * Provides ParameterResolver for TokenChargeRecorder injection.
	 */
	private record ProbabilisticTestInvocationContext(
			int sampleIndex,
			int totalSamples,
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

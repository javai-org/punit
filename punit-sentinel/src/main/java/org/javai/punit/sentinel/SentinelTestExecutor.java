package org.javai.punit.sentinel;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.TestIntent;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.ptest.bernoulli.EarlyTerminationEvaluator;
import org.javai.punit.ptest.bernoulli.FinalVerdictDecider;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;
import org.javai.punit.ptest.engine.ConfigurationResolver;
import org.javai.punit.ptest.engine.ConfigurationResolver.ResolvedConfiguration;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.statistics.DerivedThreshold;
import org.javai.punit.statistics.ThresholdDeriver;
import org.javai.punit.usecase.UseCaseFactory;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdictBuilder;

/**
 * Executes a single {@code @ProbabilisticTest} method in the Sentinel runtime,
 * producing a {@link ProbabilisticTestVerdict}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Configuration resolution (annotation → spec → system properties)</li>
 *   <li>Threshold derivation from baseline specs</li>
 *   <li>Budget monitor creation</li>
 *   <li>Delegation to {@link SentinelSampleExecutor} for the N-sample loop</li>
 *   <li>Final verdict decision and report entry construction</li>
 * </ul>
 *
 * <p>Package-private: constructed and used exclusively by {@link SentinelRunner}.
 */
class SentinelTestExecutor {

    private static final Logger logger = LogManager.getLogger(SentinelTestExecutor.class);

    private final SentinelSampleExecutor sampleExecutor;
    private final SentinelClassIntrospector introspector;
    private final ConfigurationResolver configResolver;
    private final ThresholdDeriver thresholdDeriver;
    private final FinalVerdictDecider verdictDecider;
    private final EnvironmentMetadata environmentMetadata;
    private SentinelProgressListener progressListener;

    SentinelTestExecutor(
            SentinelSampleExecutor sampleExecutor,
            SentinelClassIntrospector introspector,
            ConfigurationResolver configResolver,
            ThresholdDeriver thresholdDeriver,
            FinalVerdictDecider verdictDecider,
            EnvironmentMetadata environmentMetadata) {
        this.sampleExecutor = sampleExecutor;
        this.introspector = introspector;
        this.configResolver = configResolver;
        this.thresholdDeriver = thresholdDeriver;
        this.verdictDecider = verdictDecider;
        this.environmentMetadata = environmentMetadata;
    }

    void setProgressListener(SentinelProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Executes a single probabilistic test method and returns the verdict.
     *
     * @param method the test method
     * @param annotation the method's {@code @ProbabilisticTest} annotation
     * @param instance the sentinel class instance
     * @param factory the use case factory
     * @param sentinelClass the sentinel class (for naming and input resolution)
     * @param useCaseId the resolved use case ID
     * @return the probabilistic test verdict
     */
    ProbabilisticTestVerdict execute(
            Method method,
            ProbabilisticTest annotation,
            Object instance,
            UseCaseFactory factory,
            Class<?> sentinelClass,
            Optional<String> useCaseId) {

        String testName = sentinelClass.getSimpleName() + "." + method.getName();
        logger.info("Executing test: {}", testName);

        ResolvedConfiguration config = configResolver.resolve(annotation, testName);
        double minPassRate = resolveMinPassRate(config);
        List<Object> inputs = introspector.resolveInputs(method, sentinelClass);

        if (progressListener != null) {
            progressListener.onMethodStart(testName, config.samples());
        }

        SampleResultAggregator aggregator = new SampleResultAggregator(
                config.samples(), config.maxExampleFailures());
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(
                config.samples(), minPassRate);
        CostBudgetMonitor budgetMonitor = createBudgetMonitor(config);

        sampleExecutor.executeTestLoop(
                method, instance, factory, annotation.useCase(),
                inputs, config.samples(), aggregator, evaluator,
                budgetMonitor, config.onException(), progressListener);

        boolean passed = verdictDecider.isPassing(aggregator, minPassRate);

        if (progressListener != null) {
            progressListener.onTestComplete(testName, passed);
        }

        return buildVerdict(
                sentinelClass, method, useCaseId, config, aggregator,
                minPassRate, passed, budgetMonitor);
    }

    /**
     * Resolves the use case ID from a {@code @ProbabilisticTest} annotation.
     */
    Optional<String> resolveUseCaseId(ProbabilisticTest annotation) {
        Class<?> useCaseClass = annotation.useCase();
        if (useCaseClass != null && useCaseClass != Void.class) {
            return Optional.of(UseCaseFactory.resolveId(useCaseClass));
        }
        return Optional.empty();
    }

    private double resolveMinPassRate(ResolvedConfiguration config) {
        if (!Double.isNaN(config.minPassRate())) {
            return config.minPassRate();
        }

        if (config.specId() != null) {
            Optional<ExecutionSpecification> specOpt = configResolver.loadSpec(config.specId());
            if (specOpt.isPresent()) {
                ExecutionSpecification spec = specOpt.get();
                if (spec.hasEmpiricalBasis()) {
                    ExecutionSpecification.EmpiricalBasis basis = spec.getEmpiricalBasis();
                    DerivedThreshold derived = thresholdDeriver.deriveSampleSizeFirst(
                            basis.samples(), basis.successes(),
                            config.samples(), config.resolvedConfidence());
                    return derived.value();
                }
            }
        }

        logger.warn("No minPassRate or baseline spec available, using 0.0");
        return 0.0;
    }

    private CostBudgetMonitor createBudgetMonitor(ResolvedConfiguration config) {
        if (!config.hasTimeBudget() && !config.hasTokenBudget()) {
            return null;
        }

        CostBudgetMonitor.TokenMode tokenMode;
        if (config.hasStaticTokenCharge()) {
            tokenMode = CostBudgetMonitor.TokenMode.STATIC;
        } else if (config.hasTokenBudget()) {
            tokenMode = CostBudgetMonitor.TokenMode.DYNAMIC;
        } else {
            tokenMode = CostBudgetMonitor.TokenMode.NONE;
        }

        return new CostBudgetMonitor(
                config.timeBudgetMs(), config.tokenBudget(),
                config.tokenCharge(), tokenMode,
                config.onBudgetExhausted());
    }

    private ProbabilisticTestVerdict buildVerdict(
            Class<?> sentinelClass,
            Method method,
            Optional<String> useCaseId,
            ResolvedConfiguration config,
            SampleResultAggregator aggregator,
            double minPassRate,
            boolean passed,
            CostBudgetMonitor budgetMonitor) {

        ProbabilisticTestVerdictBuilder builder = new ProbabilisticTestVerdictBuilder()
                .identity(sentinelClass.getName(), method.getName(), useCaseId.orElse(null))
                .execution(
                        config.samples(),
                        aggregator.getSamplesExecuted(),
                        aggregator.getSuccesses(),
                        aggregator.getFailures(),
                        minPassRate,
                        aggregator.getObservedPassRate(),
                        aggregator.getElapsedMs())
                .intent(TestIntent.VERIFICATION, config.resolvedConfidence())
                .termination(
                        aggregator.getTerminationReason().orElse(TerminationReason.COMPLETED),
                        aggregator.getTerminationDetails())
                .junitPassed(passed)
                .passedStatistically(passed)
                .environmentMetadata(environmentMetadata.toMap());

        // Cost info
        if (budgetMonitor != null) {
            builder.cost(
                    budgetMonitor.getTokensConsumed(),
                    config.timeBudgetMs(),
                    config.tokenBudget(),
                    budgetMonitor.getTokenMode());
        }

        // Functional dimension
        if (aggregator.isFunctionalAsserted()) {
            builder.functionalDimension(
                    aggregator.functionalSuccesses().orElse(0),
                    aggregator.functionalFailures().orElse(0));
        }

        return builder.build();
    }
}

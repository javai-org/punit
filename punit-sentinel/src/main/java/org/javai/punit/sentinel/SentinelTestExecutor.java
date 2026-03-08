package org.javai.punit.sentinel;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.ptest.bernoulli.EarlyTerminationEvaluator;
import org.javai.punit.ptest.bernoulli.FinalVerdictDecider;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;
import org.javai.punit.ptest.engine.ConfigurationResolver;
import org.javai.punit.ptest.engine.ConfigurationResolver.ResolvedConfiguration;
import org.javai.punit.reporting.VerdictEvent;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.statistics.DerivedThreshold;
import org.javai.punit.statistics.ThresholdDeriver;
import org.javai.punit.usecase.UseCaseFactory;

/**
 * Executes a single {@code @ProbabilisticTest} method in the Sentinel runtime,
 * producing a {@link VerdictEvent}.
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

    /**
     * Executes a single probabilistic test method and returns the verdict.
     *
     * @param method the test method
     * @param annotation the method's {@code @ProbabilisticTest} annotation
     * @param instance the sentinel class instance
     * @param factory the use case factory
     * @param sentinelClass the sentinel class (for naming and input resolution)
     * @param useCaseId the resolved use case ID
     * @return the verdict event
     */
    VerdictEvent execute(
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

        SampleResultAggregator aggregator = new SampleResultAggregator(
                config.samples(), config.maxExampleFailures());
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(
                config.samples(), minPassRate);
        CostBudgetMonitor budgetMonitor = createBudgetMonitor(config);

        sampleExecutor.executeTestLoop(
                method, instance, factory, annotation.useCase(),
                inputs, config.samples(), aggregator, evaluator,
                budgetMonitor, config.onException());

        boolean passed = verdictDecider.isPassing(aggregator, minPassRate);
        Map<String, String> reportEntries = buildReportEntries(
                aggregator, config, minPassRate, passed);

        return new VerdictEvent(
                VerdictEvent.newCorrelationId(),
                testName,
                useCaseId.orElse(testName),
                passed,
                reportEntries,
                environmentMetadata.toMap(),
                Instant.now());
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

    private Map<String, String> buildReportEntries(
            SampleResultAggregator aggregator,
            ResolvedConfiguration config,
            double minPassRate,
            boolean passed) {

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("punit.samples", String.valueOf(config.samples()));
        entries.put("punit.samplesExecuted", String.valueOf(aggregator.getSamplesExecuted()));
        entries.put("punit.successes", String.valueOf(aggregator.getSuccesses()));
        entries.put("punit.failures", String.valueOf(aggregator.getFailures()));
        entries.put("punit.minPassRate", String.format("%.4f", minPassRate));
        entries.put("punit.observedPassRate", String.format("%.4f", aggregator.getObservedPassRate()));
        entries.put("punit.verdict", passed ? "PASS" : "FAIL");

        String terminationReason = aggregator.getTerminationReason()
                .map(Enum::name)
                .orElse(TerminationReason.COMPLETED.name());
        entries.put("punit.terminationReason", terminationReason);
        entries.put("punit.elapsedMs", String.valueOf(aggregator.getElapsedMs()));

        if (aggregator.isFunctionalAsserted()) {
            entries.put("punit.dimension.functional", "true");
            aggregator.functionalSuccesses().ifPresent(s ->
                    entries.put("punit.dimension.functional.successes", String.valueOf(s)));
            aggregator.functionalFailures().ifPresent(f ->
                    entries.put("punit.dimension.functional.failures", String.valueOf(f)));
        }
        if (aggregator.isLatencyAsserted()) {
            entries.put("punit.dimension.latency", "true");
            aggregator.latencySuccesses().ifPresent(s ->
                    entries.put("punit.dimension.latency.successes", String.valueOf(s)));
            aggregator.latencyFailures().ifPresent(f ->
                    entries.put("punit.dimension.latency.failures", String.valueOf(f)));
        }

        return entries;
    }
}

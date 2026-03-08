package org.javai.punit.sentinel;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.experiment.engine.EmpiricalBaselineGenerator;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.experiment.measure.MeasureOutputWriter;
import org.javai.punit.experiment.model.EmpiricalBaseline;
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
 * The Sentinel runtime engine — executes probabilistic tests and experiments
 * without JUnit, dispatching verdicts to configurable sinks.
 *
 * <p>The runner consumes {@code @Sentinel}-annotated reliability specification
 * classes directly. Each class is instantiated via its no-arg constructor, and
 * its {@link UseCaseFactory} field, test methods, and input sources are
 * discovered via reflection.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SentinelConfiguration config = SentinelConfiguration.builder()
 *     .sentinelClass(ShoppingBasketReliability.class)
 *     .verdictSink(new WebhookVerdictSink.Builder("https://alerts.example.com").build())
 *     .build();
 *
 * SentinelRunner runner = new SentinelRunner(config);
 * SentinelResult result = runner.runTests();
 *
 * if (!result.allPassed()) {
 *     System.exit(1);
 * }
 * }</pre>
 *
 * <h2>Lifecycle API</h2>
 * <ul>
 *   <li>{@link #runTests()} — execute all {@code @ProbabilisticTest} methods</li>
 *   <li>{@link #runTests(String)} — execute tests for a specific use case</li>
 *   <li>{@link #runExperiments()} — execute all {@code @MeasureExperiment} methods</li>
 * </ul>
 *
 * <p>All methods are synchronous. The caller manages scheduling.
 */
public class SentinelRunner {

    private static final Logger logger = LogManager.getLogger(SentinelRunner.class);

    private final SentinelConfiguration configuration;
    private final SentinelClassIntrospector introspector;
    private final SentinelSampleExecutor sampleExecutor;
    private final ConfigurationResolver configResolver;
    private final ThresholdDeriver thresholdDeriver;
    private final FinalVerdictDecider verdictDecider;

    public SentinelRunner(SentinelConfiguration configuration) {
        this.configuration = configuration;
        this.introspector = new SentinelClassIntrospector();
        this.sampleExecutor = new SentinelSampleExecutor();
        this.configResolver = new ConfigurationResolver(configuration.specRepository());
        this.thresholdDeriver = new ThresholdDeriver();
        this.verdictDecider = new FinalVerdictDecider();
    }

    /**
     * Executes all {@code @ProbabilisticTest} methods across all registered
     * {@code @Sentinel} classes.
     *
     * @return the aggregate result
     */
    public SentinelResult runTests() {
        return runTests(null);
    }

    /**
     * Executes {@code @ProbabilisticTest} methods for a specific use case.
     *
     * @param useCaseId the use case to run tests for, or {@code null} for all
     * @return the aggregate result
     */
    public SentinelResult runTests(String useCaseId) {
        Instant start = Instant.now();
        List<VerdictEvent> verdicts = new ArrayList<>();
        int passed = 0, failed = 0, skipped = 0;

        for (Class<?> sentinelClass : configuration.sentinelClasses()) {
            introspector.validate(sentinelClass);
            Object instance = introspector.instantiate(sentinelClass);
            UseCaseFactory factory = introspector.findUseCaseFactory(instance);
            List<Method> testMethods = introspector.findTestMethods(sentinelClass);

            for (Method method : testMethods) {
                ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);
                String methodUseCaseId = resolveUseCaseId(annotation);

                // Filter by use case if specified
                if (useCaseId != null && !useCaseId.equals(methodUseCaseId)) {
                    continue;
                }

                VerdictEvent verdict = executeTest(method, annotation, instance, factory,
                        sentinelClass, methodUseCaseId);
                verdicts.add(verdict);
                configuration.verdictSink().accept(verdict);

                if (verdict.passed()) {
                    passed++;
                } else {
                    failed++;
                }
            }
        }

        return new SentinelResult(
                passed + failed + skipped, passed, failed, skipped,
                List.copyOf(verdicts), Duration.between(start, Instant.now()));
    }

    /**
     * Executes all {@code @MeasureExperiment} methods across all registered
     * {@code @Sentinel} classes, producing baseline spec files.
     *
     * @return the aggregate result
     */
    public SentinelResult runExperiments() {
        Instant start = Instant.now();
        List<VerdictEvent> verdicts = new ArrayList<>();
        int passed = 0, failed = 0, skipped = 0;

        for (Class<?> sentinelClass : configuration.sentinelClasses()) {
            introspector.validate(sentinelClass);
            Object instance = introspector.instantiate(sentinelClass);
            UseCaseFactory factory = introspector.findUseCaseFactory(instance);
            List<Method> experimentMethods = introspector.findExperimentMethods(sentinelClass);

            for (Method method : experimentMethods) {
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);
                VerdictEvent verdict = executeExperiment(method, annotation, instance,
                        factory, sentinelClass);
                verdicts.add(verdict);
                configuration.verdictSink().accept(verdict);

                if (verdict.passed()) {
                    passed++;
                } else {
                    failed++;
                }
            }
        }

        return new SentinelResult(
                passed + failed + skipped, passed, failed, skipped,
                List.copyOf(verdicts), Duration.between(start, Instant.now()));
    }

    private VerdictEvent executeTest(
            Method method,
            ProbabilisticTest annotation,
            Object instance,
            UseCaseFactory factory,
            Class<?> sentinelClass,
            String useCaseId) {

        String testName = sentinelClass.getSimpleName() + "." + method.getName();
        logger.info("Executing test: {}", testName);

        // Resolve configuration
        ResolvedConfiguration config = configResolver.resolve(annotation, testName);

        // Resolve threshold from baseline if needed
        double minPassRate = resolveMinPassRate(config);

        // Resolve inputs
        List<Object> inputs = introspector.resolveInputs(method, sentinelClass);

        // Create execution components
        SampleResultAggregator aggregator = new SampleResultAggregator(
                config.samples(), config.maxExampleFailures());
        EarlyTerminationEvaluator evaluator = new EarlyTerminationEvaluator(
                config.samples(), minPassRate);
        CostBudgetMonitor budgetMonitor = createBudgetMonitor(config);

        // Execute the sample loop
        sampleExecutor.executeTestLoop(
                method, instance, factory, annotation.useCase(),
                inputs, config.samples(), aggregator, evaluator,
                budgetMonitor, config.onException());

        // Compute verdict
        boolean passed = verdictDecider.isPassing(aggregator, minPassRate);

        // Build report entries
        Map<String, String> reportEntries = buildTestReportEntries(
                aggregator, config, minPassRate, passed);

        return new VerdictEvent(
                VerdictEvent.newCorrelationId(),
                testName,
                useCaseId != null ? useCaseId : testName,
                passed,
                reportEntries,
                configuration.environmentMetadata().toMap(),
                Instant.now());
    }

    private VerdictEvent executeExperiment(
            Method method,
            MeasureExperiment annotation,
            Object instance,
            UseCaseFactory factory,
            Class<?> sentinelClass) {

        String experimentName = sentinelClass.getSimpleName() + "." + method.getName();
        Class<?> useCaseClass = annotation.useCase();
        String useCaseId = UseCaseFactory.resolveId(useCaseClass);
        logger.info("Executing experiment: {} (useCase: {})", experimentName, useCaseId);

        // Resolve inputs
        List<Object> inputs = introspector.resolveInputs(method, sentinelClass);
        int samples = annotation.samples();

        // Create budget monitor if configured
        CostBudgetMonitor budgetMonitor = null;
        if (annotation.timeBudgetMs() > 0 || annotation.tokenBudget() > 0) {
            budgetMonitor = new CostBudgetMonitor(
                    annotation.timeBudgetMs(), annotation.tokenBudget(),
                    0, CostBudgetMonitor.TokenMode.NONE,
                    BudgetExhaustedBehavior.FAIL);
        }

        // Execute experiment loop
        List<OutcomeCaptor> capturedOutcomes = new ArrayList<>();
        sampleExecutor.executeExperimentLoop(
                method, instance, factory, useCaseClass,
                inputs, samples, capturedOutcomes, budgetMonitor);

        // Build experiment result aggregator and generate spec
        boolean experimentPassed = generateExperimentSpec(
                capturedOutcomes, useCaseId, sentinelClass, method, annotation);

        // Build report entries
        Map<String, String> reportEntries = buildExperimentReportEntries(
                capturedOutcomes, useCaseId, experimentPassed);

        return new VerdictEvent(
                VerdictEvent.newCorrelationId(),
                experimentName,
                useCaseId,
                experimentPassed,
                reportEntries,
                configuration.environmentMetadata().toMap(),
                Instant.now());
    }

    private boolean generateExperimentSpec(
            List<OutcomeCaptor> capturedOutcomes,
            String useCaseId,
            Class<?> sentinelClass,
            Method method,
            MeasureExperiment annotation) {

        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(
                useCaseId, capturedOutcomes.size());

        for (OutcomeCaptor captor : capturedOutcomes) {
            if (captor.hasResult()) {
                UseCaseOutcome<?> outcome = captor.getContractOutcome();
                if (outcome.allPostconditionsSatisfied()) {
                    aggregator.recordSuccess(outcome);
                } else {
                    aggregator.recordFailure(outcome, "postcondition_failure");
                }
            } else if (captor.hasException()) {
                aggregator.recordException(captor.getException());
            }
        }

        // Generate baseline
        EmpiricalBaselineGenerator baselineGenerator = new EmpiricalBaselineGenerator();
        EmpiricalBaseline baseline = baselineGenerator.generate(
                aggregator, sentinelClass, method, null, annotation.expiresInDays());

        // Write spec files to the output directory
        try {
            Path specDir = resolveSpecOutputDir();
            Files.createDirectories(specDir);
            MeasureOutputWriter writer = new MeasureOutputWriter();

            // Write functional spec
            Path functionalPath = specDir.resolve(useCaseId.replace('.', '-') + ".yaml");
            writer.writeFunctional(baseline, functionalPath);
            logger.info("Functional spec written to {}", functionalPath);

            // Write latency spec when latency data is available
            if (baseline.hasLatencyDistribution()) {
                Path latencyPath = specDir.resolve(useCaseId.replace('.', '-') + ".latency.yaml");
                writer.writeLatency(baseline, latencyPath);
                logger.info("Latency spec written to {}", latencyPath);
            }

            return true;
        } catch (IOException e) {
            logger.error("Failed to write spec for {}: {}", useCaseId, e.getMessage(), e);
            return false;
        }
    }

    private Path resolveSpecOutputDir() {
        // Use punit.spec.dir system property or PUNIT_SPEC_DIR env var
        String dir = System.getProperty("punit.spec.dir");
        if (dir != null && !dir.isEmpty()) {
            return Paths.get(dir);
        }
        dir = System.getenv("PUNIT_SPEC_DIR");
        if (dir != null && !dir.isEmpty()) {
            return Paths.get(dir);
        }
        return Paths.get("punit/specs");
    }

    private double resolveMinPassRate(ResolvedConfiguration config) {
        if (!Double.isNaN(config.minPassRate())) {
            return config.minPassRate();
        }

        // Derive from baseline spec
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

        // No spec available — fail open with 0.0 (no requirement)
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

    private Map<String, String> buildTestReportEntries(
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

        // Per-dimension results
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

    private Map<String, String> buildExperimentReportEntries(
            List<OutcomeCaptor> capturedOutcomes,
            String useCaseId,
            boolean passed) {

        long successes = capturedOutcomes.stream().filter(OutcomeCaptor::hasResult).count();
        long failures = capturedOutcomes.size() - successes;

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("punit.experiment.useCaseId", useCaseId);
        entries.put("punit.experiment.samples", String.valueOf(capturedOutcomes.size()));
        entries.put("punit.experiment.successes", String.valueOf(successes));
        entries.put("punit.experiment.failures", String.valueOf(failures));
        entries.put("punit.experiment.verdict", passed ? "PASS" : "FAIL");
        return entries;
    }

    private String resolveUseCaseId(ProbabilisticTest annotation) {
        Class<?> useCaseClass = annotation.useCase();
        if (useCaseClass != null && useCaseClass != Void.class) {
            return UseCaseFactory.resolveId(useCaseClass);
        }
        return null;
    }
}

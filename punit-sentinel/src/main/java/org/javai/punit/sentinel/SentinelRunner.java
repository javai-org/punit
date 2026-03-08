package org.javai.punit.sentinel;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.ptest.bernoulli.FinalVerdictDecider;
import org.javai.punit.ptest.engine.ConfigurationResolver;
import org.javai.punit.reporting.VerdictEvent;
import org.javai.punit.statistics.ThresholdDeriver;
import org.javai.punit.usecase.UseCaseFactory;

/**
 * The Sentinel runtime engine — orchestrates probabilistic test and experiment
 * execution without JUnit, dispatching verdicts to configurable sinks.
 *
 * <p>The runner consumes {@code @Sentinel}-annotated reliability specification
 * classes directly. Each class is instantiated via its no-arg constructor, and
 * its {@link UseCaseFactory} field, test methods, and input sources are
 * discovered via reflection.
 *
 * <p>Test and experiment execution are delegated to {@link SentinelTestExecutor}
 * and {@link SentinelExperimentExecutor} respectively. This class is responsible
 * only for iterating sentinel classes, dispatching to the right executor,
 * collecting verdicts, and building the aggregate {@link SentinelResult}.
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

    private final SentinelConfiguration configuration;
    private final SentinelClassIntrospector introspector;
    private final SentinelTestExecutor testExecutor;
    private final SentinelExperimentExecutor experimentExecutor;

    public SentinelRunner(SentinelConfiguration configuration) {
        this.configuration = configuration;
        this.introspector = new SentinelClassIntrospector();

        SentinelSampleExecutor sampleExecutor = new SentinelSampleExecutor();
        EnvironmentMetadata metadata = configuration.environmentMetadata();

        this.testExecutor = new SentinelTestExecutor(
                sampleExecutor,
                introspector,
                new ConfigurationResolver(configuration.specRepository()),
                new ThresholdDeriver(),
                new FinalVerdictDecider(),
                metadata);

        this.experimentExecutor = new SentinelExperimentExecutor(
                sampleExecutor,
                introspector,
                metadata);
    }

    /**
     * Executes all {@code @ProbabilisticTest} methods across all registered
     * {@code @Sentinel} classes.
     *
     * @return the aggregate result
     */
    public SentinelResult runTests() {
        return executeTests(useCaseId -> true);
    }

    /**
     * Executes {@code @ProbabilisticTest} methods for a specific use case.
     *
     * @param useCaseId the use case to run tests for
     * @return the aggregate result
     * @throws IllegalArgumentException if useCaseId is null
     */
    public SentinelResult runTests(String useCaseId) {
        Objects.requireNonNull(useCaseId, "useCaseId must not be null; use runTests() for all");
        return executeTests(methodUseCaseId -> methodUseCaseId.map(useCaseId::equals).orElse(false));
    }

    private SentinelResult executeTests(Predicate<Optional<String>> filter) {
        Instant start = Instant.now();
        List<VerdictEvent> verdicts = new ArrayList<>();
        int passed = 0, failed = 0;

        for (Class<?> sentinelClass : configuration.sentinelClasses()) {
            introspector.validate(sentinelClass);
            Object instance = introspector.instantiate(sentinelClass);
            UseCaseFactory factory = introspector.findUseCaseFactory(instance);
            List<Method> testMethods = introspector.findTestMethods(sentinelClass);

            for (Method method : testMethods) {
                ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);
                Optional<String> methodUseCaseId = testExecutor.resolveUseCaseId(annotation);

                if (!filter.test(methodUseCaseId)) {
                    continue;
                }

                VerdictEvent verdict = testExecutor.execute(
                        method, annotation, instance, factory,
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
                passed + failed, passed, failed, 0,
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
        int passed = 0, failed = 0;

        for (Class<?> sentinelClass : configuration.sentinelClasses()) {
            introspector.validate(sentinelClass);
            Object instance = introspector.instantiate(sentinelClass);
            UseCaseFactory factory = introspector.findUseCaseFactory(instance);
            List<Method> experimentMethods = introspector.findExperimentMethods(sentinelClass);

            for (Method method : experimentMethods) {
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);
                VerdictEvent verdict = experimentExecutor.execute(
                        method, annotation, instance, factory, sentinelClass);
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
                passed + failed, passed, failed, 0,
                List.copyOf(verdicts), Duration.between(start, Instant.now()));
    }
}

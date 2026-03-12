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
import org.javai.punit.verdict.ProbabilisticTestVerdict;
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
    private SentinelProgressListener progressListener;

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
     * Sets a progress listener for verbose output during execution.
     *
     * @param listener the progress listener
     */
    public void setProgressListener(SentinelProgressListener listener) {
        this.progressListener = listener;
        this.testExecutor.setProgressListener(listener);
        this.experimentExecutor.setProgressListener(listener);
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
        List<ProbabilisticTestVerdict> verdicts = new ArrayList<>();
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

                ProbabilisticTestVerdict verdict = testExecutor.execute(
                        method, annotation, instance, factory,
                        sentinelClass, methodUseCaseId);
                verdicts.add(verdict);
                configuration.verdictSink().accept(verdict);

                if (verdict.junitPassed()) {
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
        return executeExperiments(useCaseId -> true);
    }

    /**
     * Executes {@code @MeasureExperiment} methods for a specific use case.
     *
     * @param useCaseId the use case to run experiments for
     * @return the aggregate result
     * @throws NullPointerException if useCaseId is null
     */
    public SentinelResult runExperiments(String useCaseId) {
        Objects.requireNonNull(useCaseId, "useCaseId must not be null; use runExperiments() for all");
        return executeExperiments(id -> id.equals(useCaseId));
    }

    private SentinelResult executeExperiments(Predicate<String> filter) {
        Instant start = Instant.now();
        List<ProbabilisticTestVerdict> verdicts = new ArrayList<>();
        int passed = 0, failed = 0;

        for (Class<?> sentinelClass : configuration.sentinelClasses()) {
            introspector.validate(sentinelClass);
            Object instance = introspector.instantiate(sentinelClass);
            UseCaseFactory factory = introspector.findUseCaseFactory(instance);
            List<Method> experimentMethods = introspector.findExperimentMethods(sentinelClass);

            for (Method method : experimentMethods) {
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);
                String useCaseId = UseCaseFactory.resolveId(annotation.useCase());

                if (!filter.test(useCaseId)) {
                    continue;
                }

                ProbabilisticTestVerdict verdict = experimentExecutor.execute(
                        method, annotation, instance, factory, sentinelClass);
                verdicts.add(verdict);
                configuration.verdictSink().accept(verdict);

                if (verdict.junitPassed()) {
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
     * Discovers all use cases across all registered sentinel classes,
     * grouped by type (test or experiment).
     *
     * <p>Each entry maps a use case ID to its source method name. A use case
     * may appear in both the test and experiment lists if it has both types.
     *
     * @return a record containing test and experiment use case maps
     */
    public UseCaseCatalog listUseCases() {
        List<UseCaseCatalog.Entry> entries = new ArrayList<>();

        for (Class<?> sentinelClass : configuration.sentinelClasses()) {
            String className = sentinelClass.getSimpleName();

            for (Method method : introspector.findTestMethods(sentinelClass)) {
                ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);
                Optional<String> useCaseId = testExecutor.resolveUseCaseId(annotation);
                useCaseId.ifPresent(id -> entries.add(new UseCaseCatalog.Entry(
                        "test", id, className + "." + method.getName(), annotation.samples())));
            }

            for (Method method : introspector.findExperimentMethods(sentinelClass)) {
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);
                String useCaseId = UseCaseFactory.resolveId(annotation.useCase());
                entries.add(new UseCaseCatalog.Entry(
                        "experiment", useCaseId, className + "." + method.getName(), annotation.samples()));
            }
        }

        return new UseCaseCatalog(List.copyOf(entries));
    }

    /**
     * Catalog of available use cases discovered from sentinel classes.
     *
     * @param entries the list of discovered use case entries
     */
    public record UseCaseCatalog(List<Entry> entries) {

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        /**
         * A single entry in the use case catalog.
         *
         * @param type "test" or "experiment"
         * @param useCaseId the use case identifier
         * @param name the class and method name
         * @param samples the configured sample count
         */
        public record Entry(String type, String useCaseId, String name, int samples) {}
    }
}

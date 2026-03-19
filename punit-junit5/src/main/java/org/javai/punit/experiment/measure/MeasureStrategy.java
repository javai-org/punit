package org.javai.punit.experiment.measure;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.javai.punit.api.ExperimentMode;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentModeStrategy;
import org.javai.punit.experiment.engine.ExperimentProgressReporter;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.experiment.engine.ExperimentWarmupHandler;
import org.javai.punit.experiment.engine.WarmupInvocationContext;
import org.javai.punit.experiment.engine.input.InputParameterDetector;
import org.javai.punit.experiment.engine.input.InputSourceResolver;
import org.javai.punit.experiment.engine.shared.FactorInfo;
import org.javai.punit.experiment.engine.shared.FactorResolver;
import org.javai.punit.experiment.engine.shared.ResultRecorder;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.usecase.UseCaseFactory;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Strategy for handling @MeasureExperiment.
 *
 * <p>MEASURE mode establishes reliable statistics for a single configuration
 * by running many samples (default 1000) and generating an empirical spec.
 */
public class MeasureStrategy implements ExperimentModeStrategy {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create("org.javai.punit.experiment");

    private final ExperimentWarmupHandler warmupHandler = new ExperimentWarmupHandler();

    @Override
    public boolean supports(Method testMethod) {
        return testMethod.isAnnotationPresent(MeasureExperiment.class);
    }

    @Override
    public ExperimentConfig parseConfig(Method testMethod) {
        MeasureExperiment annotation = testMethod.getAnnotation(MeasureExperiment.class);
        if (annotation == null) {
            throw new ExtensionConfigurationException(
                    "Method must be annotated with @MeasureExperiment");
        }

        Class<?> useCaseClass = annotation.useCase();
        String useCaseId = UseCaseFactory.resolveId(useCaseClass);
        UseCaseAttributes useCaseAttributes = UseCaseFactory.resolveAttributes(useCaseClass);

        return new MeasureConfig(
                useCaseClass,
                useCaseId,
                useCaseAttributes,
                annotation.samples(),
                annotation.timeBudgetMs(),
                annotation.tokenBudget(),
                annotation.experimentId(),
                annotation.expiresInDays()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Stream<TestTemplateInvocationContext> provideInvocationContexts(
            ExperimentConfig config,
            ExtensionContext context,
            ExtensionContext.Store store) {

        MeasureConfig measureConfig = (MeasureConfig) config;
        int samples = measureConfig.effectiveSamples();
        int warmup = measureConfig.warmup();
        String useCaseId = measureConfig.useCaseId();

        // Create aggregator
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(useCaseId, samples);
        store.put("aggregator", aggregator);

        AtomicBoolean terminated = new AtomicBoolean(false);
        AtomicInteger currentSample = new AtomicInteger(0);
        AtomicInteger warmupCounter = new AtomicInteger(0);
        store.put("terminated", terminated);
        store.put("currentSample", currentSample);
        store.put("warmupCounter", warmupCounter);
        store.put("mode", ExperimentMode.MEASURE);

        Method testMethod = context.getRequiredTestMethod();

        // Check for @InputSource annotation (preferred)
        InputSource inputSource = testMethod.getAnnotation(InputSource.class);
        if (inputSource != null) {
            return provideWithInputsInvocationContexts(
                    testMethod, inputSource, context.getRequiredTestClass(),
                    warmup, samples, useCaseId, store, terminated);
        }

        // Check for @FactorSource annotation (legacy)
        FactorSource factorSource = testMethod.getAnnotation(FactorSource.class);
        if (factorSource != null) {
            return provideWithFactorsInvocationContexts(
                    testMethod, factorSource, measureConfig.useCaseClass(),
                    warmup, samples, useCaseId, store, terminated);
        }

        // No input or factor source - simple sample stream (warmup + counted)
        int totalInvocations = warmup + samples;
        return Stream.iterate(1, i -> i + 1)
                .limit(totalInvocations)
                .takeWhile(i -> !terminated.get())
                .map(i -> {
                    if (i <= warmup) {
                        return (TestTemplateInvocationContext) WarmupInvocationContext.forExperiment(i, warmup);
                    }
                    int sampleIndex = i - warmup;
                    return (TestTemplateInvocationContext) new MeasureInvocationContext(
                            sampleIndex, samples, useCaseId, new OutcomeCaptor());
                });
    }

    private Stream<TestTemplateInvocationContext> provideWithInputsInvocationContexts(
            Method testMethod,
            InputSource inputSource,
            Class<?> testClass,
            int warmup,
            int samples,
            String useCaseId,
            ExtensionContext.Store store,
            AtomicBoolean terminated) {

        // Determine input type from method parameters
        Class<?> inputType = findInputParameterType(testMethod);

        // Resolve inputs
        InputSourceResolver resolver = new InputSourceResolver();
        List<Object> inputs = resolver.resolve(inputSource, testClass, inputType);

        if (inputs.isEmpty()) {
            throw new ExtensionConfigurationException(
                    "@InputSource resolved to empty list");
        }

        store.put("inputs", inputs);
        store.put("inputType", inputType);

        // Generate warmup + sample stream with cycling inputs
        int totalInvocations = warmup + samples;
        int totalInputs = inputs.size();
        Object firstInput = inputs.get(0);
        return Stream.iterate(1, i -> i + 1)
                .limit(totalInvocations)
                .takeWhile(i -> !terminated.get())
                .map(i -> {
                    if (i <= warmup) {
                        return (TestTemplateInvocationContext) WarmupInvocationContext.forExperimentWithInput(
                                i, warmup, firstInput, inputType);
                    }
                    int sampleIndex = i - warmup;
                    int inputIndex = (sampleIndex - 1) % totalInputs;
                    Object inputValue = inputs.get(inputIndex);
                    return (TestTemplateInvocationContext) new MeasureWithInputsInvocationContext(
                            sampleIndex, samples, useCaseId, new OutcomeCaptor(),
                            inputValue, inputType, inputIndex, totalInputs);
                });
    }

    private Class<?> findInputParameterType(Method method) {
        return InputParameterDetector.findInputParameterType(method);
    }

    @SuppressWarnings("unchecked")
    private Stream<TestTemplateInvocationContext> provideWithFactorsInvocationContexts(
            Method testMethod,
            FactorSource factorSource,
            Class<?> useCaseClass,
            int warmup,
            int samples,
            String useCaseId,
            ExtensionContext.Store store,
            AtomicBoolean terminated) {

        // Resolve factor stream (searches current class, then use case class)
        List<FactorArguments> factorsList = FactorResolver.resolveFactorArguments(
                testMethod, factorSource, useCaseClass);

        if (factorsList.isEmpty()) {
            throw new ExtensionConfigurationException(
                    "Factor source '" + factorSource.value() + "' returned no factors");
        }

        // Extract factor names from first FactorArguments
        List<FactorInfo> factorInfos = FactorResolver.extractFactorInfosFromArguments(
                testMethod, factorsList.get(0));
        store.put("factorInfos", factorInfos);

        // Generate warmup + sample stream with cycling factors
        int totalInvocations = warmup + samples;
        return Stream.iterate(1, i -> i + 1)
                .limit(totalInvocations)
                .takeWhile(i -> !terminated.get())
                .map(i -> {
                    if (i <= warmup) {
                        return (TestTemplateInvocationContext) WarmupInvocationContext.forExperiment(i, warmup);
                    }
                    int sampleIndex = i - warmup;
                    int factorIndex = (sampleIndex - 1) % factorsList.size();
                    FactorArguments args = factorsList.get(factorIndex);
                    Object[] factorValues = FactorResolver.extractFactorValues(args, factorInfos);
                    return (TestTemplateInvocationContext) new MeasureWithFactorsInvocationContext(
                            sampleIndex, samples, useCaseId, new OutcomeCaptor(), factorValues, factorInfos);
                });
    }

    @Override
    public void intercept(
            InvocationInterceptor.Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) throws Throwable {

        MeasureConfig config = (MeasureConfig) store.get("config", ExperimentConfig.class);
        ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
        AtomicBoolean terminated = store.get("terminated", AtomicBoolean.class);
        AtomicInteger currentSample = store.get("currentSample", AtomicInteger.class);
        Long startTimeMs = store.get("startTimeMs", Long.class);

        // 1. Warmup gate
        ExperimentWarmupHandler.WarmupResult warmupResult = warmupHandler.handle(
                invocation, store, config.warmup(), config.timeBudgetMs(), startTimeMs);
        if (warmupResult.handled()) {
            return;
        }

        int sample = currentSample.incrementAndGet();
        int effectiveSamples = config.effectiveSamples();

        // 2. Budget checks
        if (checkBudgets(config, aggregator, terminated, startTimeMs, invocation, extensionContext, store)) {
            return;
        }

        // 3. Execute sample
        ExtensionContext.Store invocationStore = extensionContext.getStore(NAMESPACE);
        OutcomeCaptor captor = invocationStore.get("captor", OutcomeCaptor.class);
        ResultRecorder.executeAndRecord(invocation, captor, aggregator);

        // 4. Progress + completion
        reportProgress(extensionContext, aggregator, sample, effectiveSamples);
        handleSampleCompletion(sample, effectiveSamples, terminated, aggregator, extensionContext, store);
    }

    /**
     * Checks time and token budgets, terminating the experiment if either is exceeded.
     *
     * @return true if a budget was exhausted and the invocation was skipped
     */
    boolean checkBudgets(
            MeasureConfig config,
            ExperimentResultAggregator aggregator,
            AtomicBoolean terminated,
            Long startTimeMs,
            InvocationInterceptor.Invocation<Void> invocation,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) {

        // Check time budget
        if (config.timeBudgetMs() > 0) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            if (elapsed >= config.timeBudgetMs()) {
                terminated.set(true);
                aggregator.setTerminated("TIME_BUDGET_EXHAUSTED",
                        "Time budget of " + config.timeBudgetMs() + "ms exceeded");
                invocation.skip();
                generateSpecIfNeeded(extensionContext, store);
                return true;
            }
        }

        // Check token budget
        if (config.tokenBudget() > 0 && aggregator.getTotalTokens() >= config.tokenBudget()) {
            terminated.set(true);
            aggregator.setTerminated("TOKEN_BUDGET_EXHAUSTED",
                    "Token budget of " + config.tokenBudget() + " exceeded");
            invocation.skip();
            generateSpecIfNeeded(extensionContext, store);
            return true;
        }

        return false;
    }

    /**
     * Handles sample completion: marks the aggregator as completed when all samples
     * have run, and triggers spec generation.
     */
    void handleSampleCompletion(
            int sample,
            int effectiveSamples,
            AtomicBoolean terminated,
            ExperimentResultAggregator aggregator,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) {

        if (sample >= effectiveSamples || terminated.get()) {
            if (!terminated.get()) {
                aggregator.setCompleted();
            }
            generateSpecIfNeeded(extensionContext, store);
        }
    }

    @Override
    public int computeTotalSamples(ExperimentConfig config, Method testMethod) {
        MeasureConfig measureConfig = (MeasureConfig) config;
        return measureConfig.effectiveSamples();
    }

    private void reportProgress(ExtensionContext context, ExperimentResultAggregator aggregator,
                                int currentSample, int totalSamples) {
        ExperimentProgressReporter.reportProgress(
                context, "MEASURE", currentSample, totalSamples,
                aggregator.getObservedSuccessRate());
    }

    private void generateSpecIfNeeded(ExtensionContext context, ExtensionContext.Store store) {
        AtomicBoolean specGenerated = store.getOrComputeIfAbsent(
                "specGenerated",
                key -> new AtomicBoolean(false),
                AtomicBoolean.class
        );

        if (specGenerated.compareAndSet(false, true)) {
            MeasureSpecGenerator generator = new MeasureSpecGenerator();
            generator.generateSpec(context, store);
        }
    }
}

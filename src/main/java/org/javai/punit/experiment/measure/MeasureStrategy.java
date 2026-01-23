package org.javai.punit.experiment.measure;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.javai.punit.api.ExperimentMode;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentModeStrategy;
import org.javai.punit.experiment.engine.ExperimentProgressReporter;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.experiment.engine.shared.FactorInfo;
import org.javai.punit.experiment.engine.shared.FactorResolver;
import org.javai.punit.experiment.engine.shared.ResultRecorder;
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
        String useCaseId = UseCaseProvider.resolveId(useCaseClass);

        return new MeasureConfig(
                useCaseClass,
                useCaseId,
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
        String useCaseId = measureConfig.useCaseId();

        // Create aggregator
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(useCaseId, samples);
        store.put("aggregator", aggregator);

        AtomicBoolean terminated = new AtomicBoolean(false);
        AtomicInteger currentSample = new AtomicInteger(0);
        store.put("terminated", terminated);
        store.put("currentSample", currentSample);
        store.put("mode", ExperimentMode.MEASURE);

        // Check for @FactorSource annotation
        Method testMethod = context.getRequiredTestMethod();
        FactorSource factorSource = testMethod.getAnnotation(FactorSource.class);

        if (factorSource != null) {
            return provideWithFactorsInvocationContexts(
                    testMethod, factorSource, measureConfig.useCaseClass(),
                    samples, useCaseId, store, terminated);
        }

        // No factor source - simple sample stream
        return Stream.iterate(1, i -> i + 1)
                .limit(samples)
                .takeWhile(i -> !terminated.get())
                .map(i -> new MeasureInvocationContext(i, samples, useCaseId, new OutcomeCaptor()));
    }

    @SuppressWarnings("unchecked")
    private Stream<TestTemplateInvocationContext> provideWithFactorsInvocationContexts(
            Method testMethod,
            FactorSource factorSource,
            Class<?> useCaseClass,
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

        // Generate sample stream with cycling factors
        return Stream.iterate(1, i -> i + 1)
                .limit(samples)
                .takeWhile(i -> !terminated.get())
                .map(i -> {
                    int factorIndex = (i - 1) % factorsList.size();
                    FactorArguments args = factorsList.get(factorIndex);
                    Object[] factorValues = FactorResolver.extractFactorValues(args, factorInfos);
                    return new MeasureWithFactorsInvocationContext(
                            i, samples, useCaseId, new OutcomeCaptor(), factorValues, factorInfos);
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

        int sample = currentSample.incrementAndGet();
        int effectiveSamples = config.effectiveSamples();

        // Check time budget
        if (config.timeBudgetMs() > 0) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            if (elapsed >= config.timeBudgetMs()) {
                terminated.set(true);
                aggregator.setTerminated("TIME_BUDGET_EXHAUSTED",
                        "Time budget of " + config.timeBudgetMs() + "ms exceeded");
                invocation.skip();
                generateSpecIfNeeded(extensionContext, store);
                return;
            }
        }

        // Check token budget
        if (config.tokenBudget() > 0 && aggregator.getTotalTokens() >= config.tokenBudget()) {
            terminated.set(true);
            aggregator.setTerminated("TOKEN_BUDGET_EXHAUSTED",
                    "Token budget of " + config.tokenBudget() + " exceeded");
            invocation.skip();
            generateSpecIfNeeded(extensionContext, store);
            return;
        }

        // Get captor from invocation context store
        ExtensionContext.Store invocationStore = extensionContext.getStore(NAMESPACE);
        OutcomeCaptor captor = invocationStore.get("captor", OutcomeCaptor.class);

        try {
            invocation.proceed();
            ResultRecorder.recordResult(captor, aggregator);
        } catch (Throwable e) {
            aggregator.recordException(e);
        }

        // Report progress
        reportProgress(extensionContext, aggregator, sample, effectiveSamples);

        // Check if this is the last sample
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

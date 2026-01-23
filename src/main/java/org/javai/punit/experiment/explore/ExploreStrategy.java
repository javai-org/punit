package org.javai.punit.experiment.explore;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.javai.punit.api.DiffableContentProvider;
import org.javai.punit.api.ExperimentMode;
import org.javai.punit.api.ExploreExperiment;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentModeStrategy;
import org.javai.punit.experiment.engine.ExperimentProgressReporter;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.experiment.engine.ResultProjectionBuilder;
import org.javai.punit.experiment.engine.shared.FactorInfo;
import org.javai.punit.experiment.engine.shared.FactorResolver;
import org.javai.punit.experiment.engine.shared.ResultRecorder;
import org.javai.punit.experiment.measure.MeasureInvocationContext;
import org.javai.punit.experiment.model.ResultProjection;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Strategy for handling @ExploreExperiment.
 *
 * <p>EXPLORE mode compares multiple configurations to understand factor effects.
 * It runs a small number of samples per configuration (default 1) and generates
 * separate spec files for each configuration, enabling comparison via diff.
 */
public class ExploreStrategy implements ExperimentModeStrategy {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create("org.javai.punit.experiment");

    @Override
    public boolean supports(Method testMethod) {
        return testMethod.isAnnotationPresent(ExploreExperiment.class);
    }

    @Override
    public ExperimentConfig parseConfig(Method testMethod) {
        ExploreExperiment annotation = testMethod.getAnnotation(ExploreExperiment.class);
        if (annotation == null) {
            throw new ExtensionConfigurationException(
                    "Method must be annotated with @ExploreExperiment");
        }

        Class<?> useCaseClass = annotation.useCase();
        String useCaseId = UseCaseProvider.resolveId(useCaseClass);

        return new ExploreConfig(
                useCaseClass,
                useCaseId,
                annotation.samplesPerConfig(),
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

        ExploreConfig exploreConfig = (ExploreConfig) config;
        int samplesPerConfig = exploreConfig.effectiveSamplesPerConfig();
        String useCaseId = exploreConfig.useCaseId();

        Method testMethod = context.getRequiredTestMethod();
        FactorSource factorSource = testMethod.getAnnotation(FactorSource.class);

        if (factorSource == null) {
            // Warn: EXPLORE without factors is equivalent to MEASURE
            context.publishReportEntry("punit.warning",
                    "EXPLORE without @FactorSource is equivalent to MEASURE. " +
                            "Consider adding @FactorSource or using @MeasureExperiment.");

            return provideSimpleInvocationContexts(exploreConfig, store);
        }

        // Resolve factor combinations (searches current class, then use case class)
        List<FactorArguments> argsList = FactorResolver.resolveFactorArguments(
                testMethod, factorSource, exploreConfig.useCaseClass());
        List<FactorInfo> factorInfos = FactorResolver.extractFactorInfos(testMethod, factorSource, argsList);

        // Store explore-mode metadata
        store.put("mode", ExperimentMode.EXPLORE);
        store.put("factorInfos", factorInfos);
        store.put("configAggregators", new LinkedHashMap<String, ExperimentResultAggregator>());
        store.put("terminated", new AtomicBoolean(false));

        // Generate invocation contexts for all configs × samples
        List<ExploreInvocationContext> invocations = new ArrayList<>();

        for (int configIndex = 0; configIndex < argsList.size(); configIndex++) {
            FactorArguments args = argsList.get(configIndex);
            Object[] factorValues = args.get();

            String configName = FactorResolver.buildConfigName(factorInfos, factorValues);

            ExperimentResultAggregator configAggregator =
                    new ExperimentResultAggregator(useCaseId + "/" + configName, samplesPerConfig);
            ((Map<String, ExperimentResultAggregator>) store.get("configAggregators", Map.class))
                    .put(configName, configAggregator);

            for (int sample = 1; sample <= samplesPerConfig; sample++) {
                invocations.add(new ExploreInvocationContext(
                        sample, samplesPerConfig, configIndex + 1, argsList.size(),
                        useCaseId, configName, factorValues, factorInfos, new OutcomeCaptor()
                ));
            }
        }

        return invocations.stream().map(c -> (TestTemplateInvocationContext) c);
    }

    private Stream<TestTemplateInvocationContext> provideSimpleInvocationContexts(
            ExploreConfig config, ExtensionContext.Store store) {

        int samplesPerConfig = config.effectiveSamplesPerConfig();
        String useCaseId = config.useCaseId();

        store.put("mode", ExperimentMode.EXPLORE);
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(useCaseId, samplesPerConfig);
        store.put("aggregator", aggregator);
        store.put("terminated", new AtomicBoolean(false));

        return Stream.iterate(1, i -> i + 1)
                .limit(samplesPerConfig)
                .map(i -> new MeasureInvocationContext(
                        i, samplesPerConfig, useCaseId, new OutcomeCaptor()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void intercept(
            InvocationInterceptor.Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) throws Throwable {

        ExploreConfig config = (ExploreConfig) store.get("config", ExperimentConfig.class);
        Map<String, ExperimentResultAggregator> configAggregators =
                store.get("configAggregators", Map.class);
        List<FactorInfo> factorInfos = store.get("factorInfos", List.class);

        int samplesPerConfig = config.effectiveSamplesPerConfig();

        // Get explore invocation context info from extension context store
        ExtensionContext.Store invocationStore = extensionContext.getStore(NAMESPACE);
        OutcomeCaptor captor = invocationStore.get("captor", OutcomeCaptor.class);
        String configName = invocationStore.get("configName", String.class);
        Integer sampleInConfig = invocationStore.get("sampleInConfig", Integer.class);
        Object[] factorValues = invocationStore.get("factorValues", Object[].class);

        // Handle simple mode (no factors)
        if (configAggregators == null) {
            ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
            try {
                invocation.proceed();
                ResultRecorder.recordResult(captor, aggregator);
            } catch (Throwable e) {
                aggregator.recordException(e);
            }
            return;
        }

        ExperimentResultAggregator aggregator = configAggregators.get(configName);
        if (aggregator == null) {
            throw new ExtensionConfigurationException(
                    "No aggregator found for configuration: " + configName);
        }

        // Set factor values on the UseCaseProvider
        Optional<UseCaseProvider> providerOpt = findUseCaseProvider(extensionContext);
        providerOpt.ifPresent(provider -> {
            if (factorValues != null && factorInfos != null) {
                List<String> factorNames = factorInfos.stream()
                        .map(FactorInfo::name)
                        .toList();
                provider.setCurrentFactorValues(factorValues, factorNames);
            }
        });

        // Get use case class for projection settings
        Class<?> useCaseClass = config.useCaseClass();
        ResultProjectionBuilder projectionBuilder = createProjectionBuilder(useCaseClass, providerOpt.orElse(null));

        try {
            invocation.proceed();
            ResultRecorder.recordResult(captor, aggregator);

            // Build result projection
            if (captor != null && captor.hasResult()) {
                ResultProjection projection = projectionBuilder.build(
                        sampleInConfig - 1,
                        captor.getContractOutcome()
                );
                aggregator.addResultProjection(projection);
            }
        } catch (Throwable e) {
            aggregator.recordException(e);

            Long startTimeMs = store.get("startTimeMs", Long.class);
            ResultProjection projection = projectionBuilder.buildError(
                    sampleInConfig - 1,
                    System.currentTimeMillis() - startTimeMs,
                    e
            );
            aggregator.addResultProjection(projection);
        }

        // Report progress for this config
        ExperimentProgressReporter.reportProgressWithConfig(
                extensionContext, "EXPLORE", configName,
                sampleInConfig, samplesPerConfig,
                aggregator.getObservedSuccessRate());

        // Check if this is the last sample for this config
        if (sampleInConfig >= samplesPerConfig) {
            aggregator.setCompleted();
            ExploreSpecGenerator generator = new ExploreSpecGenerator();
            generator.generateSpec(extensionContext, store, configName, aggregator);
        }
    }

    @Override
    public int computeTotalSamples(ExperimentConfig config, Method testMethod) {
        ExploreConfig exploreConfig = (ExploreConfig) config;
        int samplesPerConfig = exploreConfig.effectiveSamplesPerConfig();

        FactorSource factorSource = testMethod.getAnnotation(FactorSource.class);
        if (factorSource == null) {
            return samplesPerConfig;
        }

        List<FactorArguments> argsList = FactorResolver.resolveFactorArguments(
                testMethod, factorSource, exploreConfig.useCaseClass());
        return samplesPerConfig * argsList.size();
    }

    private ResultProjectionBuilder createProjectionBuilder(Class<?> useCaseClass, UseCaseProvider provider) {
        int maxDiffableLines = 5;
        int maxLineLength = 60;
        DiffableContentProvider customProvider = null;

        if (useCaseClass != null && useCaseClass != Void.class) {
            UseCase annotation = useCaseClass.getAnnotation(UseCase.class);
            if (annotation != null) {
                maxDiffableLines = annotation.maxDiffableLines();
                maxLineLength = annotation.diffableContentMaxLineLength();
            }

            if (provider != null) {
                Object instance = provider.getCurrentInstance(useCaseClass);
                if (instance instanceof DiffableContentProvider dcp) {
                    customProvider = dcp;
                }
            }
        }

        return new ResultProjectionBuilder(maxDiffableLines, maxLineLength, customProvider);
    }

    private Optional<UseCaseProvider> findUseCaseProvider(ExtensionContext context) {
        Object testInstance = context.getRequiredTestInstance();
        for (java.lang.reflect.Field field : testInstance.getClass().getDeclaredFields()) {
            if (UseCaseProvider.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                try {
                    return Optional.of((UseCaseProvider) field.get(testInstance));
                } catch (IllegalAccessException e) {
                    // Continue searching
                }
            }
        }
        return Optional.empty();
    }
}

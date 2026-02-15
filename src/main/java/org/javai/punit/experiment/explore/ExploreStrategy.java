package org.javai.punit.experiment.explore;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.javai.punit.api.ExperimentMode;
import org.javai.punit.api.ExploreExperiment;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentModeStrategy;
import org.javai.punit.experiment.engine.ExperimentProgressReporter;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.experiment.engine.ResultProjectionBuilder;
import org.javai.punit.experiment.engine.input.InputParameterDetector;
import org.javai.punit.experiment.engine.input.InputSourceResolver;
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

        // Check for @InputSource annotation (preferred)
        InputSource inputSource = testMethod.getAnnotation(InputSource.class);
        if (inputSource != null) {
            return provideWithInputsInvocationContexts(
                    testMethod, inputSource, context.getRequiredTestClass(),
                    exploreConfig, store);
        }

        // Check for @FactorSource annotation (legacy)
        FactorSource factorSource = testMethod.getAnnotation(FactorSource.class);
        if (factorSource == null) {
            // Warn: EXPLORE without inputs or factors is equivalent to MEASURE
            context.publishReportEntry("punit.warning",
                    "EXPLORE without @InputSource or @FactorSource is equivalent to MEASURE. " +
                            "Consider adding @InputSource, @FactorSource, or using @MeasureExperiment.");

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

    @SuppressWarnings("unchecked")
    private Stream<TestTemplateInvocationContext> provideWithInputsInvocationContexts(
            Method testMethod,
            InputSource inputSource,
            Class<?> testClass,
            ExploreConfig config,
            ExtensionContext.Store store) {

        int samplesPerConfig = config.effectiveSamplesPerConfig();
        String useCaseId = config.useCaseId();

        // Determine input type from method parameters
        Class<?> inputType = findInputParameterType(testMethod);

        // Resolve inputs
        InputSourceResolver resolver = new InputSourceResolver();
        List<Object> inputs = resolver.resolve(inputSource, testClass, inputType);

        if (inputs.isEmpty()) {
            throw new ExtensionConfigurationException(
                    "@InputSource resolved to empty list");
        }

        // Store explore-mode metadata
        store.put("mode", ExperimentMode.EXPLORE);
        store.put("inputs", inputs);
        store.put("inputType", inputType);
        store.put("configAggregators", new LinkedHashMap<String, ExperimentResultAggregator>());
        store.put("terminated", new AtomicBoolean(false));

        // Generate invocation contexts for all inputs × samples
        List<ExploreWithInputsInvocationContext> invocations = new ArrayList<>();

        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            Object inputValue = inputs.get(inputIndex);
            String configName = buildInputConfigName(inputValue, inputIndex);

            ExperimentResultAggregator configAggregator =
                    new ExperimentResultAggregator(useCaseId + "/" + configName, samplesPerConfig);
            ((Map<String, ExperimentResultAggregator>) store.get("configAggregators", Map.class))
                    .put(configName, configAggregator);

            for (int sample = 1; sample <= samplesPerConfig; sample++) {
                invocations.add(new ExploreWithInputsInvocationContext(
                        sample, samplesPerConfig, inputIndex, inputs.size(),
                        useCaseId, configName, inputValue, inputType, new OutcomeCaptor()
                ));
            }
        }

        return invocations.stream().map(c -> (TestTemplateInvocationContext) c);
    }

    /**
     * Finds the input parameter type from method parameters.
     */
    private Class<?> findInputParameterType(Method method) {
        return InputParameterDetector.findInputParameterType(method);
    }

    /**
     * Builds a configuration name from an input value.
     */
    private String buildInputConfigName(Object inputValue, int index) {
        if (inputValue == null) {
            return "input-" + (index + 1);
        }
        String str = inputValue.toString();
        // Truncate and sanitize for use as config name
        if (str.length() > 40) {
            str = str.substring(0, 37) + "...";
        }
        // Replace characters that might be problematic in file names
        str = str.replaceAll("[^a-zA-Z0-9_-]", "_");
        return "input-" + (index + 1) + "-" + str;
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

        ResultProjectionBuilder projectionBuilder = new ResultProjectionBuilder();

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
                    null,  // Input not available when error occurs before outcome creation
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

        // Check for @InputSource first
        InputSource inputSource = testMethod.getAnnotation(InputSource.class);
        if (inputSource != null) {
            Class<?> inputType = findInputParameterType(testMethod);
            InputSourceResolver resolver = new InputSourceResolver();
            List<Object> inputs = resolver.resolve(inputSource, testMethod.getDeclaringClass(), inputType);
            return samplesPerConfig * inputs.size();
        }

        // Check for @FactorSource
        FactorSource factorSource = testMethod.getAnnotation(FactorSource.class);
        if (factorSource == null) {
            return samplesPerConfig;
        }

        List<FactorArguments> argsList = FactorResolver.resolveFactorArguments(
                testMethod, factorSource, exploreConfig.useCaseClass());
        return samplesPerConfig * argsList.size();
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

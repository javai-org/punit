package org.javai.punit.experiment.explore;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.javai.punit.api.ConfigSource;
import org.javai.punit.api.ExperimentMode;
import org.javai.punit.api.ExploreExperiment;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.NamedConfig;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentModeStrategy;
import org.javai.punit.experiment.engine.ExperimentProgressReporter;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.experiment.engine.ExperimentWarmupHandler;
import org.javai.punit.experiment.engine.ResultProjectionBuilder;
import org.javai.punit.experiment.engine.WarmupInvocationContext;
import org.javai.punit.experiment.engine.input.InputParameterDetector;
import org.javai.punit.experiment.engine.input.InputSourceResolver;
import org.javai.punit.experiment.engine.shared.FactorInfo;
import org.javai.punit.experiment.engine.shared.FactorResolver;
import org.javai.punit.experiment.engine.shared.ResultRecorder;
import org.javai.punit.experiment.measure.MeasureInvocationContext;
import org.javai.punit.experiment.measure.MeasureWithInputsInvocationContext;
import org.javai.punit.experiment.model.ResultProjection;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.usecase.UseCaseFactory;
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

    private final ExperimentWarmupHandler warmupHandler = new ExperimentWarmupHandler();

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
        String useCaseId = UseCaseFactory.resolveId(useCaseClass);
        UseCaseAttributes useCaseAttributes = UseCaseFactory.resolveAttributes(useCaseClass);
        if (annotation.skipWarmup()) {
            useCaseAttributes = new UseCaseAttributes(0, useCaseAttributes.maxConcurrent());
        }

        return new ExploreConfig(
                useCaseClass,
                useCaseId,
                useCaseAttributes,
                annotation.samplesPerConfig(),
                annotation.timeBudgetMs(),
                annotation.tokenBudget(),
                annotation.experimentId()
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

        // Check for both @ConfigSource and @InputSource together
        InputSource inputSource = testMethod.getAnnotation(InputSource.class);
        ConfigSource configSource = testMethod.getAnnotation(ConfigSource.class);

        if (configSource != null && inputSource != null) {
            return provideWithConfigsAndInputsInvocationContexts(
                    testMethod, configSource, inputSource, context.getRequiredTestClass(),
                    exploreConfig, store);
        }

        // Check for @InputSource annotation only
        if (inputSource != null) {
            return provideWithInputsInvocationContexts(
                    testMethod, inputSource, context.getRequiredTestClass(),
                    exploreConfig, store);
        }

        // Check for @ConfigSource annotation only
        if (configSource != null) {
            return provideWithConfigsInvocationContexts(
                    testMethod, configSource, exploreConfig, store);
        }

        // Check for @FactorSource annotation (legacy) TODO: ditch this when we realise the deprecation of factors
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
        store.put("warmupCounter", new AtomicInteger(0));

        // Prepend warmup contexts if configured
        int warmup = exploreConfig.warmup();
        List<TestTemplateInvocationContext> allInvocations = new ArrayList<>();
        for (int w = 1; w <= warmup; w++) {
            allInvocations.add(WarmupInvocationContext.forExperiment(w, warmup));
        }

        // Generate invocation contexts for all configs × samples
        for (int configIndex = 0; configIndex < argsList.size(); configIndex++) {
            FactorArguments args = argsList.get(configIndex);
            Object[] factorValues = args.get();

            String configName = FactorResolver.buildConfigName(factorInfos, factorValues);

            ExperimentResultAggregator configAggregator =
                    new ExperimentResultAggregator(useCaseId + "/" + configName, samplesPerConfig);
            ((Map<String, ExperimentResultAggregator>) store.get("configAggregators", Map.class))
                    .put(configName, configAggregator);

            for (int sample = 1; sample <= samplesPerConfig; sample++) {
                allInvocations.add(new ExploreInvocationContext(
                        sample, samplesPerConfig, configIndex + 1, argsList.size(),
                        useCaseId, configName, factorValues, factorInfos, new OutcomeCaptor()
                ));
            }
        }

        return allInvocations.stream();
    }

    private Stream<TestTemplateInvocationContext> provideSimpleInvocationContexts(
            ExploreConfig config, ExtensionContext.Store store) {

        int samplesPerConfig = config.effectiveSamplesPerConfig();
        String useCaseId = config.useCaseId();

        int warmup = config.warmup();
        int totalInvocations = warmup + samplesPerConfig;
        store.put("mode", ExperimentMode.EXPLORE);
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(useCaseId, samplesPerConfig);
        store.put("aggregator", aggregator);
        store.put("terminated", new AtomicBoolean(false));
        store.put("warmupCounter", new AtomicInteger(0));

        return Stream.iterate(1, i -> i + 1)
                .limit(totalInvocations)
                .map(i -> {
                    if (i <= warmup) {
                        return (TestTemplateInvocationContext) WarmupInvocationContext.forExperiment(i, warmup);
                    }
                    int sampleIndex = i - warmup;
                    return (TestTemplateInvocationContext) new MeasureInvocationContext(
                            sampleIndex, samplesPerConfig, useCaseId, new OutcomeCaptor());
                });
    }

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

        int totalSamples = samplesPerConfig * inputs.size();

        int warmup = config.warmup();

        // Store explore-mode metadata with single aggregator (round-robin, not per-config)
        store.put("mode", ExperimentMode.EXPLORE);
        store.put("inputs", inputs);
        store.put("inputType", inputType);
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(useCaseId, totalSamples);
        store.put("aggregator", aggregator);
        store.put("totalSamples", totalSamples);
        store.put("currentSample", new AtomicInteger(0));
        store.put("terminated", new AtomicBoolean(false));
        store.put("warmupCounter", new AtomicInteger(0));

        // Generate warmup + round-robin sample stream
        int totalInvocations = warmup + totalSamples;
        int totalInputs = inputs.size();
        Object firstInput = inputs.get(0);
        return Stream.iterate(1, i -> i + 1)
                .limit(totalInvocations)
                .map(i -> {
                    if (i <= warmup) {
                        return (TestTemplateInvocationContext) WarmupInvocationContext.forExperimentWithInput(
                                i, warmup, firstInput, inputType);
                    }
                    int sampleIndex = i - warmup;
                    int inputIndex = (sampleIndex - 1) % totalInputs;
                    Object inputValue = inputs.get(inputIndex);
                    return (TestTemplateInvocationContext) new MeasureWithInputsInvocationContext(
                            sampleIndex, totalSamples, useCaseId, new OutcomeCaptor(),
                            inputValue, inputType, inputIndex, totalInputs);
                });
    }

    @SuppressWarnings("unchecked")
    private Stream<TestTemplateInvocationContext> provideWithConfigsInvocationContexts(
            Method testMethod,
            ConfigSource configSource,
            ExploreConfig config,
            ExtensionContext.Store store) {

        int samplesPerConfig = config.effectiveSamplesPerConfig();
        String useCaseId = config.useCaseId();

        List<NamedConfig<?>> configs = resolveNamedConfigs(
                testMethod, configSource, config.useCaseClass());

        if (configs.isEmpty()) {
            throw new ExtensionConfigurationException(
                    "@ConfigSource resolved to empty list");
        }

        // Store explore-mode metadata (reuses the same aggregator structure as factor mode)
        store.put("mode", ExperimentMode.EXPLORE);
        store.put("configAggregators", new LinkedHashMap<String, ExperimentResultAggregator>());
        store.put("terminated", new AtomicBoolean(false));
        store.put("warmupCounter", new AtomicInteger(0));

        // Prepend warmup contexts if configured
        int warmup = config.warmup();
        List<TestTemplateInvocationContext> allInvocations = new ArrayList<>();
        for (int w = 1; w <= warmup; w++) {
            allInvocations.add(WarmupInvocationContext.forExperiment(w, warmup));
        }

        // Generate invocation contexts for all configs x samples
        for (int configIndex = 0; configIndex < configs.size(); configIndex++) {
            NamedConfig<?> namedConfig = configs.get(configIndex);
            String configName = namedConfig.name();

            ExperimentResultAggregator configAggregator =
                    new ExperimentResultAggregator(useCaseId + "/" + configName, samplesPerConfig);
            ((Map<String, ExperimentResultAggregator>) store.get("configAggregators", Map.class))
                    .put(configName, configAggregator);

            for (int sample = 1; sample <= samplesPerConfig; sample++) {
                allInvocations.add(new ConfigInvocationContext(
                        sample, samplesPerConfig, configIndex + 1, configs.size(),
                        useCaseId, configName, namedConfig.instance(), new OutcomeCaptor()
                ));
            }
        }

        return allInvocations.stream();
    }

    @SuppressWarnings("unchecked")
    private Stream<TestTemplateInvocationContext> provideWithConfigsAndInputsInvocationContexts(
            Method testMethod,
            ConfigSource configSource,
            InputSource inputSource,
            Class<?> testClass,
            ExploreConfig config,
            ExtensionContext.Store store) {

        int samplesPerConfig = config.effectiveSamplesPerConfig();
        String useCaseId = config.useCaseId();

        // Resolve configs
        List<NamedConfig<?>> configs = resolveNamedConfigs(
                testMethod, configSource, config.useCaseClass());
        if (configs.isEmpty()) {
            throw new ExtensionConfigurationException(
                    "@ConfigSource resolved to empty list");
        }

        // Resolve inputs
        Class<?> inputType = findInputParameterType(testMethod);
        InputSourceResolver resolver = new InputSourceResolver();
        List<Object> inputs = resolver.resolve(inputSource, testClass, inputType);
        if (inputs.isEmpty()) {
            throw new ExtensionConfigurationException(
                    "@InputSource resolved to empty list");
        }

        // Store explore-mode metadata (per-config aggregators, same as config-only mode)
        store.put("mode", ExperimentMode.EXPLORE);
        store.put("configAggregators", new LinkedHashMap<String, ExperimentResultAggregator>());
        store.put("terminated", new AtomicBoolean(false));
        store.put("warmupCounter", new AtomicInteger(0));

        // Prepend warmup contexts if configured
        int warmup = config.warmup();
        int totalInputs = inputs.size();
        List<TestTemplateInvocationContext> allInvocations = new ArrayList<>();
        for (int w = 1; w <= warmup; w++) {
            allInvocations.add(WarmupInvocationContext.forExperimentWithInput(
                    w, warmup, inputs.get(0), inputType));
        }

        // Generate invocation contexts for all configs × samples (with round-robin inputs)
        for (int configIndex = 0; configIndex < configs.size(); configIndex++) {
            NamedConfig<?> namedConfig = configs.get(configIndex);
            String configName = namedConfig.name();

            ExperimentResultAggregator configAggregator =
                    new ExperimentResultAggregator(useCaseId + "/" + configName, samplesPerConfig);
            ((Map<String, ExperimentResultAggregator>) store.get("configAggregators", Map.class))
                    .put(configName, configAggregator);

            for (int sample = 1; sample <= samplesPerConfig; sample++) {
                int inputIndex = (sample - 1) % totalInputs;
                Object inputValue = inputs.get(inputIndex);
                allInvocations.add(new ConfigWithInputsInvocationContext(
                        sample, samplesPerConfig, configIndex + 1, configs.size(),
                        useCaseId, configName, namedConfig.instance(), new OutcomeCaptor(),
                        inputValue, inputType, inputIndex, totalInputs
                ));
            }
        }

        return allInvocations.stream();
    }

    /**
     * Resolves named configs from a @ConfigSource method.
     */
    @SuppressWarnings("unchecked")
    private List<NamedConfig<?>> resolveNamedConfigs(
            Method testMethod, ConfigSource configSource, Class<?> useCaseClass) {

        String methodName = configSource.value();
        Class<?> currentClass = testMethod.getDeclaringClass();

        Method sourceMethod = findStaticMethod(methodName, currentClass);
        if (sourceMethod == null && useCaseClass != null && useCaseClass != currentClass) {
            sourceMethod = findStaticMethod(methodName, useCaseClass);
        }

        if (sourceMethod == null) {
            throw new ExtensionConfigurationException(
                    "Cannot find @ConfigSource method '" + methodName + "' in " +
                            currentClass.getName() +
                            (useCaseClass != null ? " or " + useCaseClass.getName() : ""));
        }

        try {
            sourceMethod.setAccessible(true);
            Object result = sourceMethod.invoke(null);

            if (result instanceof Stream) {
                return ((Stream<NamedConfig<?>>) result).toList();
            } else if (result instanceof java.util.Collection) {
                return new ArrayList<>((java.util.Collection<NamedConfig<?>>) result);
            }

            throw new ExtensionConfigurationException(
                    "@ConfigSource method must return Stream<NamedConfig> or Collection<NamedConfig>: " +
                            methodName);
        } catch (ExtensionConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "Cannot invoke @ConfigSource method '" + methodName + "': " + e.getMessage(), e);
        }
    }

    private Method findStaticMethod(String methodName, Class<?> clazz) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Finds the input parameter type from method parameters.
     */
    private Class<?> findInputParameterType(Method method) {
        return InputParameterDetector.findInputParameterType(method);
    }


    @Override
    @SuppressWarnings("unchecked")
    public void intercept(
            InvocationInterceptor.Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) throws Throwable {

        ExploreConfig config = (ExploreConfig) store.get("config", ExperimentConfig.class);

        // 1. Warmup gate
        ExperimentWarmupHandler.WarmupResult warmupResult = warmupHandler.handle(
                invocation, store, config.warmup(), 0, null);
        if (warmupResult.handled()) {
            return;
        }

        // 2. Unpack invocation-scoped state
        ExtensionContext.Store invocationStore = extensionContext.getStore(NAMESPACE);
        OutcomeCaptor captor = invocationStore.get("captor", OutcomeCaptor.class);
        String configName = invocationStore.get("configName", String.class);
        Integer sampleInConfig = invocationStore.get("sampleInConfig", Integer.class);
        Object[] factorValues = invocationStore.get("factorValues", Object[].class);

        Map<String, ExperimentResultAggregator> configAggregators =
                store.get("configAggregators", Map.class);
        List<FactorInfo> factorInfos = store.get("factorInfos", List.class);

        // 3. Dispatch to mode
        if (configAggregators == null) {
            ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
            Integer totalSamples = store.get("totalSamples", Integer.class);
            if (totalSamples == null) {
                interceptSimpleMode(invocation, extensionContext, aggregator, captor);
            } else {
                interceptInputSourceMode(invocation, extensionContext, store, aggregator, captor, totalSamples);
            }
        } else {
            ExperimentResultAggregator aggregator = configAggregators.get(configName);
            if (aggregator == null) {
                throw new ExtensionConfigurationException(
                        "No aggregator found for configuration: " + configName);
            }
            interceptFactorMode(invocation, extensionContext, store, config, aggregator, captor,
                    configName, sampleInConfig, factorValues, factorInfos);
        }
    }

    /**
     * Simple mode (no inputs, no factors) — just execute and record.
     */
    void interceptSimpleMode(
            InvocationInterceptor.Invocation<Void> invocation,
            ExtensionContext extensionContext,
            ExperimentResultAggregator aggregator,
            OutcomeCaptor captor) throws Throwable {

        ResultRecorder.executeAndRecord(invocation, captor, aggregator);
    }

    /**
     * InputSource round-robin mode: execute, record, and build projections.
     */
    void interceptInputSourceMode(
            InvocationInterceptor.Invocation<Void> invocation,
            ExtensionContext extensionContext,
            ExtensionContext.Store store,
            ExperimentResultAggregator aggregator,
            OutcomeCaptor captor,
            int totalSamples) throws Throwable {

        AtomicInteger currentSample = store.get("currentSample", AtomicInteger.class);
        int sample = currentSample.incrementAndGet();
        ResultProjectionBuilder projectionBuilder = new ResultProjectionBuilder();

        try {
            long startNanos = System.nanoTime();
            invocation.proceed();
            Duration wallClock = Duration.ofNanos(System.nanoTime() - startNanos);
            ResultRecorder.recordResult(captor, aggregator, wallClock);

            if (captor != null && captor.hasResult()) {
                ResultProjection projection = projectionBuilder.build(
                        sample - 1, captor.getContractOutcome());
                aggregator.addResultProjection(projection);
            }
        } catch (Throwable e) {
            aggregator.recordException(e);

            Long startTimeMs = store.get("startTimeMs", Long.class);
            long elapsed = startTimeMs != null ? System.currentTimeMillis() - startTimeMs : 0;
            ResultProjection projection = projectionBuilder.buildError(
                    sample - 1, null, elapsed, e);
            aggregator.addResultProjection(projection);
        }

        ExperimentProgressReporter.reportProgress(
                extensionContext, "EXPLORE", sample, totalSamples,
                aggregator.getObservedSuccessRate());

        if (sample >= totalSamples) {
            aggregator.setCompleted();
            ExploreSpecGenerator generator = new ExploreSpecGenerator();
            generator.generateSpec(extensionContext, store, aggregator);
        }
    }

    /**
     * Factor-based mode: set factor values, execute, record, and build projections per config.
     */
    void interceptFactorMode(
            InvocationInterceptor.Invocation<Void> invocation,
            ExtensionContext extensionContext,
            ExtensionContext.Store store,
            ExploreConfig config,
            ExperimentResultAggregator aggregator,
            OutcomeCaptor captor,
            String configName,
            Integer sampleInConfig,
            Object[] factorValues,
            List<FactorInfo> factorInfos) throws Throwable {

        int samplesPerConfig = config.effectiveSamplesPerConfig();

        // Set factor values on the UseCaseProvider
        Optional<UseCaseProvider> factoryOpt = findUseCaseFactory(extensionContext);
        factoryOpt.ifPresent(provider -> {
            if (factorValues != null && factorInfos != null) {
                List<String> factorNames = factorInfos.stream()
                        .map(FactorInfo::name)
                        .toList();
                provider.setCurrentFactorValues(factorValues, factorNames);
            }
        });

        ResultProjectionBuilder projectionBuilder = new ResultProjectionBuilder();

        try {
            long startNanos = System.nanoTime();
            invocation.proceed();
            Duration wallClock = Duration.ofNanos(System.nanoTime() - startNanos);
            ResultRecorder.recordResult(captor, aggregator, wallClock);

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

        InputSource inputSource = testMethod.getAnnotation(InputSource.class);
        ConfigSource configSource = testMethod.getAnnotation(ConfigSource.class);

        // Both @ConfigSource and @InputSource: configs × samplesPerConfig
        if (configSource != null && inputSource != null) {
            List<NamedConfig<?>> configs = resolveNamedConfigs(
                    testMethod, configSource, exploreConfig.useCaseClass());
            return samplesPerConfig * configs.size();
        }

        // @InputSource only: samplesPerConfig × inputs
        if (inputSource != null) {
            Class<?> inputType = findInputParameterType(testMethod);
            InputSourceResolver resolver = new InputSourceResolver();
            List<Object> inputs = resolver.resolve(inputSource, testMethod.getDeclaringClass(), inputType);
            return samplesPerConfig * inputs.size();
        }

        // @ConfigSource only: samplesPerConfig × configs
        if (configSource != null) {
            List<NamedConfig<?>> configs = resolveNamedConfigs(
                    testMethod, configSource, exploreConfig.useCaseClass());
            return samplesPerConfig * configs.size();
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

    private Optional<UseCaseProvider> findUseCaseFactory(ExtensionContext context) {
        Object testInstance = context.getRequiredTestInstance();
        Class<?> clazz = testInstance.getClass();
        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (field.isSynthetic()) continue;
                if (UseCaseProvider.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        return Optional.of((UseCaseProvider) field.get(testInstance));
                    } catch (IllegalAccessException e) {
                        // Continue searching
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return Optional.empty();
    }
}
